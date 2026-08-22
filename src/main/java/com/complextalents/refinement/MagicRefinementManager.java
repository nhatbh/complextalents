package com.complextalents.refinement;

import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class MagicRefinementManager {

    private static final MagicRefinementManager INSTANCE = new MagicRefinementManager();

    public static MagicRefinementManager getInstance() {
        return INSTANCE;
    }

    private MagicRefinementManager() {}

    public static class RefinementState {
        public final int currentTier; // 1 to 5
        public final int refineInTier; // 0 to 4
        public final int cumulativeLevel; // 0 to 20
        public final boolean isMaxed; // cumulativeLevel >= 20
        public final boolean isScroll;

        public RefinementState(int currentTier, int refineInTier, int cumulativeLevel, boolean isMaxed, boolean isScroll) {
            this.currentTier = currentTier;
            this.refineInTier = refineInTier;
            this.cumulativeLevel = cumulativeLevel;
            this.isMaxed = isMaxed;
            this.isScroll = isScroll;
        }

        public String getTierName() {
            if (isScroll) {
                return switch (currentTier) {
                    case 1 -> "Inscribed";
                    case 2 -> "Illuminated";
                    case 3 -> "Enchanted";
                    case 4 -> "Gilded";
                    case 5 -> "Transcendent";
                    default -> "Inscribed";
                };
            } else {
                return switch (currentTier) {
                    case 1 -> "Attuned";
                    case 2 -> "Resonant";
                    case 3 -> "Radiant";
                    case 4 -> "Astral";
                    case 5 -> "Sovereign";
                    default -> "Attuned";
                };
            }
        }

        public String getRefineDisplay() {
            return getTierName() + " (+" + refineInTier + ")";
        }
    }

    public static int getBaseCumulativeLevelForStartingTier(int startingTier) {
        return switch (startingTier) {
            case 1 -> 0;  // Tier 1 Base (+0) = Lv 0
            case 2 -> 4;  // Tier 2 Base (+0) = Lv 4
            case 3 -> 8;  // Tier 3 Base (+0) = Lv 8
            case 4 -> 12; // Tier 4 Base (+0) = Lv 12
            case 5 -> 16; // Tier 5 Base (+0) = Lv 16
            default -> 0;
        };
    }

    public static int getMaxRefinesForTier(int tier) {
        return 4; // Each rank spans 4 levels (Cumulative Lv 20 Max)
    }

    public static RefinementState calculateRefinementState(int startingTier, int totalRefines, boolean isScroll) {
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
        return new RefinementState(currentTier, refineInTier, cumLevel, isMaxed, isScroll);
    }

    /**
     * Catalyst Spell Power Bonus % curve (+200% Max Cap).
     */
    public static double getCatalystSpellPowerBonus(int cumulativeLevel) {
        double[] bonuses = {
            0.00, 0.05, 0.10, 0.15, 0.20, 0.28, 0.36, 0.44, 0.52, 0.62, 0.72, 0.82, 0.92, 1.05, 1.18, 1.31, 1.44, 1.60, 1.75, 1.90, 2.00
        };
        int index = Math.max(0, Math.min(bonuses.length - 1, cumulativeLevel));
        return bonuses[index];
    }

    /**
     * Scroll Spell Damage Bonus % curve (+150% Max Cap).
     */
    public static double getScrollSpellDamageBonus(int cumulativeLevel) {
        double[] bonuses = {
            0.00, 0.05, 0.10, 0.15, 0.20, 0.26, 0.32, 0.38, 0.44, 0.51, 0.58, 0.65, 0.72, 0.80, 0.88, 0.96, 1.04, 1.15, 1.26, 1.38, 1.50
        };
        int index = Math.max(0, Math.min(bonuses.length - 1, cumulativeLevel));
        return bonuses[index];
    }

    /**
     * Scroll Mana Cost Reduction % curve (50% Max Cap).
     */
    public static double getScrollManaCostReduction(int cumulativeLevel) {
        double[] reduction = {
            0.00, 0.02, 0.04, 0.06, 0.08, 0.11, 0.14, 0.17, 0.20, 0.23, 0.26, 0.29, 0.32, 0.35, 0.38, 0.41, 0.44, 0.46, 0.48, 0.49, 0.50
        };
        int index = Math.max(0, Math.min(reduction.length - 1, cumulativeLevel));
        return reduction[index];
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
        if (cumLevel <= 4) return 1;
        else if (cumLevel <= 8) return 2;
        else if (cumLevel <= 12) return 3;
        else if (cumLevel <= 16) return 4;
        else return 5;
    }

    public static String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "\u00A7f"; // Tier 1: White
            case 2 -> "\u00A7a"; // Tier 2: Emerald Green
            case 3 -> "\u00A79"; // Tier 3: Indigo Blue
            case 4 -> "\u00A75"; // Tier 4: Purple
            case 5 -> "\u00A76"; // Tier 5: Gold
            default -> "\u00A7f";
        };
    }

    public static String getTierCrestIcon(int tier) {
        return switch (tier) {
            case 1 -> "✧";
            case 2 -> "✦";
            case 3 -> "❖";
            case 4 -> "❂";
            case 5 -> "⚜";
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

    public static boolean isScroll(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemRegistry.SCROLL.get());
    }

    public static int getMagicItemTier(ItemStack stack) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return 0;
        if (isScroll(stack)) {
            AbstractSpell spell = getScrollSpell(stack);
            if (spell != null && isEffectSpell(spell)) {
                return 0;
            }
        }
        return 1;
    }

    public static int getRefineXp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int startingTier = getMagicItemTier(stack);
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

    public enum MagicSubstatType {
        MANA_REGEN("MANA_REGEN", "Mana Regen", 0.02),
        MAX_MANA("MAX_MANA", "Max Mana", 0.02),
        COOLDOWN_REDUCTION("COOLDOWN_REDUCTION", "Cooldown Reduction", 0.015),
        CAST_TIME_REDUCTION("CAST_TIME_REDUCTION", "Cast Time Reduction", 0.015),
        MAGIC_EFFECTIVENESS("MAGIC_EFFECTIVENESS", "Magic Effectiveness", 0.015),
        SPELL_CRIT_CHANCE("SPELL_CRIT_CHANCE", "Spell Crit Chance", 0.01),
        SPELL_CRIT_DAMAGE("SPELL_CRIT_DAMAGE", "Spell Crit Damage", 0.02),
        HEAL_AND_SHIELD_POWER("HEAL_AND_SHIELD_POWER", "Heal & Shield Power", 0.02),
        SUMMONING_POWER("SUMMONING_POWER", "Summoning Power", 0.02);

        private final String key;
        private final String displayName;
        private final double baseRoll;

        MagicSubstatType(String key, String displayName, double baseRoll) {
            this.key = key;
            this.displayName = displayName;
            this.baseRoll = baseRoll;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public double getBaseRoll() { return baseRoll; }

        public String formatValue(double value) {
            if (this == MAGIC_EFFECTIVENESS) {
                double valScaled = value * 100.0;
                if (valScaled == (int) valScaled) {
                    return String.format("+%d", (int) valScaled);
                }
                return String.format("+%.1f", valScaled);
            }
            return String.format("+%.1f%%", value * 100.0);
        }

        public static MagicSubstatType fromKey(String key) {
            for (MagicSubstatType type : values()) {
                if (type.key.equalsIgnoreCase(key)) return type;
            }
            return null;
        }
    }

    public static class SubstatResult {
        public final Map<MagicSubstatType, Double> values = new LinkedHashMap<>();
        public final List<String> history = new ArrayList<>();
    }

    public static AbstractSpell getScrollSpell(ItemStack stack) {
        if (!isScroll(stack)) return null;
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) return null;
        SpellData slot = container.getSpellAtIndex(0);
        return slot != null ? slot.getSpell() : null;
    }

    public static final net.minecraft.tags.TagKey<AbstractSpell> SUMMONING_SPELLS_TAG =
            net.minecraft.tags.TagKey.create(
                    net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spells")),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("complextalents", "summoning_spells")
            );

    public static boolean isHealingSpell(AbstractSpell spell) {
        if (spell == null) return false;
        return com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell)
                == com.complextalents.classification.SpellClassificationManager.SpellType.HEAL_AND_SHIELD;
    }

    public static boolean isSummoningSpell(AbstractSpell spell) {
        if (spell == null) return false;
        
        // 1. Check the data-driven registry tag first (fully custom/addon compatible via datapacks)
        var registry = io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get();
        if (registry != null && registry.tags() != null) {
            var tag = registry.tags().getTag(SUMMONING_SPELLS_TAG);
            if (tag != null && tag.contains(spell)) {
                return true;
            }
        }

        // 2. Check JSON classifications
        return com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell)
                == com.complextalents.classification.SpellClassificationManager.SpellType.SUMMONING;
    }

    public static boolean isEffectSpell(AbstractSpell spell) {
        if (spell == null) return false;
        return com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell)
                == com.complextalents.classification.SpellClassificationManager.SpellType.EFFECT;
    }

    public static List<MagicSubstatType> getEligibleSubstats(ItemStack stack) {
        List<MagicSubstatType> list = new ArrayList<>();
        if (!isScroll(stack)) {
            return Arrays.asList(MagicSubstatType.values());
        }

        AbstractSpell spell = getScrollSpell(stack);
        boolean canHeal = isHealingSpell(spell);
        boolean canSummon = isSummoningSpell(spell);

        for (MagicSubstatType type : MagicSubstatType.values()) {
            if (type == MagicSubstatType.MANA_REGEN || type == MagicSubstatType.MAX_MANA) {
                continue;
            }
            if (type == MagicSubstatType.HEAL_AND_SHIELD_POWER && !canHeal) {
                continue;
            }
            if (type == MagicSubstatType.SUMMONING_POWER && !canSummon) {
                continue;
            }
            list.add(type);
        }
        return list;
    }

    public static SubstatResult calculateSubstats(ItemStack stack) {
        SubstatResult result = new SubstatResult();
        if (stack == null || stack.isEmpty()) return result;

        int startingTier = getMagicItemTier(stack);
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
        List<MagicSubstatType> pool = getEligibleSubstats(stack);
        List<MagicSubstatType> unlockedOrder = new ArrayList<>();

        boolean isScroll = isScroll(stack);

        int unlockedCount = 0;
        if (cumRank >= 1) unlockedCount = 1;
        if (cumRank >= 5) unlockedCount = 2;
        if (cumRank >= 9) unlockedCount = 3;
        if (cumRank >= 13) unlockedCount = 4;
        if (cumRank >= 17) unlockedCount = 5;

        List<MagicSubstatType> availablePool = new ArrayList<>(pool);
        for (int i = 0; i < unlockedCount && !availablePool.isEmpty(); i++) {
            int idx = rand.nextInt(availablePool.size());
            unlockedOrder.add(availablePool.remove(idx));
        }

        for (MagicSubstatType st : unlockedOrder) {
            result.values.put(st, 0.0);
        }

        for (int level = 1; level <= cumRank; level++) {
            int currentTier = getTierForCumulativeLevel(level);
            double tierMult = getTierMultiplier(currentTier);

            boolean isUnlockLevel = (level == 1 || level == 5 || level == 9 || level == 13 || level == 17);

            MagicSubstatType targetType;
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
                int activeUnlocked = 0;
                if (level >= 1) activeUnlocked = 1;
                if (level >= 5) activeUnlocked = 2;
                if (level >= 9) activeUnlocked = 3;
                if (level >= 13) activeUnlocked = 4;
                if (level >= 17) activeUnlocked = 5;
                activeUnlocked = Math.min(activeUnlocked, unlockedOrder.size());

                targetType = unlockedOrder.get(rand.nextInt(Math.max(1, activeUnlocked)));
            }

            double baseRoll = targetType.getBaseRoll() * tierMult;
            double gaussian = rand.nextGaussian();
            double variance = 1.0 + (gaussian * 0.15);
            variance = Math.max(0.70, Math.min(1.30, variance));

            double rolledVal = baseRoll * variance;
            double currentVal = result.values.getOrDefault(targetType, 0.0);
            result.values.put(targetType, currentVal + rolledVal);

            String crest = getTierCrestIcon(currentTier);
            String color = getTierColor(currentTier);
            String tierName = new RefinementState(currentTier, 0, level, false, isScroll).getTierName();
            String logLine = String.format("%s%s Lv.%d: %s %s (%s)", color, crest, level, targetType.getDisplayName(), targetType.formatValue(rolledVal), tierName);
            result.history.add(logLine);
        }

        return result;
    }

    public static void applyRefinementDataToStack(ItemStack stack, int newXp) {
        if (stack == null || stack.isEmpty()) return;
        int startingTier = getMagicItemTier(stack);
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

        SubstatResult result = calculateSubstats(stack);
        CompoundTag substatsTag = new CompoundTag();
        for (Map.Entry<MagicSubstatType, Double> entry : result.values.entrySet()) {
            substatsTag.putDouble(entry.getKey().getKey(), entry.getValue());
        }
        tag.put("RefineSubstats", substatsTag);

        ListTag historyTag = new ListTag();
        for (String hist : result.history) {
            historyTag.add(StringTag.valueOf(hist));
        }
        tag.put("RefineHistory", historyTag);
    }

    public static double getSpellSubstatValue(ItemStack stack, AbstractSpell spell, MagicSubstatType type) {
        if (stack == null || stack.isEmpty() || spell == null || type == null) return 0.0;

        // 1. If it's a scroll: only apply if the scroll's spell matches the cast spell
        if (isScroll(stack)) {
            AbstractSpell scrollSpell = getScrollSpell(stack);
            if (scrollSpell != null && scrollSpell.getSpellId().equals(spell.getSpellId())) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("RefineSubstats")) {
                    return tag.getCompound("RefineSubstats").getDouble(type.getKey());
                }
            }
            return 0.0;
        }

        // 2. If it's a spellbook/catalyst: check inscribed RefinedSpells NBT
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RefinedSpells")) {
            CompoundTag refinedSpells = tag.getCompound("RefinedSpells");
            String spellId = spell.getSpellId();
            if (refinedSpells.contains(spellId)) {
                CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                if (spellRefineData.contains("RefineSubstats")) {
                    return spellRefineData.getCompound("RefineSubstats").getDouble(type.getKey());
                }
            }
        }
        return 0.0;
    }
    public static double getSpellRefinementMainstatBonus(ItemStack stack, AbstractSpell spell) {
        if (stack == null || stack.isEmpty() || spell == null) return 0.0;

        int currentXp = 0;
        if (isScroll(stack)) {
            AbstractSpell scrollSpell = getScrollSpell(stack);
            if (scrollSpell != null && scrollSpell.getSpellId().equals(spell.getSpellId())) {
                currentXp = getRefineXp(stack);
            }
        } else {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("RefinedSpells")) {
                CompoundTag refinedSpells = tag.getCompound("RefinedSpells");
                String spellId = spell.getSpellId();
                if (refinedSpells.contains(spellId)) {
                    CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                    currentXp = spellRefineData.getInt("RefineXP");
                }
            }
        }

        if (currentXp > 0) {
            int cumRank = getRankFromXp(currentXp, 20);
            return getScrollSpellDamageBonus(cumRank);
        }
        return 0.0;
    }
}
