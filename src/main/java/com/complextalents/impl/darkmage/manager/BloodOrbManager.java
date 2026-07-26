package com.complextalents.impl.darkmage.manager;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.effect.DarkMageEffects;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.darkmage.S2CRemoveBloodOrbPacket;
import com.complextalents.network.darkmage.S2CSyncBloodOrbPacket;
import com.complextalents.origin.OriginManager;
import com.complextalents.util.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class BloodOrbManager {

    private static final ConcurrentHashMap<ResourceKey<Level>, List<BloodOrbData>> ACTIVE_ORBS = new ConcurrentHashMap<>();

    public static void spawnOrb(ServerPlayer owner, Vec3 pos, double densityV) {
        UUID orbId = UUID.randomUUID();
        int lifetimeTicks = 300; // 15 seconds

        BloodOrbData data = new BloodOrbData(orbId, owner.getUUID(), pos, densityV, lifetimeTicks);

        ACTIVE_ORBS.computeIfAbsent(owner.level().dimension(), k -> new ArrayList<>()).add(data);

        syncSpawn(owner.serverLevel(), data);
    }

    private static void syncSpawn(ServerLevel level, BloodOrbData data) {
        BlockPos pos = BlockPos.containing(data.pos());
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new S2CSyncBloodOrbPacket(data.id(), data.pos(), data.densityV(), data.ownerUUID(), data.lifetimeTicks()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ACTIVE_ORBS.forEach((dim, orbs) -> {
            ServerLevel level = event.getServer().getLevel(dim);
            if (level == null) return;

            Iterator<BloodOrbData> it = orbs.iterator();
            while (it.hasNext()) {
                BloodOrbData orb = it.next();
                orb.tick();

                ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(orb.ownerUUID());

                // Owner Cleanup if disconnected or dead
                if (owner == null || !owner.isAlive()) {
                    removeOrb(level, orb, false);
                    it.remove();
                    continue;
                }

                // Proximity Harvest Check (<= 2.0 blocks)
                if (owner.position().distanceTo(orb.pos()) <= 2.0) {
                    harvestOrb(level, orb, owner);
                    it.remove();
                    continue;
                }

                // Expiration (15 seconds)
                if (orb.currentTick() >= orb.lifetimeTicks()) {
                    removeOrb(level, orb, false);
                    it.remove();
                }
            }
        });
    }

    /**
     * Proximity Harvest: Absorbs the Soul Orb.
     * While Blood Pact is active: grants Soul Stasis (0.75s per V) and reduced Mana recovery.
     * While Blood Pact is inactive: grants full Mana recovery (no Stasis).
     */
    private static void harvestOrb(ServerLevel level, BloodOrbData orb, ServerPlayer owner) {
        boolean isBloodPactActive = com.complextalents.impl.darkmage.events.BloodPactTickHandler.isBloodPactActive(owner);

        if (isBloodPactActive) {
            // Soul Stasis: active ONLY while Blood Pact is toggled ON (0.75s per V, NO base value)
            int addedTicks = (int) (orb.densityV() * 15);
            MobEffectInstance existingStasis = owner.getEffect(DarkMageEffects.SOUL_STASIS.get());
            int currentDuration = existingStasis != null ? existingStasis.getDuration() : 0;
            int newDuration = currentDuration + addedTicks;

            owner.addEffect(new MobEffectInstance(
                    DarkMageEffects.SOUL_STASIS.get(),
                    newDuration,
                    0,
                    false,
                    true,
                    true
            ));

            owner.displayClientMessage(Component.literal(
                    "\u00A7dSoul Harvested! \u00A77+" + String.format("%.1f", addedTicks / 20.0) + "s Soul Stasis (5%/s Mana)"
            ), true);
        } else {
            // Full Mana recovery outside Blood Pact (20.0 Mana per V, NO base value, NO Stasis)
            double manaRestored = 20.0 * orb.densityV();
            restoreMana(owner, manaRestored);

            owner.displayClientMessage(Component.literal(
                    "\u00A7dSoul Harvested! \u00A77+" + String.format("%.0f", manaRestored) + " Mana"
            ), true);
        }

        // Blood Healing: Harvest Heal % per Density V (NO base value, scales with Origin Level)
        double healPctPerV = OriginManager.getOriginStat(owner, "harvestHealPercent");
        float totalHeal = (float) (owner.getMaxHealth() * (healPctPerV * orb.densityV()));
        if (totalHeal > 0) {
            owner.heal(totalHeal);
        }

        // Harvest Frenzy Surge: Boosts Iron's Spellbooks Cast Speed (CAST_TIME_REDUCTION)
        int originLevel = com.complextalents.origin.OriginManager.getOriginLevel(owner);
        int frenzyAmp = Math.max(0, originLevel - 1);
        MobEffectInstance frenzy = new MobEffectInstance(
                com.complextalents.impl.darkmage.effect.DarkMageEffects.HARVEST_FRENZY.get(),
                60,
                frenzyAmp,
                false, true, true
        );
        owner.addEffect(frenzy);

        // Effects
        level.playSound(null, orb.pos().x, orb.pos().y, orb.pos().z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, orb.pos().x, orb.pos().y + 0.5, orb.pos().z, 15, 0.2, 0.2, 0.2, 0.05);

        removeOrb(level, orb, false);
    }

    private static void restoreMana(ServerPlayer player, double amount) {
        try {
            io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            double maxMana = player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            magicData.setMana((float) Math.min(maxMana, magicData.getMana() + amount));
            io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player, new io.redspace.ironsspellbooks.network.SyncManaPacket(magicData));
        } catch (Throwable ignored) {}
    }

    /**
     * Soul Wave Detonation: Triggered when Blood Pact is deactivated.
     * Detonates ONLY owner's orbs within 20m radius.
     */
    public static void detonateOwnerOrbs(ServerPlayer owner, double radius) {
        ServerLevel level = owner.serverLevel();
        List<BloodOrbData> levelOrbs = ACTIVE_ORBS.get(level.dimension());
        if (levelOrbs == null || levelOrbs.isEmpty()) return;

        double spellPower = 1.0;
        ResourceLocation spellPowerAttrId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power");
        var attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(spellPowerAttrId);
        if (attr != null && owner.getAttribute(attr) != null) {
            spellPower += owner.getAttributeValue(attr);
        }

        float totalDamageDealt = 0.0f;
        int detonatedCount = 0;

        Iterator<BloodOrbData> it = levelOrbs.iterator();
        while (it.hasNext()) {
            BloodOrbData orb = it.next();
            if (orb.ownerUUID().equals(owner.getUUID()) && orb.pos().distanceTo(owner.position()) <= radius) {
                float orbDamage = (float) ((10.0 + (2.5 * orb.densityV())) * spellPower);

                // Area damage around orb: blast radius scales with density V (from 2.0m for tiny orbs up to 8.0m max for boss orbs)
                double blastRadius = Math.max(1.5, Math.min(8.0, 2.0 + 1.2 * Math.sqrt(orb.densityV())));
                AABB blastArea = new AABB(
                        orb.pos().x - blastRadius, orb.pos().y - blastRadius, orb.pos().z - blastRadius,
                        orb.pos().x + blastRadius, orb.pos().y + blastRadius, orb.pos().z + blastRadius
                );

                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, blastArea);
                for (LivingEntity target : targets) {
                    if (!TeamHelper.isAlly(owner, target)) {
                        target.invulnerableTime = 0;
                        target.hurt(level.damageSources().indirectMagic(owner, null), orbDamage);
                        totalDamageDealt += orbDamage;
                    }
                }

                // Detonation AAA particle visual & explosion sound (particle scale scales with blast radius)
                float aaaScale = (float) (0.8f + 0.3f * Math.sqrt(orb.densityV()));
                PacketHandler.sendToNearby(
                        new com.complextalents.network.S2CSpawnAAAParticlePacket(
                                ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                                orb.pos().add(0, 0.4, 0),
                                new org.joml.Vector3f(0, 0, 0),
                                aaaScale
                        ),
                        level,
                        orb.pos()
                );

                level.playSound(null, orb.pos().x, orb.pos().y, orb.pos().z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.2f);
                level.sendParticles(ParticleTypes.SOUL, orb.pos().x, orb.pos().y + 0.5, orb.pos().z, 20, 0.5, 0.5, 0.5, 0.1);

                removeOrb(level, orb, true);
                it.remove();
                detonatedCount++;
            }
        }

        if (detonatedCount > 0) {
            // Rebound Heal: % of total damage returned as direct heal
            double reboundPct = OriginManager.getOriginStat(owner, "reboundHealPercent");
            float reboundHeal = (float) (totalDamageDealt * reboundPct);
            if (reboundHeal > 0) {
                owner.heal(reboundHeal);
            }

            owner.displayClientMessage(Component.literal(
                    "\u00A7cSoul Wave! Detonated " + detonatedCount + " Orbs \u00A77(Healed: +" + String.format("%.1f", reboundHeal) + " HP)"
            ), true);
        }
    }

    private static void removeOrb(ServerLevel level, BloodOrbData orb, boolean detonate) {
        BlockPos pos = BlockPos.containing(orb.pos());
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new S2CRemoveBloodOrbPacket(orb.id(), detonate));
    }

    public static class BloodOrbData {
        private final UUID id;
        private final UUID ownerUUID;
        private final Vec3 pos;
        private final double densityV;
        private final int lifetimeTicks;
        private int currentTick = 0;

        public BloodOrbData(UUID id, UUID ownerUUID, Vec3 pos, double densityV, int lifetimeTicks) {
            this.id = id;
            this.ownerUUID = ownerUUID;
            this.pos = pos;
            this.densityV = densityV;
            this.lifetimeTicks = lifetimeTicks;
        }

        public void tick() { currentTick++; }
        public UUID id() { return id; }
        public UUID ownerUUID() { return ownerUUID; }
        public Vec3 pos() { return pos; }
        public double densityV() { return densityV; }
        public int lifetimeTicks() { return lifetimeTicks; }
        public int currentTick() { return currentTick; }
    }
}
