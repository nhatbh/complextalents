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

        if (target == null || target.level().isClientSide || target instanceof net.minecraft.world.entity.player.Player) return;
        if (source instanceof ServerPlayer player) {
            ElementalStackTracker.addTracking(player.getUUID(), target.getUUID());
        }
    }

    public static void onEntityDeath(UUID entityId) {
        ElementalStackTracker.removeEntityTracking(entityId);
    }

    public static void onPlayerLogout(UUID playerId) {
        ElementalStackTracker.removePlayerTracking(playerId);
    }
}
