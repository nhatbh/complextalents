package com.complextalents.persistence;

import com.complextalents.TalentsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified SavedData for persisting player capability data across deaths.
 * Stores origin, skill, and passive stack data keyed by player UUID.
 */
public class PlayerPersistentData extends SavedData {

    private static final String DATA_NAME = TalentsMod.MODID + "_player_data";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PlayerPersistentData.class);

    // Storage maps keyed by player UUID
    private final Map<UUID, com.complextalents.origin.capability.PlayerOriginData> originData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.skill.capability.PlayerSkillData> skillData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.passive.capability.PassiveStackData> passiveData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.weaponmastery.capability.WeaponMasteryData> weaponMasteryData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.stats.capability.GeneralStatsData> generalStatsData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.spellmastery.capability.SpellMasteryData> spellMasteryData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.impl.darkmage.data.PlayerSoulData> soulData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.impl.elementalmage.PlayerElementalMageData> elementalMageData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.impl.highpriest.data.PlayerFaithData> faithData = new ConcurrentHashMap<>();

    // Map the origin-specific static data to this instance for persistence
    // (We will rework SoulData etc. to use these maps instead of their own static ones)
    // Map the origin-specific static data to this instance for persistence (Legacy)
    private final Map<UUID, CompoundTag> legacyDarkMageData = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> legacyElementalMageData = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> legacyFaithData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, CompoundTag>> skillCustomData = new ConcurrentHashMap<>();

    /**
     * Get the global PlayerPersistentData from the Overworld level.
     */
    public static PlayerPersistentData get(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(net.minecraft.world.level.Level.OVERWORLD).getDataStorage().computeIfAbsent(
                PlayerPersistentData::load,
                PlayerPersistentData::new,
                DATA_NAME
        );
    }

    /**
     * Load from NBT - called by SavedData system.
     */
    public static PlayerPersistentData load(CompoundTag tag) {
        PlayerPersistentData data = new PlayerPersistentData();

        CompoundTag originTag = tag.getCompound("originData");
        for (String uuidStr : originTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var pod = new com.complextalents.origin.capability.PlayerOriginData();
            pod.deserializeNBT(originTag.getCompound(uuidStr));
            data.originData.put(uuid, pod);
        }

        CompoundTag skillTag = tag.getCompound("skillData");
        for (String uuidStr : skillTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var psd = new com.complextalents.skill.capability.PlayerSkillData();
            psd.deserializeNBT(skillTag.getCompound(uuidStr));
            data.skillData.put(uuid, psd);
        }

        CompoundTag passiveTag = tag.getCompound("passiveData");
        for (String uuidStr : passiveTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var psd = new com.complextalents.passive.capability.PassiveStackData();
            psd.deserializeNBT(passiveTag.getCompound(uuidStr));
            data.passiveData.put(uuid, psd);
        }

        CompoundTag darkMageTag = tag.getCompound("darkMageData");
        for (String uuidStr : darkMageTag.getAllKeys()) {
            data.legacyDarkMageData.put(UUID.fromString(uuidStr), darkMageTag.getCompound(uuidStr));
        }

        CompoundTag soulTag = tag.getCompound("soulData");
        for (String uuidStr : soulTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var sd = new com.complextalents.impl.darkmage.data.PlayerSoulData();
            sd.deserializeNBT(soulTag.getCompound(uuidStr));
            // Migrate from legacy darkMageData if no soulData entry exists yet
            data.soulData.put(uuid, sd);
            LOGGER.info("[SOUL LOAD] Loaded {} souls for {} from soulData", sd.getSouls(), uuid);
        }
        // Migrate legacy darkMageData entries that don't yet have a soulData entry
        for (String uuidStr : darkMageTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            if (!data.soulData.containsKey(uuid)) {
                CompoundTag legacyTag = darkMageTag.getCompound(uuidStr);
                if (legacyTag.contains("souls")) {
                    var sd = new com.complextalents.impl.darkmage.data.PlayerSoulData();
                    sd.deserializeNBT(legacyTag);
                    data.soulData.put(uuid, sd);
                    LOGGER.info("[SOUL LOAD] Migrated {} souls for {} from legacy darkMageData", sd.getSouls(), uuid);
                }
            }
        }
        
        // Load Elemental Mage objects
        CompoundTag elementalObjTag = tag.getCompound("elementalMageObjects");
        for (String uuidStr : elementalObjTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var emd = new com.complextalents.impl.elementalmage.PlayerElementalMageData();
            emd.deserializeNBT(elementalObjTag.getCompound(uuidStr));
            data.elementalMageData.put(uuid, emd);
        }
        // Migrate legacy elementalMageData
        CompoundTag legacyElementalTag = tag.getCompound("elementalMageData");
        for (String uuidStr : legacyElementalTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            if (!data.elementalMageData.containsKey(uuid)) {
                var emd = new com.complextalents.impl.elementalmage.PlayerElementalMageData();
                emd.deserializeNBT(legacyElementalTag.getCompound(uuidStr));
                data.elementalMageData.put(uuid, emd);
                LOGGER.info("[ELEMENTAL LOAD] Migrated stats for {} from legacy data", uuid);
            }
        }

        // Load Faith objects
        CompoundTag faithObjTag = tag.getCompound("faithObjects");
        for (String uuidStr : faithObjTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var fd = new com.complextalents.impl.highpriest.data.PlayerFaithData();
            fd.deserializeNBT(faithObjTag.getCompound(uuidStr));
            data.faithData.put(uuid, fd);
        }
        // Migrate legacy faithData
        CompoundTag legacyFaithTag = tag.getCompound("faithData");
        for (String uuidStr : legacyFaithTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            if (!data.faithData.containsKey(uuid)) {
                CompoundTag legacyF = legacyFaithTag.getCompound(uuidStr);
                if (legacyF.contains("faith")) {
                    var fd = new com.complextalents.impl.highpriest.data.PlayerFaithData();
                    fd.deserializeNBT(legacyF);
                    data.faithData.put(uuid, fd);
                    LOGGER.info("[FAITH LOAD] Migrated {} faith for {} from legacy data", fd.getFaith(), uuid);
                }
            }
        }
        
        LOGGER.info("[SOUL LOAD] Finished loading soul data. Total entries: {}", data.soulData.size());

        // Load legacy elementalMageData (CompoundTag based)
        CompoundTag elementalMageTag = tag.getCompound("elementalMageData");
        for (String uuidStr : elementalMageTag.getAllKeys()) {
            data.legacyElementalMageData.put(UUID.fromString(uuidStr), elementalMageTag.getCompound(uuidStr));
        }

        // Load legacy faithData (CompoundTag based)
        CompoundTag faithTag = tag.getCompound("faithData");
        for (String uuidStr : faithTag.getAllKeys()) {
            data.legacyFaithData.put(UUID.fromString(uuidStr), faithTag.getCompound(uuidStr));
        }

        CompoundTag weaponMasteryTag = tag.getCompound("weaponMasteryData");
        for (String uuidStr : weaponMasteryTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var wmd = new com.complextalents.weaponmastery.capability.WeaponMasteryData();
            wmd.deserializeNBT(weaponMasteryTag.getCompound(uuidStr));
            data.weaponMasteryData.put(uuid, wmd);
        }

        CompoundTag generalStatsTag = tag.getCompound("generalStatsData");
        for (String uuidStr : generalStatsTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var gsd = new com.complextalents.stats.capability.GeneralStatsData();
            gsd.deserializeNBT(generalStatsTag.getCompound(uuidStr));
            data.generalStatsData.put(uuid, gsd);
        }

        CompoundTag spellMasteryTag = tag.getCompound("spellMasteryData");
        for (String uuidStr : spellMasteryTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            var smd = new com.complextalents.spellmastery.capability.SpellMasteryData();
            smd.deserializeNBT(spellMasteryTag.getCompound(uuidStr));
            data.spellMasteryData.put(uuid, smd);
        }

        CompoundTag skillCustomTag = tag.getCompound("skillCustomData");
        for (String uuidStr : skillCustomTag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            CompoundTag playerSkillsTag = skillCustomTag.getCompound(uuidStr);
            Map<String, CompoundTag> skillMap = new ConcurrentHashMap<>();
            for (String skillId : playerSkillsTag.getAllKeys()) {
                skillMap.put(skillId, playerSkillsTag.getCompound(skillId));
            }
            data.skillCustomData.put(uuid, skillMap);
        }

        TalentsMod.LOGGER.info("[PERSISTENCE] Loaded PlayerPersistentData with {} origins, {} skills, {} custom skill entries", 
            data.originData.size(), data.skillData.size(), data.skillCustomData.size());
        return data;
    }

    /**
     * Save to NBT - called by SavedData system.
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag originTag = new CompoundTag();
        for (var entry : originData.entrySet()) {
            originTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("originData", originTag);

        CompoundTag skillTag = new CompoundTag();
        for (var entry : skillData.entrySet()) {
            skillTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("skillData", skillTag);

        CompoundTag passiveTag = new CompoundTag();
        for (var entry : passiveData.entrySet()) {
            passiveTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("passiveData", passiveTag);

        CompoundTag soulTag = new CompoundTag();
        for (var entry : soulData.entrySet()) {
            soulTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("soulData", soulTag);

        CompoundTag elementalObjTag = new CompoundTag();
        for (var entry : elementalMageData.entrySet()) {
            elementalObjTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("elementalMageObjects", elementalObjTag);

        CompoundTag faithObjTag = new CompoundTag();
        for (var entry : faithData.entrySet()) {
            faithObjTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("faithObjects", faithObjTag);

        // Save legacy CompoundTag data
        CompoundTag darkMageTag = new CompoundTag();
        for (var entry : legacyDarkMageData.entrySet()) {
            darkMageTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("darkMageData", darkMageTag);

        CompoundTag elementalMageTag = new CompoundTag();
        for (var entry : legacyElementalMageData.entrySet()) {
            elementalMageTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("elementalMageData", elementalMageTag);

        CompoundTag faithTag = new CompoundTag();
        for (var entry : legacyFaithData.entrySet()) {
            faithTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("faithData", faithTag);

        CompoundTag weaponMasteryTag = new CompoundTag();
        for (var entry : weaponMasteryData.entrySet()) {
            weaponMasteryTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("weaponMasteryData", weaponMasteryTag);

        CompoundTag generalStatsTag = new CompoundTag();
        for (var entry : generalStatsData.entrySet()) {
            generalStatsTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("generalStatsData", generalStatsTag);

        CompoundTag spellMasteryTag = new CompoundTag();
        for (var entry : spellMasteryData.entrySet()) {
            spellMasteryTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("spellMasteryData", spellMasteryTag);

        CompoundTag skillCustomTag = new CompoundTag();
        for (var entry : skillCustomData.entrySet()) {
            CompoundTag playerSkillsTag = new CompoundTag();
            for (var skillEntry : entry.getValue().entrySet()) {
                playerSkillsTag.put(skillEntry.getKey(), skillEntry.getValue());
            }
            skillCustomTag.put(entry.getKey().toString(), playerSkillsTag);
        }
        tag.put("skillCustomData", skillCustomTag);

        TalentsMod.LOGGER.info("[PERSISTENCE] Saved PlayerPersistentData with {} origins, {} skills, {} custom skill entries", 
            originData.size(), skillData.size(), skillCustomData.size());
        return tag;
    }

    // --- Accessor Methods ---

    public com.complextalents.origin.capability.PlayerOriginData getOriginData(UUID playerId) {
        return originData.computeIfAbsent(playerId, k -> new com.complextalents.origin.capability.PlayerOriginData());
    }

    public com.complextalents.skill.capability.PlayerSkillData getSkillData(UUID playerId) {
        return skillData.computeIfAbsent(playerId, k -> new com.complextalents.skill.capability.PlayerSkillData());
    }

    public com.complextalents.passive.capability.PassiveStackData getPassiveData(UUID playerId) {
        return passiveData.computeIfAbsent(playerId, k -> new com.complextalents.passive.capability.PassiveStackData());
    }

    public com.complextalents.weaponmastery.capability.WeaponMasteryData getWeaponMasteryData(UUID playerId) {
        return weaponMasteryData.computeIfAbsent(playerId, k -> new com.complextalents.weaponmastery.capability.WeaponMasteryData());
    }

    public com.complextalents.stats.capability.GeneralStatsData getGeneralStatsData(UUID playerId) {
        return generalStatsData.computeIfAbsent(playerId, k -> new com.complextalents.stats.capability.GeneralStatsData());
    }

    public com.complextalents.spellmastery.capability.SpellMasteryData getSpellMasteryData(UUID playerId) {
        return spellMasteryData.computeIfAbsent(playerId, k -> new com.complextalents.spellmastery.capability.SpellMasteryData());
    }

    public com.complextalents.impl.darkmage.data.PlayerSoulData getSoulData(UUID playerId) {
        return soulData.computeIfAbsent(playerId, k -> new com.complextalents.impl.darkmage.data.PlayerSoulData());
    }

    public com.complextalents.impl.elementalmage.PlayerElementalMageData getElementalData(UUID playerId) {
        return elementalMageData.computeIfAbsent(playerId, k -> new com.complextalents.impl.elementalmage.PlayerElementalMageData());
    }

    public com.complextalents.impl.highpriest.data.PlayerFaithData getFaithDataObj(UUID playerId) {
        return faithData.computeIfAbsent(playerId, k -> new com.complextalents.impl.highpriest.data.PlayerFaithData());
    }

    // --- Legacy/Compatibility methods for transition ---

    public void saveDarkMageData(UUID playerId, CompoundTag data) {
        legacyDarkMageData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getDarkMageData(UUID playerId) {
        return legacyDarkMageData.getOrDefault(playerId, new CompoundTag());
    }

    public void saveElementalMageData(UUID playerId, CompoundTag data) {
        legacyElementalMageData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getElementalMageData(UUID playerId) {
        return legacyElementalMageData.getOrDefault(playerId, new CompoundTag());
    }

    public void saveFaithData(UUID playerId, CompoundTag data) {
        legacyFaithData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getFaithData(UUID playerId) {
        return legacyFaithData.getOrDefault(playerId, new CompoundTag());
    }

    public void saveSkillCustomData(UUID playerId, String skillId, CompoundTag tag) {
        skillCustomData.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(skillId, tag.copy());
        setDirty();
    }

    public CompoundTag getSkillCustomData(UUID playerId, String skillId) {
        Map<String, CompoundTag> playerSkills = skillCustomData.get(playerId);
        return playerSkills != null ? playerSkills.get(skillId) : null;
    }

    public void removeAllPlayerData(UUID playerId) {
        originData.remove(playerId);
        skillData.remove(playerId);
        passiveData.remove(playerId);
        weaponMasteryData.remove(playerId);
        generalStatsData.remove(playerId);
        spellMasteryData.remove(playerId);
        soulData.remove(playerId);
        elementalMageData.remove(playerId);
        faithData.remove(playerId);
        legacyDarkMageData.remove(playerId);
        legacyElementalMageData.remove(playerId);
        legacyFaithData.remove(playerId);
        skillCustomData.remove(playerId);
        setDirty();
    }
}
