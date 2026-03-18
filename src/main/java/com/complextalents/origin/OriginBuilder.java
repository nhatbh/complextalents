package com.complextalents.origin;

import com.complextalents.origin.client.OriginRenderer;
import com.complextalents.passive.PassiveStackDef;
import com.complextalents.passive.PassiveStackRegistry;
import com.complextalents.stats.ScaledStat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builder for creating origin definitions.
 * Provides fluent API for origin registration.
 * <p>
 * Example usage:
 * <pre>{@code
 * OriginBuilder.create("complextalents", "cleric")
 *     .displayName("Cleric")
 *     .description("Holy warrior gaining Piety through healing and combat")
 *     .resourceType(ResourceType.PIETY)
 *     .maxLevel(5)
 *     .scaledStat("pietyOnHit", new double[]{3.0, 4.0, 5.0, 6.0, 8.0})
 *     .scaledStat("pietyOnHeal", new double[]{8.0, 10.0, 12.0, 15.0, 20.0})
 *     .register();
 * }</pre>
 */
public class OriginBuilder {
    private ResourceLocation id;
    private Component displayName;
    private Component description;
    private ResourceType resourceType;
    private int maxLevel = 1;
    private final Map<String, ScaledStat> scaledStats = new HashMap<>();
    private final Map<String, PassiveStackDef> passiveStacks = new HashMap<>();
    private OriginRenderer renderer;
    private double[] scaledMaxResource = null;
    private BiFunction<Integer, ServerPlayer, Double> dynamicMaxResourceCalc = null;
    private final java.util.List<Origin.OriginSkillDisplay> displaySkills = new java.util.ArrayList<>();
    private ResourceLocation activeSkillId = null;
    private java.util.function.Function<net.minecraft.world.entity.player.Player, com.lowdragmc.lowdraglib.gui.widget.WidgetGroup> customUpgradeUI = null;

    /**
     * Create a new origin builder.
     *
     * @param modId     The mod ID (namespace)
     * @param originName The origin name (path)
     * @return A new OriginBuilder instance
     */
    public static OriginBuilder create(String modId, String originName) {
        return new OriginBuilder(ResourceLocation.fromNamespaceAndPath(modId, originName));
    }

    /**
     * Create a new origin builder with a ResourceLocation.
     *
     * @param id The origin ID
     * @return A new OriginBuilder instance
     */
    public static OriginBuilder create(ResourceLocation id) {
        return new OriginBuilder(id);
    }

    private OriginBuilder(ResourceLocation id) {
        this.id = id;
    }

    /**
     * Set the display name for this origin.
     */
    public OriginBuilder displayName(String name) {
        this.displayName = Component.literal(name);
        return this;
    }

    /**
     * Set the display name for this origin (using Component).
     */
    public OriginBuilder displayName(Component name) {
        this.displayName = name;
        return this;
    }

    /**
     * Set the description for this origin.
     */
    public OriginBuilder description(String description) {
        this.description = Component.literal(description);
        return this;
    }

    /**
     * Set the description for this origin (using Component).
     */
    public OriginBuilder description(Component description) {
        this.description = description;
        return this;
    }

    /**
     * Set the resource type this origin uses.
     * Multiple origins can share the same resource type.
     */
    public OriginBuilder resourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    /**
     * Set the maximum level for this origin.
     * Default is 1, meaning the origin cannot be leveled up.
     *
     * @param maxLevel The maximum level (must be >= 1)
     * @return this builder
     * @throws IllegalArgumentException if maxLevel < 1
     */
    public OriginBuilder maxLevel(int maxLevel) {
        if (maxLevel < 1) {
            throw new IllegalArgumentException("Max level must be at least 1, got: " + maxLevel);
        }
        this.maxLevel = maxLevel;
        return this;
    }

    /**
     * Add a scaled stat that varies by origin level.
     * Use the key as the display name.
     *
     * @param name   The stat name (used as key and display name)
     * @param values Array of values per level
     * @return this builder
     */
    public OriginBuilder scaledStat(String name, double[] values) {
        return scaledStat(name, name, values);
    }

    /**
     * Add a scaled stat with separate key and display name.
     *
     * @param key         The internal key for the stat
     * @param displayName The name to show in the UI
     * @param values      Array of values per level
     * @return this builder
     */
    public OriginBuilder scaledStat(String key, String displayName, double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Scaled stat values must have at least one element");
        }
        this.scaledStats.put(key, new ScaledStat(displayName, values.clone()));
        return this;
    }

    /**
     * Add a scaled stat with separate key and display name (Component).
     *
     * @param key         The internal key for the stat
     * @param displayName The component to show in the UI
     * @param values      Array of values per level
     * @return this builder
     */
    public OriginBuilder scaledStat(String key, Component displayName, double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Scaled stat values must have at least one element");
        }
        this.scaledStats.put(key, new ScaledStat(displayName, values.clone()));
        return this;
    }

    /**
     * Add a passive stack type to this origin.
     * <p>
     * Passive stacks are tracked per-player and can be used for mechanics like:
     * - Stacks that build up over time
     * - Stacks gained/lost on events
     * - Conditional effects based on stack count
     * </p>
     * <p>
     * The origin's event handlers are responsible for all stack logic
     * (generation, decay, triggers). This only defines the stack type.
     * </p>
     *
     * @param stackName The unique name for this stack type (e.g., "grace", "frenzy")
     * @param def       The stack definition with max stacks and display info
     * @return this builder
     */
    public OriginBuilder passiveStack(String stackName, PassiveStackDef def) {
        this.passiveStacks.put(stackName, def);
        return this;
    }

    /**
     * Set a custom HUD renderer for this origin.
     * <p>
     * The renderer controls how the origin's resource and passive stacks
     * are displayed on screen. If null, the default HUD is used.
     * </p>
     *
     * @param renderer The custom renderer, or null for default HUD
     * @return this builder
     */
    public OriginBuilder renderer(OriginRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    /**
     * Set the maximum resource value that scales with origin level.
     * Values are indexed by level: index 0 = level 1, index 1 = level 2, etc.
     * If the origin level exceeds the array length, the last value is used.
     * <p>
     * If not set, the resource type's fixed max is used for all levels.
     *
     * @param values Array of max resource values per level (must have at least one value)
     * @return this builder
     * @throws IllegalArgumentException if values is null or empty
     */
    public OriginBuilder scaledMaxResource(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Scaled max resource values must have at least one element");
        }
        this.scaledMaxResource = values.clone();
        return this;
    }

    /**
     * Provide a lambda to dynamically calculate the max resource based on both the player's origin level
     * and their current player state. This overrides `scaledMaxResource` if provided.
     *
     * @param calc A function that takes (Level, ServerPlayer) and returns the max resource value.
     * @return this builder
     */
    public OriginBuilder dynamicMaxResource(BiFunction<Integer, ServerPlayer, Double> calc) {
        this.dynamicMaxResourceCalc = calc;
        return this;
    }

    /**
     * Add a passive skill for display in the origin selection UI.
     *
     * @param name The name of the passive
     * @param description A short description of the passive
     * @return this builder
     */
    public OriginBuilder passiveSkill(String name, String description) {
        this.displaySkills.add(new Origin.OriginSkillDisplay(name, description, false, null));
        return this;
    }

    /**
     * Add an active skill for display in the origin selection UI.
     * Use a ResourceLocation for the icon, or null for default context.
     *
     * @param name The name of the active skill
     * @param description A short description of the skill
     * @param icon The icon to use (nullable)
     * @return this builder
     */
    public OriginBuilder activeSkill(String name, String description, @org.jetbrains.annotations.Nullable ResourceLocation icon) {
        this.displaySkills.add(new Origin.OriginSkillDisplay(name, description, true, icon));
        return this;
    }

    /**
     * Set the primary active skill ID for this origin.
     * Used for origin skill upgrading.
     *
     * @param skillId The skill ID
     * @return this builder
     */
    public OriginBuilder activeSkillId(ResourceLocation skillId) {
        this.activeSkillId = skillId;
        return this;
    }

    /**
     * Provide a factory for creating a custom LDLib WidgetGroup for the Origin Upgrade UI.
     *
     * @param factory The factory function
     * @return this builder
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public OriginBuilder customUpgradeUI(java.util.function.Function<net.minecraft.world.entity.player.Player, com.lowdragmc.lowdraglib.gui.widget.WidgetGroup> factory) {
        this.customUpgradeUI = factory;
        return this;
    }

    /**
     * Build the origin and return a BuiltOrigin instance.
     * Call OriginRegistry.register() with the result to register it.
     */
    public BuiltOrigin build() {
        if (displayName == null) {
            displayName = Component.literal(id.getPath());
        }
        if (description == null) {
            description = Component.literal("");
        }
        return new BuiltOrigin(this);
    }

    /**
     * Build and register the origin in one step.
     * Also registers passive stack definitions with the shared PassiveStackRegistry.
     *
     * @return The registered BuiltOrigin instance
     */
    public BuiltOrigin register() {
        BuiltOrigin origin = build();
        OriginRegistry.getInstance().register(origin);

        // Register passive stack definitions with shared registry
        for (Map.Entry<String, PassiveStackDef> entry : passiveStacks.entrySet()) {
            PassiveStackRegistry.register(origin, entry.getKey(), entry.getValue());
        }

        return origin;
    }

    // Package-private getters for BuiltOrigin
    ResourceLocation getId() {
        return id;
    }

    Component getDisplayName() {
        return displayName;
    }

    Component getDescription() {
        return description;
    }

    ResourceType getResourceType() {
        return resourceType;
    }

    int getMaxLevel() {
        return maxLevel;
    }

    Map<String, ScaledStat> getScaledStats() {
        return new HashMap<>(scaledStats);
    }

    Map<String, PassiveStackDef> getPassiveStacks() {
        return new HashMap<>(passiveStacks);
    }

    OriginRenderer getRenderer() {
        return renderer;
    }

    double[] getScaledMaxResource() {
        return scaledMaxResource;
    }

    BiFunction<Integer, ServerPlayer, Double> getDynamicMaxResourceCalc() {
        return dynamicMaxResourceCalc;
    }

    java.util.List<Origin.OriginSkillDisplay> getDisplaySkills() {
        return new java.util.ArrayList<>(displaySkills);
    }

    ResourceLocation getActiveSkillId() {
        return activeSkillId;
    }

    java.util.function.Function<net.minecraft.world.entity.player.Player, com.lowdragmc.lowdraglib.gui.widget.WidgetGroup> getCustomUpgradeUI() {
        return customUpgradeUI;
    }
}
