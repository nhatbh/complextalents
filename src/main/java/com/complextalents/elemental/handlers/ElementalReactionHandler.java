package com.complextalents.elemental.handlers;

import com.complextalents.TalentsMod;
import com.complextalents.config.ElementalReactionConfig;
import com.complextalents.elemental.ElementStack;
import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementalStackTracker;
import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.api.IReactionStrategy;
import com.complextalents.elemental.events.ElementStackAppliedEvent;
import com.complextalents.elemental.events.ElementalReactionTriggeredEvent;
import com.complextalents.elemental.events.ElementalStackRemovedEvent;
import com.complextalents.elemental.registry.ReactionRegistry;
import com.complextalents.impl.elementalmage.origin.ElementalMageOrigin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


import java.util.Map;
import java.util.UUID;

/**
 * Handles elemental reaction triggering when ElementStackAppliedEvent is fired.
 * This is the second stage in the reaction chain.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalReactionHandler {

    private static final double REACTION_COST = 25.0;


    /**
     * Listens for element stack application and checks for possible reactions.
     * Triggers reactions if conditions are met.
     */
    @SubscribeEvent
    public static void onStackApplied(ElementStackAppliedEvent event) {
        if (!ElementalReactionConfig.enableElementalSystem.get()) return;

        LivingEntity target = event.getTarget();
        LivingEntity source = event.getSource();
        ElementType newElement = event.getElement();

        // Server-side only
        if (target.level().isClientSide) return;

        // Only trigger reactions if source is a player
        if (!(source instanceof ServerPlayer player)) {
            return;
        }

        UUID targetId = target.getUUID();
        Map<ElementType, ElementStack> elements = ElementalStackTracker.getEntityStacks(targetId);

        TalentsMod.LOGGER.info("REACTION_CHECK_START: Checking reactions for {} (UUID: {}). Applied element: {}. Existing stacks: {}",
            target.getName().getString(), targetId, newElement, elements != null ? elements.keySet() : "null");

        if (elements == null || elements.isEmpty()) {
            return;
        }

        // Only Elemental Mages can trigger reactions
        if (!com.complextalents.impl.elementalmage.origin.ElementalMageOrigin.isElementalMage(player)) {
            return;
        }

        // Track this entity for the player
        ElementalStackTracker.addTracking(player.getUUID(), targetId);

        // Check for reactions with existing elements
        for (Map.Entry<ElementType, ElementStack> entry : elements.entrySet()) {
            ElementType existingElement = entry.getKey();

            // Skip if same element or can't react
            if (existingElement == newElement || !existingElement.canReactWith(newElement)) {
                continue;
            }

            ElementalReaction reaction = existingElement.getReactionWith(newElement);
            if (reaction == null) {
                continue;
            }

            // Get the strategy for stack consumption check
            IReactionStrategy strategy = ReactionRegistry.getInstance().getStrategy(reaction);
            if (strategy == null) {
                continue;
            }

            // --- REACTION COST CHECK ---
            final double cost = REACTION_COST;
            var originDataCap = player.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA);
            if (originDataCap.isPresent()) {
                var data = originDataCap.resolve().get();
                if (data.getResource() < cost) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7cInsufficient Resonance to trigger reaction!"));
                    continue; 
                }
                // Deduct cost
                data.modifyResource(-cost);
                data.sync();
            }
            // ---------------------------

            // Trigger the reaction

            boolean executed = ReactionRegistry.getInstance().executeReaction(
                target, reaction, newElement, existingElement, player, 1.0f
            );

            TalentsMod.LOGGER.info("REACTION_EXECUTED: {} reaction on {} (UUID: {}). Reaction executed: {}. Existing element: {}, New element: {}",
                reaction, target.getName().getString(), targetId, executed, existingElement, newElement);

            // If reaction was executed and it consumes stacks, remove the existing element
            if (executed) {
                if (strategy.consumesStacks()) {
                    TalentsMod.LOGGER.info("CONSUMING_STACK: Removing {} stack from {} (UUID: {}) after {} reaction. Stacks before removal: {}",
                        existingElement, target.getName().getString(), targetId, reaction, elements.keySet());

                    // Fire the stack removed event before removing
                    ElementalStackRemovedEvent removedEvent = new ElementalStackRemovedEvent(
                        target, existingElement, ElementalStackRemovedEvent.RemovalReason.REACTION_CONSUMED
                    );
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(removedEvent);

                    elements.remove(existingElement);
                    TalentsMod.LOGGER.info("STACK_CONSUMED: Removed {} stack. Remaining stacks: {}",
                        existingElement, elements.keySet());
                } else {
                    TalentsMod.LOGGER.info("STRATEGY_INFO: Reaction {} has strategy: {}, Consumes stacks: {}",
                        reaction, strategy.getClass().getSimpleName(), strategy.consumesStacks());
                }
            }

            // Only trigger one reaction per stack application
            break;
        }
    }

    /**
     * Clean up tracker when an entity dies.
     * Called by ElementalStackManager's death event handler.
     *
     * @param entityId The UUID of the entity that died
     */
    public static void onEntityDeath(UUID entityId) {
        ElementalStackTracker.removeEntityTracking(entityId);
    }


    /**
     * Clean up tracker when a player disconnects.
     * Called by ElementalStackManager's logout event handler.
     *
     * @param playerId The UUID of the player that disconnected
     */
    public static void onPlayerLogout(UUID playerId) {
        ElementalStackTracker.removePlayerTracking(playerId);
    }

    /**
     * Listen for reactions being triggered to award accumulation gains.
     * $Accumulation\ Gain = \max(1, Reaction\ Damage \times 0.02)$
     */
    @SubscribeEvent
    public static void onReactionTriggered(ElementalReactionTriggeredEvent event) {
        ServerPlayer player = event.getAttacker();
        if (player == null || !ElementalMageOrigin.isElementalMage(player)) return;


        ElementType element = event.getTriggeringElement();
        float damage = event.getDamage();
        float current = com.complextalents.impl.elementalmage.ElementalMageData.getStat(player, element);

        // --- "LADDER" PROGRESSION FORMULA ---
        // f(L) = L + 0.25 * (L - 1)^2
        // f(L) = L + 0.25 * (L^2 - 2L + 1) = 0.25L^2 + 0.5L + 0.25
        double startF = 0.25 * current * current + 0.5 * current + 0.25;
        double targetF = startF + (damage * 0.002);
        
        // Final Level: L = sqrt(4 * targetF) - 1.0
        float nextVal = (float) (Math.sqrt(4.0 * targetF) - 1.0);
        float gain = nextVal - current;
        // ------------------------------------
        
        // Update accumulated power
        com.complextalents.impl.elementalmage.ElementalMageData.setStat(player, element, nextVal);
        
        // Award 1 Resonance Echo (max 5)
        if (com.complextalents.passive.PassiveManager.getPassiveStacks(player, "resonance_echo") < 5) {
            com.complextalents.passive.PassiveManager.modifyPassiveStacks(player, "resonance_echo", 1);
        }

        // Chat notification
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(String.format(
            "\u00A7b[Elemental Mage] \u00A7fPower increased: \u00A7e+%.3f \u00A7f%s (Total: %.3f)", 
            gain, element.name(), current + gain
        )));

        TalentsMod.LOGGER.debug("Accumulated {} power for {} through {} reaction (Damage: {}). Total: {}", 
            gain, element, event.getReaction(), damage, current + gain);
    }
}


