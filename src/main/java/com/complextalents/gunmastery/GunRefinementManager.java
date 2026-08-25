package com.complextalents.gunmastery;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.gunmastery.classification.GunClassificationManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BiPredicate;

public class GunRefinementManager {

    private static final GunRefinementManager INSTANCE = new GunRefinementManager();

    public static GunRefinementManager getInstance() {
        return INSTANCE;
    }

    private GunRefinementManager() {
    }

    public static class RefinementState {
        public final int currentTier; // 1 to 5
        public final int refineInTier; // 0 to 4
        public final int cumulativeLevel; // 0 to 20
        public final boolean isMaxed; // cumulativeLevel >= 20

        public RefinementState(int currentTier, int refineInTier, int cumulativeLevel, boolean isMaxed) {
            this.currentTier = currentTier;
            this.refineInTier = refineInTier;
            this.cumulativeLevel = cumulativeLevel;
            this.isMaxed = isMaxed;
        }

        public String getTierName() {
            return switch (currentTier) {
                case 1 -> "Recruit";
                case 2 -> "Trooper";
                case 3 -> "Sergeant";
                case 4 -> "Captain";
                case 5 -> "General";
                default -> "Recruit";
            };
        }

        public String getRefineDisplay() {
            return getTierName() + " (+" + refineInTier + ")";
        }
    }

    public static int getBaseCumulativeLevelForStartingTier(int startingTier) {
        return switch (startingTier) {
            case 1 -> 0; // Recruit Base (+0) = Lv 0
            case 2 -> 4; // Trooper Base (+0) = Lv 4
            case 3 -> 8; // Sergeant Base (+0) = Lv 8
            case 4 -> 12; // Captain Base (+0) = Lv 12
            case 5 -> 16; // General Base (+0) = Lv 16
            default -> 0;
        };
    }

    public static int getMaxRefinesForTier(int tier) {
        return 4; // Each rank spans 4 levels (Cumulative Lv 20 Max)
    }

    public static RefinementState calculateRefinementState(int startingTier, int totalRefines) {
        int currentTier = Math.max(1, Math.min(5, startingTier));
        int refineInTier = 0;
        int cumLevel = getBaseCumulativeLevelForStartingTier(currentTier);

        for (int i = 0; i < totalRefines; i++) {
            if (cumLevel >= 20)
                break;

            if (refineInTier < getMaxRefinesForTier(currentTier)) {
                refineInTier++;
            } else {
                if (currentTier < 5) {
                    currentTier++;
                    refineInTier = 1;
                }
            }
            cumLevel++;
        }

        cumLevel = Math.min(20, cumLevel);
        boolean isMaxed = cumLevel >= 20;
        return new RefinementState(currentTier, refineInTier, cumLevel, isMaxed);
    }

    /**
     * Mainstat Gun Damage Bonus % curve scaling across Cumulative Levels 0 to 20
     * (+100% Max Cap).
     */
    public static double getMainstatDamageBonus(int cumulativeLevel) {
        double[] bonuses = {
                0.000, // Lv 0: Base (+0.0%)
                0.002, // Lv 1: Recruit +1 (+0.2%)
                0.008, // Lv 2: Recruit +2 (+0.8%)
                0.018, // Lv 3: Recruit +3 (+1.8%)
                0.040, // Lv 4: Recruit Max (+4.0%)
                0.060, // Lv 5: Trooper +1 (+6.0%)
                0.090, // Lv 6: Trooper +2 (+9.0%)
                0.120, // Lv 7: Trooper +3 (+12.0%)
                0.160, // Lv 8: Trooper Max (+16.0%)
                0.200, // Lv 9: Sergeant +1 (+20.0%)
                0.250, // Lv 10: Sergeant +2 (+25.0%)
                0.300, // Lv 11: Sergeant +3 (+30.0%)
                0.360, // Lv 12: Sergeant Max (+36.0%)
                0.420, // Lv 13: Captain +1 (+42.0%)
                0.490, // Lv 14: Captain +2 (+49.0%)
                0.560, // Lv 15: Captain +3 (+56.0%)
                0.640, // Lv 16: Captain Max (+64.0%)
                0.720, // Lv 17: General +1 (+72.0%)
                0.810, // Lv 18: General +2 (+81.0%)
                0.900, // Lv 19: General +3 (+90.0%)
                1.000  // Lv 20: General Max Pinnacle (+100.0% Max Cap)
        };
        int index = Math.max(0, Math.min(bonuses.length - 1, cumulativeLevel));
        return bonuses[index];
    }

    public static double getTierMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1.0;
            case 2 -> 1.8;
            case 3 -> 2.8;
            case 4 -> 4.2;
            case 5 -> 6.0;
            default -> 1.0;
        };
    }

    public static int getTierForCumulativeLevel(int cumLevel) {
        if (cumLevel <= 4)
            return 1; // Recruit (Lv 1-4)
        else if (cumLevel <= 8)
            return 2; // Trooper (Lv 5-8)
        else if (cumLevel <= 12)
            return 3;// Sergeant (Lv 9-12)
        else if (cumLevel <= 16)
            return 4;// Captain (Lv 13-16)
        else
            return 5; // General (Lv 17-20)
    }

    public static String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "\u00A7f"; // Recruit: White
            case 2 -> "\u00A7a"; // Trooper: Emerald Green
            case 3 -> "\u00A79"; // Sergeant: Indigo Blue
            case 4 -> "\u00A75"; // Captain: Purple
            case 5 -> "\u00A76"; // General: Gold
            default -> "\u00A7f";
        };
    }

    public static String getTierCrestIcon(int tier) {
        return switch (tier) {
            case 1 -> "✧"; // Recruit
            case 2 -> "✦"; // Trooper
            case 3 -> "❖"; // Sergeant
            case 4 -> "❂"; // Captain
            case 5 -> "⚜"; // General
            default -> "✦";
        };
    }

    public static int getGemXpValue(CrateRarity rarity) {
        if (rarity == null)
            return 100;
        return switch (rarity) {
            case COMMON -> 100;
            case UNCOMMON -> 800;
            case RARE -> 6400;
            case EPIC -> 51200;
            case LEGENDARY -> 409600;
        };
    }

    private static final int[] CUMULATIVE_XP_TABLE = {
            0, // Lv 0
            100, // Lv 1
            250, // Lv 2
            600, // Lv 3
            1400, // Lv 4
            3000, // Lv 5
            7000, // Lv 6
            15000, // Lv 7
            28000, // Lv 8
            55000, // Lv 9
            110000, // Lv 10
            200000, // Lv 11
            350000, // Lv 12
            600000, // Lv 13
            1000000, // Lv 14
            1500000, // Lv 15
            2100000, // Lv 16
            2800000, // Lv 17
            3600000, // Lv 18
            4500000, // Lv 19
            5500000 // Lv 20 (5.5M XP Total)
    };

    public static int getXpForRank(int rank) {
        if (rank <= 0)
            return 0;
        if (rank >= CUMULATIVE_XP_TABLE.length)
            return CUMULATIVE_XP_TABLE[CUMULATIVE_XP_TABLE.length - 1];
        return CUMULATIVE_XP_TABLE[rank];
    }

    public static int getRankFromXp(int xp, int maxRank) {
        if (xp <= 0)
            return 0;
        int rank = 0;
        while (rank < maxRank && rank + 1 < CUMULATIVE_XP_TABLE.length && xp >= CUMULATIVE_XP_TABLE[rank + 1]) {
            rank++;
        }
        return rank;
    }

    public static int getRefineXp(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return 0;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0)
            return 0;

        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = getXpForRank(baseRank);

        if (!stack.hasTag())
            return startingXp;

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RefineXP")) {
            return Math.max(startingXp, tag.getInt("RefineXP"));
        } else if (tag != null && tag.contains("RefineRank")) {
            int rank = tag.getInt("RefineRank");
            int cumRank = baseRank + rank;
            return Math.max(startingXp, getXpForRank(cumRank));
        }
        return startingXp;
    }

    public static Optional<GunData> getGunData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return Optional.empty();
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(stack)).map(CommonGunIndex::getGunData);
    }

    public static boolean supportsFireMode(ItemStack stack, FireMode mode) {
        return getGunData(stack).map(data -> {
            List<FireMode> modes = data.getFireModeSet();
            return modes != null && modes.contains(mode);
        }).orElse(false);
    }

    public static boolean isBoltActionOrPump(ItemStack stack) {
        return getGunData(stack).map(data -> data.getBolt() == Bolt.MANUAL_ACTION || data.getBoltActionTime() > 0.0f).orElse(false);
    }

    public enum GunSubstatType {
        // Universal Damage & Combat
        GUN_DAMAGE("GUN_DAMAGE", "Bonus Gun Damage", 0.025, (stack, data) -> true),
        HEADSHOT_MULTIPLIER("HEADSHOT_MULTIPLIER", "Headshot Damage", 0.04, (stack, data) -> true),
        KNOCKBACK_MULTIPLIER("KNOCKBACK_MULTIPLIER", "Knockback Power", 0.045, (stack, data) -> true),
        KNOCKBACK_BASE("KNOCKBACK_BASE", "Base Knockback", 0.045, (stack, data) -> true),
        PIERCE_MULTIPLIER("PIERCE_MULTIPLIER", "Armor Pierce", 0.03, (stack, data) -> true),

        // Speed & Fire Rate
        RPM_MULTIPLIER("RPM_MULTIPLIER", "Fire Rate (RPM)", 0.025, (stack, data) -> true),
        RELOAD_SPEED("RELOAD_SPEED", "Reload Speed", 0.035, (stack, data) -> true),
        DRAW_SPEED("DRAW_SPEED", "Draw Speed", 0.04, (stack, data) -> true),
        ADS_SPEED("ADS_SPEED", "ADS Speed", 0.04, (stack, data) -> true),
        GUN_MOVEMENT_SPEED("GUN_MOVEMENT_SPEED", "Movement Speed", 0.025, (stack, data) -> true),

        // Recoil Control (Global & Specific)
        RECOIL("RECOIL", "Recoil Control", 0.03, (stack, data) -> true),
        VERTICAL_RECOIL("VERTICAL_RECOIL", "Vertical Recoil Control", 0.03, (stack, data) -> true),
        HORIZONTAL_RECOIL("HORIZONTAL_RECOIL", "Horizontal Recoil Control", 0.03, (stack, data) -> true),
        ADS_RECOIL("ADS_RECOIL", "ADS Recoil Control", 0.03, (stack, data) -> true),
        ADS_VERTICAL_RECOIL("ADS_VERTICAL_RECOIL", "ADS Vertical Recoil", 0.03, (stack, data) -> true),
        ADS_HORIZONTAL_RECOIL("ADS_HORIZONTAL_RECOIL", "ADS Horizontal Recoil", 0.03, (stack, data) -> true),
        HIP_FIRE_RECOIL("HIP_FIRE_RECOIL", "Hip Fire Recoil Control", 0.03, (stack, data) -> true),
        HIP_FIRE_VERTICAL_RECOIL("HIP_FIRE_VERTICAL_RECOIL", "Hip Fire Vertical Recoil", 0.03, (stack, data) -> true),
        HIP_FIRE_HORIZONTAL_RECOIL("HIP_FIRE_HORIZONTAL_RECOIL", "Hip Fire Horizontal Recoil", 0.03, (stack, data) -> true),

        // Accuracy & Position Damage
        ADS_ACCURACY("ADS_ACCURACY", "ADS Accuracy", 0.03, (stack, data) -> true),
        HIP_FIRE_ACCURACY("HIP_FIRE_ACCURACY", "Hip Fire Accuracy", 0.04, (stack, data) -> true),
        ADS_DAMAGE("ADS_DAMAGE", "ADS Damage", 0.03, (stack, data) -> true),
        HIP_FIRE_DAMAGE("HIP_FIRE_DAMAGE", "Hip Fire Damage", 0.03, (stack, data) -> true),

        // Ammo & Recovery
        AMMO_SAVE_CHANCE("AMMO_SAVE_CHANCE", "Ammo Save Chance", 0.012, (stack, data) -> true),
        RELOAD_AMMO_SAVE_CHANCE("RELOAD_AMMO_SAVE_CHANCE", "Reload Ammo Save Chance", 0.012, (stack, data) -> true),
        AMMO_RECOVERY_CHANCE("AMMO_RECOVERY_CHANCE", "Ammo Recovery Chance", 0.012, (stack, data) -> true),
        AMMO_RECOVERY_AMOUNT("AMMO_RECOVERY_AMOUNT", "Ammo Recovery Amount", 0.025, (stack, data) -> true),
        AMMO_RECOVERY_PERCENT("AMMO_RECOVERY_PERCENT", "Ammo Recovery %", 0.012, (stack, data) -> true),
        BONUS_AMMO_CHANCE("BONUS_AMMO_CHANCE", "Bonus Ammo Chance", 0.012, (stack, data) -> true),
        BONUS_AMMO_AMOUNT("BONUS_AMMO_AMOUNT", "Bonus Ammo Amount", 0.025, (stack, data) -> true),
        BONUS_AMMO_PERCENT("BONUS_AMMO_PERCENT", "Bonus Ammo %", 0.012, (stack, data) -> true),
        MAGAZINE_CAPACITY("MAGAZINE_CAPACITY", "Magazine Capacity", 0.045, (stack, data) -> true),

        // Conditional Substats
        BOLT_ACTION_SPEED("BOLT_ACTION_SPEED", "Bolt Action Speed", 0.04, (stack, data) -> isBoltActionOrPump(stack)),
        BURST_SPEED("BURST_SPEED", "Burst Fire Speed", 0.035, (stack, data) -> supportsFireMode(stack, FireMode.BURST)),
        BURST_DAMAGE("BURST_DAMAGE", "Burst Damage", 0.035, (stack, data) -> supportsFireMode(stack, FireMode.BURST)),
        BURST_ACCURACY("BURST_ACCURACY", "Burst Accuracy", 0.04, (stack, data) -> supportsFireMode(stack, FireMode.BURST)),

        AUTO_DAMAGE("AUTO_DAMAGE", "Auto Damage", 0.025, (stack, data) -> supportsFireMode(stack, FireMode.AUTO)),
        AUTO_ACCURACY("AUTO_ACCURACY", "Auto Accuracy", 0.03, (stack, data) -> supportsFireMode(stack, FireMode.AUTO)),

        SEMI_DAMAGE("SEMI_DAMAGE", "Semi-Auto Damage", 0.035, (stack, data) -> supportsFireMode(stack, FireMode.SEMI)),
        SEMI_ACCURACY("SEMI_ACCURACY", "Semi-Auto Accuracy", 0.04, (stack, data) -> supportsFireMode(stack, FireMode.SEMI));

        private final String key;
        private final String displayName;
        private final double baseRoll;
        private final BiPredicate<ItemStack, GunData> eligibility;

        GunSubstatType(String key, String displayName, double baseRoll, BiPredicate<ItemStack, GunData> eligibility) {
            this.key = key;
            this.displayName = displayName;
            this.baseRoll = baseRoll;
            this.eligibility = eligibility;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getBaseRoll() {
            return baseRoll;
        }

        public boolean isEligible(ItemStack stack, GunData data) {
            return eligibility != null && eligibility.test(stack, data);
        }

        public String formatValue(double value) {
            return String.format("+%.1f%%", value * 100.0);
        }

        public static GunSubstatType fromKey(String key) {
            for (GunSubstatType type : values()) {
                if (type.key.equalsIgnoreCase(key))
                    return type;
            }
            return null;
        }
    }

    public static class SubstatResult {
        public final Map<GunSubstatType, Double> values = new LinkedHashMap<>();
        public final List<String> history = new ArrayList<>();
    }

    public static List<GunSubstatType> getEligibleSubstats(ItemStack stack) {
        List<GunSubstatType> list = new ArrayList<>();
        GunData data = getGunData(stack).orElse(null);
        for (GunSubstatType type : GunSubstatType.values()) {
            if (type.isEligible(stack, data)) {
                list.add(type);
            }
        }
        return list;
    }

    public static SubstatResult calculateSubstats(ItemStack stack) {
        SubstatResult result = new SubstatResult();
        if (stack == null || stack.isEmpty())
            return result;

        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0)
            return result;

        int totalXp = getRefineXp(stack);
        int cumRank = getRankFromXp(totalXp, 20);
        if (cumRank <= 0)
            return result;

        CompoundTag tag = stack.getOrCreateTag();
        long seed;
        if (tag.contains("RefineSeed")) {
            seed = tag.getLong("RefineSeed");
        } else {
            seed = new Random().nextLong();
            tag.putLong("RefineSeed", seed);
        }

        Random rand = new Random(seed);
        List<GunSubstatType> pool = getEligibleSubstats(stack);
        List<GunSubstatType> unlockedOrder = new ArrayList<>();

        // Determine how many substats are unlocked based on cumulative rank
        int unlockedCount = 0;
        if (cumRank >= 1)
            unlockedCount = 1;
        if (cumRank >= 5)
            unlockedCount = 2;
        if (cumRank >= 9)
            unlockedCount = 3;
        if (cumRank >= 13)
            unlockedCount = 4;
        if (cumRank >= 17)
            unlockedCount = 5;

        // Deterministically pick the unlocked substat types in order
        List<GunSubstatType> availablePool = new ArrayList<>(pool);
        for (int i = 0; i < unlockedCount && !availablePool.isEmpty(); i++) {
            int idx = rand.nextInt(availablePool.size());
            unlockedOrder.add(availablePool.remove(idx));
        }

        for (GunSubstatType st : unlockedOrder) {
            result.values.put(st, 0.0);
        }

        // Roll enhancements for each level up to cumRank
        for (int level = 1; level <= cumRank; level++) {
            int currentTier = getTierForCumulativeLevel(level);
            double tierMult = getTierMultiplier(currentTier);

            // Determine if this level unlocks a new substat or enhances an existing one
            boolean isUnlockLevel = (level == 1 || level == 5 || level == 9 || level == 13 || level == 17);

            GunSubstatType targetType;
            if (isUnlockLevel) {
                int unlockIndex = switch (level) {
                    case 1 -> 0;
                    case 5 -> 1;
                    case 9 -> 2;
                    case 13 -> 3;
                    case 17 -> 4;
                    default -> 0;
                };
                if (unlockIndex < unlockedOrder.size()) {
                    targetType = unlockedOrder.get(unlockIndex);
                } else {
                    targetType = unlockedOrder.get(rand.nextInt(unlockedOrder.size()));
                }
            } else {
                // Enhance an existing unlocked substat
                int activeUnlocked = 0;
                if (level >= 1)
                    activeUnlocked = 1;
                if (level >= 5)
                    activeUnlocked = 2;
                if (level >= 9)
                    activeUnlocked = 3;
                if (level >= 13)
                    activeUnlocked = 4;
                if (level >= 17)
                    activeUnlocked = 5;
                activeUnlocked = Math.min(activeUnlocked, unlockedOrder.size());

                targetType = unlockedOrder.get(rand.nextInt(Math.max(1, activeUnlocked)));
            }

            // Calculate upgrade roll with Gaussian variance
            double baseRoll = targetType.getBaseRoll() * tierMult;
            double gaussian = rand.nextGaussian(); // mean 0, stddev 1
            double variance = 1.0 + (gaussian * 0.25); // +/- 25% stddev
            variance = Math.max(0.50, Math.min(1.50, variance));

            double rolledVal = baseRoll * variance;
            double currentVal = result.values.getOrDefault(targetType, 0.0);
            result.values.put(targetType, currentVal + rolledVal);

            String crest = getTierCrestIcon(currentTier);
            String color = getTierColor(currentTier);
            String logLine = String.format("%s%s Lv.%d: %s %s (%s)", color, crest, level, targetType.getDisplayName(),
                    targetType.formatValue(rolledVal), getTierNameForTier(currentTier));
            result.history.add(logLine);
        }

        return result;
    }

    public static String getTierNameForTier(int tier) {
        return switch (tier) {
            case 1 -> "Recruit";
            case 2 -> "Trooper";
            case 3 -> "Sergeant";
            case 4 -> "Captain";
            case 5 -> "General";
            default -> "Recruit";
        };
    }

    /**
     * Updates and caches refinement tag data on the item stack.
     */
    public static void applyRefinementDataToStack(ItemStack stack, int newXp) {
        if (stack == null || stack.isEmpty())
            return;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0)
            return;

        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int maxCumRank = 20;
        int maxXp = getXpForRank(maxCumRank);

        int clampedXp = Math.min(maxXp, Math.max(getXpForRank(baseRank), newXp));
        int cumRank = getRankFromXp(clampedXp, maxCumRank);
        int refineRank = Math.max(0, cumRank - baseRank);

        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("RefineXP", clampedXp);
        tag.putInt("RefineRank", refineRank);

        if (!tag.contains("RefineSeed")) {
            tag.putLong("RefineSeed", new Random().nextLong());
        }

        // Cache substat values in NBT for fast access
        SubstatResult result = calculateSubstats(stack);
        CompoundTag substatsTag = new CompoundTag();
        for (Map.Entry<GunSubstatType, Double> entry : result.values.entrySet()) {
            substatsTag.putDouble(entry.getKey().getKey(), entry.getValue());
        }
        tag.put("RefineSubstats", substatsTag);

        ListTag historyTag = new ListTag();
        for (String hist : result.history) {
            historyTag.add(StringTag.valueOf(hist));
        }
        tag.put("RefineHistory", historyTag);
    }

    public static ItemStack applyRandomRefinementForLoot(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (stack == null || stack.isEmpty())
            return stack;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0)
            return stack;

        if (stack.hasTag() && stack.getTag().contains("RefineRank")) {
            return stack;
        }

        double roll = random.nextDouble();
        int rolledRank = 0;
        if (roll < 0.50)
            rolledRank = 0;
        else if (roll < 0.75)
            rolledRank = 1;
        else if (roll < 0.90)
            rolledRank = 2;
        else if (roll < 0.97)
            rolledRank = 3;
        else
            rolledRank = 4;

        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int targetCumulativeLevel = baseRank + rolledRank;
        int totalXp = getXpForRank(targetCumulativeLevel);

        applyRefinementDataToStack(stack, totalXp);
        return stack;
    }
}
