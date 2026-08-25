package com.complextalents.gunmastery;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.gunmastery.classification.GunClassificationManager;
import com.tacz.guns.api.item.IGun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import net.minecraft.world.item.ItemStack;

import java.util.*;

public class GunRefinementManager {

    private static final GunRefinementManager INSTANCE = new GunRefinementManager();

    public static GunRefinementManager getInstance() {
        return INSTANCE;
    }

    private GunRefinementManager() {}

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
            case 1 -> 0;  // Recruit Base (+0) = Lv 0
            case 2 -> 4;  // Trooper Base (+0) = Lv 4
            case 3 -> 8;  // Sergeant Base (+0) = Lv 8
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
            if (cumLevel >= 20) break;

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
     * Mainstat Gun Damage Bonus % curve scaling across Cumulative Levels 0 to 20 (+200% Max Cap).
     * Incremental gain accelerates by +1.0% per level (0.5%, 1.5%, 2.5%, ... up to 19.5% at Lv 20).
     */
    public static double getMainstatDamageBonus(int cumulativeLevel) {
        double[] bonuses = {
            0.000, // Lv 0: Base (+0.0%)
            0.005, // Lv 1: Recruit +1 (+0.5%, gain +0.5%)
            0.020, // Lv 2: Recruit +2 (+2.0%, gain +1.5%)
            0.045, // Lv 3: Recruit +3 (+4.5%, gain +2.5%)
            0.080, // Lv 4: Recruit Max (+8.0%, gain +3.5%)
            0.125, // Lv 5: Trooper +1 (+12.5%, gain +4.5%)
            0.180, // Lv 6: Trooper +2 (+18.0%, gain +5.5%)
            0.245, // Lv 7: Trooper +3 (+24.5%, gain +6.5%)
            0.320, // Lv 8: Trooper Max (+32.0%, gain +7.5%)
            0.405, // Lv 9: Sergeant +1 (+40.5%, gain +8.5%)
            0.500, // Lv 10: Sergeant +2 (+50.0%, gain +9.5%)
            0.605, // Lv 11: Sergeant +3 (+60.5%, gain +10.5%)
            0.720, // Lv 12: Sergeant Max (+72.0%, gain +11.5%)
            0.845, // Lv 13: Captain +1 (+84.5%, gain +12.5%)
            0.980, // Lv 14: Captain +2 (+98.0%, gain +13.5%)
            1.125, // Lv 15: Captain +3 (+112.5%, gain +14.5%)
            1.280, // Lv 16: Captain Max (+128.0%, gain +15.5%)
            1.445, // Lv 17: General +1 (+144.5%, gain +16.5%)
            1.620, // Lv 18: General +2 (+162.0%, gain +17.5%)
            1.805, // Lv 19: General +3 (+180.5%, gain +18.5%)
            2.000  // Lv 20: General Max Pinnacle (+200.0%, gain +19.5%)
        };
        int index = Math.max(0, Math.min(bonuses.length - 1, cumulativeLevel));
        return bonuses[index];
    }

    public static double getTierMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            case 3 -> 2.2;
            case 4 -> 3.2;
            case 5 -> 4.5;
            default -> 1.0;
        };
    }

    public static int getTierForCumulativeLevel(int cumLevel) {
        if (cumLevel <= 4) return 1;      // Recruit (Lv 1-4)
        else if (cumLevel <= 8) return 2; // Trooper (Lv 5-8)
        else if (cumLevel <= 12) return 3;// Sergeant (Lv 9-12)
        else if (cumLevel <= 16) return 4;// Captain (Lv 13-16)
        else return 5;                    // General (Lv 17-20)
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
        if (rarity == null) return 100;
        return switch (rarity) {
            case COMMON -> 100;
            case UNCOMMON -> 800;
            case RARE -> 6400;
            case EPIC -> 51200;
            case LEGENDARY -> 409600;
        };
    }

    private static final int[] CUMULATIVE_XP_TABLE = {
        0,       // Lv 0
        100,     // Lv 1
        250,     // Lv 2
        600,     // Lv 3
        1400,    // Lv 4
        3000,    // Lv 5
        7000,    // Lv 6
        15000,   // Lv 7
        28000,   // Lv 8
        55000,   // Lv 9
        110000,  // Lv 10
        200000,  // Lv 11
        350000,  // Lv 12
        600000,  // Lv 13
        1000000, // Lv 14
        1500000, // Lv 15
        2100000, // Lv 16
        2800000, // Lv 17
        3600000, // Lv 18
        4500000, // Lv 19
        5500000  // Lv 20 (5.5M XP Total)
    };

    public static int getXpForRank(int rank) {
        if (rank <= 0) return 0;
        if (rank >= CUMULATIVE_XP_TABLE.length) return CUMULATIVE_XP_TABLE[CUMULATIVE_XP_TABLE.length - 1];
        return CUMULATIVE_XP_TABLE[rank];
    }

    public static int getRankFromXp(int xp, int maxRank) {
        if (xp <= 0) return 0;
        int rank = 0;
        while (rank < maxRank && rank + 1 < CUMULATIVE_XP_TABLE.length && xp >= CUMULATIVE_XP_TABLE[rank + 1]) {
            rank++;
        }
        return rank;
    }

    public static int getRefineXp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return 0;

        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = getXpForRank(baseRank);

        if (!stack.hasTag()) return startingXp;

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

    public enum GunSubstatType {
        GUN_DAMAGE("GUN_DAMAGE", "Bonus Gun Damage", 0.01, true),
        HEADSHOT_MULTIPLIER("HEADSHOT_MULTIPLIER", "Headshot Damage", 0.02, true),
        RPM_MULTIPLIER("RPM_MULTIPLIER", "Fire Rate (RPM)", 0.01, true),
        RELOAD_SPEED("RELOAD_SPEED", "Reload Speed", 0.015, true),
        RECOIL("RECOIL", "Recoil Control", 0.015, true),
        ADS_SPEED("ADS_SPEED", "ADS Speed", 0.02, true),
        ADS_ACCURACY("ADS_ACCURACY", "ADS Accuracy", 0.015, true),
        HIP_FIRE_ACCURACY("HIP_FIRE_ACCURACY", "Hip Fire Accuracy", 0.02, true),
        DRAW_SPEED("DRAW_SPEED", "Draw Speed", 0.02, true),
        AMMO_SAVE_CHANCE("AMMO_SAVE_CHANCE", "Ammo Save Chance", 0.005, true),
        BOLT_ACTION_SPEED("BOLT_ACTION_SPEED", "Bolt Action Speed", 0.02, false); // Scoped to bolt/pump guns only

        private final String key;
        private final String displayName;
        private final double baseRoll;
        private final boolean isUniversal;

        GunSubstatType(String key, String displayName, double baseRoll, boolean isUniversal) {
            this.key = key;
            this.displayName = displayName;
            this.baseRoll = baseRoll;
            this.isUniversal = isUniversal;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public double getBaseRoll() { return baseRoll; }
        public boolean isUniversal() { return isUniversal; }

        public String formatValue(double value) {
            return String.format("+%.1f%%", value * 100.0);
        }

        public static GunSubstatType fromKey(String key) {
            for (GunSubstatType type : values()) {
                if (type.key.equalsIgnoreCase(key)) return type;
            }
            return null;
        }
    }

    public static class SubstatResult {
        public final Map<GunSubstatType, Double> values = new LinkedHashMap<>();
        public final List<String> history = new ArrayList<>();
    }

    public static boolean isBoltActionOrPump(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return false;
        String idStr = stack.getItem().toString().toLowerCase();
        return idStr.contains("bolt") || idStr.contains("pump") || idStr.contains("sniper") || idStr.contains("shotgun");
    }

    public static List<GunSubstatType> getEligibleSubstats(ItemStack stack) {
        List<GunSubstatType> list = new ArrayList<>();
        boolean boltOrPump = isBoltActionOrPump(stack);
        for (GunSubstatType type : GunSubstatType.values()) {
            if (type.isUniversal() || (type == GunSubstatType.BOLT_ACTION_SPEED && boltOrPump)) {
                list.add(type);
            }
        }
        return list;
    }

    public static SubstatResult calculateSubstats(ItemStack stack) {
        SubstatResult result = new SubstatResult();
        if (stack == null || stack.isEmpty()) return result;

        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return result;

        int totalXp = getRefineXp(stack);
        int cumRank = getRankFromXp(totalXp, 20);
        if (cumRank <= 0) return result;

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
        if (cumRank >= 1) unlockedCount = 1;
        if (cumRank >= 5) unlockedCount = 2;
        if (cumRank >= 9) unlockedCount = 3;
        if (cumRank >= 13) unlockedCount = 4;
        if (cumRank >= 17) unlockedCount = 5;

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
                if (level >= 1) activeUnlocked = 1;
                if (level >= 5) activeUnlocked = 2;
                if (level >= 9) activeUnlocked = 3;
                if (level >= 13) activeUnlocked = 4;
                if (level >= 17) activeUnlocked = 5;
                activeUnlocked = Math.min(activeUnlocked, unlockedOrder.size());

                targetType = unlockedOrder.get(rand.nextInt(Math.max(1, activeUnlocked)));
            }

            // Calculate upgrade roll with Gaussian variance
            double baseRoll = targetType.getBaseRoll() * tierMult;
            double gaussian = rand.nextGaussian(); // mean 0, stddev 1
            double variance = 1.0 + (gaussian * 0.15); // +/- 15% stddev
            variance = Math.max(0.70, Math.min(1.30, variance));

            double rolledVal = baseRoll * variance;
            double currentVal = result.values.getOrDefault(targetType, 0.0);
            result.values.put(targetType, currentVal + rolledVal);

            String crest = getTierCrestIcon(currentTier);
            String color = getTierColor(currentTier);
            String logLine = String.format("%s%s Lv.%d: %s %s (%s)", color, crest, level, targetType.getDisplayName(), targetType.formatValue(rolledVal), getTierNameForTier(currentTier));
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
        if (stack == null || stack.isEmpty()) return;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return;

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
        if (stack == null || stack.isEmpty()) return stack;
        int startingTier = GunClassificationManager.getGunTier(stack);
        if (startingTier <= 0) return stack;

        if (stack.hasTag() && stack.getTag().contains("RefineRank")) {
            return stack;
        }

        double roll = random.nextDouble();
        int rolledRank = 0;
        if (roll < 0.50) rolledRank = 0;
        else if (roll < 0.75) rolledRank = 1;
        else if (roll < 0.90) rolledRank = 2;
        else if (roll < 0.97) rolledRank = 3;
        else rolledRank = 4;

        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int targetCumulativeLevel = baseRank + rolledRank;
        int totalXp = getXpForRank(targetCumulativeLevel);

        applyRefinementDataToStack(stack, totalXp);
        return stack;
    }
}
