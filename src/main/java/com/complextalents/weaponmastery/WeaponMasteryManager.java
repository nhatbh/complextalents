package com.complextalents.weaponmastery;

import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages JSON loading and parsing for Weapon Mastery paths.
 * Tier Distribution (15 Levels Max):
 * Novice (L1-2, 2 Ranks) -> Apprentice (L3-5, 3 Ranks) -> Adept (L6-9, 4 Ranks)
 * -> Expert (L10-14, 5 Ranks) -> Master (L15, 1 Pinnacle Rank)
 */
public class WeaponMasteryManager implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WeaponMasteryManager INSTANCE = new WeaponMasteryManager();
    private static final Gson GSON = new Gson();

    private final Map<ResourceLocation, WeaponPath> weaponToPathMap = new HashMap<>();
    private final Map<ResourceLocation, Integer> weaponToRequiredRankMap = new HashMap<>();

    private WeaponMasteryManager() {
    }

    public static WeaponMasteryManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (weaponToPathMap.isEmpty()) {
            loadFromClasspathResource();
        }
    }

    private void loadFromClasspathResource() {
        try (InputStream is = WeaponMasteryManager.class.getResourceAsStream("/data/complextalents/weapon_data.json")) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);
                    parseJsonArray(jsonArray);
                    LOGGER.info("Successfully loaded {} weapon mappings from classpath resource.",
                            weaponToPathMap.size());
                }
            } else {
                LOGGER.warn("Weapon Master Data JSON resource not found in classpath.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load weapon_data.json from classpath resource: ", e);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        weaponToPathMap.clear();
        weaponToRequiredRankMap.clear();

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("complextalents", "weapon_data.json");

        try {
            var resource = resourceManager.getResource(location);
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);
                    parseJsonArray(jsonArray);
                    LOGGER.info("Successfully loaded {} weapon mappings from datapack.", weaponToPathMap.size());
                }
            } else {
                LOGGER.warn("Weapon Master Data JSON not found in datapacks.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load weapon_data.json for Weapon Mastery: ", e);
        }

        if (weaponToPathMap.isEmpty()) {
            loadFromClasspathResource();
        }
    }

    private void parseJsonArray(JsonArray jsonArray) {
        for (JsonElement element : jsonArray) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("item_id") && obj.has("path") && obj.has("skill_level")) {
                boolean excluded = obj.has("excluded") && obj.get("excluded").getAsBoolean();
                if (excluded) {
                    continue; // Skip excluded weapons from mastery mappings
                }

                String itemIdStr = obj.get("item_id").getAsString();
                String pathStr = obj.get("path").getAsString();
                String skillLevelStr = obj.get("skill_level").getAsString();

                ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
                WeaponPath path = WeaponPath.fromString(pathStr);
                int requiredRankLevel = mapSkillLevelToRankValue(skillLevelStr);

                if (path != null) {
                    weaponToPathMap.put(itemId, path);
                    weaponToRequiredRankMap.put(itemId, requiredRankLevel);
                } else {
                    LOGGER.warn("Unassigned or invalid WeaponPath '{}' for item '{}'", pathStr, itemIdStr);
                }
            }
        }
    }

    private int mapSkillLevelToRankValue(String skillLevel) {
        // Maps the Rank string to starting level required (15-level tree)
        // Novice = 0
        // Apprentice = 2
        // Adept = 5
        // Expert = 9
        // Master = 14
        return switch (skillLevel.toLowerCase()) {
            case "novice" -> 0;
            case "apprentice" -> 2;
            case "adept" -> 5;
            case "expert" -> 9;
            case "master" -> 14;
            default -> 0;
        };
    }

    public WeaponPath getWeaponPath(ResourceLocation itemId) {
        if (weaponToPathMap.isEmpty()) {
            initialize();
        }
        return weaponToPathMap.get(itemId);
    }

    public WeaponPath getWeaponPath(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty())
            return null;
        if (weaponToPathMap.isEmpty()) {
            initialize();
        }
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null ? weaponToPathMap.get(id) : null;
    }

    public int getRequiredRankValue(ResourceLocation itemId) {
        if (weaponToRequiredRankMap.isEmpty()) {
            initialize();
        }
        return weaponToRequiredRankMap.getOrDefault(itemId, 0);
    }

    public int getWeaponTier(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty())
            return 0;
        if (weaponToPathMap.isEmpty()) {
            initialize();
        }
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !weaponToPathMap.containsKey(id))
            return 0;
        int rankValue = weaponToRequiredRankMap.getOrDefault(id, 0);
        return switch (rankValue) {
            case 0 -> 1; // Novice
            case 2 -> 2; // Apprentice
            case 5 -> 3; // Adept
            case 9 -> 4; // Expert
            case 14 -> 5; // Master
            default -> 1;
        };
    }

    public List<net.minecraft.world.item.Item> getWeaponsForPathAndTier(WeaponPath path, int tier) {
        int requiredRank = switch (tier) {
            case 1 -> 0; // Novice
            case 2 -> 2; // Apprentice
            case 3 -> 5; // Adept
            case 4 -> 9; // Expert
            case 5 -> 14; // Master
            default -> 0;
        };
        return getWeaponsForPathAndRank(path, requiredRank);
    }

    public List<net.minecraft.world.item.Item> getWeaponsForPathAndRank(WeaponPath path, int requiredRank) {
        List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, WeaponPath> entry : weaponToPathMap.entrySet()) {
            if (path == null || entry.getValue() == path) {
                ResourceLocation itemId = entry.getKey();
                int rank = weaponToRequiredRankMap.getOrDefault(itemId, 0);
                if (rank == requiredRank) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getValue(itemId);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        items.add(item);
                    }
                }
            }
        }
        return items;
    }

    // --- Damage Milestone Methods ---

    /**
     * Gets the total damage required to unlock the NEXT level (0 to 14).
     * Level represents current level (0 to 15).
     */
    public double getDamageRequiredForNextLevel(int currentLevel) {
        if (currentLevel >= 15)
            return Double.MAX_VALUE; // Maxed out at 15

        return switch (currentLevel) {
            // Novice (2 Ranks: L1-L2)
            case 0 -> 200.0;
            case 1 -> 500.0;

            // Apprentice (3 Ranks: L3-L5)
            case 2 -> 1200.0;
            case 3 -> 2200.0;
            case 4 -> 3500.0;

            // Adept (4 Ranks: L6-L9)
            case 5 -> 5500.0;
            case 6 -> 8000.0;
            case 7 -> 11000.0;
            case 8 -> 15000.0;

            // Expert (5 Ranks: L10-L14)
            case 9 -> 20000.0;
            case 10 -> 26000.0;
            case 11 -> 33000.0;
            case 12 -> 41000.0;
            case 13 -> 50000.0;

            // Master (1 Rank: L15 Pinnacle)
            case 14 -> 75000.0;

            default -> 75000.0;
        };
    }

    /**
     * Finds the maximum level unlocked based on accumulated damage.
     */
    public int calculateUnlockableLevel(double accumulatedDamage) {
        for (int i = 0; i < 15; i++) {
            if (accumulatedDamage < getDamageRequiredForNextLevel(i)) {
                return i;
            }
        }
        return 15; // Max level reached
    }

    /**
     * SP Cost to purchase the NEXT level (0 -> 1 costs 1 SP).
     *
     * @param currentLevel Current level (0-14)
     * @return SP cost
     */
    public int getSPCostForNextLevel(int currentLevel) {
        if (currentLevel >= 15)
            return 0; // Maxed

        return switch (currentLevel) {
            // Novice (L1-2)
            case 0, 1 -> 1;

            // Apprentice (L3-5)
            case 2 -> 1;
            case 3, 4 -> 2;

            // Adept (L6-9)
            case 5, 6 -> 2;
            case 7, 8 -> 3;

            // Expert (L10-14)
            case 9, 10 -> 3;
            case 11, 12, 13 -> 4;

            // Master (L15 Pinnacle)
            case 14 -> 10;

            default -> 1;
        };
    }

    /**
     * Required player level to unlock/purchase weapon mastery level.
     * Novice (L1-2): None (Level 1)
     * Apprentice (L3-5): Level 10
     * Adept (L6-9): Level 20
     * Expert (L10-14): Level 30
     * Master (L15): Level 50
     *
     * @param targetLevel Target level to unlock (1 to 15)
     * @return Required player level
     */
    public int getRequiredPlayerLevelForTier(int targetLevel) {
        if (targetLevel <= 2)
            return 1; // Novice
        if (targetLevel <= 5)
            return 10; // Apprentice
        if (targetLevel <= 9)
            return 20; // Adept
        if (targetLevel <= 14)
            return 30; // Expert
        return 50; // Master (L15)
    }

    /**
     * SP Cost to purchase the NEXT level, adjusted by the origin's cost multiplier.
     */
    public int getSPCostForNextLevel(int currentLevel, ResourceLocation originId) {
        int baseCost = getSPCostForNextLevel(currentLevel);
        double multiplier = ClassCostMatrix.getWeaponMasteryCostMultiplier(originId);
        return Math.max(1, (int) Math.round(baseCost * multiplier));
    }

    /**
     * Dynamically registers or overrides a weapon mapping at runtime.
     * Allows third-party mods to assign items to Weapon Master paths
     * programmatically.
     *
     * @param itemId            ResourceLocation of the item (e.g.
     *                          "my_mod:epic_sword")
     * @param path              WeaponPath to assign
     * @param requiredRankLevel Minimum required rank value (Novice=0, Apprentice=2,
     *                          Adept=5, Expert=9, Master=14)
     */
    public void registerWeaponMapping(ResourceLocation itemId, WeaponPath path, int requiredRankLevel) {
        if (itemId != null && path != null) {
            weaponToPathMap.put(itemId, path);
            weaponToRequiredRankMap.put(itemId, Math.max(0, requiredRankLevel));
            LOGGER.info("Registered API weapon mapping for '{}' -> Path: {}, RequiredRank: {}", itemId, path,
                    requiredRankLevel);
        }
    }

    // --- Refinement Math & Ascension System ---

    public static class RefinementState {
        public final int currentTier; // 1 to 5
        public final int refineInTier; // 0 to max for currentTier (0 = Base, 1..max)
        public final int cumulativeLevel; // 0 to 20
        public final boolean isMaxed; // true if currentTier == 5 && refineInTier >= max

        public RefinementState(int currentTier, int refineInTier, int cumulativeLevel, boolean isMaxed) {
            this.currentTier = currentTier;
            this.refineInTier = refineInTier;
            this.cumulativeLevel = cumulativeLevel;
            this.isMaxed = isMaxed;
        }

        public String getTierName() {
            return switch (currentTier) {
                case 1 -> "Novice";
                case 2 -> "Apprentice";
                case 3 -> "Adept";
                case 4 -> "Expert";
                case 5 -> "Master";
                default -> "Novice";
            };
        }

        public String getRefineDisplay() {
            return getTierName() + " (+" + refineInTier + ")";
        }
    }

    public static int getMaxRefinesForTier(int tier) {
        return switch (tier) {
            case 1 -> 2; // Novice: +1, +2 Max
            case 2 -> 2; // Apprentice: +1, +2 Max
            case 3 -> 3; // Adept: +1, +2, +3 Max
            case 4 -> 4; // Expert: +1, +2, +3, +4 Max
            case 5 -> 9; // Master: +1 to +9 (Cumulative Lv 20)
            default -> 2;
        };
    }

    public static int getBaseCumulativeLevelForStartingTier(int startingTier) {
        return switch (startingTier) {
            case 1 -> 0; // Tier 1 Base (+0) = Lv 0
            case 2 -> 3; // Tier 2 Base (+0) = Lv 3
            case 3 -> 5; // Tier 3 Base (+0) = Lv 5
            case 4 -> 8; // Tier 4 Base (+0) = Lv 8
            case 5 -> 12; // Tier 5 Base (+0) = Lv 12
            default -> 0;
        };
    }

    public static RefinementState calculateRefinementState(int startingTier, int totalRefines) {
        int currentTier = Math.max(1, Math.min(5, startingTier));
        int refineInTier = 0;
        int cumLevel = getBaseCumulativeLevelForStartingTier(currentTier);

        for (int i = 0; i < totalRefines; i++) {
            int maxRefines = getMaxRefinesForTier(currentTier);
            if (currentTier == 5 && refineInTier >= maxRefines) {
                break; // Maxed out at Tier 5 Lv 20
            }
            if (refineInTier < maxRefines) {
                refineInTier++;
            } else {
                // Ascend to next tier!
                currentTier++;
                refineInTier = 1; // Ascension automatically sets refine level to +1!
            }
            cumLevel++;
        }

        cumLevel = Math.min(20, cumLevel);
        boolean isMaxed = (currentTier == 5 && refineInTier >= getMaxRefinesForTier(5));
        return new RefinementState(currentTier, refineInTier, cumLevel, isMaxed);
    }

    public static double getADBonusMultiplier(int cumulativeLevel) {
        double[] bonuses = {
                0.00, // Lv 0: Tier 1 Base (+0%)
                0.05, // Lv 1: Tier 1 (+1) (+5%)
                0.10, // Lv 2: Tier 1 Max (+2) (+10%)
                0.15, // Lv 3: Tier 2 (+1) (+15%)
                0.20, // Lv 4: Tier 2 Max (+2) (+20%)
                0.27, // Lv 5: Tier 3 (+1) (+27%)
                0.35, // Lv 6: Tier 3 (+2) (+35%)
                0.45, // Lv 7: Tier 3 Max (+3) (+45%)
                0.57, // Lv 8: Tier 4 (+1) (+57%)
                0.70, // Lv 9: Tier 4 (+2) (+70%)
                0.85, // Lv 10: Tier 4 (+3) (+85%)
                1.05, // Lv 11: Tier 4 Max (+4) (+105%)
                1.30, // Lv 12: Tier 5 (+1) (+130%)
                1.60, // Lv 13: Tier 5 (+2) (+160%)
                1.95, // Lv 14: Tier 5 (+3) (+195%)
                2.35, // Lv 15: Tier 5 (+4) (+235%)
                2.85, // Lv 16: Tier 5 (+5) (+285%)
                3.45, // Lv 17: Tier 5 (+6) (+345%)
                4.15, // Lv 18: Tier 5 (+7) (+415%)
                5.00, // Lv 19: Tier 5 (+8) (+500%)
                6.00 // Lv 20: Tier 5 Max (+9) (+600%)
        };
        int index = Math.max(0, Math.min(bonuses.length - 1, cumulativeLevel));
        return bonuses[index];
    }

    public static int getRequiredMasteryLevel(int currentTier, int refineInTier) {
        return switch (currentTier) {
            case 1 -> (refineInTier <= 0) ? 0 : 1;
            case 2 -> (refineInTier <= 1) ? 2 : 3;
            case 3 -> {
                if (refineInTier <= 1)
                    yield 5;
                if (refineInTier == 2)
                    yield 6;
                yield 7;
            }
            case 4 -> {
                if (refineInTier <= 1)
                    yield 9;
                if (refineInTier == 2)
                    yield 10;
                if (refineInTier == 3)
                    yield 11;
                yield 12;
            }
            case 5 -> 14;
            default -> 0;
        };
    }

    public static String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "\u00A7f"; // Novice: White
            case 2 -> "\u00A7a"; // Apprentice: Emerald Green
            case 3 -> "\u00A79"; // Adept: Indigo Blue
            case 4 -> "\u00A75"; // Expert: Purple
            case 5 -> "\u00A76"; // Master: Gold
            default -> "\u00A7f";
        };
    }

    public static String getTierCrestIcon(int tier) {
        return switch (tier) {
            case 1 -> "✧"; // Novice Crest
            case 2 -> "✦"; // Apprentice Crest
            case 3 -> "❖"; // Adept Crest
            case 4 -> "❂"; // Expert Crest
            case 5 -> "⚜"; // Master Crest
            default -> "✦";
        };
    }

    public static String getRankNameForLevel(int level) {
        if (level <= 1)
            return "Novice [L" + (level + 1) + "]";
        if (level <= 4)
            return "Apprentice [L" + (level - 1) + "]";
        if (level <= 8)
            return "Adept [L" + (level - 4) + "]";
        if (level <= 13)
            return "Expert [L" + (level - 8) + "]";
        return "Master [Pinnacle]";
    }

    /**
     * Rolls a randomized total refine count for loot weapons with UNLOCKED tier
     * ascension.
     * Probability rule:
     * - Base (+0): 75%
     * - Refine +1: 75% of 25% = 18.75%
     * - Refine +2: 75% of 6.25% = 4.6875%
     * - Refine +3: 75% of 1.5625% = 1.171875% (Ascends tier!)
     * ...with an astronomical chance to ascend up to Master Tier 5 Pinnacle
     * (Cumulative Lv 20).
     */
    public static int rollRefineRankForLoot(int startingTier, net.minecraft.util.RandomSource random) {
        int baseLevel = getBaseCumulativeLevelForStartingTier(startingTier);
        int maxTotalRefines = 20 - baseLevel;
        int totalRefines = 0;

        while (totalRefines < maxTotalRefines) {
            if (random.nextDouble() < 0.75) {
                break;
            }
            totalRefines++;
        }
        return totalRefines;
    }

    /**
     * Rolls a randomized refine damage variance between -20% and +20% (-0.20 to
     * +0.20).
     * Uses a Gaussian normal distribution so probability drops exponentially toward
     * the edges (-20% and +20%).
     */
    /**
     * Exponential Gem XP Values scaling with CrateRarity (8x multiplier per tier):
     * - COMMON (Tier 1 Novice): 100 XP
     * - UNCOMMON (Tier 2 Apprentice): 800 XP (8x)
     * - RARE (Tier 3 Adept): 6,400 XP (8x)
     * - EPIC (Tier 4 Expert): 51,200 XP (8x)
     * - LEGENDARY (Tier 5 Master): 409,600 XP (8x)
     */
    public static int getGemXpValue(com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity rarity) {
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
            0, // Rank 0 (Unrefined)
            100, // Rank 1 (Tier 1 +1) -> 1 Novice Gem
            250, // Rank 2 (Tier 1 +2 Max) -> 2 Novice Gems
            600, // Rank 3 (Tier 2 +1) -> 1 Apprentice Gem (800 XP)
            1400, // Rank 4 (Tier 2 +2 Max) -> 2 Apprentice Gems
            3000, // Rank 5 (Tier 3 +1) -> 4 Apprentice Gems
            7000, // Rank 6 (Tier 3 +2) -> 1 Adept Gem (6,400 XP)
            15000, // Rank 7 (Tier 3 +3 Max) -> 2 Adept Gems
            28000, // Rank 8 (Tier 4 +1) -> 4 Adept Gems
            55000, // Rank 9 (Tier 4 +2) -> 1 Expert Gem (51,200 XP)
            110000, // Rank 10 (Tier 4 +3) -> 2 Expert Gems
            200000, // Rank 11 (Tier 4 +4 Max) -> 4 Expert Gems
            350000, // Rank 12 (Tier 5 +1) -> 7 Expert Gems
            600000, // Rank 13 (Tier 5 +2) -> 1 Master Gem (409,600 XP)
            1000000, // Rank 14 (Tier 5 +3) -> 2 Master Gems
            1500000, // Rank 15 (Tier 5 +4) -> 4 Master Gems
            2100000, // Rank 16 (Tier 5 +5) -> 5 Master Gems
            2800000, // Rank 17 (Tier 5 +6) -> 7 Master Gems
            3600000, // Rank 18 (Tier 5 +7) -> 9 Master Gems
            4500000, // Rank 19 (Tier 5 +8) -> 11 Master Gems
            5500000 // Rank 20 (Tier 5 +9 Max) -> 13 Master Gems (5.5M XP Total)
    };

    /**
     * Gets the total cumulative XP required to reach a specific refine rank (0 to
     * 20).
     */
    public static int getXpForRank(int rank) {
        if (rank <= 0)
            return 0;
        if (rank >= CUMULATIVE_XP_TABLE.length)
            return CUMULATIVE_XP_TABLE[CUMULATIVE_XP_TABLE.length - 1];
        return CUMULATIVE_XP_TABLE[rank];
    }

    /**
     * Calculates refine rank from total XP, capped at maxRank.
     */
    public static int getRankFromXp(int xp, int maxRank) {
        if (xp <= 0)
            return 0;
        int rank = 0;
        while (rank < maxRank && rank + 1 < CUMULATIVE_XP_TABLE.length && xp >= CUMULATIVE_XP_TABLE[rank + 1]) {
            rank++;
        }
        return rank;
    }

    public static int getMaxCumulativeRankForStartingTier(int startingTier) {
        return 20; // Unlimited ascension up to Pinnacle Rank 20 for all weapons
    }

    public static int getMaxXpForStartingTier(int startingTier) {
        return getXpForRank(20); // 5,500,000 XP (Pinnacle Max)
    }

    /**
     * Gets the current Weapon XP from item stack NBT ("RefineXP").
     * Always accounts for the starting weapon tier baseline XP as the minimum
     * default.
     * Fallback to getXpForRank(baseRank + RefineRank) for legacy items.
     */
    public static int getRefineXp(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return 0;
        int startingTier = getInstance().getWeaponTier(stack);
        int baseRank = getBaseCumulativeLevelForStartingTier(startingTier);
        int startingXp = getXpForRank(baseRank);

        if (!stack.hasTag())
            return startingXp;

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag.contains("RefineXP")) {
            return Math.max(startingXp, tag.getInt("RefineXP"));
        } else if (tag.contains("RefineRank")) {
            int rank = tag.getInt("RefineRank");
            int cumRank = baseRank + rank;
            return Math.max(startingXp, getXpForRank(cumRank));
        }
        return startingXp;
    }

    public enum SubstatType {
        PERCENT_AD("PERCENT_AD", "complextalents.substat.percent_ad", 0.01,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL),
        CRIT_CHANCE("CRIT_CHANCE", "complextalents.substat.crit_chance", 0.0075,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        CRIT_DAMAGE("CRIT_DAMAGE", "complextalents.substat.crit_damage", 0.02,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE),
        FLAT_AD("FLAT_AD", "complextalents.substat.flat_ad", 0.25,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        ARMOR("ARMOR", "complextalents.substat.armor", 0.5,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        MAX_HEALTH("MAX_HEALTH", "complextalents.substat.max_health", 0.5,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        ARMOR_SHRED("ARMOR_SHRED", "complextalents.substat.armor_shred", 0.0075,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        ARMOR_PIERCE("ARMOR_PIERCE", "complextalents.substat.armor_pierce", 0.8,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
        IMPACT("IMPACT", "complextalents.substat.impact", 0.02,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);

        private final String name;
        private final String translationKey;
        private final double baseValue;
        private final net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation;

        SubstatType(String name, String translationKey, double baseValue,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
            this.name = name;
            this.translationKey = translationKey;
            this.baseValue = baseValue;
            this.operation = operation;
        }

        public String getName() {
            return name;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public double getBaseValue() {
            return baseValue;
        }

        public net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation getOperation() {
            return operation;
        }

        public String getDisplayName() {
            return switch (this) {
                case PERCENT_AD -> "Bonus Attack Damage";
                case FLAT_AD -> "Flat Attack Damage";
                case CRIT_CHANCE -> "Critical Strike Chance";
                case CRIT_DAMAGE -> "Critical Strike Damage";
                case ARMOR -> "Armor";
                case MAX_HEALTH -> "Max Health";
                case ARMOR_SHRED -> "Armor Shred";
                case ARMOR_PIERCE -> "Armor Pierce";
                case IMPACT -> "Impact";
            };
        }

        public String formatValue(double value) {
            return switch (this) {
                case PERCENT_AD, CRIT_CHANCE, CRIT_DAMAGE, ARMOR_SHRED, ARMOR_PIERCE, IMPACT ->
                    String.format("+%.2f%%", value * 100.0);
                case FLAT_AD, ARMOR, MAX_HEALTH -> String.format("+%.2f", value);
            };
        }

        public static SubstatType fromString(String name) {
            for (SubstatType type : values()) {
                if (type.name.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    public static class SubstatResult {
        public final Map<SubstatType, Double> values = new java.util.LinkedHashMap<>();
        public final List<String> history = new java.util.ArrayList<>();
    }

    public static int getTierForCumulativeLevel(int cumulativeLevel) {
        if (cumulativeLevel <= 2)
            return 1;
        if (cumulativeLevel <= 4)
            return 2;
        if (cumulativeLevel <= 7)
            return 3;
        if (cumulativeLevel <= 11)
            return 4;
        return 5;
    }

    public static double getTierMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            case 3 -> 2.5;
            case 4 -> 5.0;
            case 5 -> 12.0;
            default -> 1.0;
        };
    }

    private static double getGaussianVariance(java.util.Random random) {
        double g = random.nextGaussian();
        // Standard deviation of 0.08, so 2.5 standard deviations = 0.20 (20%)
        double variance = g * 0.08;
        return Math.max(-0.20, Math.min(0.20, variance));
    }

    public static SubstatResult calculateSubstatsForSeedAndLevel(java.util.UUID seedUuid, int startingTier,
            int cumulativeLevel) {
        SubstatResult result = new SubstatResult();
        if (cumulativeLevel <= 0 || seedUuid == null) {
            return result;
        }

        long seed = seedUuid.getLeastSignificantBits() ^ seedUuid.getMostSignificantBits();
        java.util.Random random = new java.util.Random(seed);

        List<SubstatType> availablePool = new java.util.ArrayList<>(java.util.Arrays.asList(SubstatType.values()));
        List<SubstatType> unlockedList = new java.util.ArrayList<>();
        Map<SubstatType, Double> substatsMap = result.values;

        java.util.Collections.shuffle(availablePool, random);

        int baseCumulativeLevel = getBaseCumulativeLevelForStartingTier(startingTier);

        for (int lv = 1; lv <= cumulativeLevel; lv++) {
            int currentTier = getTierForCumulativeLevel(lv);
            double multiplier = getTierMultiplier(currentTier);

            boolean isUnlock = false;
            int numToUnlock = 0;

            if (lv == 1 || lv == 3 || lv == 5 || lv == 8 || lv == 12) {
                numToUnlock = 1;
                isUnlock = true;
            }

            if (isUnlock) {
                for (int i = 0; i < numToUnlock && !availablePool.isEmpty(); i++) {
                    SubstatType newType = availablePool.remove(0);
                    unlockedList.add(newType);

                    double rolledVar = getGaussianVariance(random);
                    double variance = (lv > baseCumulativeLevel) ? rolledVar : 0.0;
                    double baseVal = newType.getBaseValue();
                    double val = baseVal * multiplier * (1.0 + variance);
                    substatsMap.put(newType, val);

                    String crest = getTierColor(currentTier) + getTierCrestIcon(currentTier) + "\u00A7r\u00A77";
                    String varStr = (variance > 0.0) ? String.format(" \u00A7a(+%.2f%%)", variance * 100.0)
                            : (variance < 0.0) ? String.format(" \u00A7c(%.2f%%)", variance * 100.0)
                                    : " \u00A77(0.00%)";
                    String logLine = String.format("%s %s %s%s", crest, newType.formatValue(val),
                            newType.getDisplayName(), varStr);
                    result.history.add(logLine);
                }
            } else {
                if (!unlockedList.isEmpty()) {
                    int index = random.nextInt(unlockedList.size());
                    SubstatType typeToUpgrade = unlockedList.get(index);

                    double rolledVar = getGaussianVariance(random);
                    double variance = (lv > baseCumulativeLevel) ? rolledVar : 0.0;
                    double baseVal = typeToUpgrade.getBaseValue();
                    double upgradeAmt = baseVal * multiplier * (1.0 + variance);

                    double currentVal = substatsMap.getOrDefault(typeToUpgrade, 0.0);
                    substatsMap.put(typeToUpgrade, currentVal + upgradeAmt);

                    String crest = getTierColor(currentTier) + getTierCrestIcon(currentTier) + "\u00A7r\u00A77";
                    String varStr = (variance > 0.0) ? String.format(" \u00A7a(+%.2f%%)", variance * 100.0)
                            : (variance < 0.0) ? String.format(" \u00A7c(%.2f%%)", variance * 100.0)
                                    : " \u00A77(0.00%)";
                    String logLine = String.format("%s %s %s%s", crest, typeToUpgrade.formatValue(upgradeAmt),
                            typeToUpgrade.getDisplayName(), varStr);
                    result.history.add(logLine);
                }
            }
        }

        return result;
    }

    public static java.util.UUID getOrCreateRefineSeed(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return java.util.UUID.randomUUID();
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("RefineSeed")) {
            tag.putUUID("RefineSeed", java.util.UUID.randomUUID());
        }
        return tag.getUUID("RefineSeed");
    }

    public static void cacheSubstatsInNBT(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return;
        int startingTier = getInstance().getWeaponTier(stack);
        if (startingTier <= 0)
            return;

        int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(stack);
        RefinementState state = calculateRefinementState(startingTier, totalRefines);

        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        java.util.UUID seedUuid = getOrCreateRefineSeed(stack);

        if (totalRefines <= 0) {
            tag.remove("RefineSubstats");
            tag.remove("RefineHistory");
            tag.putInt("SubstatVersion", 1);
            return;
        }

        SubstatResult result = calculateSubstatsForSeedAndLevel(seedUuid, startingTier, state.cumulativeLevel);

        net.minecraft.nbt.ListTag substatsList = new net.minecraft.nbt.ListTag();
        for (Map.Entry<SubstatType, Double> entry : result.values.entrySet()) {
            net.minecraft.nbt.CompoundTag substatTag = new net.minecraft.nbt.CompoundTag();
            substatTag.putString("Type", entry.getKey().getName());
            substatTag.putDouble("Value", entry.getValue());
            substatsList.add(substatTag);
        }
        tag.put("RefineSubstats", substatsList);

        net.minecraft.nbt.ListTag historyList = new net.minecraft.nbt.ListTag();
        for (String logLine : result.history) {
            historyList.add(net.minecraft.nbt.StringTag.valueOf(logLine));
        }
        tag.put("RefineHistory", historyList);

        tag.putInt("SubstatVersion", 1);
    }

    public static Map<SubstatType, Double> getCachedSubstats(net.minecraft.world.item.ItemStack stack) {
        Map<SubstatType, Double> substats = new java.util.LinkedHashMap<>();
        if (stack == null || stack.isEmpty())
            return substats;

        int startingTier = getInstance().getWeaponTier(stack);
        if (startingTier <= 0)
            return substats;

        int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(stack);
        if (totalRefines > 0) {
            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains("SubstatVersion") || !tag.contains("RefineSubstats")) {
                cacheSubstatsInNBT(stack);
            }
        }

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RefineSubstats", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("RefineSubstats", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.nbt.CompoundTag entry = list.getCompound(i);
                SubstatType type = SubstatType.fromString(entry.getString("Type"));
                if (type != null) {
                    substats.put(type, entry.getDouble("Value"));
                }
            }
        }
        return substats;
    }

    public static List<String> getCachedHistory(net.minecraft.world.item.ItemStack stack) {
        List<String> history = new java.util.ArrayList<>();
        if (stack == null || stack.isEmpty())
            return history;

        int startingTier = getInstance().getWeaponTier(stack);
        if (startingTier <= 0)
            return history;

        int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(stack);
        if (totalRefines > 0) {
            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains("SubstatVersion") || !tag.contains("RefineHistory")) {
                cacheSubstatsInNBT(stack);
            }
        }

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RefineHistory", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("RefineHistory", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                history.add(list.getString(i));
            }
        }
        return history;
    }

    public static double getADBonusMultiplier(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return 0.0;
        int startingTier = getInstance().getWeaponTier(stack);
        if (startingTier <= 0)
            return 0.0;

        int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(stack);
        RefinementState state = calculateRefinementState(startingTier, totalRefines);
        return getADBonusMultiplier(state.cumulativeLevel);
    }

    public static net.minecraft.world.item.ItemStack applyRandomRefinementForLoot(
            net.minecraft.world.item.ItemStack stack, net.minecraft.util.RandomSource random) {
        if (stack == null || stack.isEmpty())
            return stack;

        int startingTier = getInstance().getWeaponTier(stack);
        if (startingTier <= 0) {
            return stack;
        }

        if (stack.hasTag() && stack.getTag().contains("RefineRank")) {
            return stack;
        }

        int rolledRank = rollRefineRankForLoot(startingTier, random);
        if (rolledRank > 0) {
            int baseLevel = getBaseCumulativeLevelForStartingTier(startingTier);
            int targetCumulativeLevel = baseLevel + rolledRank;
            int totalXp = getXpForRank(targetCumulativeLevel);

            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("RefineRank", rolledRank);
            tag.putInt("RefineXP", totalXp);
            tag.putBoolean("Unbreakable", true);
            getOrCreateRefineSeed(stack);
            cacheSubstatsInNBT(stack);
            stack.setDamageValue(0);
        }
        return stack;
    }

    public record WeaponTitle(String title, boolean isPostfix) {
        public String formatName(String originalName) {
            if (isPostfix) {
                return originalName + " " + title;
            } else {
                return title + " " + originalName;
            }
        }
    }

    public static String getTierStylePrefix(int tier) {
        return switch (tier) {
            case 1 -> "\u00A7f"; // Novice (Tier 1): White, Regular
            case 2 -> "\u00A7a\u00A7o"; // Apprentice (Tier 2 - Mid): Emerald Green, Italic
            case 3 -> "\u00A79\u00A7o"; // Adept (Tier 3 - Mid): Indigo Blue, Italic
            case 4 -> "\u00A75\u00A7l"; // Expert (Tier 4 - Last): Dark Purple, Bold
            case 5 -> "\u00A76\u00A7l"; // Master (Tier 5 - Last): Gold, Bold
            default -> "\u00A7f";
        };
    }

    private static final WeaponTitle[] BLADEMASTER_TITLES = new WeaponTitle[] {
            new WeaponTitle("Glinting", false),
            new WeaponTitle("Breeze-bound", false),
            new WeaponTitle("Gale-carved", false),
            new WeaponTitle("Silvershore", false),
            new WeaponTitle("of the Zephyr", true),
            new WeaponTitle("Wind-walker's", false),
            new WeaponTitle("Sky-severing", false),
            new WeaponTitle("of Solstice", true),
            new WeaponTitle("Valkyrie's", false),
            new WeaponTitle("Storm-carved", false),
            new WeaponTitle("Astral-honed", false),
            new WeaponTitle("of the Boreas", true),
            new WeaponTitle("Aether-woven", false),
            new WeaponTitle("Star-cleaving", false),
            new WeaponTitle("of the Firmament", true),
            new WeaponTitle("Celestial", false),
            new WeaponTitle("Constellation's", false),
            new WeaponTitle("of the Eclipse", true),
            new WeaponTitle("Cosmic-edged", false),
            new WeaponTitle("Empyrean", false),
            new WeaponTitle("of the Zenith", true)
    };

    private static final WeaponTitle[] COLOSSUS_TITLES = new WeaponTitle[] {
            new WeaponTitle("Hefty", false),
            new WeaponTitle("Crag-bound", false),
            new WeaponTitle("Ground-heaved", false),
            new WeaponTitle("Stone-breaker's", false),
            new WeaponTitle("of the Quarry", true),
            new WeaponTitle("Tectonic", false),
            new WeaponTitle("Magma-wrought", false),
            new WeaponTitle("of Gaia", true),
            new WeaponTitle("Titan-struck", false),
            new WeaponTitle("Monolithic", false),
            new WeaponTitle("Fissure-carved", false),
            new WeaponTitle("of the Abyss", true),
            new WeaponTitle("World-heaved", false),
            new WeaponTitle("Behemoth's", false),
            new WeaponTitle("of Primordial Earth", true),
            new WeaponTitle("Seismic", false),
            new WeaponTitle("Shattered-Realm", false),
            new WeaponTitle("of the Deep Core", true),
            new WeaponTitle("Cataclysmic", false),
            new WeaponTitle("Leviathan's", false),
            new WeaponTitle("of Ragnarok", true)
    };

    private static final WeaponTitle[] VANGUARD_TITLES = new WeaponTitle[] {
            new WeaponTitle("Stout", false),
            new WeaponTitle("Iron-tipped", false),
            new WeaponTitle("Steadfast", false),
            new WeaponTitle("Valorous", false),
            new WeaponTitle("of the Frontline", true),
            new WeaponTitle("War-forged", false),
            new WeaponTitle("Siege-breaker's", false),
            new WeaponTitle("of Valhalla", true),
            new WeaponTitle("Phalanx-bound", false),
            new WeaponTitle("Aegis-carved", false),
            new WeaponTitle("War-herald's", false),
            new WeaponTitle("of Conquest", true),
            new WeaponTitle("Champion's", false),
            new WeaponTitle("Ironclad-destiny", false),
            new WeaponTitle("of the Pantheon", true),
            new WeaponTitle("Bastion-born", false),
            new WeaponTitle("Imperishable", false),
            new WeaponTitle("of Immortal Honor", true),
            new WeaponTitle("Warmaster's", false),
            new WeaponTitle("Sovereign-vanguard", false),
            new WeaponTitle("of Eternal Triumph", true)
    };

    private static final WeaponTitle[] REAPER_TITLES = new WeaponTitle[] {
            new WeaponTitle("Grim", false),
            new WeaponTitle("Dusk-notched", false),
            new WeaponTitle("Blood-bound", false),
            new WeaponTitle("Shadow-threaded", false),
            new WeaponTitle("of the Styx", true),
            new WeaponTitle("Wraith-forged", false),
            new WeaponTitle("Life-drained", false),
            new WeaponTitle("of Hades", true),
            new WeaponTitle("Dread-honed", false),
            new WeaponTitle("Phantom-blade", false),
            new WeaponTitle("Abyssal-born", false),
            new WeaponTitle("of the Underworld", true),
            new WeaponTitle("Nether-reaped", false),
            new WeaponTitle("Void-harvested", false),
            new WeaponTitle("of Tartarus", true),
            new WeaponTitle("Eclipse-forged", false),
            new WeaponTitle("Soul-bound", false),
            new WeaponTitle("of Extinguished Stars", true),
            new WeaponTitle("Doom-herald's", false),
            new WeaponTitle("Eldritch-reaper", false),
            new WeaponTitle("of Oblivion", true)
    };

    private static final WeaponTitle[] JUGGERNAUT_TITLES = new WeaponTitle[] {
            new WeaponTitle("Hardened", false),
            new WeaponTitle("Iron-cast", false),
            new WeaponTitle("Steel-bound", false),
            new WeaponTitle("Bulwark-wrought", false),
            new WeaponTitle("of Iron", true),
            new WeaponTitle("Unbroken", false),
            new WeaponTitle("Immovable", false),
            new WeaponTitle("of Olympus", true),
            new WeaponTitle("Dreadnought's", false),
            new WeaponTitle("Fortified", false),
            new WeaponTitle("Unyielding", false),
            new WeaponTitle("of the Anvil", true),
            new WeaponTitle("Colossus-bound", false),
            new WeaponTitle("Obsidian-forged", false),
            new WeaponTitle("of Mythic Will", true),
            new WeaponTitle("Bastion-core", false),
            new WeaponTitle("Impervious", false),
            new WeaponTitle("of Adamant", true),
            new WeaponTitle("Titanium-sovereign", false),
            new WeaponTitle("Overlord-forged", false),
            new WeaponTitle("of the Aegis", true)
    };

    private static final WeaponTitle[] BRAWLER_TITLES = new WeaponTitle[] {
            new WeaponTitle("Bruised", false),
            new WeaponTitle("Fist-honed", false),
            new WeaponTitle("Impact-bound", false),
            new WeaponTitle("Pummeling", false),
            new WeaponTitle("of the Wild", true),
            new WeaponTitle("Fierce-hearted", false),
            new WeaponTitle("Iron-fist", false),
            new WeaponTitle("of Fenrir", true),
            new WeaponTitle("Thunder-striking", false),
            new WeaponTitle("Rampaging", false),
            new WeaponTitle("Bone-shattered", false),
            new WeaponTitle("of Ashen Fury", true),
            new WeaponTitle("Shockwave-wrought", false),
            new WeaponTitle("Berserker's", false),
            new WeaponTitle("of the Colosseum", true),
            new WeaponTitle("Gladiator's", false),
            new WeaponTitle("World-pummeled", false),
            new WeaponTitle("of Tempest Fury", true),
            new WeaponTitle("Dread-combative", false),
            new WeaponTitle("Apex-force", false),
            new WeaponTitle("of Titan Strike", true)
    };

    public static WeaponTitle getWeaponTitle(WeaponPath path, int cumulativeLevel) {
        int level = Math.max(0, Math.min(20, cumulativeLevel));
        if (path == null)
            path = WeaponPath.BLADEMASTER;

        return switch (path.name()) {
            case "BLADEMASTER" -> BLADEMASTER_TITLES[level];
            case "COLOSSUS" -> COLOSSUS_TITLES[level];
            case "VANGUARD" -> VANGUARD_TITLES[level];
            case "REAPER" -> REAPER_TITLES[level];
            case "JUGGERNAUT" -> JUGGERNAUT_TITLES[level];
            case "BRAWLER" -> BRAWLER_TITLES[level];
            default -> BLADEMASTER_TITLES[level];
        };
    }
}
