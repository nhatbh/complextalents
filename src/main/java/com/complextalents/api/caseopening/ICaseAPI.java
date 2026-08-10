package com.complextalents.api.caseopening;

import com.complextalents.caseopening.CaseReward;
import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Public API for the ComplexTalents Case (Crate) System.
 */
public interface ICaseAPI {

    /**
     * Creates a Weapon Case ItemStack of a given path and rarity.
     * @param path Weapon path
     * @param rarity Crate rarity
     * @return Weapon Case ItemStack
     */
    ItemStack createWeaponCaseItem(WeaponPath path, CrateRarity rarity);

    /**
     * Creates a Magic Case ItemStack of a given school and rarity.
     * @param schoolId School ResourceLocation
     * @param rarity Crate rarity
     * @return Magic Case ItemStack
     */
    ItemStack createMagicCaseItem(ResourceLocation schoolId, CrateRarity rarity);

    /**
     * Triggers the unboxing UI screen for a Weapon Case on a server player.
     * @param player Target player
     * @param path Weapon path
     * @param rarity Crate rarity
     */
    void openWeaponCase(ServerPlayer player, WeaponPath path, CrateRarity rarity);

    /**
     * Triggers the unboxing UI screen for a Magic Case on a server player.
     * @param player Target player
     * @param schoolId School ResourceLocation
     * @param rarity Crate rarity
     */
    void openMagicCase(ServerPlayer player, ResourceLocation schoolId, CrateRarity rarity);

    /**
     * Builds the weighted CaseReward loot pool for a Weapon Case.
     * @param path Weapon path
     * @param rarity Crate rarity
     * @return List of CaseReward entries
     */
    List<CaseReward> buildWeaponPool(WeaponPath path, CrateRarity rarity);

    /**
     * Builds the weighted CaseReward loot pool for a Magic Case.
     * @param schoolId School ResourceLocation
     * @param rarity Crate rarity
     * @return List of CaseReward entries
     */
    List<CaseReward> buildMagicPool(ResourceLocation schoolId, CrateRarity rarity);

    /**
     * Rolls a random CaseReward from a pool using weighted probability.
     * @param pool List of rewards
     * @param random RandomSource
     * @return Winning CaseReward
     */
    CaseReward rollFromPool(List<CaseReward> pool, RandomSource random);

    /**
     * Grants a CaseReward to a player (handles spell auto-learning or item inventory addition).
     * @param player Target player
     * @param reward Winning reward
     */
    void grantRewardToPlayer(ServerPlayer player, CaseReward reward);
}
