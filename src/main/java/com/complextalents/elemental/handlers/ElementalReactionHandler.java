package com.complextalents.elemental.handlers;

import com.complextalents.TalentsMod;
import com.complextalents.config.ElementalReactionConfig;
import com.complextalents.elemental.ElementStack;
import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementalStackTracker;
import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.api.IReactionStrategy;
import com.complextalents.elemental.effects.ElementalEffects;
import com.complextalents.elemental.events.ElementStackAppliedEvent;
import com.complextalents.elemental.events.ElementalDamageEvent;
import com.complextalents.elemental.events.ElementalStackRemovedEvent;
import com.complextalents.elemental.registry.ReactionRegistry;
import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import com.complextalents.impl.elementalmage.origin.ElementalMageOrigin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * Handles elemental reaction triggering, the Alternating Rule for Prismatic Echoes,
 * the 12s Refresh Loop, and the Apex Catalyst instant reaction mechanics.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalReactionHandler {

    private static final double REACTION_COST = 25.0;

    /**
     * Listens to all elemental damage events dealt by an Elemental Mage:
     * 1. Refresh Loop: Resets the 12-second decay timer for all active Prismatic Echoes.
     */
    @SubscribeEvent
    public static void onElementalDamage(ElementalDamageEvent event) {
        if (!ElementalReactionConfig.enableElementalSystem.get()) return;

        if (!(event.getSource() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!ElementalMageOrigin.isElementalMage(player)) return;

        ElementType element = event.getElement();
        if (element == null) return;

        long gameTime = player.level().getGameTime();

        player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
            // Refresh Loop: Reset decay timestamp on any elemental hit
            cap.setLastDamageTick(gameTime);
        });
    }

    /**
     * Listens for element stack application to check for reactions.
     * Checks Apex Catalyst (during Harmonic Convergence) first, then normal reactions.
     * Echoes are acquired upon successful reaction activation.
     */
    @SubscribeEvent
    public static void onStackApplied(ElementStackAppliedEvent event) {
        if (!ElementalReactionConfig.enableElementalSystem.get()) return;

        LivingEntity target = event.getTarget();
        LivingEntity source = event.getSource();
        ElementType newElement = event.getElement();

        if (target == null || target.level().isClientSide) return;
        if (!(source instanceof ServerPlayer player)) return;
        if (!ElementalMageOrigin.isElementalMage(player)) return;

        UUID targetId = target.getUUID();
        ElementalStackTracker.addTracking(player.getUUID(), targetId);

        // --- APEX CATALYST (Harmonic Convergence Instant Reaction Window) ---
        if (player.hasEffect(ElementalEffects.HARMONIC_CONVERGENCE.get())) {
            var capOpt = player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
            if (capOpt.isPresent()) {
                ElementType apexElement = capOpt.get().getApexElement();
                if (apexElement != null && newElement.canReactWith(apexElement)) {
                    ElementalReaction apexReaction = newElement.getReactionWith(apexElement);
                    if (apexReaction != null) {
                        // Check Resonance Cost
                        if (hasAndDeductResonance(player, REACTION_COST)) {
                            ReactionRegistry.getInstance().executeReaction(
                                    target, apexReaction, newElement, apexElement, player, 1.0f
                            );
                            TalentsMod.LOGGER.info("APEX_CATALYST: Instant reaction {} triggered between {} and stored Apex {}",
                                    apexReaction, newElement, apexElement);
                            return; // Apex reaction executed!
                        }
                    }
                }
            }
        }

        // --- NORMAL REACTION CHECK ---
        Map<ElementType, ElementStack> elements = ElementalStackTracker.getEntityStacks(targetId);
        if (elements == null || elements.isEmpty()) return;

        for (Map.Entry<ElementType, ElementStack> entry : elements.entrySet()) {
            ElementType existingElement = entry.getKey();

            if (existingElement == newElement || !existingElement.canReactWith(newElement)) {
                continue;
            }

            ElementalReaction reaction = existingElement.getReactionWith(newElement);
            if (reaction == null) continue;

            IReactionStrategy strategy = ReactionRegistry.getInstance().getStrategy(reaction);
            if (strategy == null) continue;

            // Check Resonance Cost
            if (!hasAndDeductResonance(player, REACTION_COST)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7cInsufficient Resonance to trigger reaction!"));
                continue;
            }

            // Trigger standard reaction
            boolean executed = ReactionRegistry.getInstance().executeReaction(
                    target, reaction, newElement, existingElement, player, 1.0f
            );

            if (executed) {
                // Echo acquired upon reaction activation!
                player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> cap.addEcho(newElement));

                if (strategy.consumesStacks()) {
                    ElementalStackRemovedEvent removedEvent = new ElementalStackRemovedEvent(
                            target, existingElement, ElementalStackRemovedEvent.RemovalReason.REACTION_CONSUMED
                    );
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(removedEvent);
                    elements.remove(existingElement);
                }
            }

            break; // Max 1 reaction per stack application
        }
    }

    private static boolean hasAndDeductResonance(ServerPlayer player, double cost) {
        var originDataCap = player.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA);
        if (originDataCap.isPresent()) {
            var data = originDataCap.resolve().get();
            if (data.getResource() >= cost) {
                data.modifyResource(-cost);
                data.sync();
                return true;
            }
        }
        return false;
    }

    public static void onEntityDeath(UUID entityId) {
        ElementalStackTracker.removeEntityTracking(entityId);
    }

    public static void onPlayerLogout(UUID playerId) {
        ElementalStackTracker.removePlayerTracking(playerId);
    }
}
