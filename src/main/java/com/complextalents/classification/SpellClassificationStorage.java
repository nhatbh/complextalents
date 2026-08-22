package com.complextalents.classification;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.network.chat.Component;

import java.util.*;

public class SpellClassificationStorage {

    private static final Map<String, SpellClassificationManager.SpellType> dataMap = new LinkedHashMap<>();
    private static boolean initialized = false;

    // Filter, Sort, Page states
    private static String typeFilter = "ALL";
    private static int sortMode = 0; // 0 = Name A-Z, 1 = Type
    private static int currentPage = 0;

    public static void ensureInitialized() {
        if (initialized && !dataMap.isEmpty()) {
            return;
        }
        initStorage();
    }

    public static void initStorage() {
        dataMap.clear();

        // Load latest from manager
        SpellClassificationManager.load();

        var registry = SpellRegistry.REGISTRY.get();
        if (registry != null) {
            for (AbstractSpell spell : registry.getValues()) {
                if (spell == null || spell == SpellRegistry.none()) continue;
                String spellId = spell.getSpellId();
                SpellClassificationManager.SpellType type = SpellClassificationManager.getOrAutoClassify(spell);
                dataMap.put(spellId, type);
            }
        }
        initialized = true;
    }

    public static Map<String, SpellClassificationManager.SpellType> getDataMap() {
        ensureInitialized();
        return dataMap;
    }

    public static SpellClassificationManager.SpellType getSpellType(String spellId) {
        ensureInitialized();
        return dataMap.get(spellId);
    }

    public static void cycleType(String spellId) {
        ensureInitialized();
        SpellClassificationManager.SpellType current = dataMap.get(spellId);
        if (current == null) current = SpellClassificationManager.SpellType.DAMAGE;

        SpellClassificationManager.SpellType[] types = SpellClassificationManager.SpellType.values();
        int idx = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == current) {
                idx = i;
                break;
            }
        }
        dataMap.put(spellId, types[(idx + 1) % types.length]);
    }

    public static void exportToManager() {
        ensureInitialized();
        List<SpellClassificationManager.SpellClassificationEntry> exportList = new ArrayList<>();
        dataMap.forEach((spellId, type) -> {
            exportList.add(new SpellClassificationManager.SpellClassificationEntry(spellId, type.name()));
        });
        SpellClassificationManager.saveList(exportList);
    }

    public static List<Component> getStatisticsLore() {
        ensureInitialized();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§6=== Spell Statistics ==="));
        lore.add(Component.literal("§7Total Spells: §f" + dataMap.size()));
        lore.add(Component.literal(""));

        Map<SpellClassificationManager.SpellType, Integer> counts = new HashMap<>();
        for (SpellClassificationManager.SpellType t : dataMap.values()) {
            counts.put(t, counts.getOrDefault(t, 0) + 1);
        }

        lore.add(Component.literal("§dTypes Breakdown:"));
        for (SpellClassificationManager.SpellType type : SpellClassificationManager.SpellType.values()) {
            int count = counts.getOrDefault(type, 0);
            lore.add(Component.literal(" §8- §f" + type.getDisplayName() + ": §b" + count));
        }

        return lore;
    }

    public static String getTypeFilter() { return typeFilter; }
    public static void setTypeFilter(String filter) { typeFilter = filter; }

    public static int getSortMode() { return sortMode; }
    public static void setSortMode(int mode) { sortMode = mode; }

    public static int getCurrentPage() { return currentPage; }
    public static void setCurrentPage(int page) { currentPage = page; }
}
