package com.complextalents.util;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class WeaponFinder {

    /**
     * Finds every item registered in the game that modifies the base attack damage
     * when held in the main hand.
     *
     * @return A list of items that are considered weapons.
     */
    public static List<Item> getAllWeapons() {
        List<Item> weapons = new ArrayList<>();

        // 1. Iterate through every item registered in the game
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            
            // 2. Get the attribute modifiers for holding the item in the main hand
            Multimap<Attribute, AttributeModifier> modifiers = item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND);

            // 3. Check if the item modifies the base attack damage
            if (modifiers.containsKey(Attributes.ATTACK_DAMAGE)) {
                weapons.add(item);
            }
        }

        return weapons;
    }
}
