package com.complextalents.elemental.registry;

import com.complextalents.TalentsMod;
import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.api.IReactionStrategy;
import com.complextalents.elemental.api.ReactionContext;
import com.complextalents.elemental.events.ElementalReactionTriggeredEvent;
import com.complextalents.elemental.strategies.reactions.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for elemental reaction strategies.
 * Manages registration, lookup, execution, and initialization of reaction implementations.
 * Implemented as a singleton for global access.
 * Thread-safe for concurrent access.
 */
public class ReactionRegistry {

    private static ReactionRegistry INSTANCE;

    private final Map<ElementalReaction, IReactionStrategy> strategies;
    private final Map<String, ElementalReaction> nameToReactionMap;
    private final List<IReactionStrategy> sortedStrategies;

    // Lock for thread safety during modifications
    private final Object registryLock = new Object();
    private volatile boolean initialized = false;

    /**
     * Private constructor for singleton pattern.
     * Reactions can be registered dynamically as needed.
     */
    private ReactionRegistry() {
        this.strategies = new ConcurrentHashMap<>();
        this.nameToReactionMap = new ConcurrentHashMap<>();
        this.sortedStrategies = new ArrayList<>();
    }

    /**
     * Gets the singleton instance of the ReactionRegistry.
     *
     * @return The ReactionRegistry instance
     */
    public static ReactionRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (ReactionRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ReactionRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Initializes the registry with default reaction strategies.
     * Should be called during mod initialization.
     */
    public void initialize() {
        synchronized (registryLock) {
            if (initialized) {
                TalentsMod.LOGGER.warn("ReactionRegistry already initialized");
                return;
            }

            TalentsMod.LOGGER.info("Initializing Elemental Reaction Registry");

            // Register all default reactions
            registerDefaultReactions();

            // Sort strategies by priority
            updateSortedStrategies();

            initialized = true;
            TalentsMod.LOGGER.info("Registered {} elemental reactions", strategies.size());
        }
    }

    /**
     * Executes a reaction using the registered strategy.
     *
     * @param target The target entity
     * @param reaction The reaction type
     * @param triggeringElement The element that triggered the reaction
     * @param existingElement The element already on the target
     * @param attacker The attacking player
     * @param damageMultiplier Damage multiplier added by skills
     * @return true if the reaction was executed, false otherwise
     */
    public boolean executeReaction(LivingEntity target, ElementalReaction reaction,
                                  ElementType triggeringElement, ElementType existingElement,
                                  ServerPlayer attacker, float damageMultiplier) {

        // Get the strategy for this reaction
        IReactionStrategy strategy = getStrategy(reaction);
        if (strategy == null) {
            return false;
        }

        final float mastery = calculateElementalMastery(attacker);
        float currentDamageMultiplier = damageMultiplier;

        // --- HARMONIC CONVERGENCE REACTION CRITS ---
        boolean isCrit = false;
        if (attacker.hasEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get())) {
            var cap = attacker.getCapability(com.complextalents.impl.elementalmage.ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
            if (cap.isPresent()) {
                double critChance = cap.get().getConvergenceCritChance();
                if (attacker.getRandom().nextDouble() < critChance) {
                    isCrit = true;
                    currentDamageMultiplier *= (1.0f + cap.get().getConvergenceCritDamage());
                }
            }
        }
        // -------------------------------------------

        // Build the reaction context
        ReactionContext context = ReactionContext.builder()
            .target(target)
            .attacker(attacker)
            .reaction(reaction)
            .triggeringElement(triggeringElement)
            .existingElement(existingElement)
            .damageMultiplier(currentDamageMultiplier)
            .elementalMastery(mastery)
            .level((ServerLevel) target.level())
            .build();

        // Check if the reaction can trigger
        if (!strategy.canTrigger(context)) {
            return false;
        }

        // Calculate final damage before execution
        float finalDamage = strategy.calculateDamage(context);

        // Fire the reaction triggered event
        ElementalReactionTriggeredEvent reactionEvent = new ElementalReactionTriggeredEvent(
            target, attacker, reaction, triggeringElement, existingElement,
            finalDamage, mastery, damageMultiplier
        );
        MinecraftForge.EVENT_BUS.post(reactionEvent);

        // Check if event was canceled
        if (reactionEvent.isCanceled()) {
            return false;
        }

        // Update damage from event (in case handler modified it)
        finalDamage = reactionEvent.getDamage();

        // Execute the reaction
        strategy.execute(context);

        // --- NOTIFY CRIT ---
        if (isCrit) {
            attacker.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                String.format("\u00A76\u2736 Reaction Critical! \u00A7f%.1f damage", finalDamage)));
        }
        // ------------------



        return true;
    }

    /**
     * Registers a reaction strategy.
     *
     * @param reaction The reaction type
     * @param strategy The strategy implementation
     * @throws IllegalArgumentException if reaction is already registered
     */
    public void register(ElementalReaction reaction, IReactionStrategy strategy) {
        Objects.requireNonNull(reaction, "Reaction type cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");

        synchronized (registryLock) {
            if (strategies.containsKey(reaction)) {
                throw new IllegalArgumentException(
                    "Reaction " + reaction + " is already registered");
            }

            strategies.put(reaction, strategy);
            nameToReactionMap.put(reaction.name().toLowerCase(), reaction);

            // Update sorted list
            updateSortedStrategies();

        }
    }

    /**
     * Registers a reaction strategy, replacing any existing registration.
     *
     * @param reaction The reaction type
     * @param strategy The strategy implementation
     */
    public void registerOrReplace(ElementalReaction reaction, IReactionStrategy strategy) {
        Objects.requireNonNull(reaction, "Reaction type cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");

        synchronized (registryLock) {
            strategies.put(reaction, strategy);
            nameToReactionMap.put(reaction.name().toLowerCase(), reaction);

            // Update sorted list
            updateSortedStrategies();

        }
    }

    /**
     * Unregisters a reaction strategy.
     *
     * @param reaction The reaction type to unregister
     * @return The unregistered strategy, or null if not found
     */
    @Nullable
    public IReactionStrategy unregister(ElementalReaction reaction) {
        synchronized (registryLock) {
            IReactionStrategy removed = strategies.remove(reaction);
            if (removed != null) {
                nameToReactionMap.remove(reaction.name().toLowerCase());
                updateSortedStrategies();
            }
            return removed;
        }
    }

    /**
     * Gets the strategy for a specific reaction type.
     *
     * @param reaction The reaction type
     * @return The strategy, or null if not registered
     */
    @Nullable
    public IReactionStrategy getStrategy(ElementalReaction reaction) {
        return strategies.get(reaction);
    }

    /**
     * Gets a reaction by name (case-insensitive).
     *
     * @param name The reaction name
     * @return The reaction type, or null if not found
     */
    @Nullable
    public ElementalReaction getReactionByName(String name) {
        return nameToReactionMap.get(name.toLowerCase());
    }

    /**
     * Gets all registered reaction types.
     *
     * @return Unmodifiable set of registered reactions
     */
    public Set<ElementalReaction> getRegisteredReactions() {
        return Collections.unmodifiableSet(strategies.keySet());
    }

    /**
     * Gets all strategies sorted by priority (highest first).
     *
     * @return Unmodifiable list of sorted strategies
     */
    public List<IReactionStrategy> getSortedStrategies() {
        return Collections.unmodifiableList(sortedStrategies);
    }

    /**
     * Checks if a reaction is registered.
     *
     * @param reaction The reaction type
     * @return true if registered
     */
    public boolean isRegistered(ElementalReaction reaction) {
        return strategies.containsKey(reaction);
    }

    /**
     * Clears all registered strategies.
     * Useful for testing or reload scenarios.
     */
    public void clear() {
        synchronized (registryLock) {
            strategies.clear();
            nameToReactionMap.clear();
            sortedStrategies.clear();
            initialized = false;
            TalentsMod.LOGGER.info("Cleared reaction registry");
        }
    }

    /**
     * Reloads the registry, re-registering all default reactions.
     */
    public void reload() {
        synchronized (registryLock) {
            clear();
            initialize();
            TalentsMod.LOGGER.info("Reloaded reaction registry");
        }
    }

    /**
     * Updates the sorted strategies list based on priority.
     */
    private void updateSortedStrategies() {
        sortedStrategies.clear();
        sortedStrategies.addAll(strategies.values());
        sortedStrategies.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    /**
     * Registers all default reaction strategies.
     */
    private void registerDefaultReactions() {
        // Register original five reaction implementations
        register(ElementalReaction.MELT, new MeltReaction());
        register(ElementalReaction.VAPORIZE, new VaporizeReaction());
        register(ElementalReaction.OVERLOADED, new OverloadReaction());
        register(ElementalReaction.BURNING, new BurningReaction());
        register(ElementalReaction.VOIDFIRE, new VoidfireReaction());

        // Register new Ice reactions
        register(ElementalReaction.FREEZE, new FreezeReaction());
        register(ElementalReaction.SUPERCONDUCT, new SuperconductReaction());
        register(ElementalReaction.PERMAFROST, new PermafrostReaction());
        register(ElementalReaction.FRACTURE, new FractureReaction());

        // Register Aqua reactions
        register(ElementalReaction.ELECTRO_CHARGED, new ElectroChargedReaction());
        register(ElementalReaction.SPRING, new SpringReaction());

        // Register Nature reactions
        register(ElementalReaction.BLOOM, new BloomReaction());

        // Register Lightning reactions
        register(ElementalReaction.FLUX, new FluxReaction());

        // Register Lightning + Nature reaction
        register(ElementalReaction.OVERGROWTH, new OvergrowthReaction());

        TalentsMod.LOGGER.info("Registered 14 default elemental reaction strategies");
    }

    /**
     * Calculates elemental mastery using the isolated Accumulated Power track.
     * $Effective\ Mastery = Raw\ Mastery \times Harmony\ Multiplier$
     *
     * @param player The player to calculate mastery for
     * @return The effective mastery
     */
    public float calculateElementalMastery(ServerPlayer player) {
        float rawMastery = calculateRawMastery(player);
        float harmonyMultiplier = calculateHarmonyMultiplier(player);
        return rawMastery * harmonyMultiplier;
    }

    /**
     * Calculates the Raw Mastery: $1 + \sum (Accumulated_i \times Weight_i)$
     * Weights: [0.40, 0.20, 0.15, 0.10, 0.10, 0.05] (sorted by power)
     */
    private float calculateRawMastery(ServerPlayer player) {
        List<Float> values = new ArrayList<>();
        for (ElementType type : ElementType.values()) {
            values.add(com.complextalents.impl.elementalmage.ElementalMageData.getStat(player, type));
        }
        
        // Ensure we handle at least some values
        if (values.isEmpty()) return 1.0f;
        
        // Sort descending
        values.sort(Collections.reverseOrder());
        
        float[] weights = {0.40f, 0.20f, 0.15f, 0.10f, 0.10f, 0.05f};
        float weightedSum = 0.0f;
        
        for (int i = 0; i < values.size() && i < weights.length; i++) {
            weightedSum += values.get(i) * weights[i];
        }
        
        return weightedSum;
    }

    /**
     * Calculates the Harmony Multiplier based on the ratio $R = E_{others} / E_{max}$.
     * Piecewise linear scaling:
     * - $R \approx 0 \to 0.2x$
     * - $R \approx 1 \to 1.0x$
     * - $R \ge 4 \to 1.3x$
     */
    private float calculateHarmonyMultiplier(ServerPlayer player) {
        float maxVal = 0.0f;
        float totalSum = 0.0f;
        
        for (ElementType type : ElementType.values()) {
            float val = com.complextalents.impl.elementalmage.ElementalMageData.getStat(player, type);
            if (val > maxVal) maxVal = val;
            totalSum += val;
        }

        float othersSum = totalSum - maxVal;
        float R = othersSum / maxVal;

        // --- NEW HARMONY CALIBRATION [0.5 - 1.1] ---
        // Formula: 0.5 + 0.12 * R (Cap R at 5.0)
        float clampedR = Math.min(5.0f, R);
        return 0.5f + (0.12f * clampedR);
    }




}