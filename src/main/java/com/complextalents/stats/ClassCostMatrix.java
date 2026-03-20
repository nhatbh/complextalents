package com.complextalents.stats;

import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores the cost matrix for stat ranks and mastery costs across different classes.
 */
public class ClassCostMatrix {

    private static final Map<ResourceLocation, Map<StatType, Integer>> MATRIX = new HashMap<>();
    private static final Map<ResourceLocation, Double> SPELL_MASTERY_COST_MULTIPLIERS = new HashMap<>();
    private static final Map<ResourceLocation, Double> WEAPON_MASTERY_COST_MULTIPLIERS = new HashMap<>();


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
     * Get the spell mastery cost multiplier for an origin.
     * This allows origins to define spell affinity by multiplying spell mastery costs.
     *
     * @param originId The origin ID
     * @return The cost multiplier (e.g., 0.5 = 50% cost, 1.5 = 150% cost). Default is 1.0.
     */
    public static double getSpellMasteryCostMultiplier(ResourceLocation originId) {
        return SPELL_MASTERY_COST_MULTIPLIERS.getOrDefault(originId, 1.0);
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
         * Set the spell mastery cost multiplier for this origin.
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
    }
}
