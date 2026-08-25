package com.complextalents.persistence;

import com.complextalents.TalentsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified SavedData for persisting player capability data across deaths and dimension changes.
 * Stores data keyed by player NAME (not UUID) to avoid UUID instability in offline/LAN mode,
 * where new ServerPlayer entities created during respawn can receive a different UUID than
 * the original player entity.
 */
public class PlayerPersistentData extends SavedData {

    private static final String DATA_NAME = TalentsMod.MODID + "_player_data";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PlayerPersistentData.class);

    // Storage maps keyed by player NAME
    private final Map<String, com.complextalents.origin.capability.PlayerOriginData> originData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.skill.capability.PlayerSkillData> skillData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.passive.capability.PassiveStackData> passiveData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.weaponmastery.capability.WeaponMasteryData> weaponMasteryData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.gunmastery.capability.GunMasteryData> gunMasteryData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.stats.capability.GeneralStatsData> generalStatsData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.spellmastery.capability.SpellMasteryData> spellMasteryData = new ConcurrentHashMap<>();
    private final Map<String, com.complextalents.impl.elementalmage.PlayerElementalMageData> elementalMageData = new ConcurrentHashMap<>();

    // Legacy / skill-specific storage
    private final Map<String, CompoundTag> legacyElementalMageData = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CompoundTag>> skillCustomData = new ConcurrentHashMap<>();

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
        for (String name : originTag.getAllKeys()) {
            var pod = new com.complextalents.origin.capability.PlayerOriginData();
            pod.deserializeNBT(originTag.getCompound(name));
            data.originData.put(name, pod);
        }

        CompoundTag skillTag = tag.getCompound("skillData");
        for (String name : skillTag.getAllKeys()) {
            var psd = new com.complextalents.skill.capability.PlayerSkillData();
            psd.deserializeNBT(skillTag.getCompound(name));
            data.skillData.put(name, psd);
        }

        CompoundTag passiveTag = tag.getCompound("passiveData");
        for (String name : passiveTag.getAllKeys()) {
            var psd = new com.complextalents.passive.capability.PassiveStackData();
            psd.deserializeNBT(passiveTag.getCompound(name));
            data.passiveData.put(name, psd);
        }

        // Load Elemental Mage objects
        CompoundTag elementalObjTag = tag.getCompound("elementalMageObjects");
        for (String name : elementalObjTag.getAllKeys()) {
            var emd = new com.complextalents.impl.elementalmage.PlayerElementalMageData();
            emd.deserializeNBT(elementalObjTag.getCompound(name));
            data.elementalMageData.put(name, emd);
        }

        // Load legacy elementalMageData (CompoundTag based)
        CompoundTag elementalMageTag = tag.getCompound("elementalMageData");
        for (String name : elementalMageTag.getAllKeys()) {
            data.legacyElementalMageData.put(name, elementalMageTag.getCompound(name));
        }

        CompoundTag weaponMasteryTag = tag.getCompound("weaponMasteryData");
        for (String name : weaponMasteryTag.getAllKeys()) {
            var wmd = new com.complextalents.weaponmastery.capability.WeaponMasteryData();
            wmd.deserializeNBT(weaponMasteryTag.getCompound(name));
            data.weaponMasteryData.put(name, wmd);
        }

        CompoundTag gunMasteryTag = tag.getCompound("gunMasteryData");
        for (String name : gunMasteryTag.getAllKeys()) {
            var gmd = new com.complextalents.gunmastery.capability.GunMasteryData();
            gmd.deserializeNBT(gunMasteryTag.getCompound(name));
            data.gunMasteryData.put(name, gmd);
        }

        CompoundTag generalStatsTag = tag.getCompound("generalStatsData");
        for (String name : generalStatsTag.getAllKeys()) {
            var gsd = new com.complextalents.stats.capability.GeneralStatsData();
            gsd.deserializeNBT(generalStatsTag.getCompound(name));
            data.generalStatsData.put(name, gsd);
        }

        CompoundTag spellMasteryTag = tag.getCompound("spellMasteryData");
        for (String name : spellMasteryTag.getAllKeys()) {
            var smd = new com.complextalents.spellmastery.capability.SpellMasteryData();
            smd.deserializeNBT(spellMasteryTag.getCompound(name));
            data.spellMasteryData.put(name, smd);
        }

        CompoundTag skillCustomTag = tag.getCompound("skillCustomData");
        for (String name : skillCustomTag.getAllKeys()) {
            CompoundTag playerSkillsTag = skillCustomTag.getCompound(name);
            Map<String, CompoundTag> skillMap = new ConcurrentHashMap<>();
            for (String skillId : playerSkillsTag.getAllKeys()) {
                skillMap.put(skillId, playerSkillsTag.getCompound(skillId));
            }
            data.skillCustomData.put(name, skillMap);
        }

        LOGGER.info("[PERSISTENCE] Loaded PlayerPersistentData with {} origins, {} skills, {} custom skill entries",
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
            originTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("originData", originTag);

        CompoundTag skillTag = new CompoundTag();
        for (var entry : skillData.entrySet()) {
            skillTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("skillData", skillTag);

        CompoundTag passiveTag = new CompoundTag();
        for (var entry : passiveData.entrySet()) {
            passiveTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("passiveData", passiveTag);

        CompoundTag elementalObjTag = new CompoundTag();
        for (var entry : elementalMageData.entrySet()) {
            elementalObjTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("elementalMageObjects", elementalObjTag);

        CompoundTag elementalMageTag = new CompoundTag();
        for (var entry : legacyElementalMageData.entrySet()) {
            elementalMageTag.put(entry.getKey(), entry.getValue());
        }
        tag.put("elementalMageData", elementalMageTag);

        CompoundTag weaponMasteryTag = new CompoundTag();
        for (var entry : weaponMasteryData.entrySet()) {
            weaponMasteryTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("weaponMasteryData", weaponMasteryTag);

        CompoundTag gunMasteryTag = new CompoundTag();
        for (var entry : gunMasteryData.entrySet()) {
            gunMasteryTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("gunMasteryData", gunMasteryTag);

        CompoundTag generalStatsTag = new CompoundTag();
        for (var entry : generalStatsData.entrySet()) {
            generalStatsTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("generalStatsData", generalStatsTag);

        CompoundTag spellMasteryTag = new CompoundTag();
        for (var entry : spellMasteryData.entrySet()) {
            spellMasteryTag.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put("spellMasteryData", spellMasteryTag);

        CompoundTag skillCustomTag = new CompoundTag();
        for (var entry : skillCustomData.entrySet()) {
            CompoundTag playerSkillsTag = new CompoundTag();
            for (var skillEntry : entry.getValue().entrySet()) {
                playerSkillsTag.put(skillEntry.getKey(), skillEntry.getValue());
            }
            skillCustomTag.put(entry.getKey(), playerSkillsTag);
        }
        tag.put("skillCustomData", skillCustomTag);

        LOGGER.info("[PERSISTENCE] Saved PlayerPersistentData with {} origins, {} skills, {} custom skill entries",
            originData.size(), skillData.size(), skillCustomData.size());
        return tag;
    }

    // --- Accessor Methods (keyed by player name) ---

    public com.complextalents.origin.capability.PlayerOriginData getOriginData(String playerName) {
        return originData.computeIfAbsent(playerName, k -> new com.complextalents.origin.capability.PlayerOriginData());
    }

    public com.complextalents.skill.capability.PlayerSkillData getSkillData(String playerName) {
        return skillData.computeIfAbsent(playerName, k -> new com.complextalents.skill.capability.PlayerSkillData());
    }

    public com.complextalents.passive.capability.PassiveStackData getPassiveData(String playerName) {
        return passiveData.computeIfAbsent(playerName, k -> new com.complextalents.passive.capability.PassiveStackData());
    }

    public com.complextalents.weaponmastery.capability.WeaponMasteryData getWeaponMasteryData(String playerName) {
        return weaponMasteryData.computeIfAbsent(playerName, k -> new com.complextalents.weaponmastery.capability.WeaponMasteryData());
    }

    public com.complextalents.gunmastery.capability.GunMasteryData getGunMasteryData(String playerName) {
        return gunMasteryData.computeIfAbsent(playerName, k -> new com.complextalents.gunmastery.capability.GunMasteryData());
    }

    public com.complextalents.stats.capability.GeneralStatsData getGeneralStatsData(String playerName) {
        return generalStatsData.computeIfAbsent(playerName, k -> new com.complextalents.stats.capability.GeneralStatsData());
    }

    public com.complextalents.spellmastery.capability.SpellMasteryData getSpellMasteryData(String playerName) {
        return spellMasteryData.computeIfAbsent(playerName, k -> new com.complextalents.spellmastery.capability.SpellMasteryData());
    }

    public com.complextalents.impl.elementalmage.PlayerElementalMageData getElementalData(String playerName) {
        return elementalMageData.computeIfAbsent(playerName, k -> new com.complextalents.impl.elementalmage.PlayerElementalMageData());
    }

    // --- Legacy/Compatibility methods ---

    public void saveElementalMageData(String playerName, CompoundTag data) {
        legacyElementalMageData.put(playerName, data.copy());
        setDirty();
    }

    public CompoundTag getElementalMageData(String playerName) {
        return legacyElementalMageData.getOrDefault(playerName, new CompoundTag());
    }

    public void saveSkillCustomData(String playerName, String skillId, CompoundTag tag) {
        skillCustomData.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>()).put(skillId, tag.copy());
        setDirty();
    }

    public CompoundTag getSkillCustomData(String playerName, String skillId) {
        Map<String, CompoundTag> playerSkills = skillCustomData.get(playerName);
        return playerSkills != null ? playerSkills.get(skillId) : null;
    }

    public void removeAllPlayerData(String playerName) {
        originData.remove(playerName);
        skillData.remove(playerName);
        passiveData.remove(playerName);
        weaponMasteryData.remove(playerName);
        generalStatsData.remove(playerName);
        spellMasteryData.remove(playerName);
        elementalMageData.remove(playerName);
        legacyElementalMageData.remove(playerName);
        skillCustomData.remove(playerName);
        setDirty();
    }
}
