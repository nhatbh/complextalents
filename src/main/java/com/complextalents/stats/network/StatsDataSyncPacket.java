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
    private final Map<StatType, Integer> originRanks;

    public StatsDataSyncPacket(Map<StatType, Integer> statRanks, Map<StatType, Integer> originRanks) {
        this.statRanks = statRanks == null ? new EnumMap<>(StatType.class) : new EnumMap<>(statRanks);
        this.originRanks = originRanks == null ? new EnumMap<>(StatType.class) : new EnumMap<>(originRanks);
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

        Map<StatType, Integer> originRanks = new EnumMap<>(StatType.class);
        int originCount = buffer.readVarInt();
        for (int i = 0; i < originCount; i++) {
            StatType type = buffer.readEnum(StatType.class);
            int rank = buffer.readVarInt();
            originRanks.put(type, rank);
        }

        return new StatsDataSyncPacket(statRanks, originRanks);
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

        buffer.writeVarInt(originRanks.size());
        for (Map.Entry<StatType, Integer> entry : originRanks.entrySet()) {
            buffer.writeEnum(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    /**
     * Handle this packet on the client side.
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            handleClientSide(statRanks, originRanks);
        });
        context.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClientSide(Map<StatType, Integer> statRanks, Map<StatType, Integer> originRanks) {
        // Update client-side stats cache
        com.complextalents.stats.client.ClientStatsData.updateStatRanks(statRanks);
        com.complextalents.stats.client.ClientStatsData.updateOriginRanks(originRanks);

        // Also update client-side player stats capability
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(com.complextalents.stats.capability.GeneralStatsDataProvider.STATS_DATA)
                    .ifPresent(statsData -> {
                        for (Map.Entry<StatType, Integer> entry : statRanks.entrySet()) {
                            // Use the internal map directly to avoid re-syncing to server
                            statsData.setStatRank(entry.getKey(), entry.getValue());
                        }
                        for (Map.Entry<StatType, Integer> entry : originRanks.entrySet()) {
                            statsData.setOriginStatRank(entry.getKey(), entry.getValue());
                        }
                    });
        }
    }

    /**
     * Helper method to send this packet.
     */
    public static void send(net.minecraft.server.level.ServerPlayer player, Map<StatType, Integer> statRanks, Map<StatType, Integer> originRanks) {
        StatsDataSyncPacket packet = new StatsDataSyncPacket(statRanks, originRanks);
        com.complextalents.network.PacketHandler.sendTo(packet, player);
    }
}
