package com.complextalents.classification;

import com.complextalents.refinement.MagicRefinementManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SpellClassificationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpellClassificationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File PRIMARY_FILE = new File("spell_classifications.json");
    private static final File CONFIG_FILE = new File("config/spell_classifications.json");
    private static final File RESOURCE_FILE = new File("src/main/resources/data/complextalents/spell_classifications.json");

    public enum SpellType {
        HEAL_AND_SHIELD("Heal and Shield"),
        EFFECT("Effect"),
        SUMMONING("Summoning"),
        DAMAGE("Damage");

        private final String displayName;

        SpellType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class SpellClassificationEntry {
        public String spell_id = "";
        public String type = "DAMAGE";

        public SpellClassificationEntry() {}

        public SpellClassificationEntry(String spell_id, String type) {
            this.spell_id = spell_id;
            this.type = type;
        }
    }

    private static Map<String, SpellType> runtimeClassifications = new TreeMap<>();

    public static void load() {
        runtimeClassifications = new TreeMap<>();

        // 1. Try dev workspace resource file
        if (RESOURCE_FILE.exists()) {
            try (FileReader reader = new FileReader(RESOURCE_FILE, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<SpellClassificationEntry>>() {}.getType();
                List<SpellClassificationEntry> dataList = GSON.fromJson(reader, type);
                if (dataList != null && !dataList.isEmpty()) {
                    for (SpellClassificationEntry entry : dataList) {
                        if (entry.spell_id != null && !entry.spell_id.isEmpty()) {
                            try {
                                runtimeClassifications.put(entry.spell_id, SpellType.valueOf(entry.type.toUpperCase()));
                            } catch (IllegalArgumentException e) {
                                runtimeClassifications.put(entry.spell_id, SpellType.DAMAGE);
                            }
                        }
                    }
                    LOGGER.info("Loaded {} spell classifications from dev resource file {}", runtimeClassifications.size(), RESOURCE_FILE.getAbsolutePath());
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load spell classifications from dev resource file {}", RESOURCE_FILE.getAbsolutePath(), e);
            }
        }

        // 2. Try reading from classpath resource stream (mod's data)
        InputStream is = SpellClassificationManager.class.getResourceAsStream("/data/complextalents/spell_classifications.json");
        if (is != null) {
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<SpellClassificationEntry>>() {}.getType();
                List<SpellClassificationEntry> dataList = GSON.fromJson(reader, type);
                if (dataList != null && !dataList.isEmpty()) {
                    for (SpellClassificationEntry entry : dataList) {
                        if (entry.spell_id != null && !entry.spell_id.isEmpty()) {
                            try {
                                runtimeClassifications.put(entry.spell_id, SpellType.valueOf(entry.type.toUpperCase()));
                            } catch (IllegalArgumentException e) {
                                runtimeClassifications.put(entry.spell_id, SpellType.DAMAGE);
                            }
                        }
                    }
                    LOGGER.info("Loaded {} spell classifications from classpath resource", runtimeClassifications.size());
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load spell classifications from classpath resource", e);
            }
        }

        LOGGER.warn("No spell classifications found in mod's data. Spells will default to DAMAGE.");
    }

    public static void generateAndSave() {
        // 1. Read existing dump file if it exists, to preserve user manual edits in the dump
        Map<String, SpellType> dumpClassifications = new TreeMap<>();
        List<File> candidates = new ArrayList<>();
        try {
            candidates.add(FMLPaths.GAMEDIR.get().resolve("spell_classifications.json").toFile());
            candidates.add(FMLPaths.CONFIGDIR.get().resolve("spell_classifications.json").toFile());
        } catch (Exception ignored) {}
        candidates.add(PRIMARY_FILE);
        candidates.add(CONFIG_FILE);

        for (File targetFile : candidates) {
            if (targetFile != null && targetFile.exists()) {
                try (FileReader reader = new FileReader(targetFile, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<List<SpellClassificationEntry>>() {}.getType();
                    List<SpellClassificationEntry> dataList = GSON.fromJson(reader, type);
                    if (dataList != null && !dataList.isEmpty()) {
                        for (SpellClassificationEntry entry : dataList) {
                            if (entry.spell_id != null && !entry.spell_id.isEmpty()) {
                                try {
                                    dumpClassifications.put(entry.spell_id, SpellType.valueOf(entry.type.toUpperCase()));
                                } catch (IllegalArgumentException e) {
                                    dumpClassifications.put(entry.spell_id, SpellType.DAMAGE);
                                }
                            }
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Iterate all registered spells, keeping pre-existing manual classifications or defaulting to DAMAGE
        List<SpellClassificationEntry> exportList = new ArrayList<>();
        var registry = io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get();
        if (registry != null) {
            for (AbstractSpell spell : registry.getValues()) {
                if (spell == null || spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) continue;
                String id = spell.getSpellId();
                SpellType type = dumpClassifications.getOrDefault(id, SpellType.DAMAGE);
                exportList.add(new SpellClassificationEntry(id, type.name()));
            }
        }

        // 3. Save the dump file
        try {
            saveToFile(FMLPaths.GAMEDIR.get().resolve("spell_classifications.json").toFile(), exportList);
            saveToFile(FMLPaths.CONFIGDIR.get().resolve("spell_classifications.json").toFile(), exportList);
        } catch (Exception ignored) {}

        saveToFile(PRIMARY_FILE, exportList);
        saveToFile(CONFIG_FILE, exportList);

        // Also save to RESOURCE_FILE if in workspace
        if (RESOURCE_FILE.getParentFile() != null && RESOURCE_FILE.getParentFile().exists()) {
            saveToFile(RESOURCE_FILE, exportList);
        }
    }

    public static void saveList(List<SpellClassificationEntry> exportList) {
        try {
            saveToFile(FMLPaths.GAMEDIR.get().resolve("spell_classifications.json").toFile(), exportList);
            saveToFile(FMLPaths.CONFIGDIR.get().resolve("spell_classifications.json").toFile(), exportList);
        } catch (Exception ignored) {}

        saveToFile(PRIMARY_FILE, exportList);
        saveToFile(CONFIG_FILE, exportList);

        if (RESOURCE_FILE.getParentFile() != null && RESOURCE_FILE.getParentFile().exists()) {
            saveToFile(RESOURCE_FILE, exportList);
        }

        // Reload changes into runtime immediately
        load();
    }

    private static void saveToFile(File file, List<SpellClassificationEntry> exportList) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(exportList, writer);
            }
            LOGGER.info("Saved {} spell classifications to {}", exportList.size(), file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save spell classifications to {}", file.getAbsolutePath(), e);
        }
    }

    public static SpellType getOrAutoClassify(AbstractSpell spell) {
        if (spell == null) return SpellType.DAMAGE;
        String idStr = spell.getSpellId();
        if (runtimeClassifications.containsKey(idStr)) {
            return runtimeClassifications.get(idStr);
        }
        return SpellType.DAMAGE;
    }
}
