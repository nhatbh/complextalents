package com.complextalents.refinement;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.RefinementGemItem;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WeaponRefinementRecipe implements SmithingRecipe {

    private final ResourceLocation id;

    public WeaponRefinementRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return stack.getItem() instanceof RefinementGemItem;
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return WeaponMasteryManager.getInstance().getWeaponTier(stack) > 0;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return WeaponMasteryManager.getInstance().getWeaponTier(stack) > 0;
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack gemStack = container.getItem(0);       // Left: Refinement Gem
        ItemStack mainStack = container.getItem(1);      // Middle: Main Weapon
        ItemStack sacStack = container.getItem(2);       // Right: Sacrificial Weapon

        if (gemStack.isEmpty() || mainStack.isEmpty() || sacStack.isEmpty()) {
            return false;
        }

        // 1. Validate Gem
        if (!(gemStack.getItem() instanceof RefinementGemItem gemItem)) {
            return false;
        }

        // 2. Validate Weapons & Items Match
        if (mainStack.getItem() != sacStack.getItem()) {
            return false;
        }

        int weaponTier = WeaponMasteryManager.getInstance().getWeaponTier(mainStack);
        if (weaponTier <= 0) {
            return false;
        }

        // 3. Max Rank Check based on Weapon Level Rank (Novice: 1, Apprentice: 2, Adept: 3, Expert: 4, Master: 5)
        int maxRank = getMaxRefineRankForTier(weaponTier);
        int currentRank = getRefineRank(mainStack);
        if (currentRank >= maxRank) {
            return false;
        }

        // 4. Gem Tier Match Check (Gem Tier must match weapon tier)
        CrateRarity requiredGemTier = getGemTierForWeaponTier(weaponTier);
        if (gemItem.getTier() != requiredGemTier) {
            return false;
        }

        // 5. Sacrificial Rank Check (Sacrificial Rank <= Main Rank)
        int sacRank = getRefineRank(sacStack);
        if (sacRank > currentRank) {
            return false;
        }

        return true;
    }

    @Override
    public @NotNull ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack mainStack = container.getItem(1);
        if (mainStack.isEmpty()) return ItemStack.EMPTY;

        // Clone main stack preserving all NBT, enchantments, durability, name, etc.
        ItemStack result = mainStack.copy();
        result.setCount(1);

        int currentRank = getRefineRank(mainStack);
        CompoundTag tag = result.getOrCreateTag();
        tag.putInt("RefineRank", currentRank + 1);

        return result;
    }

    @Override
    public @NotNull ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY; // Dynamic smithing recipe result
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.WEAPON_REFINEMENT_SERIALIZER.get();
    }

    public static int getRefineRank(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("RefineRank")) {
            return stack.getTag().getInt("RefineRank");
        }
        return 0;
    }

    public static int getMaxRefineRankForTier(int tier) {
        // Novice = Tier 1 (1 max), Apprentice = Tier 2 (2 max), Adept = Tier 3 (3 max), Expert = Tier 4 (4 max), Master = Tier 5 (5 max)
        return Math.min(5, Math.max(1, tier));
    }

    public static CrateRarity getGemTierForWeaponTier(int tier) {
        switch (tier) {
            case 1: return CrateRarity.COMMON;
            case 2: return CrateRarity.UNCOMMON;
            case 3: return CrateRarity.RARE;
            case 4: return CrateRarity.EPIC;
            case 5: default: return CrateRarity.LEGENDARY;
        }
    }

    public static class Serializer implements RecipeSerializer<WeaponRefinementRecipe> {
        @Override
        public @NotNull WeaponRefinementRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            return new WeaponRefinementRecipe(recipeId);
        }

        @Override
        public @Nullable WeaponRefinementRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            return new WeaponRefinementRecipe(recipeId);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, WeaponRefinementRecipe recipe) {
        }
    }
}
