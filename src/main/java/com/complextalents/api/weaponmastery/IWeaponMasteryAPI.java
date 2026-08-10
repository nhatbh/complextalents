package com.complextalents.api.weaponmastery;

import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Public API for the ComplexTalents Weapon Mastery System.
 */
public interface IWeaponMasteryAPI {

    /**
     * Gets the WeaponPath assigned to an item ID.
     * @param itemId ResourceLocation of the item
     * @return The WeaponPath, or null if unassigned
     */
    WeaponPath getWeaponPath(ResourceLocation itemId);

    /**
     * Gets the WeaponPath assigned to an ItemStack.
     * @param stack Target ItemStack
     * @return The WeaponPath, or null if unassigned
     */
    WeaponPath getWeaponPath(ItemStack stack);

    /**
     * Gets the minimum required rank value for an item ID.
     * @param itemId ResourceLocation of the item
     * @return Rank value (Novice=0, Apprentice=2, Adept=5, Expert=9, Master=14)
     */
    int getRequiredRankValue(ResourceLocation itemId);

    /**
     * Gets the weapon tier (1 to 5) for an ItemStack.
     * @param stack Target ItemStack
     * @return Tier 1-5 (0 if not a registered weapon)
     */
    int getWeaponTier(ItemStack stack);

    /**
     * Gets a player's accumulated damage for a weapon path.
     * @param player Target player
     * @param path Weapon path
     * @return Accumulated damage
     */
    double getAccumulatedDamage(Player player, WeaponPath path);

    /**
     * Adds accumulated damage to a player's weapon path.
     * @param player Target player
     * @param path Weapon path
     * @param amount Damage amount to add
     */
    void addAccumulatedDamage(Player player, WeaponPath path, double amount);

    /**
     * Gets a player's purchased mastery level for a weapon path (0-15).
     * @param player Target player
     * @param path Weapon path
     * @return Mastery level (0-15)
     */
    int getMasteryLevel(Player player, WeaponPath path);

    /**
     * Sets a player's purchased mastery level for a weapon path.
     * @param player Target player
     * @param path Weapon path
     * @param level Mastery level to set (0-15)
     */
    void setMasteryLevel(Player player, WeaponPath path, int level);

    /**
     * Gets all accumulated damage values for a player across all weapon paths.
     * @param player Target player
     * @return Map of WeaponPath to damage
     */
    Map<WeaponPath, Double> getAllAccumulatedDamage(Player player);

    /**
     * Gets all mastery levels for a player across all weapon paths.
     * @param player Target player
     * @return Map of WeaponPath to mastery level
     */
    Map<WeaponPath, Integer> getAllMasteryLevels(Player player);

    /**
     * Gets the required player leveling level to purchase/unlock a target weapon mastery level.
     * @param targetLevel Target level to unlock (1 to 15)
     * @return Required player level (Novice=1, Apprentice=10, Adept=20, Expert=30, Master=50)
     */
    int getRequiredPlayerLevelForTier(int targetLevel);

    /**
     * Dynamically registers or overrides a weapon mapping at runtime.
     * Allows third-party mods to register custom items to Weapon Master paths programmatically.
     *
     * @param itemId ResourceLocation of the item (e.g., "custommod:heavy_mace")
     * @param path WeaponPath to assign
     * @param requiredRankLevel Minimum required rank value (Novice=0, Apprentice=2, Adept=5, Expert=9, Master=14)
     */
    void registerWeaponOverride(ResourceLocation itemId, WeaponPath path, int requiredRankLevel);
}
