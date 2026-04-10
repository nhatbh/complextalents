package com.complextalents.impl.darkmage.manager;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.effect.DarkMageEffects;
import com.complextalents.impl.darkmage.events.BloodPactTickHandler;
import com.complextalents.network.Messages;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.darkmage.S2CSyncBloodOrbPacket;
import com.complextalents.network.darkmage.S2CRemoveBloodOrbPacket;
import com.complextalents.util.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class BloodOrbManager {

    private static final ConcurrentHashMap<ResourceKey<Level>, List<BloodOrbData>> ACTIVE_ORBS = new ConcurrentHashMap<>();
    private static final List<DetonationZone> ACTIVE_DETONATIONS = new CopyOnWriteArrayList<>();

    public static void spawnOrb(ServerPlayer owner, Vec3 pos, int tier) {
        UUID orbId = UUID.randomUUID();
        int lifetime = getDurationForTier(tier);
        double souls = com.complextalents.impl.darkmage.data.SoulData.getSouls(owner);
        double soulMultiplier = calculateSoulMultiplier(tier, souls);
        
        BloodOrbData data = new BloodOrbData(orbId, owner.getUUID(), pos, tier, lifetime, soulMultiplier);
        
        ACTIVE_ORBS.computeIfAbsent(owner.level().dimension(), k -> new ArrayList<>()).add(data);
        
        // Sync to clients
        syncSpawn(owner.serverLevel(), data);
    }

    private static void syncSpawn(ServerLevel level, BloodOrbData data) {
        BlockPos pos = new BlockPos((int)data.pos().x, (int)data.pos().y, (int)data.pos().z);
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new S2CSyncBloodOrbPacket(data.id(), data.pos(), data.tier(), data.ownerUUID(), data.lifetimeTicks()));
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
                
                // Owner Cleanup
                if (owner == null || !owner.isAlive()) {
                    removeOrb(level, orb, false);
                    it.remove();
                    continue;
                }

                // T5 Detonation Hook
                if (orb.tier() == 5 && !BloodPactTickHandler.isBloodPactActive(owner)) {
                    detonate(level, orb, owner);
                    it.remove();
                    continue;
                }

                // Mechanics (every 20 ticks)
                if (orb.currentTick() % 20 == 0) {
                    handleMechanics(level, orb, owner);
                }

                // Expiration
                if (orb.currentTick() >= orb.lifetimeTicks()) {
                    removeOrb(level, orb, false);
                    it.remove();
                }
            }
        });

        // Tick Active Detonations
        ACTIVE_DETONATIONS.removeIf(DetonationZone::tick);
    }

    private static double calculateSoulMultiplier(int tier, double souls) {
        return switch (tier) {
            case 2 -> Math.min(3.0, 1.0 + ((souls - 500) / 1000.0) * 2.0);    // 500-1500
            case 3 -> Math.min(3.0, 1.0 + ((souls - 1500) / 1500.0) * 2.0);   // 1500-3000
            case 4 -> Math.min(3.0, 1.0 + ((souls - 3000) / 3666.0) * 2.0);   // 3000-6666
            case 5 -> 1.0 + ((souls - 6666) / 5000.0);                        // 6666+ Infinite
            default -> 1.0;
        };
    }

    private static void handleMechanics(ServerLevel level, BloodOrbData orb, ServerPlayer owner) {
        switch (orb.tier()) {
            case 2 -> handleTether(orb, owner);
            case 3, 4, 5 -> handleAoE(level, orb, owner);
        }
    }

    private static void handleTether(BloodOrbData orb, ServerPlayer owner) {
        if (owner.position().distanceTo(orb.pos()) <= 32.0) {
            owner.heal(owner.getMaxHealth() * 0.06f); // Buffed from 0.03f
        }
    }

    private static void handleAoE(ServerLevel level, BloodOrbData orb, ServerPlayer owner) {
        double radius = orb.tier() == 3 ? 14.0 : (orb.tier() == 4 ? 18.0 : 22.0); // Expanded radii
        float baseHealPercent = orb.tier() == 3 ? 0.08f : 0.10f; // Buffed from 4%/5%

        AABB area = new AABB(orb.pos().x - radius, orb.pos().y - radius, orb.pos().z - radius,
                             orb.pos().x + radius, orb.pos().y + radius, orb.pos().z + radius);
        
        level.getEntitiesOfClass(LivingEntity.class, area)
                .forEach(entity -> {
                    if (TeamHelper.isAlly(owner, entity)) {
                        float healAmount = (owner.getMaxHealth() * baseHealPercent) + (orb.amplifiedDamage() * 0.5f);
                        entity.heal(healAmount);
                    } else if (orb.tier() >= 4) {
                        handleSiphoningField(level, orb, entity);
                    }
                });
        
        orb.resetAmplifiedDamage();
    }

    private static void handleSiphoningField(ServerLevel level, BloodOrbData orb, LivingEntity enemy) {
        ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(orb.ownerUUID());
        int slownessLevel = orb.tier() == 5 ? 2 : 1;
        enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, slownessLevel));

        float baseDamage = orb.tier() == 5 ? 15.0f : 5.0f; // Buffed from 2.0 / 1.0
        float damage = (float) (baseDamage * orb.soulMultiplier());
        
        // Attribution and Damage
        enemy.hurt(level.damageSources().indirectMagic(owner, null), damage);
        orb.addAmplifiedDamage(damage);
        
        TalentsMod.LOGGER.debug("Blood Orb Siphon: Dealt {} damage to {} (Owner: {})", 
                String.format("%.2f", damage), enemy.getName().getString(), owner != null ? owner.getName().getString() : "Unknown");
    }

    private static void detonate(ServerLevel level, BloodOrbData orb, ServerPlayer owner) {
        // Initial "Aura" Sound
        level.playSound(null, orb.pos().x, orb.pos().y, orb.pos().z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
        
        // AAA Particle: bloodlance (Starts immediately at T=0)
        Messages.spawnAAAParticle(level, orb.pos(), "bloodlance", new Vector3f(0), 2.0f);

        // Fixed base values decoupled from remaining time
        float totalHeal = owner.getMaxHealth() * 0.5f; // Flat 50% Heal
        float totalDamage = (float) (500.0 * orb.soulMultiplier()); // Buffed Base from 100.0 to 500.0
        int bleedDuration = (int) (200 * orb.soulMultiplier());

        // Create the Detonation Zone (Lifecycle managed by TickEvent)
        ACTIVE_DETONATIONS.add(new DetonationZone(level, orb.pos(), owner, totalDamage, totalHeal, bleedDuration));

        removeOrb(level, orb, true);
    }

    private static void removeOrb(ServerLevel level, BloodOrbData orb, boolean detonate) {
        BlockPos pos = new BlockPos((int)orb.pos().x, (int)orb.pos().y, (int)orb.pos().z);
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new S2CRemoveBloodOrbPacket(orb.id(), detonate));
    }

    private static int getDurationForTier(int tier) {
        return switch (tier) {
            case 2 -> 100; // 5s
            case 3 -> 160; // 8s
            case 4 -> 200; // 10s
            case 5 -> 240; // 12s
            default -> 100;
        };
    }

    private static class BloodOrbData {
        private final UUID id;
        private final UUID ownerUUID;
        private final Vec3 pos;
        private final int tier;
        private final int lifetimeTicks;
        private final double soulMultiplier;
        private int currentTick = 0;
        private float amplifiedDamage = 0.0f;

        public BloodOrbData(UUID id, UUID ownerUUID, Vec3 pos, int tier, int lifetimeTicks, double soulMultiplier) {
            this.id = id;
            this.ownerUUID = ownerUUID;
            this.pos = pos;
            this.tier = tier;
            this.lifetimeTicks = lifetimeTicks;
            this.soulMultiplier = soulMultiplier;
        }

        public void tick() { currentTick++; }
        public UUID id() { return id; }
        public UUID ownerUUID() { return ownerUUID; }
        public Vec3 pos() { return pos; }
        public int tier() { return tier; }
        public int lifetimeTicks() { return lifetimeTicks; }
        public int currentTick() { return currentTick; }
        public double soulMultiplier() { return soulMultiplier; }
        public float amplifiedDamage() { return amplifiedDamage; }
        public void addAmplifiedDamage(float d) { amplifiedDamage += d; }
        public void resetAmplifiedDamage() { amplifiedDamage = 0; }
    }

    /**
     * Represents a Tier 5 detonation zone synced with the 'bloodlance' particle animation.
     */
    private static class DetonationZone {
        private final ServerLevel level;
        private final Vec3 pos;
        private final ServerPlayer owner;
        private final float damagePerTick;
        private final float healPerTick;
        private final int bleedDuration;
        private int currentTick = 0;

        DetonationZone(ServerLevel level, Vec3 pos, ServerPlayer owner, float totalDamage, float totalHeal, int bleedDuration) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.damagePerTick = totalDamage / 27.0f; // Active for 27 ticks
            this.healPerTick = totalHeal / 27.0f;
            this.bleedDuration = bleedDuration;
        }

        /**
         * Ticks the detonation zone.
         * T=0-35: Aura phase (no damage)
         * T=36-63: Active phase (piercing lances)
         * @return true if the zone has finished its lifecycle and should be removed.
         */
        boolean tick() {
            currentTick++;

            if (currentTick >= 36 && currentTick <= 63) {
                applyActiveEffects();
            }

            return currentTick >= 63;
        }

        private void applyActiveEffects() {
            // 1. Healing flurry
            if (owner != null && owner.isAlive()) {
                owner.heal(healPerTick);
            }

            // 2. Piercing Sounds (rhythmic)
            if (currentTick % 2 == 0) {
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            // 3. Area Damage
            double radius = 20.0;
            AABB area = new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                                 pos.x + radius, pos.y + radius, pos.z + radius);

            level.getEntitiesOfClass(LivingEntity.class, area)
                    .forEach(entity -> {
                        if (!TeamHelper.isAlly(owner, entity)) {
                            // Scale down damage per tick
                            // BYPASS DAMAGE COOLDOWN: Standard Minecraft entities have 10-20 tick invulnerability.
                            // To deal damage EVERY tick, we must reset the timer.
                            entity.invulnerableTime = 0;
                            
                            entity.hurt(level.damageSources().indirectMagic(owner, null), damagePerTick);
                            
                            // Apply bleed once at the start of the protrusion
                            if (currentTick == 36) {
                                entity.addEffect(new MobEffectInstance(DarkMageEffects.BLEED.get(), bleedDuration));
                            }

                            if (currentTick % 20 == 0) { // Log once per second during the flurry to avoid spam
                                TalentsMod.LOGGER.info("Blood Orb Detonation: Dealing {} damage per tick to {} (Owner: {})", 
                                        String.format("%.2f", damagePerTick), entity.getName().getString(), owner != null ? owner.getName().getString() : "Unknown");
                            }
                        }
                    });
        }
    }
}
