package com.complextalents.classification;

import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WeaponClassificationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeaponClassificationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File PRIMARY_FILE = new File("weapon_classifications.json");
    private static final File CONFIG_FILE = new File("config/weapon_classifications.json");
    private static final File RESOURCE_FILE = new File("src/main/resources/data/complextalents/weapon_data.json");

    public static class WeaponData {
        public int id = 0;
        public String item_id = "";
        public String path = "blademaster";
        public String skill_level = "novice";
        public boolean excluded = false;

        public WeaponData() {}

        public WeaponData(int id, String item_id, String path, String skill_level, boolean excluded) {
            this.id = id;
            this.item_id = item_id;
            this.path = path;
            this.skill_level = skill_level;
            this.excluded = excluded;
        }
    }

    private static Map<String, WeaponData> classifications = new TreeMap<>();

    public static void load() {
        classifications = new TreeMap<>();

        List<File> candidates = new ArrayList<>();
        try {
            candidates.add(FMLPaths.GAMEDIR.get().resolve("weapon_classifications.json").toFile());
            candidates.add(FMLPaths.CONFIGDIR.get().resolve("weapon_classifications.json").toFile());
        } catch (Exception ignored) {}

        candidates.add(PRIMARY_FILE);
        candidates.add(CONFIG_FILE);
        candidates.add(RESOURCE_FILE);

        for (File targetFile : candidates) {
            if (targetFile != null && targetFile.exists()) {
                try (FileReader reader = new FileReader(targetFile, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<List<WeaponData>>() {}.getType();
                    List<WeaponData> dataList = GSON.fromJson(reader, type);
                    if (dataList != null && !dataList.isEmpty()) {
                        for (WeaponData wd : dataList) {
                            if (wd.item_id != null && !wd.item_id.isEmpty()) {
                                classifications.put(wd.item_id, wd);
                            }
                        }
                        LOGGER.info("Loaded {} weapon classifications from file {}", classifications.size(), targetFile.getAbsolutePath());
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load weapon classifications from {}", targetFile.getAbsolutePath(), e);
                }
            }
        }

        // Try reading from embedded resource stream in JAR/Assets
        InputStream is = WeaponClassificationManager.class.getResourceAsStream("/data/complextalents/weapon_data.json");
        if (is == null) {
            is = WeaponClassificationManager.class.getResourceAsStream("/data/complextalents/weapon_classifications.json");
        }
        if (is != null) {
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<WeaponData>>() {}.getType();
                List<WeaponData> dataList = GSON.fromJson(reader, type);
                if (dataList != null && !dataList.isEmpty()) {
                    for (WeaponData wd : dataList) {
                        if (wd.item_id != null && !wd.item_id.isEmpty()) {
                            classifications.put(wd.item_id, wd);
                        }
                    }
                    LOGGER.info("Loaded {} weapon classifications from classpath resource", classifications.size());
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load weapon classifications from classpath resource", e);
            }
        }

        // If exported JSON does not exist yet, generate based on current weapon mastery data
        LOGGER.info("No weapon_classifications.json found. Populating from WeaponMasteryManager...");
        populateFromWeaponMastery();
    }

    public static void populateFromWeaponMastery() {
        WeaponMasteryManager manager = WeaponMasteryManager.getInstance();
        int counter = 1;
        for (var entry : net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation key = entry.getKey().location();
            WeaponPath path = manager.getWeaponPath(key);
            if (path != null) {
                int tier = manager.getWeaponTier(new net.minecraft.world.item.ItemStack(entry.getValue()));
                String skillLevel = mapTierToSkillLevel(tier);
                WeaponData data = new WeaponData(counter++, key.toString(), path.name().toLowerCase(), skillLevel, false);
                classifications.put(key.toString(), data);
            }
        }
    }

    public static String mapTierToSkillLevel(int tier) {
        return switch (tier) {
            case 1 -> "novice";
            case 2 -> "apprentice";
            case 3 -> "adept";
            case 4 -> "expert";
            case 5 -> "master";
            default -> "novice";
        };
    }

    public static void saveMap(Map<String, WeaponData> newMap) {
        classifications = new TreeMap<>(newMap);
        save();
    }

    public static void save() {
        List<WeaponData> exportList = new ArrayList<>();
        int counter = 1;
        for (Map.Entry<String, WeaponData> entry : classifications.entrySet()) {
            WeaponData data = entry.getValue();
            data.id = counter++;
            data.item_id = entry.getKey();
            exportList.add(data);
        }

        try {
            saveToFile(FMLPaths.GAMEDIR.get().resolve("weapon_classifications.json").toFile(), exportList);
            saveToFile(FMLPaths.CONFIGDIR.get().resolve("weapon_classifications.json").toFile(), exportList);
        } catch (Exception ignored) {}

        saveToFile(PRIMARY_FILE, exportList);
        saveToFile(CONFIG_FILE, exportList);
        if (RESOURCE_FILE.getParentFile().exists()) {
            saveToFile(RESOURCE_FILE, exportList);
        }
    }

    private static void saveToFile(File file, List<WeaponData> exportList) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(exportList, writer);
            }
            LOGGER.info("Saved {} weapon classifications to {}", exportList.size(), file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save weapon classifications to {}", file.getAbsolutePath(), e);
        }
    }

    public static WeaponData getWeaponData(String weaponId) {
        if (classifications.isEmpty()) {
            load();
        }
        return classifications.get(weaponId);
    }

    public static Map<String, WeaponData> getClassifications() {
        if (classifications.isEmpty()) {
            load();
        }
        return classifications;
    }
}
