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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages JSON loading and parsing for Weapon Mastery paths.
 * Tier Distribution (15 Levels Max):
 * Novice (L1-2, 2 Ranks) -> Apprentice (L3-5, 3 Ranks) -> Adept (L6-9, 4 Ranks) -> Expert (L10-14, 5 Ranks) -> Master (L15, 1 Pinnacle Rank)
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
    }

    private void parseJsonArray(JsonArray jsonArray) {
        for (JsonElement element : jsonArray) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("item_id") && obj.has("path") && obj.has("skill_level")) {
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
                    LOGGER.warn("Invalid WeaponPath '{}' for item '{}'", pathStr, itemIdStr);
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
        return weaponToPathMap.get(itemId);
    }

    public WeaponPath getWeaponPath(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null ? weaponToPathMap.get(id) : null;
    }

    public int getRequiredRankValue(ResourceLocation itemId) {
        return weaponToRequiredRankMap.getOrDefault(itemId, 0);
    }

    public int getWeaponTier(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !weaponToPathMap.containsKey(id)) return 0;
        int rankValue = weaponToRequiredRankMap.getOrDefault(id, 0);
        return switch (rankValue) {
            case 0 -> 1;  // Novice
            case 2 -> 2;  // Apprentice
            case 5 -> 3;  // Adept
            case 9 -> 4;  // Expert
            case 14 -> 5; // Master
            default -> 1;
        };
    }

    public List<net.minecraft.world.item.Item> getWeaponsForPathAndTier(WeaponPath path, int tier) {
        int requiredRank = switch (tier) {
            case 1 -> 0;  // Novice
            case 2 -> 2;  // Apprentice
            case 3 -> 5;  // Adept
            case 4 -> 9;  // Expert
            case 5 -> 14; // Master
            default -> 0;
        };
        return getWeaponsForPathAndRank(path, requiredRank);
    }

    public List<net.minecraft.world.item.Item> getWeaponsForPathAndRank(WeaponPath path, int requiredRank) {
        List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, WeaponPath> entry : weaponToPathMap.entrySet()) {
            if (entry.getValue() == path) {
                ResourceLocation itemId = entry.getKey();
                int rank = weaponToRequiredRankMap.getOrDefault(itemId, 0);
                if (rank == requiredRank) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
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
        if (currentLevel >= 15) return 0; // Maxed

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
            case 14 -> 5;

            default -> 1;
        };
    }

    /**
     * SP Cost to purchase the NEXT level, adjusted by the origin's cost multiplier.
     */
    public int getSPCostForNextLevel(int currentLevel, ResourceLocation originId) {
        int baseCost = getSPCostForNextLevel(currentLevel);
        double multiplier = ClassCostMatrix.getWeaponMasteryCostMultiplier(originId);
        return Math.max(1, (int) Math.round(baseCost * multiplier));
    }
}
