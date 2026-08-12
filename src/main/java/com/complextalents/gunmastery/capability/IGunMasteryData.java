package com.complextalents.gunmastery.capability;

import com.complextalents.tacz.GunType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Map;

/**
 * Interface for Gun Mastery data capability.
 * Tracks accumulated damage and purchased mastery levels for each gun archetype.
 */
public interface IGunMasteryData extends INBTSerializable<CompoundTag> {

    /**
     * Gets the accumulated damage for a specific gun archetype.
     * @param type The gun archetype.
     * @return Total damage accumulated.
     */
    double getAccumulatedDamage(GunType type);

    /**
     * Adds damage to a specific gun archetype.
     * @param type The gun archetype.
     * @param amount The amount of damage dealt.
     */
    void addAccumulatedDamage(GunType type, double amount);

    /**
     * Gets the purchased mastery level for a specific gun archetype.
     * Range: 0 to 20.
     * @param type The gun archetype.
     * @return The currently purchased mastery level.
     */
    int getMasteryLevel(GunType type);

    /**
     * Sets the purchased mastery level for a specific gun archetype.
     * @param type The gun archetype.
     * @param level The new level (0 to 20).
     */
    void setMasteryLevel(GunType type, int level);

    /**
     * Gets all accumulated damage map.
     */
    Map<GunType, Double> getAllAccumulatedDamage();

    /**
     * Gets all mastery levels map.
     */
    Map<GunType, Integer> getAllMasteryLevels();

    /**
     * Copy data from another IGunMasteryData instance (e.g., on player respawn).
     */
    void copyFrom(IGunMasteryData other);

    /**
     * Resets all accumulated damage and mastery levels to 0.
     */
    void reset();

    /**
     * Sync data to client.
     */
    void sync();
}

