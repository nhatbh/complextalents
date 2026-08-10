package com.complextalents.api.origin;

import com.complextalents.origin.ResourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Public API for the ComplexTalents Origin & Class Level System.
 */
public interface IOriginAPI {

    /**
     * Gets the player's active origin ID.
     * @param player Target player
     * @return Active origin ResourceLocation, or null if none
     */
    ResourceLocation getActiveOrigin(Player player);

    /**
     * Sets the player's active origin ID.
     * @param player Target player
     * @param originId Origin ResourceLocation to assign, or null to clear
     */
    void setActiveOrigin(Player player, ResourceLocation originId);

    /**
     * Gets the player's current origin level (1-5).
     * @param player Target player
     * @return Origin level
     */
    int getOriginLevel(Player player);

    /**
     * Sets the player's origin level (1-5).
     * @param player Target player
     * @param level New origin level (1 to 5)
     */
    void setOriginLevel(Player player, int level);

    /**
     * Gets the resource type (Mana, Energy, Rage, Focus, Flow, etc.) of the player's active origin.
     * @param player Target player
     * @return ResourceType or null
     */
    ResourceType getResourceType(Player player);

    /**
     * Gets the player's current origin class resource value.
     * @param player Target player
     * @return Current resource value
     */
    double getResource(Player player);

    /**
     * Sets the player's current origin class resource value.
     * @param player Target player
     * @param value New resource value
     */
    void setResource(Player player, double value);

    /**
     * Modifies the player's current origin class resource value.
     * @param player Target player
     * @param delta Amount to add (can be negative)
     */
    void modifyResource(Player player, double delta);

    /**
     * Clears all origin data from the player.
     * @param player Target player
     */
    void clearOrigin(Player player);
}
