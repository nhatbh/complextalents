package com.complextalents.item;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
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
        tooltipComponents.add(Component.literal("Used in Smithing Table to refine " + tier.getDisplayName() + " weapons.").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Combine with an identical sacrificial weapon.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
