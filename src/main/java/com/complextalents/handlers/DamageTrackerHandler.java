package com.complextalents.handlers;

import com.complextalents.TalentsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live damage tracking system for players.
 * Listens to incoming damage events and displays raw damage vs actual damage received in chat.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class DamageTrackerHandler {

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Float> RAW_DAMAGE_MAP = new ConcurrentHashMap<>();

    public static boolean toggleTracking(UUID playerId) {
        if (ENABLED_PLAYERS.contains(playerId)) {
            ENABLED_PLAYERS.remove(playerId);
            RAW_DAMAGE_MAP.remove(playerId);
            return false;
        } else {
            ENABLED_PLAYERS.add(playerId);
            return true;
        }
    }

    public static boolean isTracking(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isTracking(player.getUUID())) {
                // Record raw unmitigated damage
                RAW_DAMAGE_MAP.put(player.getUUID(), event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!isTracking(player.getUUID())) return;

            Float rawDamageObj = RAW_DAMAGE_MAP.remove(player.getUUID());
            float rawDamage = rawDamageObj != null ? rawDamageObj : event.getAmount();
            float actualDamage = event.getAmount();

            float mitigated = rawDamage - actualDamage;
            if (mitigated < 0) mitigated = 0;

            double reductionPercent = rawDamage > 0 ? (mitigated / rawDamage) * 100.0 : 0.0;

            double armor = player.getArmorValue();
            double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

            String message = String.format(
                    "§c[Damage Log] §fIncoming: §c%.1f §7| §aReceived: §e%.1f §7| §bMitigated: §f%.1f §7(§e%.1f%% Reduction§7) §7[Armor: %.1f, Toughness: %.1f]",
                    rawDamage, actualDamage, mitigated, reductionPercent, armor, toughness
            );

            player.sendSystemMessage(Component.literal(message));
        }
    }
}
