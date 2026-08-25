package com.complextalents.mixin.tacz;

import com.complextalents.tacz.GunAttributeType;
import com.complextalents.tacz.GunAttributes;
import com.complextalents.tacz.GunType;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageCraft;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GunSmithTableMenu.class, remap = false)
public abstract class GunSmithTableMenuMixin {

    @Shadow(remap = false)
    @Nullable
    private GunSmithTableRecipe getRecipe(ResourceLocation recipeId, RecipeManager recipeManager) {
        throw new IllegalStateException("Shadow failed");
    }

    @Inject(
        method = "doCraft",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void injectDoCraftHead(ResourceLocation recipeId, Player player, CallbackInfo ci) {
        if (player == null) {
            return;
        }

        GunSmithTableRecipe recipe = getRecipe(recipeId, player.level().getRecipeManager());
        if (recipe == null) {
            return;
        }

        ItemStack resultStack = recipe.getResultItem(player.level().registryAccess()).copy();
        if (resultStack.isEmpty()) {
            return;
        }

        // Verify if crafted item is TACZ Ammo or Ammo Box
        boolean isAmmo = resultStack.getItem() instanceof IAmmo || resultStack.getItem() instanceof IAmmoBox;
        if (!isAmmo) {
            // Non-ammo item (guns, attachments, etc.) -> Let TACZ proceed with standard doCraft
            return;
        }

        // Cancel TACZ default doCraft for ammo items so we can handle custom yield & chunking
        ci.cancel();

        player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handler -> {
            // Handle material deduction if not creative mode
            if (!player.isCreative()) {
                Int2IntArrayMap recordCount = new Int2IntArrayMap();
                List<GunSmithTableIngredient> ingredients = recipe.getInputs();

                for (GunSmithTableIngredient ingredient : ingredients) {
                    int count = 0;
                    for (int slotIndex = 0; slotIndex < handler.getSlots(); slotIndex++) {
                        ItemStack stack = handler.getStackInSlot(slotIndex);
                        int stackCount = stack.getCount();
                        if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                            count += stackCount;
                            if (count <= ingredient.getCount()) {
                                recordCount.put(slotIndex, stackCount);
                            } else {
                                int remaining = count - ingredient.getCount();
                                recordCount.put(slotIndex, stackCount - remaining);
                                break;
                            }
                        }
                    }
                    // Material count insufficient -> Craft fails
                    if (count < ingredient.getCount()) {
                        return;
                    }
                }

                // Deduct ingredients
                for (int slotIndex : recordCount.keySet()) {
                    handler.extractItem(slotIndex, recordCount.get(slotIndex), false);
                }
            }

            // Spawn output ammo items with dynamic attribute yield multiplier
            Level level = player.level();
            if (!level.isClientSide) {
                double multiplier = GunAttributes.getValue(player, GunAttributeType.AMMO_CRAFTING_YIELD, GunType.GLOBAL);
                if (multiplier < 0) {
                    multiplier = 1.0;
                }

                int baseCount = resultStack.getCount();
                int totalCount = (int) Math.round(baseCount * multiplier);
                if (totalCount <= 0) {
                    totalCount = baseCount;
                }

                // Dynamically retrieve max stack size for this specific ammo item
                int maxStackSize = resultStack.getMaxStackSize();
                if (maxStackSize <= 0) {
                    maxStackSize = 64;
                }

                while (totalCount > 0) {
                    int chunkCount = Math.min(totalCount, maxStackSize);
                    ItemStack chunkStack = resultStack.copy();
                    chunkStack.setCount(chunkCount);

                    ItemEntity itemEntity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), chunkStack);
                    itemEntity.setPickUpDelay(0);
                    level.addFreshEntity(itemEntity);

                    totalCount -= chunkCount;
                }
            }

            // Sync container and send craft network response to client
            player.inventoryMenu.broadcastFullState();
            int menuContainerId = ((AbstractContainerMenu) (Object) this).containerId;
            NetworkHandler.sendToClientPlayer(new ServerMessageCraft(menuContainerId), player);
        });
    }
}
