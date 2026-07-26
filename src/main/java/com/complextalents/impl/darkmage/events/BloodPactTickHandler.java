package com.complextalents.impl.darkmage.events;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.effect.DarkMageEffects;
import com.complextalents.impl.darkmage.manager.BloodOrbManager;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.impl.darkmage.skill.BloodPactSkill;
import com.complextalents.origin.OriginManager;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.capability.SkillDataProvider;
import com.complextalents.skill.event.SkillToggleTerminationEvent;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handler for Blood Pact ongoing effects.
 * Handles:
 * - Ramping Current HP drain (paused by Soul Stasis)
 * - Ramping Shadow Spell Power bonus
 * - Blood Magic: Damage Stagger (Magic damage converted to Bleed over 3s)
 * - Soul Wave Detonation when Blood Pact terminates
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class BloodPactTickHandler {

    private static final int TICK_INTERVAL = 2;
    private static final ConcurrentHashMap<UUID, Long> activationStartTick = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> totalActiveTicks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        long gameTime = server.getTickCount();

        if (gameTime % TICK_INTERVAL != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (Player player : level.players()) {
                if (!(player instanceof ServerPlayer serverPlayer)) continue;
                if (!serverPlayer.isAlive() || !DarkMageOrigin.isDarkMage(serverPlayer)) continue;

                UUID playerId = serverPlayer.getUUID();

                // Soul Stasis Mana Recovery: 5% Max Mana per second while Soul Stasis is active
                if (serverPlayer.hasEffect(DarkMageEffects.SOUL_STASIS.get())) {
                    restoreStasisMana(serverPlayer, TICK_INTERVAL);
                }

                if (!isBloodPactActive(serverPlayer)) {
                    activationStartTick.remove(playerId);
                    totalActiveTicks.remove(playerId);
                    com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_active", 0);
                    com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_ticks", 0);
                    com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_dmg", 0);
                    continue;
                }

                activationStartTick.putIfAbsent(playerId, gameTime);

                // Soul Stasis Check: If active, pause HP drain and ramp progress!
                boolean isStasisActive = serverPlayer.hasEffect(DarkMageEffects.SOUL_STASIS.get());

                if (!isStasisActive) {
                    long active = totalActiveTicks.getOrDefault(playerId, 0L) + TICK_INTERVAL;
                    totalActiveTicks.put(playerId, active);
                }

                long activeTicks = totalActiveTicks.getOrDefault(playerId, 0L);
                double activeSeconds = activeTicks / 20.0;

                // Ramp multiplier: scales up to 2.5x max over 30 seconds
                double rampMultiplier = Math.min(2.5, 1.0 + (activeSeconds / 30.0) * 1.5);
                double baseBonus = OriginManager.getOriginStat(serverPlayer, "bloodPactSpellPowerBonus");
                int currentDmgPct = (int) Math.round(baseBonus * rampMultiplier * 100.0);

                com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_active", 1);
                com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_ticks", (int) activeTicks);
                com.complextalents.passive.PassiveManager.setPassiveStacks(serverPlayer, "blood_pact_dmg", currentDmgPct);

                // Update Ramped Shadow Spell Power bonus
                if (gameTime % 10 == 0) {
                    BloodPactSkill.updateRampedSpellPower(serverPlayer, rampMultiplier);
                }

                // If Soul Stasis is active, skip HP drain!
                if (isStasisActive) {
                    if (gameTime % 10 == 0) {
                        spawnStasisParticles(serverPlayer.serverLevel(), serverPlayer);
                    }
                    continue;
                }

                // Drain Current HP (8%-4% base per second scaled by ramp)
                double baseDrainRate = OriginManager.getOriginStat(serverPlayer, "bloodPactHpDrainPercent");
                float currentHp = serverPlayer.getHealth();
                float hpToDrain = (float) (currentHp * baseDrainRate * rampMultiplier / 20.0 * TICK_INTERVAL);

                // Critical HP Check (1.0 HP minimum)
                if (currentHp - hpToDrain <= 1.0f) {
                    activationStartTick.remove(playerId);
                    totalActiveTicks.remove(playerId);

                    // Trigger Soul Wave Detonation before deactivating
                    BloodOrbManager.detonateOwnerOrbs(serverPlayer, 20.0);

                    MinecraftForge.EVENT_BUS.post(new SkillToggleTerminationEvent(
                            serverPlayer,
                            BloodPactSkill.ID,
                            SkillToggleTerminationEvent.TerminationReason.INSUFFICIENT_RESOURCE
                    ));
                    serverPlayer.sendSystemMessage(Component.literal("\u00A7cBlood Pact terminated - HP critical!"));
                    continue;
                }

                serverPlayer.setHealth(currentHp - hpToDrain);

                if (gameTime % 10 == 0) {
                    spawnBleedingParticles(serverPlayer.serverLevel(), serverPlayer);
                }
            }
        }
    }

    /**
     * Skill Toggle Termination Listener: Detonates owner's Soul Orbs when Blood Pact is manually toggled off.
     */
    @SubscribeEvent
    public static void onSkillToggleTermination(SkillToggleTerminationEvent event) {
        if (event.getSkillId().equals(BloodPactSkill.ID) && event.getPlayer() != null) {
            ServerPlayer player = event.getPlayer();
            activationStartTick.remove(player.getUUID());
            totalActiveTicks.remove(player.getUUID());

            // Trigger Soul Wave Detonation
            BloodOrbManager.detonateOwnerOrbs(player, 20.0);
        }
    }

    /**
     * Blood Magic Passive: Incoming magic/spell damage is staggered into a decaying 3s Bleed.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (isBloodPactActive(player) && (event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) || event.getSource().getMsgId().contains("magic") || event.getSource().getMsgId().contains("spell"))) {
            float incomingDamage = event.getAmount();
            if (incomingDamage <= 0) return;

            // Reduce instant damage by 70%, convert remaining 70% into 3s Bleed
            event.setAmount(incomingDamage * 0.3f);

            // Apply Bleed effect (duration 60 ticks = 3s)
            player.addEffect(new MobEffectInstance(DarkMageEffects.BLEED.get(), 60, (int) (incomingDamage * 0.7f), false, true));
            player.displayClientMessage(Component.literal("\u00A7c[Blood Magic] Staggered spell damage into Bleed!"), true);
        }
    }

    private static void spawnBleedingParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() / 2.0;
        double z = player.getZ();

        BlockParticleOption bloodSplatter = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(bloodSplatter, x, y, z, 8, 0.3, 0.4, 0.3, 0.1);

        DustParticleOptions bloodMist = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.0f), 1.2f);
        level.sendParticles(bloodMist, x, y, z, 5, 0.25, 0.35, 0.25, 0.03);
    }

    private static void spawnStasisParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() / 2.0;
        double z = player.getZ();

        DustParticleOptions stasisGlow = new DustParticleOptions(new Vector3f(0.8f, 0.1f, 0.5f), 1.5f);
        level.sendParticles(stasisGlow, x, y, z, 6, 0.3, 0.4, 0.3, 0.05);
    }

    public static boolean isBloodPactActive(ServerPlayer player) {
        IPlayerSkillData data = player.getCapability(SkillDataProvider.SKILL_DATA).orElse(null);
        if (data == null) return false;
        return data.isToggleActive(BloodPactSkill.ID);
    }

    private static void restoreStasisMana(ServerPlayer player, int tickInterval) {
        try {
            io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            double maxMana = player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            double manaGain = maxMana * (0.05 / 20.0) * tickInterval;
            magicData.setMana((float) Math.min(maxMana, magicData.getMana() + manaGain));
            io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player, new io.redspace.ironsspellbooks.network.SyncManaPacket(magicData));
        } catch (Throwable ignored) {}
    }
}
