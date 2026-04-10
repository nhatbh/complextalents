package com.complextalents.impl.darkmage.client;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.util.BloodParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class BloodOrbRenderer {

    private static final Map<UUID, ClientBloodOrbData> ACTIVE_ORBS = new ConcurrentHashMap<>();

    public static void addOrb(UUID orbId, Vec3 pos, int tier, UUID ownerUUID, int lifetime) {
        ACTIVE_ORBS.put(orbId, new ClientBloodOrbData(orbId, pos, tier, ownerUUID, lifetime));
    }

    public static void removeOrb(UUID orbId, boolean detonate) {
        ClientBloodOrbData orb = ACTIVE_ORBS.remove(orbId);
        if (orb != null && detonate) {
            triggerDetonationVisual(orb);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE_ORBS.clear();
            return;
        }

        Iterator<Map.Entry<UUID, ClientBloodOrbData>> it = ACTIVE_ORBS.entrySet().iterator();
        while (it.hasNext()) {
            ClientBloodOrbData orb = it.next().getValue();
            orb.tick();

            if (orb.currentTick >= orb.lifetime) {
                it.remove();
                continue;
            }

            renderOrb(level, orb);
        }
    }

    private static void renderOrb(ClientLevel level, ClientBloodOrbData orb) {
        // 1. Render Orb Core (Reduced count)
        double coreChaos = orb.tier == 5 ? 0.15 : 0.05;
        BloodParticleHelper.spawnParticleCircle(level, orb.pos, 0.4, BloodParticleHelper.BLOOD_MIST, 5, coreChaos);
        BloodParticleHelper.spawnParticleVerticalCircle(level, orb.pos, 0.4, BloodParticleHelper.BLOOD_MIST, 5, coreChaos);
        BloodParticleHelper.spawnParticleVerticalCircleZ(level, orb.pos, 0.4, BloodParticleHelper.BLOOD_MIST, 5, coreChaos);

        // Tier 5 Volatility Sparks
        if (orb.tier == 5 && level.random.nextFloat() < 0.2f) {
            level.addParticle(ParticleTypes.SOUL, orb.pos.x, orb.pos.y, orb.pos.z, 
                (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.1);
        }

        // 2. Render Tether (Tier 2 only - continuous beam to owner)
        if (orb.tier == 2) {
            Player owner = level.getPlayerByUUID(orb.ownerUUID);
            if (owner != null && owner == Minecraft.getInstance().player) { // Only render tether for the local player if they are the owner
                Vec3 ownerPos = owner.position().add(0, owner.getBbHeight() / 2.0, 0);
                if (ownerPos.distanceTo(orb.pos) <= 32.0) {
                     BloodParticleHelper.spawnParticleBeam(level, orb.pos, ownerPos, BloodParticleHelper.BLOOD_MIST, 1.2);
                }
            }
        }

        // 3. Render AoE Visuals (Tier 3-5)
        if (orb.tier >= 3) {
            double radius = orb.tier == 3 ? 10.0 : (orb.tier == 4 ? 14.0 : 16.0);
            
            // Pulse effect every 20 ticks
            if (orb.currentTick % 20 == 0) {
                // AoE Pulse Circle
                BloodParticleHelper.spawnParticleCircle(level, orb.pos, radius, BloodParticleHelper.BLOOD_MIST, (int) (radius * 6), 0.1);
                
                // T4/T5 Siphoning Field ground indicator (pulse-aligned)
                if (orb.tier >= 4) {
                    BloodParticleHelper.spawnParticleCircle(level, orb.pos.subtract(0, 0.9, 0), radius, BloodParticleHelper.BLOOD_MIST, (int)(radius * 4), 0.02);
                    
                    // T4 Siphon Visual: Particles flying from radius to center
                    for (int i = 0; i < 15; i++) {
                        double angle = level.random.nextDouble() * Math.PI * 2;
                        Vec3 start = orb.pos.add(Math.cos(angle) * radius, (level.random.nextDouble() - 0.5) * 2.0, Math.sin(angle) * radius);
                        Vec3 vel = orb.pos.subtract(start).normalize().scale(0.4);
                        level.addParticle(BloodParticleHelper.BLOOD_SPLATTER, start.x, start.y, start.z, vel.x, vel.y, vel.z);
                    }
                }
            }
        }
    }

    private static void triggerDetonationVisual(ClientBloodOrbData orb) {
        // Detonation visual is now handled by AAA Particles triggered from the server.
    }

    private static class ClientBloodOrbData {
        private final UUID id;
        private final Vec3 pos;
        private final int tier;
        private final UUID ownerUUID;
        private final int lifetime;
        private int currentTick = 0;

        public ClientBloodOrbData(UUID id, Vec3 pos, int tier, UUID ownerUUID, int lifetime) {
            this.id = id;
            this.pos = pos;
            this.tier = tier;
            this.ownerUUID = ownerUUID;
            this.lifetime = lifetime;
        }

        public void tick() { currentTick++; }
    }
}
