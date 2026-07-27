package com.complextalents.item;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MagicAugmentItem extends Item {

    public enum AugmentType {
        POWER("Power", "Spell Damage", CrateRarity.COMMON),
        MANA_SAVER("Mana Saver", "Mana Cost Reduction", CrateRarity.COMMON),
        HASTE("Haste", "Cooldown Reduction", CrateRarity.COMMON),
        SPEED("Speed", "Cast Speed", CrateRarity.UNCOMMON),
        PRECISION("Precision", "Spell Crit Chance", CrateRarity.UNCOMMON),
        FATAL("Fatal", "Spell Crit Damage", CrateRarity.UNCOMMON),
        VAMPIRISM("Vampirism", "Spell Lifesteal", CrateRarity.RARE),
        PIERCE("Pierce", "Resist Penetration", CrateRarity.RARE),
        OVERCLOCK("Overclock", "Spell Level Boost", CrateRarity.EPIC),
        RECAST("Recast", "Cooldown Reset Chance", CrateRarity.LEGENDARY);

        private final String displayName;
        private final String statDescription;
        private final CrateRarity minRarity;

        AugmentType(String displayName, String statDescription, CrateRarity minRarity) {
            this.displayName = displayName;
            this.statDescription = statDescription;
            this.minRarity = minRarity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getStatDescription() {
            return statDescription;
        }

        public CrateRarity getMinRarity() {
            return minRarity;
        }
    }

    private final AugmentType augmentType;

    public MagicAugmentItem(Properties properties, AugmentType augmentType) {
        super(properties);
        this.augmentType = augmentType;
    }

    public AugmentType getAugmentType() {
        return augmentType;
    }

    public static CrateRarity getTier(ItemStack stack) {
        if (stack.getItem() instanceof MagicAugmentItem augItem) {
            if (stack.hasTag() && stack.getTag().contains("Tier")) {
                int tierOrdinal = stack.getTag().getInt("Tier");
                CrateRarity[] rarities = CrateRarity.values();
                if (tierOrdinal >= 0 && tierOrdinal < rarities.length) {
                    return rarities[tierOrdinal];
                }
            }
            return augItem.getAugmentType().getMinRarity();
        }
        return CrateRarity.COMMON;
    }

    public static ItemStack createStack(MagicAugmentItem item, CrateRarity tier, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("Tier", tier.ordinal());
        return stack;
    }

    public static String getBonusText(AugmentType type, CrateRarity tier) {
        int tierLevel = switch (tier) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
        };

        return switch (type) {
            case POWER -> "+" + switch (tierLevel) { case 1 -> 5; case 2 -> 10; case 3 -> 15; case 4 -> 22; default -> 30; } + "% Spell Damage";
            case MANA_SAVER -> "-" + switch (tierLevel) { case 1 -> 6; case 2 -> 12; case 3 -> 18; case 4 -> 24; default -> 30; } + "% Mana Cost";
            case HASTE -> "-" + switch (tierLevel) { case 1 -> 6; case 2 -> 12; case 3 -> 18; case 4 -> 24; default -> 30; } + "% Cooldown";
            case SPEED -> "+" + switch (tierLevel) { case 2 -> 10; case 3 -> 18; case 4 -> 25; default -> 35; } + "% Cast Speed";
            case PRECISION -> "+" + switch (tierLevel) { case 2 -> 6; case 3 -> 10; case 4 -> 15; default -> 20; } + "% Spell Crit Chance";
            case FATAL -> "+" + switch (tierLevel) { case 2 -> 20; case 3 -> 32; case 4 -> 45; default -> 60; } + "% Spell Crit Damage";
            case VAMPIRISM -> "+" + switch (tierLevel) { case 3 -> 8; case 4 -> 14; default -> 20; } + "% Spell Lifesteal";
            case PIERCE -> "+" + switch (tierLevel) { case 3 -> 10; case 4 -> 18; default -> 25; } + "% Resist Penetration";
            case OVERCLOCK -> (tierLevel >= 5 ? "+2" : "+1") + " Spell Level Boost";
            case RECAST -> "35% Cooldown Reset Chance";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        CrateRarity tier = getTier(stack);
        tooltipComponents.add(Component.literal("Tier: " + tier.getDisplayName()).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("Effect: " + getBonusText(augmentType, tier)).withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.literal("Socket in Smithing Table onto a Scroll or Spellbook.").withStyle(ChatFormatting.GRAY));
    }
}
