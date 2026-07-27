package com.complextalents.refinement;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MagicAugmentItem;
import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SpellAugmentRecipe implements SmithingRecipe {

    private final ResourceLocation id;

    public SpellAugmentRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return stack.getItem() instanceof MagicAugmentItem;
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return isSpellItem(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return isSpellItem(stack) || stack.getItem() instanceof MagicAugmentItem;
    }

    public static boolean isSpellItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer container = ISpellContainer.get(stack);
                return container != null && !container.isEmpty();
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static int getSpellTier(ItemStack stack, int slotIndex) {
        try {
            if (ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer container = ISpellContainer.get(stack);
                if (container != null && !container.isEmpty()) {
                    SpellData spellData = container.getSpellAtIndex(slotIndex);
                    if (spellData != null && spellData.getSpell() != null) {
                        AbstractSpell spell = spellData.getSpell();
                        SpellRarity rarity = spell.getRarity(spellData.getLevel());
                        return Math.max(1, Math.min(5, rarity.getValue() + 1));
                    }
                }
            }
        } catch (Exception ignored) {}
        return 1;
    }

    public static int getSpellTier(ItemStack stack) {
        return getSpellTier(stack, 0);
    }

    public static List<CompoundTag> getAugments(ItemStack stack) {
        return getAugments(stack, 0);
    }

    public static List<CompoundTag> getAugments(ItemStack stack, int slotIndex) {
        List<CompoundTag> augments = new ArrayList<>();
        if (stack.isEmpty() || !stack.hasTag()) return augments;

        CompoundTag tag = stack.getTag();
        if (tag.contains("SpellAugmentData", Tag.TAG_COMPOUND)) {
            CompoundTag dataTag = tag.getCompound("SpellAugmentData");
            String key = "Slot_" + slotIndex;
            if (dataTag.contains(key, Tag.TAG_LIST)) {
                ListTag list = dataTag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    augments.add(list.getCompound(i));
                }
                return augments;
            }
        }

        // Backwards compatibility for root "SpellAugments" tag on slot 0
        if (slotIndex == 0 && tag.contains("SpellAugments", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SpellAugments", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                augments.add(list.getCompound(i));
            }
        }
        return augments;
    }

    public static void setAugments(ItemStack stack, int slotIndex, List<CompoundTag> augments) {
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag dataTag = tag.contains("SpellAugmentData", Tag.TAG_COMPOUND)
                ? tag.getCompound("SpellAugmentData")
                : new CompoundTag();
        String key = "Slot_" + slotIndex;

        if (augments == null || augments.isEmpty()) {
            dataTag.remove(key);
            if (slotIndex == 0) tag.remove("SpellAugments");
        } else {
            ListTag list = new ListTag();
            for (CompoundTag aug : augments) {
                list.add(aug.copy());
            }
            dataTag.put(key, list);
            if (slotIndex == 0) tag.put("SpellAugments", list);
        }
        tag.put("SpellAugmentData", dataTag);
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack slot0 = container.getItem(0);
        ItemStack slot1 = container.getItem(1);
        ItemStack slot2 = container.getItem(2);

        if (!isSpellItem(slot1)) {
            return false;
        }

        ItemStack gemStack = ItemStack.EMPTY;
        if (slot0.getItem() instanceof MagicAugmentItem) {
            gemStack = slot0;
        } else if (slot2.getItem() instanceof MagicAugmentItem) {
            gemStack = slot2;
        }

        if (gemStack.isEmpty()) {
            return false;
        }

        int targetSlot = 0;
        int spellTier = getSpellTier(slot1, targetSlot);
        int maxSockets = Math.max(1, Math.min(5, spellTier));
        List<CompoundTag> currentAugments = getAugments(slot1, targetSlot);
        if (currentAugments.size() >= maxSockets) {
            return false;
        }

        CrateRarity gemTier = MagicAugmentItem.getTier(gemStack);
        int gemTierVal = gemTier.ordinal() + 1;
        if (gemTierVal > spellTier) {
            return false;
        }

        return true;
    }

    @Override
    public @NotNull ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack mainStack = container.getItem(1);
        ItemStack slot0 = container.getItem(0);
        ItemStack slot2 = container.getItem(2);

        ItemStack gemStack = ItemStack.EMPTY;
        if (slot0.getItem() instanceof MagicAugmentItem) {
            gemStack = slot0;
        } else if (slot2.getItem() instanceof MagicAugmentItem) {
            gemStack = slot2;
        }

        if (mainStack.isEmpty() || gemStack.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = mainStack.copy();
        result.setCount(1);

        if (gemStack.getItem() instanceof MagicAugmentItem augItem) {
            int targetSlot = 0;
            List<CompoundTag> augments = getAugments(result, targetSlot);

            CompoundTag newAugment = new CompoundTag();
            newAugment.putString("Type", augItem.getAugmentType().name());
            newAugment.putInt("Tier", MagicAugmentItem.getTier(gemStack).ordinal());
            augments.add(newAugment);

            setAugments(result, targetSlot, augments);
        }

        return result;
    }

    @Override
    public @NotNull ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.SPELL_AUGMENT_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<SpellAugmentRecipe> {
        @Override
        public @NotNull SpellAugmentRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            return new SpellAugmentRecipe(recipeId);
        }

        @Override
        public @Nullable SpellAugmentRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            return new SpellAugmentRecipe(recipeId);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SpellAugmentRecipe recipe) {
        }
    }
}
