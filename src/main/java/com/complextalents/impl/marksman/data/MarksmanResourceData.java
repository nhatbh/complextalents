package com.complextalents.impl.marksman.data;

import com.complextalents.impl.marksman.network.S2CAdrenalineStatePacket;
import com.complextalents.network.PacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side state manager for Marksman Mobility Resource (Điểm Cơ Động, 0-100 pts).
 */
public class MarksmanResourceData {

    public static final String NBT_MOBILITY = "marksman_mobility_resource";

    public static float getMobility(Player player) {
        if (player == null) return 0.0f;
        CompoundTag nbt = player.getPersistentData();
        if (!nbt.contains(NBT_MOBILITY)) {
            nbt.putFloat(NBT_MOBILITY, 50.0f); // Default starting Mobility
        }
        return Math.min(100.0f, Math.max(0.0f, nbt.getFloat(NBT_MOBILITY)));
    }

    public static void setMobility(ServerPlayer player, float value) {
        if (player == null) return;
        float clamped = Math.min(100.0f, Math.max(0.0f, value));
        player.getPersistentData().putFloat(NBT_MOBILITY, clamped);
        syncToClient(player);
    }

    public static void addMobility(ServerPlayer player, float amount) {
        if (player == null || amount <= 0.0f) return;
        float current = getMobility(player);
        setMobility(player, current + amount);
    }

    public static boolean consumeMobility(ServerPlayer player, float amount) {
        if (player == null) return false;
        float current = getMobility(player);
        if (current < amount) {
            return false;
        }
        setMobility(player, current - amount);
        return true;
    }

    public static void syncToClient(ServerPlayer player) {
        float mobility = getMobility(player);
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CAdrenalineStatePacket(false, 1, 0.0f, 100.0f, mobility, 0, 0, -1, Vec3.ZERO, false)
        );
    }
}
