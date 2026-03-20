package com.complextalents.persistence;

import com.complextalents.TalentsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
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

    // Storage maps keyed by player UUID
    private final Map<UUID, com.complextalents.origin.capability.PlayerOriginData> originData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.skill.capability.PlayerSkillData> skillData = new ConcurrentHashMap<>();
    private final Map<UUID, com.complextalents.passive.capability.PassiveStackData> passiveData = new ConcurrentHashMap<>();

    // Map the origin-specific static data to this instance for persistence
    // (We will rework SoulData etc. to use these maps instead of their own static ones)
    private final Map<UUID, CompoundTag> darkMageData = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> elementalMageData = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> faithData = new ConcurrentHashMap<>();

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
            data.darkMageData.put(UUID.fromString(uuidStr), darkMageTag.getCompound(uuidStr));
        }

        CompoundTag elementalMageTag = tag.getCompound("elementalMageData");
        for (String uuidStr : elementalMageTag.getAllKeys()) {
            data.elementalMageData.put(UUID.fromString(uuidStr), elementalMageTag.getCompound(uuidStr));
        }

        CompoundTag faithTag = tag.getCompound("faithData");
        for (String uuidStr : faithTag.getAllKeys()) {
            data.faithData.put(UUID.fromString(uuidStr), faithTag.getCompound(uuidStr));
        }

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

        CompoundTag darkMageTag = new CompoundTag();
        for (var entry : darkMageData.entrySet()) {
            darkMageTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("darkMageData", darkMageTag);

        CompoundTag elementalMageTag = new CompoundTag();
        for (var entry : elementalMageData.entrySet()) {
            elementalMageTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("elementalMageData", elementalMageTag);

        CompoundTag faithTag = new CompoundTag();
        for (var entry : faithData.entrySet()) {
            faithTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("faithData", faithTag);

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

    // --- Legacy/Compatibility methods for transition ---

    public void saveDarkMageData(UUID playerId, CompoundTag data) {
        darkMageData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getDarkMageData(UUID playerId) {
        return darkMageData.get(playerId);
    }

    public void saveElementalMageData(UUID playerId, CompoundTag data) {
        elementalMageData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getElementalMageData(UUID playerId) {
        return elementalMageData.get(playerId);
    }

    public void saveFaithData(UUID playerId, CompoundTag data) {
        faithData.put(playerId, data.copy());
        setDirty();
    }

    public CompoundTag getFaithData(UUID playerId) {
        return faithData.get(playerId);
    }

    public void removeAllPlayerData(UUID playerId) {
        originData.remove(playerId);
        skillData.remove(playerId);
        passiveData.remove(playerId);
        darkMageData.remove(playerId);
        elementalMageData.remove(playerId);
        faithData.remove(playerId);
        setDirty();
    }
}
