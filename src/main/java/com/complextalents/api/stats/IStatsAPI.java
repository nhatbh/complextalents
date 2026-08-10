package com.complextalents.api.stats;

import com.complextalents.stats.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Public API for the ComplexTalents General Stats System.
 */
public interface IStatsAPI {

    /**
     * Gets a player's purchased general stat rank for a given StatType.
     * @param player Target player
     * @param statType StatType enum
     * @return Purchased rank count
     */
    int getStatRank(Player player, StatType statType);

    /**
     * Sets a player's purchased general stat rank for a given StatType and updates attribute modifiers.
     * @param player Target player
     * @param statType StatType enum
     * @param rank New rank value
     */
    void setStatRank(Player player, StatType statType, int rank);

    /**
     * Gets a player's origin-specific base stat rank for a given StatType.
     * @param player Target player
     * @param statType StatType enum
     * @return Origin stat rank count
     */
    int getOriginStatRank(Player player, StatType statType);

    /**
     * Sets a player's origin-specific base stat rank for a given StatType and updates attribute modifiers.
     * @param player Target player
     * @param statType StatType enum
     * @param rank New rank value
     */
    void setOriginStatRank(Player player, StatType statType, int rank);

    /**
     * Gets the total combined rank (Purchased + Origin Base) for a given StatType.
     * @param player Target player
     * @param statType StatType enum
     * @return Total stat rank
     */
    int getTotalStatRank(Player player, StatType statType);

    /**
     * Gets all purchased general stat ranks for a player.
     * @param player Target player
     * @return Map of StatType to rank
     */
    Map<StatType, Integer> getAllStatRanks(Player player);

    /**
     * Gets all origin base stat ranks for a player.
     * @param player Target player
     * @return Map of StatType to origin rank
     */
    Map<StatType, Integer> getAllOriginStatRanks(Player player);

    /**
     * Gets the player's recorded highest combat power.
     * @param player Target player
     * @return Highest combat power
     */
    int getHighestCombatPower(Player player);

    /**
     * Sets the player's recorded highest combat power.
     * @param player Target player
     * @param combatPower New combat power
     */
    void setHighestCombatPower(Player player, int combatPower);

    /**
     * Calculates the SP cost for upgrading a stat type to the next rank for a player based on their origin class cost matrix.
     * @param player Target player
     * @param statType StatType enum
     * @return SP cost per rank
     */
    int getSPCostPerRank(Player player, StatType statType);

    /**
     * Re-applies all stat attribute modifiers for a player based on current total ranks.
     * @param player Target player
     */
    void reapplyAllModifiers(Player player);

    /**
     * Resets all purchased stat ranks to 0 and restores origin base stat ranks to match the player's active origin baseline.
     * @param player Target player
     */
    void resetStatsToOrigin(Player player);

    /**
     * Resets all purchased stat ranks to 0 and sets origin base stat ranks to match the specified origin ID baseline.
     * @param player Target player
     * @param originId ResourceLocation of the target origin (e.g. "complextalents:warrior")
     */
    void resetStatsToOrigin(Player player, ResourceLocation originId);
}
