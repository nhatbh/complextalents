package com.complextalents.impl.marksman.client;

import net.minecraft.world.phys.Vec3;

/**
 * Client-side state container for Marksman Adrenaline State & Dismiss Resource.
 */
public class ClientAdrenalineFXHandler {

    private static boolean active = false;
    private static int skillLevel = 1;
    private static float remainingSecs = 0.0f;
    private static float maxSecs = 10.0f;
    private static float dismissResource = 0.0f;
    private static int dismissCount = 0;
    private static int overclockStacks = 0;
    private static int bossEntityId = -1;
    private static Vec3 bossMarkOffset = Vec3.ZERO;
    private static boolean bossMarkRespawning = false;

    public static void updateState(boolean isActive, int level, float remSecs, float max, float dismissRes, int count,
                                   int stacks, int bossId, Vec3 markOffset, boolean respawning) {
        active = isActive;
        skillLevel = level;
        remainingSecs = remSecs;
        maxSecs = max;
        dismissResource = dismissRes;
        dismissCount = count;
        overclockStacks = stacks;
        bossEntityId = bossId;
        bossMarkOffset = markOffset;
        bossMarkRespawning = respawning;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getSkillLevel() {
        return skillLevel;
    }

    public static float getRemainingSecs() {
        return remainingSecs;
    }

    public static float getMaxSecs() {
        return maxSecs;
    }

    public static float getDismissResource() {
        return dismissResource;
    }

    public static int getDismissCount() {
        return dismissCount;
    }

    public static int getOverclockStacks() {
        return overclockStacks;
    }

    public static int getBossEntityId() {
        return bossEntityId;
    }

    public static Vec3 getBossMarkOffset() {
        return bossMarkOffset;
    }

    public static boolean isBossMarkRespawning() {
        return bossMarkRespawning;
    }
}
