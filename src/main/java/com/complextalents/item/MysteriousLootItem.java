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
        if (path != null) {
            tag.putString("WeaponPath", path.name());
        } else {
            tag.putString("WeaponPath", "ALL");
        }
        tag.putString("CrateRarity", rarity.name());
        return stack;
    }

    public static ItemStack createMagicCase(ResourceLocation schoolId, CrateRarity rarity) {
        ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_LOOT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CaseType", "MAGIC");
        if (schoolId != null) {
            tag.putString("SchoolId", schoolId.toString());
        } else {
            tag.putString("SchoolId", "ALL");
        }
        tag.putString("CrateRarity", rarity.name());
        return stack;
    }

    public static ItemStack createGunCase(com.complextalents.tacz.GunType gunType, CrateRarity rarity) {
        ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_LOOT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CaseType", "GUN");
        if (gunType != null) {
            tag.putString("GunType", gunType.name());
        } else {
            tag.putString("GunType", "ALL");
        }
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
                String pathName = "All";
                if (!"ALL".equalsIgnoreCase(pathStr)) {
                    WeaponPath path = WeaponPath.fromString(pathStr);
                    if (path != null) pathName = capitalize(path.name());
                }
                return Component.literal("§6" + pathName + " Weapon Case §7(" + rarity.getDisplayName() + "§7)");
            } else if ("MAGIC".equalsIgnoreCase(type)) {
                String schoolIdStr = stack.getTag().getString("SchoolId");
                String schoolName = "All";
                if (!"ALL".equalsIgnoreCase(schoolIdStr)) {
                    ResourceLocation schoolId = ResourceLocation.tryParse(schoolIdStr);
                    if (schoolId != null) schoolName = getSchoolDisplayName(schoolId);
                }
                return Component.literal("§b" + schoolName + " Magic Case §7(" + rarity.getDisplayName() + "§7)");
            } else if ("GUN".equalsIgnoreCase(type)) {
                String gunTypeStr = stack.getTag().getString("GunType");
                String displayTypeName = "All";
                try {
                    com.complextalents.tacz.GunType gt = com.complextalents.tacz.GunType.valueOf(gunTypeStr.toUpperCase());
                    displayTypeName = gt.getDisplayName();
                } catch (Exception ignored) {}
                return Component.literal("§d" + displayTypeName + " Firearm Case §7(" + rarity.getDisplayName() + "§7)");
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
                    String pathStr = tag.getString("WeaponPath");
                    WeaponPath path = "ALL".equalsIgnoreCase(pathStr) ? null : WeaponPath.fromString(pathStr);
                    List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(path);
                    if (!validRarities.contains(rarity)) {
                        rarity = validRarities.get(validRarities.size() - 1);
                    }
                    pool = DynamicCasePoolBuilder.buildWeaponPool(path, rarity);
                } else if ("MAGIC".equalsIgnoreCase(caseType)) {
                    String schoolIdStr = tag.getString("SchoolId");
                    ResourceLocation schoolId = "ALL".equalsIgnoreCase(schoolIdStr) ? null : ResourceLocation.tryParse(schoolIdStr);
                    List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForSchool(schoolId);
                    if (!validRarities.contains(rarity)) {
                        rarity = validRarities.get(validRarities.size() - 1);
                    }
                    pool = DynamicCasePoolBuilder.buildMagicPool(schoolId, rarity);
                } else if ("GUN".equalsIgnoreCase(caseType)) {
                    String gunTypeStr = tag.getString("GunType");
                    com.complextalents.tacz.GunType gunType = null;
                    if (!"ALL".equalsIgnoreCase(gunTypeStr)) {
                        try {
                            gunType = com.complextalents.tacz.GunType.valueOf(gunTypeStr.toUpperCase());
                        } catch (Exception ignored) {}
                    }
                    List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForGunType(gunType);
                    if (!validRarities.contains(rarity)) {
                        rarity = validRarities.get(validRarities.size() - 1);
                    }
                    pool = DynamicCasePoolBuilder.buildGunPool(gunType, rarity);
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
                WeaponPath path = "ALL".equalsIgnoreCase(pathStr) ? null : WeaponPath.fromString(pathStr);
                String pathName = path != null ? capitalize(path.name()) : "All";

                tooltip.add(Component.literal("§7Weapon Mastery Path: §6" + pathName));

                double[] pcts = DynamicCasePoolBuilder.getWeaponTierPercentages(path, rarity);
                appendDropRateTooltip(tooltip, pcts);

                tooltip.add(Component.literal("§eRight-click to unbox " + pathName + " weapons!"));
            } else if ("MAGIC".equalsIgnoreCase(caseType)) {
                String schoolIdStr = stack.getTag().getString("SchoolId");
                ResourceLocation schoolId = "ALL".equalsIgnoreCase(schoolIdStr) ? null : ResourceLocation.tryParse(schoolIdStr);
                String schoolName = schoolId != null ? getSchoolDisplayName(schoolId) : "All";

                tooltip.add(Component.literal("§7Magic School: §b" + schoolName));

                double[] pcts = DynamicCasePoolBuilder.getMagicTierPercentages(schoolId, rarity);
                appendDropRateTooltip(tooltip, pcts);

                tooltip.add(Component.literal("§a★ Unboxed spells are automatically learned!"));
                tooltip.add(Component.literal("§eRight-click to unbox " + schoolName + " magic!"));
            } else if ("GUN".equalsIgnoreCase(caseType)) {
                String gunTypeStr = stack.getTag().getString("GunType");
                com.complextalents.tacz.GunType gunType = null;
                String displayTypeName = "All";
                try {
                    gunType = com.complextalents.tacz.GunType.valueOf(gunTypeStr.toUpperCase());
                    displayTypeName = gunType.getDisplayName();
                } catch (Exception ignored) {}

                tooltip.add(Component.literal("§7Firearm Archetype: §d" + displayTypeName));

                double[] pcts = DynamicCasePoolBuilder.getGunTierPercentages(gunType, rarity);
                appendDropRateTooltip(tooltip, pcts);

                tooltip.add(Component.literal("§eRight-click to unbox " + displayTypeName + " firearms!"));
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
