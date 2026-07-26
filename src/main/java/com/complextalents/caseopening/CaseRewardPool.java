package com.complextalents.caseopening;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CaseRewardPool {
    private static final List<CaseReward> DEFAULT_REWARDS = new ArrayList<>();

    static {
        // Mil-Spec Grade (Common - Weight 60)
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.DIAMOND, 5), CaseRarity.MIL_SPEC, 25, Component.literal("Bundle of Diamonds")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.EMERALD, 16), CaseRarity.MIL_SPEC, 25, Component.literal("Trader's Stash")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.GOLDEN_APPLE, 3), CaseRarity.MIL_SPEC, 20, Component.literal("Golden Apples")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.EXPERIENCE_BOTTLE, 32), CaseRarity.MIL_SPEC, 20, Component.literal("Bottles o' Enchanting")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.IRON_BLOCK, 4), CaseRarity.MIL_SPEC, 20, Component.literal("Heavy Iron Blocks")));

        // Restricted (Uncommon - Weight 30)
        ItemStack enchantedBook1 = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook1.enchant(Enchantments.SHARPNESS, 5);
        DEFAULT_REWARDS.add(new CaseReward(enchantedBook1, CaseRarity.RESTRICTED, 12, Component.literal("Tome of Sharpness V")));

        ItemStack enchantedBook2 = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook2.enchant(Enchantments.MENDING, 1);
        DEFAULT_REWARDS.add(new CaseReward(enchantedBook2, CaseRarity.RESTRICTED, 12, Component.literal("Tome of Mending")));

        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.NETHERITE_INGOT, 2), CaseRarity.RESTRICTED, 10, Component.literal("Netherite Ingots")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.ANCIENT_DEBRIS, 6), CaseRarity.RESTRICTED, 10, Component.literal("Ancient Debris Cache")));

        // Classified (Rare - Weight 12)
        ItemStack godPick = new ItemStack(Items.NETHERITE_PICKAXE);
        godPick.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
        godPick.enchant(Enchantments.UNBREAKING, 3);
        godPick.enchant(Enchantments.BLOCK_FORTUNE, 3);
        godPick.setHoverName(Component.literal("§d★ Master Miner's Netherite Pickaxe"));
        DEFAULT_REWARDS.add(new CaseReward(godPick, CaseRarity.CLASSIFIED, 6));

        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.TOTEM_OF_UNDYING, 2), CaseRarity.CLASSIFIED, 5, Component.literal("§dAegis of Undying")));
        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2), CaseRarity.CLASSIFIED, 5, Component.literal("§dEnchanted Golden Apples")));

        // Covert (Exotic - Weight 4)
        ItemStack godSword = new ItemStack(Items.NETHERITE_SWORD);
        godSword.enchant(Enchantments.SHARPNESS, 5);
        godSword.enchant(Enchantments.MOB_LOOTING, 3);
        godSword.enchant(Enchantments.FIRE_ASPECT, 2);
        godSword.enchant(Enchantments.UNBREAKING, 3);
        godSword.setHoverName(Component.literal("§c★ Dragon Slayer Blade"));
        DEFAULT_REWARDS.add(new CaseReward(godSword, CaseRarity.COVERT, 3));

        DEFAULT_REWARDS.add(new CaseReward(new ItemStack(Items.NETHER_STAR, 3), CaseRarity.COVERT, 3, Component.literal("§cCluster of Nether Stars")));

        // Special Item (Gold - High weight 150 for testing)
        ItemStack legendaryElytra = new ItemStack(Items.ELYTRA);
        legendaryElytra.enchant(Enchantments.UNBREAKING, 3);
        legendaryElytra.enchant(Enchantments.MENDING, 1);
        legendaryElytra.setHoverName(Component.literal("§6★ SPECIAL: Celestial Wings of Glory"));
        DEFAULT_REWARDS.add(new CaseReward(legendaryElytra, CaseRarity.SPECIAL, 150));
    }

    public static List<CaseReward> getAllRewards() {
        return Collections.unmodifiableList(DEFAULT_REWARDS);
    }

    public static int getTotalWeight() {
        return DEFAULT_REWARDS.stream().mapToInt(CaseReward::getWeight).sum();
    }

    public static double getDropRatePercentage(CaseReward reward) {
        int totalWeight = getTotalWeight();
        if (totalWeight <= 0) return 0.0;
        return (double) reward.getWeight() / totalWeight * 100.0;
    }

    public static CaseReward rollReward(RandomSource random) {
        int totalWeight = getTotalWeight();
        int roll = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (CaseReward reward : DEFAULT_REWARDS) {
            currentWeight += reward.getWeight();
            if (roll < currentWeight) {
                return reward;
            }
        }
        return DEFAULT_REWARDS.get(0);
    }

    public static List<CaseReward> generateCarouselSequence(RandomSource random, CaseReward winningReward, int totalItems, int winningIndex) {
        List<CaseReward> sequence = new ArrayList<>(totalItems);
        for (int i = 0; i < totalItems; i++) {
            if (i == winningIndex) {
                sequence.add(winningReward);
            } else {
                sequence.add(rollReward(random));
            }
        }
        return sequence;
    }
}
