package com.complextalents.gunmastery.classification;

import com.complextalents.tacz.GunType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages TACZ firearm classifications, tiers, and mastery level requirements loaded from gun_data.json.
 */
public class GunClassificationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GunClassificationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File PRIMARY_FILE = new File("gun_data.json");
    private static final File CONFIG_FILE = new File("config/gun_data.json");
    private static final File RESOURCE_FILE = new File("src/main/resources/data/complextalents/gun_data.json");

    public static class GunEntry {
        public int id;
        public String item_id = "";
        public String archetype = "PISTOL";
        public String rank = "Recruit";
        public int tier = 1;

        public GunEntry() {}

        public GunEntry(int id, String item_id, String archetype, String rank, int tier) {
            this.id = id;
            this.item_id = item_id;
            this.archetype = archetype;
            this.rank = rank;
            this.tier = tier;
        }

        /**
         * Returns the minimum Gun Mastery level required for this gun's rank tier:
         * Tier 1 (Recruit): Level 0 (usable with no level requirement at base)
         * Tier 2 (Trooper): Level 5
         * Tier 3 (Sergeant): Level 9
         * Tier 4 (Captain): Level 13
         * Tier 5 (General): Level 17
         */
        public int getRequiredMasteryLevel() {
            return switch (tier) {
                case 1 -> 0;  // Recruit
                case 2 -> 5;  // Trooper
                case 3 -> 9;  // Sergeant
                case 4 -> 13; // Captain
                case 5 -> 17; // General
                default -> 0;
            };
        }

        public GunType getGunType() {
            try {
                return GunType.valueOf(archetype.toUpperCase());
            } catch (Exception e) {
                return GunType.PISTOL;
            }
        }
    }

    private static Map<String, GunEntry> gunEntries = new TreeMap<>();

    public static void load() {
        gunEntries = new TreeMap<>();

        List<File> candidates = new ArrayList<>();
        try {
            candidates.add(FMLPaths.GAMEDIR.get().resolve("gun_data.json").toFile());
            candidates.add(FMLPaths.CONFIGDIR.get().resolve("gun_data.json").toFile());
        } catch (Exception ignored) {}

        candidates.add(PRIMARY_FILE);
        candidates.add(CONFIG_FILE);
        candidates.add(RESOURCE_FILE);

        for (File targetFile : candidates) {
            if (targetFile != null && targetFile.exists()) {
                try (FileReader reader = new FileReader(targetFile, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<List<GunEntry>>() {}.getType();
                    List<GunEntry> dataList = GSON.fromJson(reader, type);
                    if (dataList != null && !dataList.isEmpty()) {
                        for (GunEntry entry : dataList) {
                            if (entry.item_id != null && !entry.item_id.isEmpty()) {
                                gunEntries.put(entry.item_id, entry);
                            }
                        }
                        LOGGER.info("Loaded {} gun data classifications from {}", gunEntries.size(), targetFile.getAbsolutePath());
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load gun data from {}", targetFile.getAbsolutePath(), e);
                }
            }
        }

        // Fallback: Read from classpath resource stream
        InputStream is = GunClassificationManager.class.getResourceAsStream("/data/complextalents/gun_data.json");
        if (is != null) {
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<GunEntry>>() {}.getType();
                List<GunEntry> dataList = GSON.fromJson(reader, type);
                if (dataList != null && !dataList.isEmpty()) {
                    for (GunEntry entry : dataList) {
                        if (entry.item_id != null && !entry.item_id.isEmpty()) {
                            gunEntries.put(entry.item_id, entry);
                        }
                    }
                    LOGGER.info("Loaded {} gun data classifications from classpath resource", gunEntries.size());
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load gun data from classpath resource", e);
            }
        }

        LOGGER.warn("No gun_data.json found for GunClassificationManager.");
    }

    public static GunEntry getGunEntry(String itemId) {
        if (gunEntries.isEmpty()) {
            load();
        }
        return gunEntries.get(itemId);
    }

    public static GunEntry getGunEntry(ResourceLocation res) {
        if (res == null) return null;
        return getGunEntry(res.toString());
    }

    public static GunEntry getGunEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        ResourceLocation gunRes = iGun != null ? iGun.getGunId(stack) : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (gunRes == null) return null;
        return getGunEntry(gunRes);
    }

    public static int getRequiredMasteryLevel(String itemId) {
        GunEntry entry = getGunEntry(itemId);
        return entry != null ? entry.getRequiredMasteryLevel() : 0;
    }

    public static int getRequiredMasteryLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        GunEntry entry = getGunEntry(stack);
        if (entry == null) return 0;
        int baseReq = entry.getRequiredMasteryLevel();
        if (stack.hasTag() && stack.getTag().contains("RefineXP")) {
            int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(stack);
            int baseRank = com.complextalents.gunmastery.GunRefinementManager.getBaseCumulativeLevelForStartingTier(entry.tier);
            int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);
            int refineInTier = Math.max(0, cumRank - baseRank);
            return Math.min(20, baseReq + refineInTier);
        }
        return baseReq;
    }

    public static int getGunTier(ItemStack stack) {
        GunEntry entry = getGunEntry(stack);
        return entry != null ? entry.tier : 0;
    }

    public static Map<String, GunEntry> getGunEntries() {
        if (gunEntries.isEmpty()) {
            load();
        }
        return Collections.unmodifiableMap(gunEntries);
    }
}
