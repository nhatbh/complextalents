package com.complextalents.api.spellmastery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;

/**
 * Public API for the ComplexTalents Spell Mastery System.
 */
public interface ISpellMasteryAPI {

    /**
     * Gets the mastery level for a specific magic school (0-5: Novice to Apex/Legendary).
     * @param player Target player
     * @param schoolId ResourceLocation of the magic school (e.g., "irons_spellbooks:fire")
     * @return Mastery level (0-5)
     */
    int getMasteryLevel(Player player, ResourceLocation schoolId);

    /**
     * Sets the mastery level for a specific magic school.
     * @param player Target player
     * @param schoolId ResourceLocation of the magic school
     * @param level Mastery level to set
     */
    void setMasteryLevel(Player player, ResourceLocation schoolId, int level);

    /**
     * Checks if a spell tier is learned by the player.
     * @param player Target player
     * @param spellId ResourceLocation of the spell
     * @param level Level of the spell
     * @return True if learned, false otherwise
     */
    boolean isSpellLearned(Player player, ResourceLocation spellId, int level);

    /**
     * Grants a spell tier to the player.
     * @param player Target player
     * @param spellId ResourceLocation of the spell
     * @param level Level of the spell
     */
    void learnSpell(Player player, ResourceLocation spellId, int level);

    /**
     * Removes a spell from the player's learned spells.
     * @param player Target player
     * @param spellId ResourceLocation of the spell
     */
    void forgetSpell(Player player, ResourceLocation spellId);

    /**
     * Gets all learned spells for a player.
     * @param player Target player
     * @return Set of spell ResourceLocations
     */
    Set<ResourceLocation> getLearnedSpells(Player player);

    /**
     * Gets all magic school mastery levels for a player.
     * @param player Target player
     * @return Map of school ResourceLocations to mastery levels
     */
    Map<ResourceLocation, Integer> getAllMasteryLevels(Player player);

    /**
     * Gets the purchased mastery tier level for a school.
     * @param player Target player
     * @param schoolId School ResourceLocation
     * @return Purchased tier level
     */
    int getPurchasedMastery(Player player, ResourceLocation schoolId);

    /**
     * Records a purchased mastery upgrade and consumes SP on server.
     * @param player Target player
     * @param schoolId School ResourceLocation
     * @param tier Tier level purchased
     * @param cost SP cost
     */
    void purchaseMastery(Player player, ResourceLocation schoolId, int tier, int cost);
}
