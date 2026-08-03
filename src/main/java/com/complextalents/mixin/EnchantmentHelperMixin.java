package com.complextalents.mixin;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    /**
     * Disables all enchantments on armor items (Head, Chest, Legs, Feet) when durability is at broken threshold (durability <= 1 / damage >= maxDamage - 1).
     */
    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void complextalents$disableBrokenArmorEnchantments(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack != null && !stack.isEmpty() && isBrokenArmor(stack)) {
            cir.setReturnValue(0);
        }
    }

    private static boolean isBrokenArmor(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        var slot = net.minecraft.world.entity.LivingEntity.getEquipmentSlotForItem(stack);
        boolean isArmorSlot = slot == net.minecraft.world.entity.EquipmentSlot.HEAD
                || slot == net.minecraft.world.entity.EquipmentSlot.CHEST
                || slot == net.minecraft.world.entity.EquipmentSlot.LEGS
                || slot == net.minecraft.world.entity.EquipmentSlot.FEET;
        
        if (stack.getItem() instanceof ArmorItem || isArmorSlot) {
            // Remaining durability <= 1 means current damage >= maxDamage - 1
            return stack.getDamageValue() >= stack.getMaxDamage() - 1;
        }
        return false;
    }
}
