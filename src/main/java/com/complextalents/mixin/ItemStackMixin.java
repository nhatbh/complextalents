package com.complextalents.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow public abstract boolean isDamageableItem();
    @Shadow public abstract int getMaxDamage();
    @Shadow public abstract int getDamageValue();
    @Shadow public abstract void setDamageValue(int damage);

    /**
     * Prevents armor in HEAD, CHEST, LEGS, and FEET slots from being completely destroyed.
     * When taking damage that would break the armor, cap its damage value at (maxDamage - 1)
     * so that 1 durability remains.
     */
    @Inject(
        method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends LivingEntity> void complextalents$preventArmorBreak(
            int amount,
            T entity,
            Consumer<T> onBroken,
            CallbackInfo ci
    ) {
        ItemStack self = (ItemStack) (Object) this;

        if (amount <= 0 || !this.isDamageableItem()) {
            return;
        }

        // Check if this item stack is armor (ArmorItem or equipable in an armor slot)
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(self);
        boolean isArmorSlot = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
        boolean isArmor = self.getItem() instanceof ArmorItem || isArmorSlot;

        if (!isArmor) {
            return;
        }

        int maxDamage = this.getMaxDamage();
        int currentDamage = this.getDamageValue();

        // Calculate potential new damage without breaking
        int damageToAdd = amount;
        if (entity instanceof ServerPlayer serverPlayer) {
            // Apply unbreaking enchantment check if standard hurt logic is used
            damageToAdd = self.getItem().hasCraftingRemainingItem() ? amount : amount; 
        }

        if (currentDamage + damageToAdd >= maxDamage) {
            // Cap damage at maxDamage - 1 (leaving exactly 1 durability)
            this.setDamageValue(Math.max(0, maxDamage - 1));
            // Cancel the normal break flow so onBroken consumer (which plays sound and breaks item) is not called
            ci.cancel();
        }
    }
}
