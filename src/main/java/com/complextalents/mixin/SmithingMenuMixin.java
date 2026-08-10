package com.complextalents.mixin;

import com.complextalents.item.RefinementGemItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeRefinement(Player player, ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return;
        CompoundTag tag = stack.getTag();
        SmithingMenu menu = (SmithingMenu) (Object) this;

        // Handle Extra Gem Stack Consumption (Slot 0 is the Template / Gem slot)
        if (tag.contains("GemsUsed")) {
            int gemsUsed = tag.getInt("GemsUsed");
            tag.remove("GemsUsed");

            if (gemsUsed > 1) {
                Slot gemSlot = menu.getSlot(0);
                if (gemSlot != null && gemSlot.hasItem() && gemSlot.getItem().getItem() instanceof RefinementGemItem) {
                    gemSlot.getItem().shrink(gemsUsed - 1);
                }
            }
        }

        // Handle Sacrifice Weapon Preservation (Slot 0 is the Template / Sacrifice Weapon slot)
        if (tag.contains("ResetSacrificeItem")) {
            CompoundTag sacTag = tag.getCompound("ResetSacrificeItem");
            tag.remove("ResetSacrificeItem");
            tag.remove("RecycledSlotIndex");

            ItemStack resetWeapon = ItemStack.of(sacTag);
            if (!resetWeapon.isEmpty()) {
                Slot sacSlot = menu.getSlot(0);
                if (sacSlot != null) {
                    // Set count to 2 so vanilla's shrink(1) right after leaves count = 1 intact in Slot 0!
                    resetWeapon.setCount(2);
                    sacSlot.set(resetWeapon);
                }
            }
        }
    }
}
