package com.complextalents.impl.warrior;

import com.complextalents.epicfight.event.EpicFightGuardEvent;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.warrior.S2CWarriorParryPacket;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.util.UUIDHelper;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKeys;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.UUID;

/**
 * Handles Weapon-Path Perfect Parry mechanics for the Warrior origin.
 */
@Mod.EventBusSubscriber(modid = "complextalents")
public class WarriorOriginHandler {

    private static final UUID BLADEMASTER_PARRY_AD_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "blademaster_parry_ad");
    private static final UUID JUGGERNAUT_PARRY_AS_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "juggernaut_parry_as");
    private static final UUID BRAWLER_PARRY_SPEED_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "brawler_parry_speed");

    private static final java.util.Map<UUID, Long> LAST_COLOSSUS_PARRY_TIME = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> REAPER_CRIT_BUFF_EXPIRY = new java.util.HashMap<>();

    public record VanguardPoiseBuff(int expiryTick, double poisePct) {}
    private static final java.util.Map<UUID, VanguardPoiseBuff> VANGUARD_POISE_BUFF = new java.util.HashMap<>();

    public record TimedModifier(UUID playerUuid, Attribute attribute, UUID modifierUuid, int expireTick) {}
    private static final java.util.List<TimedModifier> ACTIVE_TIMED_MODIFIERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            // Clean up expired timed attribute modifiers
            for (TimedModifier tm : ACTIVE_TIMED_MODIFIERS) {
                if (player.getUUID().equals(tm.playerUuid()) && player.tickCount >= tm.expireTick()) {
                    var instance = player.getAttribute(tm.attribute());
                    if (instance != null) {
                        instance.removeModifier(tm.modifierUuid());
                    }
                    ACTIVE_TIMED_MODIFIERS.remove(tm);
                }
            }
        }
    }

    private static boolean isWarrior(ServerPlayer player) {
        ResourceLocation origin = OriginManager.getOriginId(player);
        return WarriorOrigin.ID.equals(origin);
    }

    @SubscribeEvent
    public static void onGuard(EpicFightGuardEvent event) {
        ServerPlayer player = event.getPlayer();
        if (isWarrior(player)) {
            ServerPlayerPatch playerpatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
            if (playerpatch != null) {
                float refund = event.getStaminaConsumed();
                if (event.isParry()) {
                    float currentStamina = playerpatch.getStamina();
                    playerpatch.resetActionTick();
                    playerpatch.setStamina(currentStamina + refund);

                    // Reset penalty on perfect parry
                    SkillContainer container = event.getContainer();
                    container.getDataManager().setDataSync(SkillDataKeys.PENALTY.get(), 0.0F);
                    container.getDataManager().setDataSync(SkillDataKeys.PENALTY_RESTORE_COUNTER.get(),
                            player.tickCount);

                    // Activate weapon path bonus ONLY on Perfect Parry
                    triggerWeaponPathParryBonus(player);
                }
            }
        }
    }

    private static void triggerWeaponPathParryBonus(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
        if (path == null) return;

        int originLevel = OriginManager.getOriginLevel(player);
        Origin origin = OriginRegistry.getInstance().getOrigin(WarriorOrigin.ID);
        if (origin == null) return;

        switch (path) {
            case BLADEMASTER -> {
                double adPct = origin.getScaledStat("blademaster_parry_ad", originLevel);
                applyTimedAttributeModifier(player, Attributes.ATTACK_DAMAGE, BLADEMASTER_PARRY_AD_UUID,
                        "Blademaster Parry AD", adPct, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);

                PacketHandler.sendTo(new S2CWarriorParryPacket("BLADEMASTER", "hud.complextalents.warrior.parry.blademaster", (int) (adPct * 100), 100, "⚔", 0xFFFFAA00), player);
            }
            case COLOSSUS -> {
                double cooldownSec = origin.getScaledStat("colossus_parry_cooldown", originLevel);
                double kbForce = origin.getScaledStat("colossus_parry_kb", originLevel);

                long now = System.currentTimeMillis();
                long lastTrigger = LAST_COLOSSUS_PARRY_TIME.getOrDefault(player.getUUID(), 0L);
                if (now - lastTrigger >= (long) (cooldownSec * 1000.0)) {
                    LAST_COLOSSUS_PARRY_TIME.put(player.getUUID(), now);
                    triggerColossusExplosion(player, originLevel, kbForce);

                    PacketHandler.sendTo(new S2CWarriorParryPacket("COLOSSUS", "hud.complextalents.warrior.parry.colossus", 0, 40, "💥", 0xFFFFFF55), player);
                }
            }
            case REAPER -> {
                double durSec = origin.getScaledStat("reaper_parry_crit_dur", originLevel);
                int durationTicks = (int) (durSec * 20.0);
                REAPER_CRIT_BUFF_EXPIRY.put(player.getUUID(), player.tickCount + durationTicks);

                PacketHandler.sendTo(new S2CWarriorParryPacket("REAPER", "hud.complextalents.warrior.parry.reaper", 0, durationTicks, "💀", 0xFFFF5555), player);
            }
            case JUGGERNAUT -> {
                double asPct = origin.getScaledStat("juggernaut_parry_as", originLevel);
                applyTimedAttributeModifier(player, Attributes.ATTACK_SPEED, JUGGERNAUT_PARRY_AS_UUID,
                        "Juggernaut Parry AS", asPct, AttributeModifier.Operation.MULTIPLY_BASE, 100);

                PacketHandler.sendTo(new S2CWarriorParryPacket("JUGGERNAUT", "hud.complextalents.warrior.parry.juggernaut", (int) (asPct * 100), 100, "⚡", 0xFF55FF55), player);
            }
            case VANGUARD -> {
                double poisePct = origin.getScaledStat("vanguard_parry_poise", originLevel);
                int durationTicks = 100; // 5.0 seconds
                VANGUARD_POISE_BUFF.put(player.getUUID(), new VanguardPoiseBuff(player.tickCount + durationTicks, poisePct));

                PacketHandler.sendTo(new S2CWarriorParryPacket("VANGUARD", "hud.complextalents.warrior.parry.vanguard", (int) (poisePct * 100), 100, "🛡", 0xFF5555FF), player);
            }
            case BRAWLER -> {
                double speedPct = origin.getScaledStat("brawler_parry_speed", originLevel);
                applyTimedAttributeModifier(player, Attributes.MOVEMENT_SPEED, BRAWLER_PARRY_SPEED_UUID,
                        "Brawler Parry Speed", speedPct, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);

                PacketHandler.sendTo(new S2CWarriorParryPacket("BRAWLER", "hud.complextalents.warrior.parry.brawler", (int) (speedPct * 100), 100, "👟", 0xFFFF55FF), player);
            }
        }
    }

    private static void applyTimedAttributeModifier(ServerPlayer player, Attribute attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation, int durationTicks) {
        if (attribute == null) return;
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
            instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            ACTIVE_TIMED_MODIFIERS.add(new TimedModifier(player.getUUID(), attribute, uuid, player.tickCount + durationTicks));
        }
    }

    private static void triggerColossusExplosion(ServerPlayer player, int originLevel, double kbForce) {
        Vec3 pos = player.position();
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.2);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);

            double radius = 4.0;
            AABB area = player.getBoundingBox().inflate(radius);
            float damageAmount = 5.0f + (float) originLevel * 4.0f;

            level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isAlliedTo(player)).forEach(entity -> {
                entity.hurt(player.damageSources().mobAttack(player), damageAmount);

                Vec3 dir = entity.position().subtract(pos);
                double dist = Math.max(0.1, dir.length());
                Vec3 normDir = dir.normalize();
                entity.knockback(kbForce, -normDir.x, -normDir.z);

                if (com.nhatbh.basedefensev2.api.PoiseAPI.hasPoise(entity)) {
                    com.nhatbh.basedefensev2.api.PoiseAPI.damagePoise(entity, damageAmount * 1.5f, 0.0f, player, player.damageSources().mobAttack(player), true, "ComplexTalents");
                }
            });
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isWarrior(player)) {
            Integer expiry = REAPER_CRIT_BUFF_EXPIRY.get(player.getUUID());
            if (expiry != null && player.tickCount <= expiry) {
                event.setResult(Event.Result.ALLOW);
                event.setDamageModifier(Math.max(1.5f, event.getDamageModifier()));
            }
        }
    }

    @SubscribeEvent
    public static void onPoiseDamage(com.nhatbh.basedefensev2.api.event.PoiseDamageEvent event) {
        if (event.getAttacker() instanceof ServerPlayer player && isWarrior(player)) {
            VanguardPoiseBuff buff = VANGUARD_POISE_BUFF.get(player.getUUID());
            if (buff != null && player.tickCount <= buff.expiryTick) {
                event.setAmount((float) (event.getAmount() * (1.0 + buff.poisePct)));
            }
        }
    }

    /**
     * When a player selects the Warrior origin, automatically grant them the
     * epicfight:guard skill.
     */
    @SubscribeEvent
    public static void onOriginChange(com.complextalents.origin.events.OriginChangeEvent event) {
        if (event.getChangeType() == com.complextalents.origin.events.OriginChangeEvent.ChangeType.SET
                && WarriorOrigin.ID.equals(event.getOriginId())) {
            ServerPlayer player = event.getPlayer();
            var server = player.getServer();
            if (server != null) {
                String cmd = "epicfight skill add " + player.getGameProfile().getName() + " guard epicfight:guard";
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), cmd);
            }
        }
    }
}
