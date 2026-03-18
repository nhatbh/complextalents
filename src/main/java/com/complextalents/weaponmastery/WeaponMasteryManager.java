package com.complextalents.weaponmastery;

import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages JSON loading and parsing for Weapon Mastery paths.
 */
public class WeaponMasteryManager implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WeaponMasteryManager INSTANCE = new WeaponMasteryManager();
    private static final Gson GSON = new Gson();

    private final Map<ResourceLocation, WeaponPath> weaponToPathMap = new HashMap<>();
    private final Map<ResourceLocation, Integer> weaponToRequiredRankMap = new HashMap<>();

    private WeaponMasteryManager() {}

    public static WeaponMasteryManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        // Initialization can remain empty as the data is loaded via the reload listener
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
        // Maps the Rank string to the starting level required
        // Novice implies Levels 1-5 (Starts requiring Rank 1, or level > 0 depending on implementation. Let's say Novice is index 0)
        // Novice = 0
        // Apprentice = 5
        // Adept = 10
        // Expert = 15
        // Master = 20
        return switch (skillLevel.toLowerCase()) {
            case "novice" -> 0;
            case "apprentice" -> 5;
            case "adept" -> 10;
            case "expert" -> 15;
            case "master" -> 20;
            default -> 0;
        };
    }

    public WeaponPath getWeaponPath(ResourceLocation itemId) {
        return weaponToPathMap.get(itemId);
    }

    public int getRequiredRankValue(ResourceLocation itemId) {
        return weaponToRequiredRankMap.getOrDefault(itemId, 0);
    }

    // --- Damage Milestone Methods ---
    
    /**
     * Gets the total damage required to fully complete a sub-level.
     * Starts from Level 1 (index 0) to Level 25.
     * Level represents current level, starting at 0 to 25.
     */
    public double getDamageRequiredForNextLevel(int currentLevel) {
        if (currentLevel >= 25) return Double.MAX_VALUE; // Maxed out
        
        if (currentLevel < 5) {
            // Novice 1-5
            return 1000.0 * (currentLevel + 1); // 1000 to 5000 max
        } else if (currentLevel < 10) {
            // Apprentice
            return 5000.0 + (4000.0 * (currentLevel - 4)); // 9000 to 25000 max
        } else if (currentLevel < 15) {
            // Adept
            return 25000.0 + (15000.0 * (currentLevel - 9)); // 40000 to 100000 max
        } else if (currentLevel < 20) {
            // Expert
            return 100000.0 + (50000.0 * (currentLevel - 14)); // 150000 to 350000 max
        } else {
            // Master
            return 350000.0 + (130000.0 * (currentLevel - 19)); // 480000 to 1000000 max
        }
    }

    /**
     * Finds the maximum level unlocked based on accumulated damage.
     */
    public int calculateUnlockableLevel(double accumulatedDamage) {
        for (int i = 0; i < 25; i++) {
            if (accumulatedDamage < getDamageRequiredForNextLevel(i)) {
                return i;
            }
        }
        return 25; // Max level reached
    }

    /**
     * SP Cost to purchase the NEXT level (0 -> 1 costs Novice amount).
     * @param currentLevel Current level (0-24)
     * @return SP cost
     */
    public int getSPCostForNextLevel(int currentLevel) {
        if (currentLevel < 5) return 1;
        if (currentLevel < 10) return 2;
        if (currentLevel < 15) return 2;
        if (currentLevel < 20) return 3;
        if (currentLevel < 25) return 4;
        return 0; // Maxed
    }
}
