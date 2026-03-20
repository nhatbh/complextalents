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

    static {
        // Initialize with defaults
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
        }
    }

    /**
     * Get the rank of a specific stat.
     * @param type The stat type
     * @return The rank/level of the stat, 0 if not set
     */
    public static int getStatRank(StatType type) {
        return statRanks.getOrDefault(type, 0);
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
     * Get all stat ranks.
     * @return A copy of all stat ranks
     */
    public static Map<StatType, Integer> getAllRanks() {
        return new EnumMap<>(statRanks);
    }

    /**
     * Clear all stat data (e.g., on disconnect).
     */
    public static void reset() {
        for (StatType type : StatType.values()) {
            statRanks.put(type, 0);
        }
    }
}
