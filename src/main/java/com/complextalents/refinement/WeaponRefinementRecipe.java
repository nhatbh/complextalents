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

    /**
     * Checks if a weapon has recyclable XP (60% of XP gained beyond its starting tier base XP).
     */
    public static boolean isRecyclableWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(stack);
        if (startingTier <= 0) return false;

        int totalXp = WeaponMasteryManager.getRefineXp(stack);
        int baseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = WeaponMasteryManager.getXpForRank(baseRank);

        int gainedXp = Math.max(0, totalXp - startingXp);
        return gainedXp > 0;
    }

    /**
     * Calculates the recyclable XP from a weapon:
     * 60% of actual refined XP (total XP minus starting tier base XP).
     */
    public static int getRecyclableXp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(stack);
        if (startingTier <= 0) return 0;

        int totalXp = WeaponMasteryManager.getRefineXp(stack);
        int baseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = WeaponMasteryManager.getXpForRank(baseRank);

        int gainedXp = Math.max(0, totalXp - startingXp);
        return (int) (gainedXp * 0.60);
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return stack.getItem() instanceof RefinementGemItem || isRecyclableWeapon(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return WeaponMasteryManager.getInstance().getWeaponTier(stack) > 0;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return false; // Addition slot (Slot 2) is unused for refinement/recycling
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack slot0 = container.getItem(0);       // Template slot (Gem OR Sacrifice Weapon)
        ItemStack mainStack = container.getItem(1);   // Base slot (Target Weapon to enhance)
        ItemStack slot2 = container.getItem(2);       // Addition slot (Must be empty)

        if (!slot2.isEmpty() || mainStack.isEmpty() || slot0.isEmpty()) {
            return false;
        }

        int targetTier = WeaponMasteryManager.getInstance().getWeaponTier(mainStack);
        if (targetTier <= 0) {
            return false;
        }

        // Check if target weapon is already at max XP (Rank 20 Pinnacle = 5.5M XP)
        int maxCumRank = WeaponMasteryManager.getMaxCumulativeRankForStartingTier(targetTier);
        int maxXp = WeaponMasteryManager.getXpForRank(maxCumRank);
        int currentXp = WeaponMasteryManager.getRefineXp(mainStack);

        if (currentXp >= maxXp) {
            return false;
        }

        // Mode 1: Refinement Gem in Template slot (Slot 0)
        if (slot0.getItem() instanceof RefinementGemItem) {
            return true;
        }

        // Mode 2: Weapon Recycling Recipe (Sacrifice weapon in Template slot 0)
        if (isRecyclableWeapon(slot0) && slot0 != mainStack) {
            return getRecyclableXp(slot0) > 0;
        }

        return false;
    }

    @Override
    public @NotNull ItemStack assemble(Container container, RegistryAccess registryAccess) {
        if (!matches(container, null)) return ItemStack.EMPTY;

        ItemStack slot0 = container.getItem(0);
        ItemStack mainStack = container.getItem(1);

        // Clone main target weapon preserving NBT, enchantments, durability, name, etc.
        ItemStack result = mainStack.copy();
        result.setCount(1);

        int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(mainStack);
        int baseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int maxCumRank = WeaponMasteryManager.getMaxCumulativeRankForStartingTier(startingTier);
        int maxXp = WeaponMasteryManager.getXpForRank(maxCumRank);
        int currentXp = WeaponMasteryManager.getRefineXp(mainStack);

        int xpGained = 0;
        int gemsToConsume = 0;
        ItemStack sacrificeStack = ItemStack.EMPTY;

        if (slot0.getItem() instanceof RefinementGemItem gemItem) {
            int gemsAvailable = slot0.getCount();
            int xpNeeded = maxXp - currentXp;
            int xpPerGem = WeaponMasteryManager.getGemXpValue(gemItem.getTier());

            int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
            gemsToConsume = Math.max(1, Math.min(gemsAvailable, gemsNeeded));
            xpGained = gemsToConsume * xpPerGem;
        } else if (isRecyclableWeapon(slot0)) {
            sacrificeStack = slot0;
            xpGained = getRecyclableXp(sacrificeStack);
        }

        if (xpGained <= 0) return ItemStack.EMPTY;

        int newXp = Math.min(maxXp, currentXp + xpGained);
        int newCumRank = WeaponMasteryManager.getRankFromXp(newXp, maxCumRank);
        int newRefineRank = Math.max(0, newCumRank - baseRank);

        CompoundTag tag = result.getOrCreateTag();

        // Ensure RefineSeed exists on mainStack and result
        java.util.UUID seedUuid = WeaponMasteryManager.getOrCreateRefineSeed(mainStack);
        tag.putUUID("RefineSeed", seedUuid);

        tag.putInt("RefineXP", newXp);
        tag.putInt("RefineRank", newRefineRank);

        if (gemsToConsume > 0) {
            tag.putInt("GemsUsed", gemsToConsume);
        } else if (!sacrificeStack.isEmpty()) {
            // Build reset sacrifice weapon stack (XP reset to starting tier base XP, preserving item!)
            ItemStack resetSacrifice = sacrificeStack.copy();
            resetSacrifice.setCount(1);

            int sacStartingTier = WeaponMasteryManager.getInstance().getWeaponTier(resetSacrifice);
            int sacBaseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(sacStartingTier);
            int sacStartingXp = WeaponMasteryManager.getXpForRank(sacBaseRank);

            CompoundTag sacTag = resetSacrifice.getOrCreateTag();
            sacTag.putInt("RefineXP", sacStartingXp);
            sacTag.putInt("RefineRank", 0);

            if (sacTag.contains("RefineVariances", net.minecraft.nbt.Tag.TAG_LIST)) {
                sacTag.remove("RefineVariances");
            }

            tag.put("ResetSacrificeItem", resetSacrifice.save(new CompoundTag()));
        }

        // Restore all durability damage
        result.setDamageValue(0);

        // Make Unbreakable on refinement
        tag.putBoolean("Unbreakable", true);

        // Preserve existing per-level variances and roll for ALL new manual refine ranks gained!
        net.minecraft.nbt.ListTag varianceList = tag.contains("RefineVariances", net.minecraft.nbt.Tag.TAG_LIST)
                ? tag.getList("RefineVariances", net.minecraft.nbt.Tag.TAG_FLOAT).copy()
                : new net.minecraft.nbt.ListTag();

        while (varianceList.size() < newRefineRank) {
            int rankToRoll = varianceList.size() + 1;
            float v = WeaponMasteryManager.rollRefineVarianceForRank(result, rankToRoll);
            varianceList.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        tag.put("RefineVariances", varianceList);

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
        if (stack == null || stack.isEmpty()) return 0;
        int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(stack);
        if (startingTier <= 0) return 0;
        int baseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int maxCumRank = WeaponMasteryManager.getMaxCumulativeRankForStartingTier(startingTier);
        int xp = WeaponMasteryManager.getRefineXp(stack);
        int cumRank = WeaponMasteryManager.getRankFromXp(xp, maxCumRank);
        return Math.max(0, cumRank - baseRank);
    }

    public static int getMaxRefineRankForTier(int tier) {
        return WeaponMasteryManager.getMaxRefinesForTier(tier);
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
