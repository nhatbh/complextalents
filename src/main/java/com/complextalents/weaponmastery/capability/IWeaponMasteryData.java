package com.complextalents.weaponmastery.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Map;

/**
 * Interface for Weapon Mastery data capability.
 * Tracks accumulated damage and purchased mastery levels for each weapon path.
 */
public interface IWeaponMasteryData extends INBTSerializable<CompoundTag> {

    /**
     * Enum defining the 6 weapon paths.
     */
    enum WeaponPath {
        BLADEMASTER,
        COLOSSUS,
        VANGUARD,
        REAPER,
        JUGGERNAUT,
        BRAWLER;

        public static WeaponPath fromString(String name) {
            for (WeaponPath path : values()) {
                if (path.name().equalsIgnoreCase(name)) {
                    return path;
                }
            }
            return null;
        }
    }

    /**
     * Gets the accumulated damage for a specific path.
     * @param path The weapon path.
     * @return Total damage accumulated.
     */
    double getAccumulatedDamage(WeaponPath path);

    /**
     * Adds damage to a specific path.
     * @param path The weapon path.
     * @param amount The amount of damage dealt.
     */
    void addAccumulatedDamage(WeaponPath path, double amount);

    /**
     * Gets the purchased mastery level for a specific path.
     * Starts at 0, max 25 (Rank 5, Level 5).
     * @param path The weapon path.
     * @return The currently purchased mastery level.
     */
    int getMasteryLevel(WeaponPath path);

    /**
     * Sets the purchased mastery level for a specific path.
     * @param path The weapon path.
     * @param level The new level (0-25).
     */
    void setMasteryLevel(WeaponPath path, int level);

    /**
     * Gets all purchased mastery levels.
     * @return A map of paths to mastery levels.
     */
    Map<WeaponPath, Integer> getAllMasteryLevels();

    /**
     * Gets all accumulated damage values.
     * @return A map of paths to accumulated damage.
     */
    Map<WeaponPath, Double> getAllAccumulatedDamage();

    /**
     * Sets all mastery levels (used for syncing).
     */
    void setAllMasteryLevels(Map<WeaponPath, Integer> levels);

    /**
     * Sets all accumulated damage (used for syncing).
     */
    void setAllAccumulatedDamage(Map<WeaponPath, Double> damageMap);

    /**
     * Synchronizes data to the client.
     */
    void sync();
}
