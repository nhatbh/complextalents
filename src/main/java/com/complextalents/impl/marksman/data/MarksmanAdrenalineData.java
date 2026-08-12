package com.complextalents.impl.marksman.data;

import com.complextalents.impl.marksman.network.S2CAdrenalineStatePacket;
import com.complextalents.network.PacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side state manager for Marksman Adrenaline Mode & Segmented Dismiss Resource.
 * Manages Adrenaline duration directly without relying on MobEffect potion instances.
 */
public class MarksmanAdrenalineData {

    public static final String NBT_ACTIVE = "tacz_adrenaline_active";
    public static final String NBT_REMAINING_SECS = "tacz_adrenaline_rem_secs";
    public static final String NBT_LEVEL = "tacz_adrenaline_level";
    public static final String NBT_BASE_SECS = "tacz_adrenaline_base_secs";
    public static final String NBT_MAX_SECS = "tacz_adrenaline_max_secs";
    public static final String NBT_KILL_COUNT = "tacz_adrenaline_kill_count";
    public static final String NBT_DISMISS_RESOURCE = "tacz_adrenaline_dismiss_resource";
    public static final String NBT_DISMISS_COUNT = "tacz_adrenaline_dismiss_count";

    public static final int MAX_DISMISS_PER_ADRENALINE = 2;

    public static boolean isActive(Player player) {
        if (player == null) return false;
        CompoundTag nbt = player.getPersistentData();
        return nbt.getBoolean(NBT_ACTIVE) && nbt.getFloat(NBT_REMAINING_SECS) > 0.0f;
    }

    public static int getSkillLevel(Player player) {
        return Math.max(1, player.getPersistentData().getInt(NBT_LEVEL));
    }

    public static float getRemainingSecs(Player player) {
        return Math.max(0.0f, player.getPersistentData().getFloat(NBT_REMAINING_SECS));
    }

    public static float getBaseSecs(Player player) {
        float b = player.getPersistentData().getFloat(NBT_BASE_SECS);
        return b > 0 ? b : 15.0f;
    }

    public static float getMaxSecs(Player player) {
        float m = player.getPersistentData().getFloat(NBT_MAX_SECS);
        return m > 0 ? m : getBaseSecs(player) * 2.0f;
    }

    public static int getKillCount(Player player) {
        return player.getPersistentData().getInt(NBT_KILL_COUNT);
    }

    public static float getDismissResource(Player player) {
        return player.getPersistentData().getFloat(NBT_DISMISS_RESOURCE);
    }

    public static int getDismissCount(Player player) {
        return player.getPersistentData().getInt(NBT_DISMISS_COUNT);
    }

    public static boolean canDismiss(Player player) {
        return getDismissCount(player) < MAX_DISMISS_PER_ADRENALINE && getDismissResource(player) >= 100.0f;
    }

    public static void addDismissResource(ServerPlayer player, float amount) {
        CompoundTag nbt = player.getPersistentData();
        int used = nbt.getInt(NBT_DISMISS_COUNT);
        if (used >= MAX_DISMISS_PER_ADRENALINE) return;

        float maxCap = (MAX_DISMISS_PER_ADRENALINE - used) * 100.0f;
        float current = nbt.getFloat(NBT_DISMISS_RESOURCE);
        float updated = Math.min(maxCap, Math.max(0.0f, current + amount));
        nbt.putFloat(NBT_DISMISS_RESOURCE, updated);
        syncToClient(player);
    }

    public static void consumeDismissCharge(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        int used = nbt.getInt(NBT_DISMISS_COUNT) + 1;
        float currentRes = nbt.getFloat(NBT_DISMISS_RESOURCE);
        float remainingRes = Math.max(0.0f, currentRes - 100.0f);

        nbt.putInt(NBT_DISMISS_COUNT, used);
        nbt.putFloat(NBT_DISMISS_RESOURCE, remainingRes);
        syncToClient(player);
    }

    public static int incrementKillCount(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        int count = nbt.getInt(NBT_KILL_COUNT) + 1;
        nbt.putInt(NBT_KILL_COUNT, count);
        return count;
    }

    public static void activate(ServerPlayer player, int skillLevel, float baseDurationSec) {
        CompoundTag nbt = player.getPersistentData();
        nbt.putBoolean(NBT_ACTIVE, true);
        nbt.putFloat(NBT_REMAINING_SECS, baseDurationSec);
        nbt.putInt(NBT_LEVEL, skillLevel);
        nbt.putFloat(NBT_BASE_SECS, baseDurationSec);
        nbt.putFloat(NBT_MAX_SECS, baseDurationSec * 2.0f);
        nbt.putInt(NBT_KILL_COUNT, 0);
        nbt.putFloat(NBT_DISMISS_RESOURCE, 0.0f);
        nbt.putInt(NBT_DISMISS_COUNT, 0);

        syncToClient(player);
    }

    public static void deactivate(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        nbt.putBoolean(NBT_ACTIVE, false);
        nbt.putFloat(NBT_REMAINING_SECS, 0.0f);
        syncToClient(player);
    }

    public static void addDuration(ServerPlayer player, float secs) {
        if (!isActive(player)) return;

        CompoundTag nbt = player.getPersistentData();
        float current = nbt.getFloat(NBT_REMAINING_SECS);
        float max = getMaxSecs(player);
        float updated = Math.min(max, current + secs);

        nbt.putFloat(NBT_REMAINING_SECS, updated);
        syncToClient(player);
    }

    public static void deductDuration(ServerPlayer player, float secs) {
        if (!isActive(player)) return;

        CompoundTag nbt = player.getPersistentData();
        float current = nbt.getFloat(NBT_REMAINING_SECS);
        float updated = current - secs;

        if (updated <= 0.0f) {
            deactivate(player);
        } else {
            nbt.putFloat(NBT_REMAINING_SECS, updated);
            syncToClient(player);
        }
    }

    public static void tickServer(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        if (!nbt.getBoolean(NBT_ACTIVE)) return;

        float current = nbt.getFloat(NBT_REMAINING_SECS);
        float next = current - 0.05f; // 1 tick = 0.05 seconds

        if (next <= 0.0f) {
            deactivate(player);
        } else {
            nbt.putFloat(NBT_REMAINING_SECS, next);
            syncToClient(player);
        }
    }

    public static void syncToClient(ServerPlayer player) {
        boolean active = isActive(player);
        int lvl = getSkillLevel(player);
        float rem = getRemainingSecs(player);
        float max = getMaxSecs(player);
        float dismissRes = getDismissResource(player);
        int dismissCount = getDismissCount(player);

        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CAdrenalineStatePacket(active, lvl, rem, max, dismissRes, dismissCount, 0, -1, Vec3.ZERO, false)
        );
    }
}
