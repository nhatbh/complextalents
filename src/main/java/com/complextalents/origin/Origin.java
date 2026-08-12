package com.complextalents.origin;

import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.passive.PassiveOwner;
import com.complextalents.passive.PassiveStackDef;
import com.complextalents.stats.ScaledStat;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Interface for origins.
 * <p>
 * An origin represents a player archetype that provides:
 * - A resource type (shared across origins that use it)
 * - Scaled stats that vary by origin level
 * - Event-driven behaviors (handled by separate event handler classes)
 * - Passive stacks (managed by shared passive system)
 */
public interface Origin extends PassiveOwner {

    /**
     * Unique identifier for this origin (e.g., "complextalents:cleric")
     */
    ResourceLocation getId();

    /**
     * Get the owner ID for passive stack registration.
     * Implemented as part of PassiveOwner interface.
     *
     * @return The origin ID
     */
    @Override
    default ResourceLocation getOwnerId() {
        return getId();
    }

    /**
     * Get the owner type for passive stack registration.
     * Implemented as part of PassiveOwner interface.
     *
     * @return "origin"
     */
    @Override
    default String getOwnerType() {
        return "origin";
    }

    /**
     * Display name for this origin.
     */
    Component getDisplayName();

    /**
     * Description of this origin's mechanics.
     */
    Component getDescription();

    /**
     * The resource type this origin uses.
     * Multiple origins can share the same resource type.
     *
     * @return The resource type, or null if this origin has no resource
     */
    ResourceType getResourceType();

    /**
     * Get the maximum resource value for a specific origin level.
     * Default implementation returns the resource type's fixed max.
     * Implementations can override to use scaling arrays.
     *
     * @param level The origin level
     * @return The maximum resource value
     */
    default double getMaxResource(int level) {
        ResourceType type = getResourceType();
        return type != null ? type.getMax() : 0;
    }

    /**
     * Get the maximum resource value for a specific origin level, taking into account the player's current state.
     *
     * @param level The origin level
     * @param player The player instance
     * @return The maximum resource value
     */
    default double getMaxResource(int level, net.minecraft.server.level.ServerPlayer player) {
        return getMaxResource(level);
    }

    /**
     * Maximum level this origin can be upgraded to.
     * Default is 1 (no leveling).
     */
    int getMaxLevel();

    /**
     * Get all scaled stats defined for this origin.
     *
     * @return Map of stat key to ScaledStat definition
     */
    default Map<String, ScaledStat> getScaledStats() {
        return Map.of();
    }

    /**
     * Default list of upgradable stats for origins.
     */
    java.util.List<StatType> DEFAULT_UPGRADABLE_STATS = java.util.List.of(
            StatType.FLAT_AD,
            StatType.PERCENT_AD,
            StatType.AP,
            StatType.ARMOR_PEN,
            StatType.LUCK_CRIT,
            StatType.MAX_HP,
            StatType.MAX_MANA,
            StatType.HEAL_AND_SHIELD,
            StatType.CDR
    );

    /**
     * Get the list of upgradable StatTypes for this origin shown on the Progression Screen.
     *
     * @return List of StatType
     */
    default java.util.List<StatType> getUpgradableStats() {
        return DEFAULT_UPGRADABLE_STATS;
    }

    /**
     * Get the base stats (initial General Stat ranks) provided by this origin.
     *
     * @return Map of StatType to base rank
     */
    default Map<StatType, Integer> getBaseStats() {
        return Map.of();
    }


    /**
     * Get a scaled stat value for a specific level.
     * If the level exceeds the defined values, the last value is used.
     *
     * @param statName The stat name (e.g., "pietyOnHit")
     * @param level    The origin level
     * @return The scaled stat value, or 0 if not found
     */
    double getScaledStat(String statName, int level);

    /**
     * Get passive stack definitions for this origin.
     * Returns map of stack name -> definition.
     *
     * @return Map of passive stack definitions (empty by default)
     */
    default Map<String, PassiveStackDef> getPassiveStacks() {
        return Map.of();
    }

    /**
     * Get a specific passive stack definition.
     *
     * @param stackName The stack type name
     * @return The stack definition, or null if not found
     */
    @Nullable
    default PassiveStackDef getPassiveStackDef(String stackName) {
        return null;
    }

    /**
     * Get the custom HUD renderer for this origin.
     * If null, the default HUD renderer is used.
     *
     * @return The custom renderer, or null for default HUD
     */
    @OnlyIn(Dist.CLIENT)
    @Nullable
    default OriginRenderer getRenderer() {
        return null;
    }

    /**
     * Record representing a skill to display on the origin selection UI.
     */
    record OriginSkillDisplay(String name, String description, boolean isActive, @Nullable ResourceLocation icon) {}

    /**
     * Get the list of skills (active and passive) to display in the UI.
     *
     * @return List of OriginSkillDisplay
     */
    default java.util.List<OriginSkillDisplay> getDisplaySkills() {
        return java.util.Collections.emptyList();
    }

    /**
     * Get the unique ID of the primary active skill for this origin.
     * Used for upgrading origin active skills.
     */
    @Nullable
    default ResourceLocation getActiveSkillId() {
        return null;
    }

    /**
     * Calculates the level armor bonus for a player with this origin at a given ComplexTalents player level.
     * Default formula: +1.2 Armor per level above 1.
     *
     * @param playerLevel The player's ComplexTalents level
     * @return The bonus armor value
     */
    default double getLevelArmorBonus(int playerLevel) {
        if (playerLevel <= 1) return 0.0;
        return Math.round((playerLevel - 1) * 1.2);
    }

    /**
     * Calculates the level armor toughness bonus for a player with this origin at a given ComplexTalents player level.
     * Default formula: +0.3 Toughness per level above 1 (capped at 30.0).
     *
     * @param playerLevel The player's ComplexTalents level
     * @return The bonus armor toughness value
     */
    default double getLevelToughnessBonus(int playerLevel) {
        if (playerLevel <= 1) return 0.0;
        return Math.min(30.0, (playerLevel - 1) * 0.3);
    }

    /**
     * Calculates the level max health bonus for a player with this origin at a given ComplexTalents player level.
     * Default formula: Math.pow(playerLevel - 1, 1.15) * 0.2696
     *
     * @param playerLevel The player's ComplexTalents level
     * @return The bonus max health value
     */
    default double getLevelHealthBonus(int playerLevel) {
        if (playerLevel <= 1) return 0.0;
        return Math.round(Math.pow(playerLevel - 1, 1.15) * 0.2696);
    }

    /**
     * Get the list of custom firearm confusion messages for this origin.
     *
     * @return List of reaction message strings
     */
    default java.util.List<String> getGunConfusionMessages() {
        return java.util.Collections.emptyList();
    }

    /**
     * Get a random firearm confusion message for this origin.
     *
     * @param rand RandomSource generator
     * @return Firearm confusion message
     */
    default String getRandomGunConfusionMessage(net.minecraft.util.RandomSource rand) {
        java.util.List<String> msgs = getGunConfusionMessages();
        if (msgs == null || msgs.isEmpty()) {
            return getDefaultGunConfusionMessage(rand);
        }
        return msgs.get(rand.nextInt(msgs.size()));
    }

    /**
     * Default firearm confusion messages for unassigned/origin-less players.
     */
    static String getDefaultGunConfusionMessage(net.minecraft.util.RandomSource rand) {
        return "origin.complextalents.default.gun_msg." + (rand.nextInt(4) + 1);
    }
}
