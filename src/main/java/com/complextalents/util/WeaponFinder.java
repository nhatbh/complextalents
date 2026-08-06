package com.complextalents.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.ArrayList;
import java.util.List;

public class WeaponFinder {

    /**
     * Finds every item registered in the game that has an Epic Fight Item Capability,
     * strictly excluding armor items that equip into armor slots.
     *
     * @return A list of all weapon items with Epic Fight capabilities.
     */
    public static List<Item> getAllWeapons() {
        List<Item> weapons = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (isWeaponCandidate(item)) {
                weapons.add(item);
            }
        }

        return weapons;
    }

    public static boolean isWeaponCandidate(Item item) {
        if (item == null || item == Items.AIR) return false;

        // 1. Exclude ArmorItem class
        if (item instanceof ArmorItem) return false;

        ItemStack stack = new ItemStack(item);

        // 2. Exclude items equipped into armor slots (HEAD, CHEST, LEGS, FEET)
        try {
            EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                return false;
            }
        } catch (Exception ignored) {}

        if (item instanceof Equipable equipable) {
            try {
                EquipmentSlot slot = equipable.getEquipmentSlot();
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    return false;
                }
            } catch (Exception ignored) {}
        }

        // 3. Check Epic Fight Item Capability
        try {
            CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(stack);
            if (cap != null && cap != CapabilityItem.EMPTY) {
                if (cap.getWeaponCategory() != null && cap.getWeaponCategory() != CapabilityItem.WeaponCategories.NOT_WEAPON) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }
}
