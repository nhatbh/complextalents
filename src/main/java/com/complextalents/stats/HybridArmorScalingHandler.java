package com.complextalents.stats;

import com.complextalents.TalentsMod;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamically converts all equipment armor (vanilla and modded) into a split ratio of Flat Armor and Percentage Armor.
 * 
 * <ul>
 *   <li><b>Flat Armor Retention:</b> Retains 50% of the item's original flat armor value.</li>
 *   <li><b>Percentage Armor Conversion:</b> Grants +2.5% Percentage Armor per original flat armor point (MULTIPLY_BASE).</li>
 * </ul>
 * 
 * <p>Example (Diamond Chestplate, original +8 flat armor):</p>
 * <ul>
 *   <li>+4.0 Flat Armor</li>
 *   <li>+20.0% Armor Multiplier (scales player base & level-up armor)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class HybridArmorScalingHandler {

    // Retain 50% of original flat armor
    private static final double FLAT_ARMOR_RETENTION_RATIO = 0.5;

    // +2.5% armor multiplier per original flat armor point (0.025)
    private static final double PERCENT_PER_ARMOR_POINT = 0.025;

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        net.minecraft.world.entity.EquipmentSlot slot = event.getSlotType();
        if (slot != net.minecraft.world.entity.EquipmentSlot.HEAD &&
            slot != net.minecraft.world.entity.EquipmentSlot.CHEST &&
            slot != net.minecraft.world.entity.EquipmentSlot.LEGS &&
            slot != net.minecraft.world.entity.EquipmentSlot.FEET) {
            return;
        }

        List<AttributeModifier> flatArmorModifiers = new ArrayList<>();

        // Collect all flat armor modifiers on the item
        for (AttributeModifier modifier : event.getModifiers().get(Attributes.ARMOR)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                flatArmorModifiers.add(modifier);
            }
        }

        // Process flat armor modifiers
        for (AttributeModifier flatMod : flatArmorModifiers) {
            double originalFlatAmount = flatMod.getAmount();
            if (originalFlatAmount <= 0) continue;

            // 1. Remove original flat modifier
            event.removeModifier(Attributes.ARMOR, flatMod);

            // 2. Re-add retained flat armor (50% of original)
            double newFlatAmount = originalFlatAmount * FLAT_ARMOR_RETENTION_RATIO;
            if (newFlatAmount > 0) {
                AttributeModifier scaledFlatMod = new AttributeModifier(
                        flatMod.getId(),
                        flatMod.getName() + " (Flat Split)",
                        newFlatAmount,
                        AttributeModifier.Operation.ADDITION
                );
                event.addModifier(Attributes.ARMOR, scaledFlatMod);
            }

            // 3. Add percentage bonus (+2.5% per original flat armor point)
            double percentAmount = originalFlatAmount * PERCENT_PER_ARMOR_POINT;
            UUID percentUuid = new UUID(
                    flatMod.getId().getMostSignificantBits() ^ 0x55555555L,
                    flatMod.getId().getLeastSignificantBits() ^ 0xAAAAAAAAL
            );

            AttributeModifier percentMod = new AttributeModifier(
                    percentUuid,
                    flatMod.getName() + " (% Scaling Bonus)",
                    percentAmount,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            event.addModifier(Attributes.ARMOR, percentMod);
        }
    }

    /**
     * Adds percentage armor bonus info to armor item tooltips.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Check if the item is an armor piece or has armor modifiers in head/chest/legs/feet slots
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            
            var modifiers = stack.getAttributeModifiers(slot).get(Attributes.ARMOR);
            for (AttributeModifier mod : modifiers) {
                if (mod.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE && mod.getName().contains("% Scaling Bonus")) {
                    double percent = mod.getAmount() * 100.0;
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                            String.format("§9+%.1f%% Armor Multiplier", percent)
                    ));
                }
            }
        }
    }
}
