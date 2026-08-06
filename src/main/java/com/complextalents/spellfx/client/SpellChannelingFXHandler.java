package com.complextalents.spellfx.client;

import com.complextalents.TalentsMod;
import com.complextalents.util.IronParticleHelper;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side handler for natural elemental channeling particle effects.
 * Spawns low-density, atmospheric particle effects grounded in physical magic concepts
 * (e.g. rapid ambient temperature drop for Ice, radiant End Rod light for Holy, Enchanting glyphs for Evocation)
 * while players actively channel/cast Iron's Spells.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class SpellChannelingFXHandler {

    private static float particleTickCounter = 0.0f;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.player == null) return;

        particleTickCounter += 0.2f;

        for (Player player : mc.level.players()) {
            if (player == null || !player.isAlive()) continue;

            boolean isLocalPlayer = player.getUUID().equals(mc.player.getUUID());
            boolean isCasting = false;
            String spellId = "";

            if (isLocalPlayer) {
                isCasting = ClientMagicData.isCasting();
                spellId = ClientMagicData.getCastingSpellId();
            }

            if (!isCasting) {
                SyncedSpellData syncedData = ClientMagicData.getSyncedSpellData(player);
                if (syncedData != null && syncedData.isCasting()) {
                    isCasting = true;
                    spellId = syncedData.getCastingSpellId();
                }
            }

            if (!isCasting || spellId == null || spellId.isEmpty()) {
                continue;
            }

            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null || spell.getSchoolType() == null) continue;

            String schoolPath = spell.getSchoolType().getId().getPath().toLowerCase();

            // Spawn low-density, refined atmospheric particles around casting player
            spawnChannelingParticles(mc.level, player, schoolPath);
        }
    }

    private static void spawnChannelingParticles(ClientLevel level, Player player, String schoolPath) {
        Vec3 pos = player.position();
        double px = pos.x;
        double py = pos.y;
        double pz = pos.z;
        double eyeY = py + player.getEyeHeight() * 0.75;

        net.minecraft.util.RandomSource rand = player.getRandom();

        switch (schoolPath) {
            case "ice" -> {
                // Ice: Low density frost mist & sinking ice crystals
                for (int i = 0; i < 2; i++) {
                    double radius = 0.6 + rand.nextDouble() * 0.7;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + 0.3 + rand.nextDouble() * (player.getBbHeight() + 0.2);
                    double spawnZ = pz + Math.sin(angle) * radius;

                    double vx = (px - spawnX) * 0.03;
                    double vy = -0.012 - rand.nextDouble() * 0.01;
                    double vz = (pz - spawnZ) * 0.03;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("snowflake") :
                            IronParticleHelper.getIronParticle("ice");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "fire" -> {
                // Fire: Low density embers drifting upward in thermal draft
                for (int i = 0; i < 2; i++) {
                    double radius = 0.5 + rand.nextDouble() * 0.6;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + rand.nextDouble() * 0.7;
                    double spawnZ = pz + Math.sin(angle) * radius;

                    double vx = (px - spawnX) * 0.02;
                    double vy = 0.03 + rand.nextDouble() * 0.03;
                    double vz = (pz - spawnZ) * 0.02;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("ember") :
                            IronParticleHelper.getIronParticle("fire");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "lightning" -> {
                // Lightning: Subtle static sparks snapping near body
                for (int i = 0; i < 2; i++) {
                    double spawnX = px + (rand.nextDouble() - 0.5) * 1.0;
                    double spawnY = py + 0.4 + rand.nextDouble() * player.getBbHeight();
                    double spawnZ = pz + (rand.nextDouble() - 0.5) * 1.0;

                    double vx = (rand.nextDouble() - 0.5) * 0.04;
                    double vy = (rand.nextDouble() - 0.5) * 0.04;
                    double vz = (rand.nextDouble() - 0.5) * 0.04;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("electricity") :
                            IronParticleHelper.getIronParticle("lightning");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "holy" -> {
                // Holy: Radiant Divine Beams of Light (End Rod & Glow particles)
                for (int i = 0; i < 2; i++) {
                    double radius = 0.3 + rand.nextDouble() * 0.5;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + 0.2 + rand.nextDouble() * (player.getBbHeight() + 0.4);
                    double spawnZ = pz + Math.sin(angle) * radius;

                    // Beams float gently upward with pure radiant white-gold light
                    double vy = 0.03 + rand.nextDouble() * 0.02;

                    ParticleOptions particle = (i % 2 == 0) ?
                            ParticleTypes.END_ROD :
                            ParticleTypes.GLOW;

                    level.addParticle(particle, spawnX, spawnY, spawnZ, 0, vy, 0);
                }
            }
            case "ender" -> {
                // Ender: Low density void specks drifting inward
                for (int i = 0; i < 2; i++) {
                    double radius = 0.8 + rand.nextDouble() * 0.5;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = eyeY + (rand.nextDouble() - 0.5) * 0.5;
                    double spawnZ = pz + Math.sin(angle) * radius;

                    double vx = (px - spawnX) * 0.04;
                    double vy = (eyeY - spawnY) * 0.04;
                    double vz = (pz - spawnZ) * 0.04;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("unstable_ender") :
                            IronParticleHelper.getIronParticle("portal");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "blood" -> {
                // Blood: Low density crimson mist
                for (int i = 0; i < 2; i++) {
                    double radius = 0.4 + rand.nextDouble() * 0.4;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + 0.3 + rand.nextDouble() * 1.0;
                    double spawnZ = pz + Math.sin(angle) * radius;

                    double vx = (rand.nextDouble() - 0.5) * 0.015;
                    double vy = 0.025 + rand.nextDouble() * 0.02;
                    double vz = (rand.nextDouble() - 0.5) * 0.015;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("acid_bubble") :
                            IronParticleHelper.getIronParticle("blood");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "evocation" -> {
                // Evocation: Enchanting Table Glyph Runes floating inward toward the player
                for (int i = 0; i < 2; i++) {
                    double radius = 0.8 + rand.nextDouble() * 0.5;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + 0.3 + rand.nextDouble() * 1.0;
                    double spawnZ = pz + Math.sin(angle) * radius;

                    // Enchanting glyphs drift toward eye level
                    double vx = (px - spawnX) * 0.15;
                    double vy = (eyeY - spawnY) * 0.15;
                    double vz = (pz - spawnZ) * 0.15;

                    ParticleOptions particle = (i % 2 == 0) ?
                            ParticleTypes.ENCHANT :
                            IronParticleHelper.getIronParticle("magic");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "eldritch" -> {
                // Eldritch: Low density shadow vapor
                for (int i = 0; i < 2; i++) {
                    double spawnX = px + (rand.nextDouble() - 0.5) * 0.7;
                    double spawnY = eyeY + (rand.nextDouble() - 0.5) * 0.3;
                    double spawnZ = pz + (rand.nextDouble() - 0.5) * 0.7;

                    double vx = (rand.nextDouble() - 0.5) * 0.02;
                    double vy = -0.015 - rand.nextDouble() * 0.015;
                    double vz = (rand.nextDouble() - 0.5) * 0.02;

                    ParticleOptions particle = IronParticleHelper.getIronParticle("smoke");
                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            case "nature" -> {
                // Nature: Low density green spores
                for (int i = 0; i < 2; i++) {
                    double radius = 0.5 + rand.nextDouble() * 0.5;
                    double angle = rand.nextDouble() * Math.PI * 2.0;

                    double spawnX = px + Math.cos(angle) * radius;
                    double spawnY = py + 0.4 + rand.nextDouble() * 1.0;
                    double spawnZ = pz + Math.sin(angle) * radius;

                    double vx = Math.cos(angle) * 0.015;
                    double vy = 0.02 + rand.nextDouble() * 0.02;
                    double vz = Math.sin(angle) * 0.015;

                    ParticleOptions particle = (i % 2 == 0) ?
                            IronParticleHelper.getIronParticle("nature") :
                            IronParticleHelper.getIronParticle("firefly");

                    if (particle != null) {
                        level.addParticle(particle, spawnX, spawnY, spawnZ, vx, vy, vz);
                    }
                }
            }
            default -> {
                // Default: Single soft ambient mana dust
                double spawnX = px + (rand.nextDouble() - 0.5) * 0.8;
                double spawnY = py + 0.2 + rand.nextDouble() * player.getBbHeight();
                double spawnZ = pz + (rand.nextDouble() - 0.5) * 0.8;

                ParticleOptions particle = IronParticleHelper.getIronParticle("magic");
                if (particle != null) {
                    level.addParticle(particle, spawnX, spawnY, spawnZ, 0, 0.025, 0);
                }
            }
        }
    }
}
