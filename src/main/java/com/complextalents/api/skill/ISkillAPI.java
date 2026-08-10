package com.complextalents.api.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Public API for the ComplexTalents Active Skill & Form System.
 */
public interface ISkillAPI {

    /**
     * Gets the skill assigned to a specific slot.
     * @param player Target player
     * @param slotIndex Slot index (0)
     * @return Skill ResourceLocation or null if empty
     */
    ResourceLocation getSkillInSlot(Player player, int slotIndex);

    /**
     * Assigns a skill to a specific slot.
     * @param player Target player
     * @param slotIndex Slot index (0)
     * @param skillId Skill ResourceLocation to assign, or null to clear
     */
    void setSkillInSlot(Player player, int slotIndex, ResourceLocation skillId);

    /**
     * Gets the skill level for a specific skill.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     * @return Skill level (1 or higher)
     */
    int getSkillLevel(Player player, ResourceLocation skillId);

    /**
     * Sets the skill level for a specific skill.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     * @param level Skill level (must be >= 1)
     */
    void setSkillLevel(Player player, ResourceLocation skillId, int level);

    /**
     * Checks if a skill is currently on active casting cooldown.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     * @return True if on cooldown
     */
    boolean isOnCooldown(Player player, ResourceLocation skillId);

    /**
     * Gets the remaining active cooldown duration in seconds.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     * @return Remaining seconds, or 0.0 if not on cooldown
     */
    double getCooldown(Player player, ResourceLocation skillId);

    /**
     * Sets active casting cooldown for a skill.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     * @param seconds Cooldown duration in seconds
     */
    void setCooldown(Player player, ResourceLocation skillId, double seconds);

    /**
     * Clears active casting cooldown for a skill.
     * @param player Target player
     * @param skillId Skill ResourceLocation
     */
    void clearCooldown(Player player, ResourceLocation skillId);

    /**
     * Gets all skill IDs that have been learned/leveled by the player.
     * @param player Target player
     * @return Set of learned skill ResourceLocations
     */
    Set<ResourceLocation> getAllLearnedSkills(Player player);

    /**
     * Gets the currently active transformation/form skill ID.
     * @param player Target player
     * @return Active form ResourceLocation, or null if no form active
     */
    ResourceLocation getActiveForm(Player player);

    /**
     * Sets the active transformation/form skill ID.
     * @param player Target player
     * @param formSkillId Form skill ResourceLocation, or null to clear
     */
    void setActiveForm(Player player, ResourceLocation formSkillId);
}
