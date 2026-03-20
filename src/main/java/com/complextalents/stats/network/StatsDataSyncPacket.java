package com.complextalents.stats.network;

import com.complextalents.stats.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sync player stats data from server to client.
 */
public class StatsDataSyncPacket {
    private final Map<StatType, Integer> statRanks;

    public StatsDataSyncPacket(Map<StatType, Integer> statRanks) {
        this.statRanks = statRanks == null ? new EnumMap<>(StatType.class) : new EnumMap<>(statRanks);
    }

    /**
     * Decode a stats data sync packet from a buffer.
     */
    public static StatsDataSyncPacket decode(FriendlyByteBuf buffer) {
        Map<StatType, Integer> statRanks = new EnumMap<>(StatType.class);
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            StatType type = buffer.readEnum(StatType.class);
            int rank = buffer.readVarInt();
            statRanks.put(type, rank);
        }
        return new StatsDataSyncPacket(statRanks);
    }

    /**
     * Encode this packet to a buffer.
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(statRanks.size());
        for (Map.Entry<StatType, Integer> entry : statRanks.entrySet()) {
            buffer.writeEnum(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    /**
     * Handle this packet on the client side.
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            handleClientSide(statRanks);
        });
        context.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClientSide(Map<StatType, Integer> statRanks) {
        // Update client-side stats cache
        com.complextalents.stats.client.ClientStatsData.updateStatRanks(statRanks);

        // Also update client-side player stats capability
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(com.complextalents.stats.capability.GeneralStatsDataProvider.STATS_DATA)
                    .ifPresent(statsData -> {
                        for (Map.Entry<StatType, Integer> entry : statRanks.entrySet()) {
                            // Use the internal map directly to avoid re-syncing to server
                            statsData.getAllRanks().put(entry.getKey(), entry.getValue());
                        }
                    });
        }
    }

    /**
     * Helper method to send this packet.
     */
    public static void send(net.minecraft.server.level.ServerPlayer player, Map<StatType, Integer> statRanks) {
        StatsDataSyncPacket packet = new StatsDataSyncPacket(statRanks);
        com.complextalents.network.PacketHandler.sendTo(packet, player);
    }
}
