package com.complextalents.item;

import com.complextalents.caseopening.CaseReward;
import com.complextalents.caseopening.CaseRewardPool;
import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.caseopening.S2COpenCaseScreenPacket;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MysteriousLootItem extends Item {

    public MysteriousLootItem(Properties properties) {
        super(properties);
    }

    // --- Helper NBT Factory Methods ---

    public static ItemStack createWeaponCase(WeaponPath path, CrateRarity rarity) {
        ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_LOOT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CaseType", "WEAPON");
        tag.putString("WeaponPath", path.name());
        tag.putString("CrateRarity", rarity.name());
        return stack;
    }

    public static ItemStack createMagicCase(ResourceLocation schoolId, CrateRarity rarity) {
        ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_LOOT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CaseType", "MAGIC");
        tag.putString("SchoolId", schoolId.toString());
        tag.putString("CrateRarity", rarity.name());
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CaseType")) {
            String type = stack.getTag().getString("CaseType");
            CrateRarity rarity = parseRarity(stack);

            if ("WEAPON".equalsIgnoreCase(type)) {
                String pathStr = stack.getTag().getString("WeaponPath");
                WeaponPath path = WeaponPath.fromString(pathStr);
                String pathName = path != null ? capitalize(path.name()) : capitalize(pathStr);
                return Component.literal("§6" + pathName + " Weapon Case §7(" + rarity.getDisplayName() + "§7)");
            } else if ("MAGIC".equalsIgnoreCase(type)) {
                String schoolIdStr = stack.getTag().getString("SchoolId");
                ResourceLocation schoolId = ResourceLocation.tryParse(schoolIdStr);
                String schoolName = getSchoolDisplayName(schoolId);
                return Component.literal("§b" + schoolName + " Magic Case §7(" + rarity.getDisplayName() + "§7)");
            }
        }
        return super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            CompoundTag tag = itemStack.getTag();
            List<CaseReward> pool;

            if (tag != null && tag.contains("CaseType")) {
                String caseType = tag.getString("CaseType");
                CrateRarity rarity = parseRarity(itemStack);

                if ("WEAPON".equalsIgnoreCase(caseType)) {
                    WeaponPath path = WeaponPath.fromString(tag.getString("WeaponPath"));
                    if (path == null) path = WeaponPath.BLADEMASTER;
                    List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(path);
                    if (!validRarities.contains(rarity)) {
                        rarity = validRarities.get(validRarities.size() - 1);
                    }
                    pool = DynamicCasePoolBuilder.buildWeaponPool(path, rarity);
                } else if ("MAGIC".equalsIgnoreCase(caseType)) {
                    ResourceLocation schoolId = ResourceLocation.tryParse(tag.getString("SchoolId"));
                    if (schoolId == null) schoolId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire");
                    List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForSchool(schoolId);
                    if (!validRarities.contains(rarity)) {
                        rarity = validRarities.get(validRarities.size() - 1);
                    }
                    pool = DynamicCasePoolBuilder.buildMagicPool(schoolId, rarity);
                } else {
                    pool = createDefaultPool(level);
                }
            } else {
                pool = createDefaultPool(level);
            }

            CaseReward winningReward = DynamicCasePoolBuilder.rollFromPool(pool, level.getRandom());

            int targetWinningIndex = 65 + level.getRandom().nextInt(25);
            int totalCarouselItems = targetWinningIndex + 20;

            List<CaseReward> sequence = new ArrayList<>(totalCarouselItems);
            for (int i = 0; i < totalCarouselItems; i++) {
                if (i == targetWinningIndex) {
                    sequence.add(winningReward);
                } else {
                    sequence.add(DynamicCasePoolBuilder.rollFromPool(pool, level.getRandom()));
                }
            }

            ItemStack rewardStack = winningReward.getStack();

            // Give item to player inventory
            if (!player.getInventory().add(rewardStack.copy())) {
                player.drop(rewardStack.copy(), false);
            }

            // Automatic Spell Mastery Learning Hook for magic rewards (silently learned without spoiling unboxing result)
            if (rewardStack.hasTag() && rewardStack.getTag().contains("SpellId")) {
                ResourceLocation spellId = ResourceLocation.parse(rewardStack.getTag().getString("SpellId"));
                int rawLevel = rewardStack.getTag().getInt("SpellLevel");
                final int spellLevel = rawLevel <= 0 ? 1 : rawLevel;

                serverPlayer.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                    data.learnSpell(spellId, spellLevel);
                    data.sync();
                });
            }

            // Send packet to client for unboxing animation
            PacketHandler.sendTo(new S2COpenCaseScreenPacket(sequence, targetWinningIndex, winningReward, pool), serverPlayer);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    private List<CaseReward> createDefaultPool(Level level) {
        List<CaseReward> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pool.add(CaseRewardPool.rollReward(level.getRandom()));
        }
        return pool;
    }

    private CrateRarity parseRarity(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CrateRarity")) {
            try {
                return CrateRarity.valueOf(stack.getTag().getString("CrateRarity").toUpperCase());
            } catch (Exception ignored) {}
        }
        return CrateRarity.COMMON;
    }

    private static String getSchoolDisplayName(ResourceLocation schoolId) {
        if (schoolId != null && SchoolRegistry.REGISTRY != null && SchoolRegistry.REGISTRY.get() != null) {
            SchoolType school = SchoolRegistry.getSchool(schoolId);
            if (school != null) {
                return school.getDisplayName().getString();
            }
        }
        if (schoolId == null) return "Magic";
        String path = schoolId.getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1);
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    private static final String[] TIER_NAMES = new String[]{
            "§9● Common (Tier 1)",
            "§a● Uncommon (Tier 2)",
            "§d● Rare (Tier 3)",
            "§c● Epic (Tier 4)",
            "§6★ Legendary (Tier 5)"
    };

    private void appendDropRateTooltip(List<Component> tooltip, double[] percentages) {
        tooltip.add(Component.literal("§7Drop Rates:"));
        for (int i = 0; i < 5; i++) {
            double pct = percentages[i];
            if (pct > 0.0) {
                String pctStr = String.format(java.util.Locale.ROOT, "%.1f%%", pct);
                if (pctStr.endsWith(".0%")) {
                    pctStr = String.format(java.util.Locale.ROOT, "%.0f%%", pct);
                }
                tooltip.add(Component.literal("  " + TIER_NAMES[i] + ": §f" + pctStr));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag().contains("CaseType")) {
            String caseType = stack.getTag().getString("CaseType");
            CrateRarity rarity = parseRarity(stack);

            tooltip.add(Component.literal("§7Crate Rarity: " + rarity.getDisplayName()));

            if ("WEAPON".equalsIgnoreCase(caseType)) {
                String pathStr = stack.getTag().getString("WeaponPath");
                WeaponPath path = WeaponPath.fromString(pathStr);
                if (path == null) path = WeaponPath.BLADEMASTER;
                String pathName = capitalize(path.name());

                tooltip.add(Component.literal("§7Weapon Mastery Path: §6" + pathName));

                double[] pcts = DynamicCasePoolBuilder.getWeaponTierPercentages(path, rarity);
                appendDropRateTooltip(tooltip, pcts);

                tooltip.add(Component.literal("§eRight-click to unbox " + pathName + " weapons!"));
            } else if ("MAGIC".equalsIgnoreCase(caseType)) {
                String schoolIdStr = stack.getTag().getString("SchoolId");
                ResourceLocation schoolId = ResourceLocation.tryParse(schoolIdStr);
                if (schoolId == null) schoolId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire");
                String schoolName = getSchoolDisplayName(schoolId);

                tooltip.add(Component.literal("§7Magic School: §b" + schoolName));

                double[] pcts = DynamicCasePoolBuilder.getMagicTierPercentages(schoolId, rarity);
                appendDropRateTooltip(tooltip, pcts);

                tooltip.add(Component.literal("§a★ Unboxed spells are automatically learned!"));
                tooltip.add(Component.literal("§eRight-click to unbox " + schoolName + " magic!"));
            }
        } else {
            tooltip.add(Component.literal("§7A mysterious container sealed with ancient magic."));
            double[] pcts = DynamicCasePoolBuilder.getMagicTierPercentages(
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire"), CrateRarity.COMMON);
            appendDropRateTooltip(tooltip, pcts);
            tooltip.add(Component.literal("§eRight-click to start unboxing!"));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
