package com.complextalents.refinement;

import com.complextalents.gunmastery.GunRefinementManager;
import com.complextalents.gunmastery.classification.GunClassificationManager;
import com.complextalents.item.RefinementGemItem;
import com.google.gson.JsonObject;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunRefinementRecipe implements SmithingRecipe {

    private final ResourceLocation id;

    public GunRefinementRecipe(ResourceLocation id) {
        this.id = id;
    }

    public static boolean isRecyclableGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return false;

        int totalXp = GunRefinementManager.getRefineXp(stack);
        int baseRank = GunRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = GunRefinementManager.getXpForRank(baseRank);

        int gainedXp = Math.max(0, totalXp - startingXp);
        return gainedXp > 0;
    }

    public static int getRecyclableXp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return 0;

        int totalXp = GunRefinementManager.getRefineXp(stack);
        int baseRank = GunRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = GunRefinementManager.getXpForRank(baseRank);

        int gainedXp = Math.max(0, totalXp - startingXp);
        return (int) (gainedXp * 0.60);
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return stack.getItem() instanceof RefinementGemItem || isRecyclableGun(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return GunClassificationManager.getGunTier(stack) > 0;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return false; // Slot 2 unused
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack slot0 = container.getItem(0);       // Template slot (Gem OR Sacrifice Gun)
        ItemStack mainStack = container.getItem(1);   // Base slot (Target Gun)
        ItemStack slot2 = container.getItem(2);       // Addition slot (Must be empty)

        if (!slot2.isEmpty() || mainStack.isEmpty() || slot0.isEmpty()) {
            return false;
        }

        int targetTier = GunClassificationManager.getGunTier(mainStack);
        if (targetTier <= 0) {
            return false;
        }

        int maxCumRank = 20; // 20 Pinnacle = 5.5M XP
        int maxXp = GunRefinementManager.getXpForRank(maxCumRank);
        int currentXp = GunRefinementManager.getRefineXp(mainStack);

        if (currentXp >= maxXp) {
            return false;
        }

        if (slot0.getItem() instanceof RefinementGemItem) {
            return true;
        }

        if (isRecyclableGun(slot0) && slot0 != mainStack) {
            return getRecyclableXp(slot0) > 0;
        }

        return false;
    }

    @Override
    public @NotNull ItemStack assemble(Container container, RegistryAccess registryAccess) {
        if (!matches(container, null)) return ItemStack.EMPTY;

        ItemStack slot0 = container.getItem(0);
        ItemStack mainStack = container.getItem(1);

        ItemStack result = mainStack.copy();
        result.setCount(1);

        int maxCumRank = 20;
        int maxXp = GunRefinementManager.getXpForRank(maxCumRank);
        int currentXp = GunRefinementManager.getRefineXp(mainStack);

        int xpGained = 0;
        int gemsToConsume = 0;
        ItemStack sacrificeStack = ItemStack.EMPTY;

        if (slot0.getItem() instanceof RefinementGemItem gemItem) {
            int gemsAvailable = slot0.getCount();
            int xpNeeded = maxXp - currentXp;
            int xpPerGem = GunRefinementManager.getGemXpValue(gemItem.getTier());

            int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
            gemsToConsume = Math.max(1, Math.min(gemsAvailable, gemsNeeded));
            xpGained = gemsToConsume * xpPerGem;
        } else if (isRecyclableGun(slot0)) {
            sacrificeStack = slot0;
            xpGained = getRecyclableXp(sacrificeStack);
        }

        if (xpGained <= 0) return ItemStack.EMPTY;

        int newXp = Math.min(maxXp, currentXp + xpGained);
        GunRefinementManager.applyRefinementDataToStack(result, newXp);

        net.minecraft.nbt.CompoundTag tag = result.getOrCreateTag();
        if (gemsToConsume > 0) {
            tag.putInt("GemsUsed", gemsToConsume);
        } else if (!sacrificeStack.isEmpty()) {
            ItemStack resetSacrifice = sacrificeStack.copy();
            resetSacrifice.setCount(1);

            int sacStartingTier = GunClassificationManager.getGunTier(resetSacrifice);
            int sacBaseRank = GunRefinementManager.getBaseCumulativeLevelForStartingTier(sacStartingTier);
            int sacStartingXp = GunRefinementManager.getXpForRank(sacBaseRank);

            GunRefinementManager.applyRefinementDataToStack(resetSacrifice, sacStartingXp);
            tag.put("ResetSacrificeItem", resetSacrifice.save(new net.minecraft.nbt.CompoundTag()));
        }

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GUN_REFINEMENT_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<GunRefinementRecipe> {
        @Override
        public GunRefinementRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new GunRefinementRecipe(id);
        }

        @Override
        public GunRefinementRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new GunRefinementRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, GunRefinementRecipe recipe) {
        }
    }
}
