package com.complextalents.classification;

import com.complextalents.util.WeaponFinder;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class WeaponClassificationStorage {

    public static final String[] PATHS = new String[]{"blademaster", "vanguard", "reaper", "juggernaut", "colossus", "brawler", "unassigned"};
    public static final String[] TIERS = new String[]{"novice", "apprentice", "adept", "expert", "master"};

    private static final Map<String, WeaponClassificationManager.WeaponData> dataMap = new LinkedHashMap<>();
    private static boolean initialized = false;

    // Filter and Sort Session States
    private static String pathFilter = "ALL";
    private static String tierFilter = "ALL";
    private static int excludedFilterMode = 0; // 0 = Hide Excluded, 1 = Show All, 2 = Show Only Excluded
    private static int sortMode = 0; // 0 = Tier Ascending, 1 = Tier Descending, 2 = Name A-Z
    private static int currentPage = 0;

    public static void ensureInitialized() {
        if (initialized && !dataMap.isEmpty()) {
            return;
        }
        initStorage();
    }

    public static void initStorage() {
        dataMap.clear();

        WeaponClassificationManager.load();
        Map<String, WeaponClassificationManager.WeaponData> existing = WeaponClassificationManager.getClassifications();
        if (existing != null && !existing.isEmpty()) {
            dataMap.putAll(existing);
        }

        WeaponMasteryManager masteryManager = WeaponMasteryManager.getInstance();
        List<Item> allWeapons = WeaponFinder.getAllWeapons();

        for (Item item : allWeapons) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) continue;

            String itemId = key.toString();
            if (!dataMap.containsKey(itemId)) {
                WeaponPath path = masteryManager.getWeaponPath(key);
                if (path != null) {
                    int tier = masteryManager.getWeaponTier(new ItemStack(item));
                    String skillLevel = WeaponClassificationManager.mapTierToSkillLevel(tier);
                    dataMap.put(itemId, new WeaponClassificationManager.WeaponData(0, itemId, path.name().toLowerCase(), skillLevel, false));
                } else {
                    dataMap.put(itemId, new WeaponClassificationManager.WeaponData(0, itemId, "unassigned", "novice", false));
                }
            }
        }

        initialized = true;
    }

    public static Map<String, WeaponClassificationManager.WeaponData> getDataMap() {
        ensureInitialized();
        return dataMap;
    }

    public static WeaponClassificationManager.WeaponData getWeaponData(String itemId) {
        ensureInitialized();
        return dataMap.get(itemId);
    }

    public static void cyclePath(String itemId) {
        ensureInitialized();
        WeaponClassificationManager.WeaponData data = dataMap.get(itemId);
        if (data == null) return;

        String current = data.path.toLowerCase();
        int idx = -1;
        for (int i = 0; i < PATHS.length; i++) {
            if (PATHS[i].equals(current)) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + 1) % PATHS.length;
        data.path = PATHS[nextIdx];
    }

    public static void cycleTier(String itemId) {
        ensureInitialized();
        WeaponClassificationManager.WeaponData data = dataMap.get(itemId);
        if (data == null) return;

        String current = data.skill_level.toLowerCase();
        int idx = -1;
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(current)) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + 1) % TIERS.length;
        data.skill_level = TIERS[nextIdx];
    }

    public static void toggleExcluded(String itemId) {
        ensureInitialized();
        WeaponClassificationManager.WeaponData data = dataMap.get(itemId);
        if (data == null) return;

        data.excluded = !data.excluded;
    }

    public static List<Component> getStatisticsLore() {
        ensureInitialized();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§6=== Weapon Statistics ==="));
        lore.add(Component.literal("§7Total Registered Weapons: §f" + dataMap.size()));
        lore.add(Component.literal(""));

        Map<String, Integer> pathCounts = new HashMap<>();
        Map<String, Integer> tierCounts = new HashMap<>();
        int excludedCount = 0;

        for (WeaponClassificationManager.WeaponData wd : dataMap.values()) {
            if (wd.excluded) {
                excludedCount++;
            } else {
                pathCounts.put(wd.path.toLowerCase(), pathCounts.getOrDefault(wd.path.toLowerCase(), 0) + 1);
                tierCounts.put(wd.skill_level.toLowerCase(), tierCounts.getOrDefault(wd.skill_level.toLowerCase(), 0) + 1);
            }
        }

        lore.add(Component.literal("§9Paths Breakdown:"));
        for (String path : PATHS) {
            int count = pathCounts.getOrDefault(path.toLowerCase(), 0);
            lore.add(Component.literal(" §8- §f" + capitalize(path) + ": §b" + count));
        }

        lore.add(Component.literal(""));
        lore.add(Component.literal("§eTiers Breakdown:"));
        for (String tier : TIERS) {
            int count = tierCounts.getOrDefault(tier.toLowerCase(), 0);
            lore.add(Component.literal(" §8- §f" + capitalize(tier) + ": §a" + count));
        }

        lore.add(Component.literal(""));
        lore.add(Component.literal("§cExcluded: §f" + excludedCount));

        return lore;
    }

    public static void exportToManager() {
        ensureInitialized();
        WeaponClassificationManager.saveMap(dataMap);
    }

    // Session State Getters & Setters
    public static String getPathFilter() { return pathFilter; }
    public static void setPathFilter(String filter) { pathFilter = filter; }

    public static String getTierFilter() { return tierFilter; }
    public static void setTierFilter(String filter) { tierFilter = filter; }

    public static int getExcludedFilterMode() { return excludedFilterMode; }
    public static void setExcludedFilterMode(int mode) { excludedFilterMode = mode; }

    public static int getSortMode() { return sortMode; }
    public static void setSortMode(int mode) { sortMode = mode; }

    public static int getCurrentPage() { return currentPage; }
    public static void setCurrentPage(int page) { currentPage = page; }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
