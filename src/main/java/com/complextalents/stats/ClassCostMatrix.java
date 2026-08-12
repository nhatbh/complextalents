package com.complextalents.stats;

import com.complextalents.elemental.ElementType;
import com.complextalents.spellmastery.SpellSchool;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores the cost matrix for stat ranks and mastery costs across different classes.
 */
public class ClassCostMatrix {

    private static final Map<ResourceLocation, Map<StatType, Integer>> MATRIX = new HashMap<>();
    private static final Map<ResourceLocation, Double> SPELL_MASTERY_COST_MULTIPLIERS = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, Double>> SCHOOL_SPELL_MASTERY_COST_MULTIPLIERS = new HashMap<>();
    private static final Map<ResourceLocation, Double> WEAPON_MASTERY_COST_MULTIPLIERS = new HashMap<>();
    private static final Map<ResourceLocation, Double> GUN_MASTERY_COST_MULTIPLIERS = new HashMap<>();


    static {
        // Costs are now registered dynamically by each origin module
    }

    public static CostBuilder defineCosts(String originPath) {
        return new CostBuilder(ResourceLocation.fromNamespaceAndPath("complextalents", originPath));
    }

    public static CostBuilder defineCosts(ResourceLocation originId) {
        return new CostBuilder(originId);
    }

    public static int getCost(ResourceLocation originId, StatType stat) {
        Map<StatType, Integer> classCosts = MATRIX.get(originId);
        if (classCosts == null) return 4; // Default to most expensive if unknown
        return classCosts.getOrDefault(stat, 4);
    }

    /**
     * Get the general spell mastery cost multiplier for an origin.
     * This allows origins to define spell affinity by multiplying spell mastery costs.
     *
     * @param originId The origin ID
     * @return The cost multiplier (e.g., 0.5 = 50% cost, 1.5 = 150% cost). Default is 1.0.
     */
    public static double getSpellMasteryCostMultiplier(ResourceLocation originId) {
        return SPELL_MASTERY_COST_MULTIPLIERS.getOrDefault(originId, 1.0);
    }

    /**
     * Get the spell mastery cost multiplier for a specific magic school for an origin using ResourceLocation.
     * If no specific school multiplier is set, it falls back to the general spell mastery cost multiplier for the origin.
     *
     * @param originId The origin ID
     * @param schoolId The school resource location (e.g., irons_spellbooks:holy, irons_spellbooks:fire)
     * @return The cost multiplier for this specific school.
     */
    public static double getSchoolSpellMasteryCostMultiplier(ResourceLocation originId, ResourceLocation schoolId) {
        if (schoolId != null && "eldritch".equalsIgnoreCase(schoolId.getPath())) {
            boolean isDarkMage = ResourceLocation.fromNamespaceAndPath("complextalents", "dark_mage").equals(originId);
            boolean isSpellblade = ResourceLocation.fromNamespaceAndPath("complextalents", "spellblade").equals(originId);
            if (!isDarkMage && !isSpellblade) {
                return -1.0;
            }
        }
        Map<ResourceLocation, Double> schoolMap = SCHOOL_SPELL_MASTERY_COST_MULTIPLIERS.get(originId);
        if (schoolMap != null && schoolId != null) {
            Double schoolMult = schoolMap.get(schoolId);
            if (schoolMult != null) {
                return schoolMult;
            }
        }
        return getSpellMasteryCostMultiplier(originId);
    }

    /**
     * Get the spell mastery cost multiplier for a specific magic school for an origin using SchoolType.
     */
    public static double getSchoolSpellMasteryCostMultiplier(ResourceLocation originId, SchoolType schoolType) {
        if (schoolType == null) return getSpellMasteryCostMultiplier(originId);
        return getSchoolSpellMasteryCostMultiplier(originId, schoolType.getId());
    }

    /**
     * Get the spell mastery cost multiplier for a specific magic school using SpellSchool enum.
     */
    public static double getSchoolSpellMasteryCostMultiplier(ResourceLocation originId, SpellSchool school) {
        if (school == null) return getSpellMasteryCostMultiplier(originId);
        return getSchoolSpellMasteryCostMultiplier(originId, school.getLocation());
    }

    /**
     * Get the spell mastery cost multiplier for a specific magic school using ElementType enum.
     */
    public static double getSchoolSpellMasteryCostMultiplier(ResourceLocation originId, ElementType element) {
        if (element == null) return getSpellMasteryCostMultiplier(originId);
        SpellSchool school = mapElementTypeToSpellSchool(element);
        return school != null ? getSchoolSpellMasteryCostMultiplier(originId, school) : getSpellMasteryCostMultiplier(originId);
    }

    private static SpellSchool mapElementTypeToSpellSchool(ElementType element) {
        if (element == null) return null;
        return switch (element) {
            case FIRE -> SpellSchool.FIRE;
            case ICE -> SpellSchool.ICE;
            case LIGHTNING -> SpellSchool.LIGHTNING;
            case NATURE -> SpellSchool.NATURE;
            case AQUA -> SpellSchool.AQUA;
            case HOLY -> SpellSchool.HOLY;
            case EVOCATION -> SpellSchool.EVOCATION;
            case ENDER -> SpellSchool.ENDER;
            case ELDRITCH -> SpellSchool.ELDRITCH;
            case BLOOD -> SpellSchool.BLOOD;
        };
    }

    /**
     * Get the weapon mastery cost multiplier for an origin.
     * This allows origins to define weapon affinity by multiplying weapon mastery costs.
     *
     * @param originId The origin ID
     * @return The cost multiplier (e.g., 0.5 = 50% cost, 1.5 = 150% cost). Default is 1.0.
     */
    public static double getWeaponMasteryCostMultiplier(ResourceLocation originId) {
        return WEAPON_MASTERY_COST_MULTIPLIERS.getOrDefault(originId, 1.0);
    }

    /**
     * Get the gun mastery cost multiplier for an origin.
     * Return negative value (< 0) if Gun Mastery is locked/disallowed for this origin.
     *
     * @param originId The origin ID
     * @return The cost multiplier (e.g., 1.0 = normal cost, < 0 = disallowed). Default is -1.0 (disallowed).
     */
    public static double getGunMasteryCostMultiplier(ResourceLocation originId) {
        return GUN_MASTERY_COST_MULTIPLIERS.getOrDefault(originId, -1.0);
    }

    public static class CostBuilder {
        private final ResourceLocation id;
        private final Map<StatType, Integer> costs = new HashMap<>();

        public CostBuilder(ResourceLocation id) {
            this.id = id;
            MATRIX.put(id, costs);
        }

        public CostBuilder cost(StatType stat, int cost) {
            costs.put(stat, cost);
            return this;
        }

        /**
         * Set the general spell mastery cost multiplier for this origin.
         * This defines spell affinity by adjusting the cost of learning spell masteries.
         *
         * @param multiplier The multiplier (e.g., 0.5 = 50% cost, 1.5 = 150% cost)
         * @return this builder for chaining
         */
        public CostBuilder spellMasteryCostMultiplier(double multiplier) {
            SPELL_MASTERY_COST_MULTIPLIERS.put(id, multiplier);
            return this;
        }

        /**
         * Set a specific magic school spell mastery cost multiplier for this origin using ResourceLocation.
         *
         * @param schoolId The school resource location (e.g. ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy"))
         * @param multiplier The multiplier (e.g. 0.5 = 50% cost for holy spells)
         * @return this builder for chaining
         */
        public CostBuilder schoolSpellMasteryCostMultiplier(ResourceLocation schoolId, double multiplier) {
            SCHOOL_SPELL_MASTERY_COST_MULTIPLIERS
                    .computeIfAbsent(id, k -> new HashMap<>())
                    .put(schoolId, multiplier);
            return this;
        }

        /**
         * Set a specific magic school spell mastery cost multiplier for this origin using SchoolType.
         */
        public CostBuilder schoolSpellMasteryCostMultiplier(SchoolType schoolType, double multiplier) {
            if (schoolType != null) {
                schoolSpellMasteryCostMultiplier(schoolType.getId(), multiplier);
            }
            return this;
        }

        /**
         * Set a specific magic school spell mastery cost multiplier for this origin using SpellSchool enum.
         */
        public CostBuilder schoolSpellMasteryCostMultiplier(SpellSchool school, double multiplier) {
            if (school != null) {
                schoolSpellMasteryCostMultiplier(school.getLocation(), multiplier);
            }
            return this;
        }

        /**
         * Set a specific magic school spell mastery cost multiplier for this origin using ElementType enum.
         */
        public CostBuilder schoolSpellMasteryCostMultiplier(ElementType element, double multiplier) {
            SpellSchool school = mapElementTypeToSpellSchool(element);
            if (school != null) {
                schoolSpellMasteryCostMultiplier(school.getLocation(), multiplier);
            }
            return this;
        }

        /**
         * Set a specific magic school spell mastery cost multiplier for this origin using string path.
         */
        public CostBuilder schoolSpellMasteryCostMultiplier(String schoolPath, double multiplier) {
            SpellSchool school = SpellSchool.fromString(schoolPath);
            if (school != null) {
                return schoolSpellMasteryCostMultiplier(school.getLocation(), multiplier);
            }
            ResourceLocation loc = schoolPath.contains(":")
                    ? ResourceLocation.parse(schoolPath)
                    : ResourceLocation.fromNamespaceAndPath("irons_spellbooks", schoolPath);
            return schoolSpellMasteryCostMultiplier(loc, multiplier);
        }

        /**
         * Set the weapon mastery cost multiplier for this origin.
         * This defines weapon affinity by adjusting the cost of learning weapon masteries.
         *
         * @param multiplier The multiplier (e.g., 0.5 = 50% cost, 1.5 = 150% cost)
         * @return this builder for chaining
         */
        public CostBuilder weaponMasteryCostMultiplier(double multiplier) {
            WEAPON_MASTERY_COST_MULTIPLIERS.put(id, multiplier);
            return this;
        }

        /**
         * Set the gun mastery cost multiplier for this origin.
         * Set to < 0 to disallow gun mastery progression for this origin.
         *
         * @param multiplier The cost multiplier (e.g., 1.0 = normal cost, < 0 = disallowed)
         * @return this builder for chaining
         */
        public CostBuilder gunMasteryCostMultiplier(double multiplier) {
            GUN_MASTERY_COST_MULTIPLIERS.put(id, multiplier);
            return this;
        }
    }
}

