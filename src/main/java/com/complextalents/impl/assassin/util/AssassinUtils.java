package com.complextalents.impl.assassin.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Utility methods for Assassin mechanics.
 */
public class AssassinUtils {

    public static boolean isBackstab(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;

        double dist = attacker.distanceTo(target);
        net.minecraft.nbt.CompoundTag data = target.getPersistentData();

        Vec3 targetLook;
        if (dist <= 2.0) {
            if (data.contains("AssassinLockedBackstabX")) {
                targetLook = new Vec3(
                        data.getDouble("AssassinLockedBackstabX"),
                        data.getDouble("AssassinLockedBackstabY"),
                        data.getDouble("AssassinLockedBackstabZ"));
            } else {
                targetLook = target.getLookAngle();
                data.putDouble("AssassinLockedBackstabX", targetLook.x);
                data.putDouble("AssassinLockedBackstabY", targetLook.y);
                data.putDouble("AssassinLockedBackstabZ", targetLook.z);
                data.putFloat("AssassinLockedBackstabYaw", target.getYRot());
            }
        } else {
            if (dist > 2.5 && data.contains("AssassinLockedBackstabX")) {
                clearLockedBackstab(target);
            }
            targetLook = target.getLookAngle();
        }

        Vec3 attackerLook = attacker.getLookAngle();
        double dot = attackerLook.dot(targetLook);
        return dot > 0.6; // Both looking in roughly same direction
    }

    public static void clearLockedBackstab(LivingEntity entity) {
        if (entity == null) return;
        net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
        data.remove("AssassinLockedBackstabX");
        data.remove("AssassinLockedBackstabY");
        data.remove("AssassinLockedBackstabZ");
        data.remove("AssassinLockedBackstabYaw");
    }

    public static boolean isEntityOnCooldown(LivingEntity entity, long gameTime) {
        net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
        if (data.contains("AssassinMarkCooldown")) {
            return gameTime < data.getLong("AssassinMarkCooldown");
        }
        return false;
    }

    public static void setEntityCooldown(LivingEntity entity, long startTime, long expirationTime) {
        net.minecraft.nbt.CompoundTag data = entity.getPersistentData();
        data.putLong("AssassinMarkStartTime", startTime);
        data.putLong("AssassinMarkCooldown", expirationTime);
    }
}
