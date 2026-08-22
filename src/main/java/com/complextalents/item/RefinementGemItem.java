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
        tooltipComponents.add(Component.literal("Used in the Refining Anvil to refine weapons, guns, and spells.")
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
            case COMMON -> "A gray, gemlike stone containing a faint residue of ancient energy.";
            case UNCOMMON -> "A rough, stone-colored crystal formation shot through with orange veins.";
            case RARE -> "A brilliant blue gem pulsing with a steady current of pure energy.";
            case EPIC -> "A deep purple gem radiating intense, mystical forces.";
            case LEGENDARY -> "A gold gem representing the pinnacle of ancient power.";
        };
    }
}
