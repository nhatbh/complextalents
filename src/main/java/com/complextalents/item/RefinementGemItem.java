package com.complextalents.item;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RefinementGemItem extends Item {

    private final CrateRarity tier;

    public RefinementGemItem(Properties properties, CrateRarity tier) {
        super(properties);
        this.tier = tier;
    }

    public CrateRarity getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        int xp = WeaponMasteryManager.getGemXpValue(tier);

        // Simple usage explanation
        tooltipComponents.add(Component.literal("Used in the Smithing Table to enhance Weapons & Guns.")
                .withStyle(ChatFormatting.GRAY));

        // XP granted
        tooltipComponents.add(Component.literal("Grants ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("+" + String.format("%,d", xp) + " Refinement XP").withStyle(ChatFormatting.YELLOW)));

        // Flavor text
        String flavor = getFlavorText(tier);
        if (flavor != null && !flavor.isEmpty()) {
            tooltipComponents.add(Component.literal(flavor).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private String getFlavorText(CrateRarity tier) {
        return switch (tier) {
            case COMMON -> "Infused with faint energy, suitable for basic armaments.";
            case UNCOMMON -> "Resonates with refined power, strengthening seasoned gear.";
            case RARE -> "Pulsing with potent mana, elevating battle-proven weapons.";
            case EPIC -> "Radiates intense force, forging weapons fit for legends.";
            case LEGENDARY -> "A pinnacle matrix of ancient craftsmanship.";
        };
    }
}
