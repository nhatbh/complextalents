package com.complextalents.gunmastery;

import com.complextalents.gunmastery.capability.IGunMasteryData;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.tacz.GunAttributeType;
import com.complextalents.tacz.GunType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

public class GunMasteryManager {

    private static final GunMasteryManager INSTANCE = new GunMasteryManager();

    public static GunMasteryManager getInstance() {
        return INSTANCE;
    }

    // Damage required to unlock each level (1-indexed, index 0 unused)
    // Level 1: 0, Level 2: 500, Level 3: 1,200, Level 4: 2,200, etc.
    private static final double[] REQUIRED_DAMAGE_PER_LEVEL = new double[] {
            0.0,
            0.0,       // Level 1
            500.0,     // Level 2
            1200.0,    // Level 3
            2200.0,    // Level 4 (Recruit cap)
            3500.0,    // Level 5 (Trooper start)
            5500.0,    // Level 6
            8000.0,    // Level 7
            11000.0,   // Level 8 (Trooper cap)
            15000.0,   // Level 9 (Sergeant start)
            20000.0,   // Level 10
            26000.0,   // Level 11
            33000.0,   // Level 12 (Sergeant cap)
            41000.0,   // Level 13 (Captain start)
            50000.0,   // Level 14
            60000.0,   // Level 15
            72000.0,   // Level 16 (Pistol Max Cap)
            85000.0,   // Level 17 (General start)
            100000.0,  // Level 18
            120000.0,  // Level 19
            150000.0   // Level 20 (General Max Cap)
    };

    /**
     * Get target level for upgrading from current level.
     * Pistol starting level is 1 (Recruit). Non-pistol archetypes start at level 5 (Trooper).
     */
    public int getNextLevel(GunType type, int currentLevel) {
        if (currentLevel <= 0) {
            return type == GunType.PISTOL ? 1 : 5;
        }
        return currentLevel + 1;
    }

    /**
     * Get damage required to purchase next level.
     * @param currentLevel Current level (0 to 19).
     * @return Damage required to unlock next level. Level 0 requires 0.0 damage.
     */
    public double getDamageRequiredForNextLevel(GunType type, int currentLevel) {
        if (currentLevel <= 0) return 0.0;
        int nextLevel = currentLevel + 1;
        if (nextLevel < 1 || nextLevel >= REQUIRED_DAMAGE_PER_LEVEL.length) {
            return Double.MAX_VALUE;
        }
        return REQUIRED_DAMAGE_PER_LEVEL[nextLevel];
    }

    public double getDamageRequiredForNextLevel(int currentLevel) {
        return getDamageRequiredForNextLevel(GunType.PISTOL, currentLevel);
    }

    /**
     * Get player level required to unlock target mastery level.
     */
    public int getRequiredPlayerLevelForTier(int targetLevel) {
        if (targetLevel <= 4) return 1;    // Recruit
        if (targetLevel <= 8) return 10;   // Trooper
        if (targetLevel <= 12) return 20;  // Sergeant
        if (targetLevel <= 16) return 35;  // Captain
        return 50;                         // General
    }

    /**
     * Get maximum level cap for a gun archetype.
     * All active gun archetypes cap at 20 (General).
     */
    public int getMaxLevel(GunType type) {
        if (type == GunType.RPG || type == GunType.GLOBAL) return 0;
        return 20;
    }

    /**
     * Check if a non-pistol archetype can be unlocked.
     * Unlocking requires Pistol level >= 5 (Trooper L5).
     */
    public boolean canUnlockArchetype(GunType type, IGunMasteryData data, int originLevel) {
        if (type == GunType.PISTOL) return true;
        if (type == GunType.RPG || type == GunType.GLOBAL) return false;
        if (data == null) return false;
        if (data.getMasteryLevel(GunType.PISTOL) < 5) return false;
        if (data.getMasteryLevel(type) >= 5) return true; // Already unlocked

        int k = 0;
        for (Map.Entry<GunType, Integer> entry : data.getAllMasteryLevels().entrySet()) {
            GunType otherType = entry.getKey();
            if (otherType != GunType.PISTOL && otherType != GunType.RPG && otherType != GunType.GLOBAL && otherType != type) {
                if (entry.getValue() != null && entry.getValue() >= 5) {
                    k++;
                }
            }
        }

        int maxSlots = originLevel < 3 ? 1 : (originLevel < 5 ? 2 : 3);
        return k < maxSlots;
    }

    public boolean canUnlockArchetype(GunType type, IGunMasteryData data) {
        return canUnlockArchetype(type, data, 1);
    }

    /**
     * Calculate base SP cost for upgrading to a target level.
     */
    public int getBaseSPCost(int targetLevel) {
        if (targetLevel <= 4) return 1;    // Recruit: 1 SP
        if (targetLevel <= 8) return 2;    // Trooper: 2 SP
        if (targetLevel <= 12) return 3;   // Sergeant: 3 SP
        if (targetLevel <= 16) return 4;   // Captain: 4 SP
        return 5;                          // General: 5 SP
    }

    /**
     * Get SP cost for upgrading a specific gun archetype to its next level, accounting for
     * origin multiplier without ramping cost penalties per additional archetype.
     */
    public int getSPCostForNextLevel(GunType type, int currentLevel, Map<GunType, Integer> masteryLevels, ResourceLocation originId) {
        double originMult = ClassCostMatrix.getGunMasteryCostMultiplier(originId);
        if (originMult < 0) return -1; // Disallowed for this origin

        int nextLevel = getNextLevel(type, currentLevel);
        int baseCost = getBaseSPCost(nextLevel);

        return (int) Math.round(baseCost * originMult);
    }

    public int getSPCostForNextLevel(GunType type, int currentLevel, IGunMasteryData data, ResourceLocation originId) {
        Map<GunType, Integer> map = data != null ? data.getAllMasteryLevels() : null;
        return getSPCostForNextLevel(type, currentLevel, map, originId);
    }

    /**
     * Calculate stat bonus for a given archetype, attribute, and mastery level.
     * Returns the exact value boost (e.g. +0.80 for +80% Gun Damage).
     */
    public double getStatBonus(GunType type, GunAttributeType attribute, int level) {
        if (type == null || attribute == null || level <= 0) return 0.0;
        if (type == GunType.RPG || type == GunType.GLOBAL) return 0.0;

        int maxLvl = getMaxLevel(type);
        int clampedLevel = Math.min(level, maxLvl);

        return switch (type) {
            case PISTOL -> {
                if (clampedLevel < 1) yield 0.0;
                double pct = clampedLevel / 20.0;
                if (attribute == GunAttributeType.GUN_DAMAGE) yield 0.50 * pct;       // +50% Max (at L20)
                if (attribute == GunAttributeType.RELOAD_SPEED) yield 1.00 * pct;     // +100% Max (at L20)
                yield 0.0;
            }
            case SNIPER -> {
                if (clampedLevel < 5) yield 0.0;
                double pct = (clampedLevel - 4) / 16.0;
                if (attribute == GunAttributeType.HEADSHOT_MULTIPLIER) yield 1.50 * pct; // +150% Max
                if (attribute == GunAttributeType.PIERCE_MULTIPLIER) yield 0.80 * pct;   // +80% Max (+4 Pierce)
                yield 0.0;
            }
            case RIFLE -> {
                if (clampedLevel < 5) yield 0.0;
                double pct = (clampedLevel - 4) / 16.0;
                if (attribute == GunAttributeType.GUN_DAMAGE) yield 0.80 * pct;       // +80% Max
                if (attribute == GunAttributeType.FORTITUDE) yield 1.00 * pct;        // +100% Max (50% Heart Rate Cut)
                yield 0.0;
            }
            case SHOTGUN -> {
                if (clampedLevel < 5) yield 0.0;
                double pct = (clampedLevel - 4) / 16.0;
                if (attribute == GunAttributeType.HIP_FIRE_DAMAGE) yield 1.00 * pct;  // +100% Max
                if (attribute == GunAttributeType.AMMO_SAVE_CHANCE) yield 0.30 * pct; // +30% Max
                yield 0.0;
            }
            case SMG -> {
                if (clampedLevel < 5) yield 0.0;
                double pct = (clampedLevel - 4) / 16.0;
                if (attribute == GunAttributeType.GUN_DAMAGE) yield 0.50 * pct;       // +50% Max
                if (attribute == GunAttributeType.RPM_MULTIPLIER) yield 0.50 * pct;   // +50% Max Fire Rate
                yield 0.0;
            }
            case MG -> {
                if (clampedLevel < 5) yield 0.0;
                double pct = (clampedLevel - 4) / 16.0;
                if (attribute == GunAttributeType.GUN_DAMAGE) yield 0.60 * pct;       // +60% Max
                if (attribute == GunAttributeType.MAGAZINE_CAPACITY) yield 1.00 * pct;// +100% Max Mag
                yield 0.0;
            }
            default -> 0.0;
        };
    }

    public String getRankName(int level) {
        if (level <= 0) return "Locked";
        if (level <= 4) return "Recruit";
        if (level <= 8) return "Trooper";
        if (level <= 12) return "Sergeant";
        if (level <= 16) return "Captain";
        return "General";
    }

    public String getRankColor(int level) {
        if (level <= 0) return "\u00A78"; // Dark Gray
        if (level <= 4) return "\u00A7f"; // White
        if (level <= 8) return "\u00A7a"; // Green
        if (level <= 12) return "\u00A79"; // Blue
        if (level <= 16) return "\u00A75"; // Purple
        return "\u00A76";                 // Gold
    }

    public int getRankAccentColor(int level) {
        if (level <= 0) return 0xFF4B5563;
        if (level <= 4) return 0xFF3B82F6; // Blue
        if (level <= 8) return 0xFF10B981; // Green
        if (level <= 12) return 0xFF6366F1; // Indigo
        if (level <= 16) return 0xFFA855F7; // Purple
        return 0xFFF59E0B;                 // Amber Gold
    }
}
