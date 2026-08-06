package com.complextalents.elemental.handlers;

import com.complextalents.TalentsMod;
import com.complextalents.config.ElementalReactionConfig;
import com.complextalents.elemental.ElementalStackTracker;
import com.complextalents.elemental.events.ElementStackAppliedEvent;
import com.complextalents.elemental.events.ElementalDamageEvent;
import com.complextalents.impl.elementalmage.origin.ElementalMageOrigin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Handles elemental reaction tracking for players and entities.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalReactionHandler {

    /**
     * Listens to elemental damage events.
     */
    @SubscribeEvent
    public static void onElementalDamage(ElementalDamageEvent event) {
        if (!ElementalReactionConfig.enableElementalSystem.get()) return;
        if (!(event.getSource() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!ElementalMageOrigin.isElementalMage(player)) return;
    }

    /**
     * Listens for element stack application to check for reactions.
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
