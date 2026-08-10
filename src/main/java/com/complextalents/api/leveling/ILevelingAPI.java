package com.complextalents.api.leveling;

import com.complextalents.leveling.data.LevelStats;
import com.complextalents.leveling.events.level.PlayerXPResetEvent;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public API for the ComplexTalents Leveling System.
 */
public interface ILevelingAPI {

    /**
     * Gets the current leveling level of a player.
     * @param player Target player
     * @return Current level (minimum 1)
     */
    int getLevel(ServerPlayer player);

    /**
     * Gets the current XP progress towards the next level for a player.
     * @param player Target player
     * @return Current level XP progress
     */
    double getCurrentXP(ServerPlayer player);

    /**
     * Gets the total accumulated XP earned by a player across all levels.
     * @param player Target player
     * @return Total XP
     */
    double getTotalXP(ServerPlayer player);

    /**
     * Gets the total skill points earned by a player.
     * @param player Target player
     * @return Total earned skill points
     */
    int getTotalSkillPoints(ServerPlayer player);

    /**
     * Gets the number of skill points already spent by a player.
     * @param player Target player
     * @return Consumed skill points
     */
    int getConsumedSkillPoints(ServerPlayer player);

    /**
     * Gets the available skill points for a player (Total - Consumed).
     * @param player Target player
     * @return Available skill points
     */
    int getAvailableSkillPoints(ServerPlayer player);

    /**
     * Consumes skill points for a player.
     * @param player Target player
     * @param amount Amount to consume
     * @return True if successful, false if insufficient points
     */
    boolean consumeSkillPoints(ServerPlayer player, int amount);

    /**
     * Awards XP to a player using full event pipeline.
     * @param player Target player
     * @param amount Base XP amount
     * @param source Source of XP
     * @param context XP Context metadata
     * @return True if XP was successfully awarded
     */
    boolean awardXP(ServerPlayer player, double amount, XPSource source, XPContext context);

    /**
     * Convenience method to award XP to a player with a custom source description.
     * @param player Target player
     * @param amount XP amount
     * @param customReason Custom source description
     * @return True if XP was successfully awarded
     */
    boolean awardXP(ServerPlayer player, double amount, String customReason);

    /**
     * Gets a complete snapshot of leveling stats for a player.
     * @param player Target player
     * @return LevelStats snapshot
     */
    LevelStats getStats(ServerPlayer player);

    /**
     * Resets current progress XP for a player.
     * @param player Target player
     * @param reason Reason for reset
     */
    void resetCurrentXP(ServerPlayer player, PlayerXPResetEvent.ResetReason reason);

    /**
     * Sets a player's total accumulated XP and automatically recalculates their level, 
     * current XP progress towards the next level, and earned Skill Points.
     * @param player Target player
     * @param newTotalXP New total accumulated XP amount
     */
    void setTotalXP(ServerPlayer player, double newTotalXP);
}
