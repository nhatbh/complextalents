package com.complextalents.elemental.registry;

import com.complextalents.TalentsMod;
import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.api.IReactionStrategy;
import com.complextalents.elemental.api.ReactionContext;
import com.complextalents.elemental.events.ElementalReactionTriggeredEvent;
import com.complextalents.elemental.strategies.reactions.*;
import com.complextalents.impl.elementalmage.ElementalMageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for elemental reaction strategies.
 * Manages registration, lookup, execution, and hard-capped spell power scaling.
 */
public class ReactionRegistry {

    private static ReactionRegistry INSTANCE;

    private final Map<ElementalReaction, IReactionStrategy> strategies;
    private final Map<String, ElementalReaction> nameToReactionMap;
    private final List<IReactionStrategy> sortedStrategies;

    private final Object registryLock = new Object();
    private volatile boolean initialized = false;

    private ReactionRegistry() {
        this.strategies = new ConcurrentHashMap<>();
        this.nameToReactionMap = new ConcurrentHashMap<>();
        this.sortedStrategies = new ArrayList<>();
    }

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

    public void initialize() {
        synchronized (registryLock) {
            if (initialized) {
                TalentsMod.LOGGER.warn("ReactionRegistry already initialized");
                return;
            }

            TalentsMod.LOGGER.info("Initializing Elemental Reaction Registry");
            registerDefaultReactions();
            updateSortedStrategies();
            initialized = true;
            TalentsMod.LOGGER.info("Registered {} elemental reactions", strategies.size());
        }
    }

    /**
     * Executes a reaction using hard-capped Spell Power & Harmony Multiplier scaling.
     */
    public boolean executeReaction(LivingEntity target, ElementalReaction reaction,
                                  ElementType triggeringElement, ElementType existingElement,
                                  ServerPlayer attacker, float damageMultiplier) {

        IReactionStrategy strategy = getStrategy(reaction);
        if (strategy == null) {
            return false;
        }

        // --- HARD-CAPPED SPELL POWER SCALING ---
        float harmonyMult = ElementalMageData.getEffectiveHarmonyMultiplier(attacker);
        float trigSp = getElementalSpellPower(attacker, triggeringElement);
        float existSp = getElementalSpellPower(attacker, existingElement);
        float avgElemSp = (trigSp + existSp) / 2.0f;

        float reactionPower = harmonyMult * (1.0f + avgElemSp * 0.02f);
        float currentDamageMultiplier = damageMultiplier;

        // --- ELEMENTAL MAGE ORIGIN REACTION BONUS ---
        if (com.complextalents.impl.elementalmage.origin.ElementalMageOrigin.isElementalMage(attacker)) {
            double reactionBonus = com.complextalents.origin.OriginManager.getOriginStat(attacker, "reaction_damage_bonus");
            currentDamageMultiplier *= (1.0f + (float) reactionBonus);
        }

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

        ReactionContext context = ReactionContext.builder()
            .target(target)
            .attacker(attacker)
            .reaction(reaction)
            .triggeringElement(triggeringElement)
            .existingElement(existingElement)
            .damageMultiplier(currentDamageMultiplier)
            .elementalMastery(reactionPower)
            .level((ServerLevel) target.level())
            .build();

        if (!strategy.canTrigger(context)) {
            return false;
        }

        float finalDamage = strategy.calculateDamage(context);

        ElementalReactionTriggeredEvent reactionEvent = new ElementalReactionTriggeredEvent(
            target, attacker, reaction, triggeringElement, existingElement,
            finalDamage, reactionPower, damageMultiplier
        );
        MinecraftForge.EVENT_BUS.post(reactionEvent);

        if (reactionEvent.isCanceled()) {
            return false;
        }

        finalDamage = reactionEvent.getDamage();
        strategy.execute(context);

        if (isCrit) {
            attacker.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                String.format("\u00A76\u2736 Reaction Critical! \u00A7f%.1f damage", finalDamage)));
        }

        return true;
    }

    private float getElementalSpellPower(ServerPlayer player, ElementType element) {
        float genSp = 1.0f;
        try {
            Attribute genAttr = io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER.get();
            if (genAttr != null) {
                AttributeInstance inst = player.getAttribute(genAttr);
                if (inst != null) {
                    genSp = (float) inst.getValue();
                }
            }
        } catch (Throwable ignored) {}

        if (element == null) return genSp;

        float elemSp = 1.0f;
        ResourceLocation attrId = ElementalMageData.getElementalAttributeId(element);
        if (attrId != null) {
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrId);
            if (attr != null) {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    elemSp = (float) inst.getValue();
                }
            }
        }
        return genSp * elemSp;
    }

    public float calculateElementalMastery(ServerPlayer player) {
        return ElementalMageData.getEffectiveHarmonyMultiplier(player);
    }

    public void register(ElementalReaction reaction, IReactionStrategy strategy) {
        Objects.requireNonNull(reaction, "Reaction type cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");

        synchronized (registryLock) {
            if (strategies.containsKey(reaction)) {
                throw new IllegalArgumentException("Reaction " + reaction + " is already registered");
            }
            strategies.put(reaction, strategy);
            nameToReactionMap.put(reaction.name().toLowerCase(), reaction);
            updateSortedStrategies();
        }
    }

    public void registerOrReplace(ElementalReaction reaction, IReactionStrategy strategy) {
        Objects.requireNonNull(reaction, "Reaction type cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");

        synchronized (registryLock) {
            strategies.put(reaction, strategy);
            nameToReactionMap.put(reaction.name().toLowerCase(), reaction);
            updateSortedStrategies();
        }
    }

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

    @Nullable
    public IReactionStrategy getStrategy(ElementalReaction reaction) {
        return strategies.get(reaction);
    }

    @Nullable
    public ElementalReaction getReactionByName(String name) {
        return nameToReactionMap.get(name.toLowerCase());
    }

    public Set<ElementalReaction> getRegisteredReactions() {
        return Collections.unmodifiableSet(strategies.keySet());
    }

    public List<IReactionStrategy> getSortedStrategies() {
        return Collections.unmodifiableList(sortedStrategies);
    }

    public boolean isRegistered(ElementalReaction reaction) {
        return strategies.containsKey(reaction);
    }

    public void clear() {
        synchronized (registryLock) {
            strategies.clear();
            nameToReactionMap.clear();
            sortedStrategies.clear();
            initialized = false;
            TalentsMod.LOGGER.info("Cleared reaction registry");
        }
    }

    public void reload() {
        synchronized (registryLock) {
            clear();
            initialize();
            TalentsMod.LOGGER.info("Reloaded reaction registry");
        }
    }

    private void updateSortedStrategies() {
        sortedStrategies.clear();
        sortedStrategies.addAll(strategies.values());
        sortedStrategies.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    private void registerDefaultReactions() {
        register(ElementalReaction.MELT, new MeltReaction());
        register(ElementalReaction.VAPORIZE, new VaporizeReaction());
        register(ElementalReaction.OVERLOADED, new OverloadReaction());
        register(ElementalReaction.BURNING, new BurningReaction());
        register(ElementalReaction.VOIDFIRE, new VoidfireReaction());

        register(ElementalReaction.FREEZE, new FreezeReaction());
        register(ElementalReaction.SUPERCONDUCT, new SuperconductReaction());
        register(ElementalReaction.PERMAFROST, new PermafrostReaction());
        register(ElementalReaction.FRACTURE, new FractureReaction());

        register(ElementalReaction.ELECTRO_CHARGED, new ElectroChargedReaction());
        register(ElementalReaction.SPRING, new SpringReaction());

        register(ElementalReaction.BLOOM, new BloomReaction());
        register(ElementalReaction.FLUX, new FluxReaction());
        register(ElementalReaction.OVERGROWTH, new OvergrowthReaction());

        TalentsMod.LOGGER.info("Registered 14 default elemental reaction strategies");
    }
}