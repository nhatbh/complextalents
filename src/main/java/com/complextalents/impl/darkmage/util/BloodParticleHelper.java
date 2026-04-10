package com.complextalents.impl.darkmage.util;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class BloodParticleHelper {

    // Standard Blood Pact Particles
    public static final BlockParticleOption BLOOD_SPLATTER = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    public static final DustParticleOptions BLOOD_MIST = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.0f), 1.2f);

    /**
     * Spawns a horizontal circle of particles.
     * Must be called on the Client-side level.
     *
     * @param level    The level (Client-side)
     * @param center   The center point of the circle
     * @param radius   The radius of the circle
     * @param particle The particle type (e.g., BLOOD_MIST)
     * @param count    How many particles make up the circle
     * @param velocity The velocity variance (for chaotic effects, normally 0)
     */
    public static void spawnParticleCircle(Level level, Vec3 center, double radius, Object particle, int count, double velocity) {
        if (!level.isClientSide) return;

        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x + (radius * Math.cos(angle));
            double y = center.y;
            double z = center.z + (radius * Math.sin(angle));

            double vx = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vy = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vz = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;

            if (particle instanceof BlockParticleOption bpo) {
                level.addParticle(bpo, x, y, z, vx, vy, vz);
            } else if (particle instanceof DustParticleOptions dpo) {
                level.addParticle(dpo, x, y, z, vx, vy, vz);
            }
        }
    }

    /**
     * Spawns a vertical circle of particles.
     * Must be called on the Client-side level.
     */
    public static void spawnParticleVerticalCircle(Level level, Vec3 center, double radius, Object particle, int count, double velocity) {
        if (!level.isClientSide) return;

        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x + (radius * Math.cos(angle));
            double y = center.y + (radius * Math.sin(angle));
            double z = center.z;

            double vx = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vy = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vz = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;

            if (particle instanceof BlockParticleOption bpo) {
                level.addParticle(bpo, x, y, z, vx, vy, vz);
            } else if (particle instanceof DustParticleOptions dpo) {
                level.addParticle(dpo, x, y, z, vx, vy, vz);
            }
        }
    }

    /**
     * Spawns a vertical circle of particles aligned to the Z axis.
     * Must be called on the Client-side level.
     */
    public static void spawnParticleVerticalCircleZ(Level level, Vec3 center, double radius, Object particle, int count, double velocity) {
        if (!level.isClientSide) return;

        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x;
            double y = center.y + (radius * Math.cos(angle));
            double z = center.z + (radius * Math.sin(angle));

            double vx = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vy = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;
            double vz = velocity > 0 ? (level.random.nextDouble() - 0.5) * velocity : 0;

            if (particle instanceof BlockParticleOption bpo) {
                level.addParticle(bpo, x, y, z, vx, vy, vz);
            } else if (particle instanceof DustParticleOptions dpo) {
                level.addParticle(dpo, x, y, z, vx, vy, vz);
            }
        }
    }

    /**
     * Spawns a horizontal circle of particles on the server.
     *
     * @param level    The ServerLevel
     * @param center   The center point of the circle
     * @param radius   The radius of the circle
     * @param particle The particle type
     * @param count    How many particles make up the circle
     */
    public static void sendParticleCircle(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x + (radius * Math.cos(angle));
            double y = center.y;
            double z = center.z + (radius * Math.sin(angle));

            level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns a vertical circle of particles on the server.
     */
    public static void sendParticleVerticalCircle(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x + (radius * Math.cos(angle));
            double y = center.y + (radius * Math.sin(angle));
            double z = center.z;

            level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns a vertical circle (Z-axis) of particles on the server.
     */
    public static void sendParticleVerticalCircleZ(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
        double increment = (2 * Math.PI) / count;

        for (int i = 0; i < count; i++) {
            double angle = i * increment;
            double x = center.x;
            double y = center.y + (radius * Math.cos(angle));
            double z = center.z + (radius * Math.sin(angle));

            level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns a beam of particles between two points on the server.
     *
     * @param level    The ServerLevel
     * @param start    The starting coordinate
     * @param end      The ending coordinate
     * @param particle The particle type
     * @param step     The distance step between particles (e.g. 0.25)
     */
    public static void sendParticleBeam(ServerLevel level, Vec3 start, Vec3 end, ParticleOptions particle, double step) {
        double distance = start.distanceTo(end);
        if (distance <= 0) return;

        Vec3 direction = end.subtract(start).normalize();

        for (double d = 0; d <= distance; d += step) {
            double x = start.x + (direction.x * d);
            double y = start.y + (direction.y * d);
            double z = start.z + (direction.z * d);

            level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns a beam of particles between two points.
     * Must be called on the Client-side level.
     *
     * @param level    The level (Client-side)
     * @param start    The starting coordinate
     * @param end      The ending coordinate
     * @param particle The particle type
     * @param step     The distance step between particles (e.g. 0.25)
     */
    public static void spawnParticleBeam(Level level, Vec3 start, Vec3 end, Object particle, double step) {
        if (!level.isClientSide) return;

        double distance = start.distanceTo(end);
        if (distance <= 0) return;

        Vec3 direction = end.subtract(start).normalize();

        for (double d = 0; d <= distance; d += step) {
            double x = start.x + (direction.x * d);
            double y = start.y + (direction.y * d);
            double z = start.z + (direction.z * d);

            if (particle instanceof BlockParticleOption bpo) {
                level.addParticle(bpo, x, y, z, 0, 0, 0);
            } else if (particle instanceof DustParticleOptions dpo) {
                level.addParticle(dpo, x, y, z, 0, 0, 0);
            }
        }
    }
}
