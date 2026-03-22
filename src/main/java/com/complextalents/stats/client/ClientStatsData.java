package com.complextalents.stats.client;

import com.complextalents.stats.StatType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side cache for player stat data.
 * Updated when StatsDataSyncPacket is received from the server.
 */
@OnlyIn(Dist.CLIENT)
public class ClientStatsData {
    private static final Map<StatType, Integer> statRanks = new EnumMap<>(StatType.class);
    private static final Map<StatType, Integer> originRanks = new EnumMap<>(StatType.class);

    static {
        // Initialize with defaults
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
            originRanks.put(type, 0);
        }
    }

    /**
     * Get the TOTAL rank of a specific stat (purchased + origin base).
     * @param type The stat type
     * @return The total rank/level of the stat, 0 if not set
     */
    public static int getStatRank(StatType type) {
        return statRanks.getOrDefault(type, 0) + originRanks.getOrDefault(type, 0);
    }

    /**
     * Get the purchased rank of a specific stat.
     */
    public static int getPurchasedRank(StatType type) {
        return statRanks.getOrDefault(type, 0);
    }

    /**
     * Get the origin base rank of a specific stat.
     */
    public static int getOriginRank(StatType type) {
        return originRanks.getOrDefault(type, 0);
    }

    /**
     * Update stat ranks from server sync packet.
     * Called by StatsDataSyncPacket.
     */
    public static void updateStatRanks(Map<StatType, Integer> newRanks) {
        statRanks.clear();
        for (StatType type : StatType.values()) {
            statRanks.put(type, newRanks.getOrDefault(type, 0));
        }
    }

    /**
     * Update origin ranks from server sync packet.
     * Called by StatsDataSyncPacket.
     */
    public static void updateOriginRanks(Map<StatType, Integer> newOriginRanks) {
        originRanks.clear();
        for (StatType type : StatType.values()) {
            originRanks.put(type, newOriginRanks.getOrDefault(type, 0));
        }
    }

    /**
     * Get all stat ranks (total).
     * @return A map of total stat ranks
     */
    public static Map<StatType, Integer> getAllRanks() {
        Map<StatType, Integer> totalRanks = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            totalRanks.put(type, getStatRank(type));
        }
        return totalRanks;
    }

    /**
     * Clear all stat data (e.g., on disconnect).
     */
    public static void reset() {
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
            originRanks.put(type, 0);
        }
    }
}
