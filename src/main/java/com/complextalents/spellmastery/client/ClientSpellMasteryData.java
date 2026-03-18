package com.complextalents.spellmastery.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side storage for player's spell mastery data.
 * Used for UI and local logic verification.
 */
public class ClientSpellMasteryData {
    private static final Map<ResourceLocation, Integer> learnedSpells = new HashMap<>(); // SpellID -> Max Level Learned
    private static final Map<ResourceLocation, Integer> purchasedMasteryLevels = new HashMap<>(); // SchoolID -> Tier
    private static int totalSPSpentOnMastery = 0;

    public static void updateData(CompoundTag nbt) {
        learnedSpells.clear();
        if (nbt.contains("LearnedSpellsMap")) {
            CompoundTag spellsNbt = nbt.getCompound("LearnedSpellsMap");
            for (String key : spellsNbt.getAllKeys()) {
                learnedSpells.put(ResourceLocation.parse(key), spellsNbt.getInt(key));
            }
        } else if (nbt.contains("LearnedSpells")) {
            // Migration
            ListTag legacySpells = nbt.getList("LearnedSpells", Tag.TAG_STRING);
            for (int i = 0; i < legacySpells.size(); i++) {
                learnedSpells.put(ResourceLocation.parse(legacySpells.getString(i)), 1);
            }
        }

        purchasedMasteryLevels.clear();
        if (nbt.contains("PurchasedMasteryMap")) {
            CompoundTag purchasedNbt = nbt.getCompound("PurchasedMasteryMap");
            for (String key : purchasedNbt.getAllKeys()) {
                purchasedMasteryLevels.put(ResourceLocation.parse(key), purchasedNbt.getInt(key));
            }
        }
        
        totalSPSpentOnMastery = nbt.getInt("TotalSPSpentOnMastery");
    }

    public static int getMasteryLevel(ResourceLocation schoolId) {
        int commonCount = 0;
        int uncommonCount = 0;
        int rareCount = 0;
        int epicCount = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : learnedSpells.entrySet()) {
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(entry.getKey());
            if (spell != null && spell.getSchoolType().getId().equals(schoolId)) {
                io.redspace.ironsspellbooks.api.spells.SpellRarity maxRarity = spell.getRarity(entry.getValue());
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.COMMON.getValue()) commonCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.UNCOMMON.getValue()) uncommonCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.RARE.getValue()) rareCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.EPIC.getValue()) epicCount++;
            }
        }

        int calculated = 0;
        if (commonCount >= 3) {
            if (uncommonCount >= 3) {
                if (rareCount >= 2) {
                    if (epicCount >= 2) {
                        calculated = 4; // Legendary unlocked
                    } else {
                        calculated = 3; // Epic unlocked
                    }
                } else {
                    calculated = 2; // Rare unlocked
                }
            } else {
                calculated = 1; // Uncommon unlocked
            }
        }
        
        return Math.max(calculated, purchasedMasteryLevels.getOrDefault(schoolId, 0));
    }

    public static boolean isSpellLearned(ResourceLocation spellId, int level) {
        return learnedSpells.getOrDefault(spellId, 0) >= level;
    }

    public static Set<ResourceLocation> getLearnedSpells() {
        return new HashSet<>(learnedSpells.keySet());
    }

    public static Map<ResourceLocation, Integer> getAllMasteryLevels() {
        Map<ResourceLocation, Integer> allMastery = new HashMap<>();
        io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues().forEach(school -> {
            int level = getMasteryLevel(school.getId());
            if (level > 0) allMastery.put(school.getId(), level);
        });
        return allMastery;
    }

    public static int getLearnedCount(ResourceLocation schoolId, io.redspace.ironsspellbooks.api.spells.SpellRarity rarity) {
        int count = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : learnedSpells.entrySet()) {
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(entry.getKey());
            if (spell != null && spell.getSchoolType().getId().equals(schoolId)) {
                if (spell.getRarity(entry.getValue()).getValue() >= rarity.getValue()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int getPurchasedMastery(ResourceLocation schoolId) {
        return purchasedMasteryLevels.getOrDefault(schoolId, 0);
    }
    
    public static int getTotalSPSpentOnMastery() {
        return totalSPSpentOnMastery;
    }
}
