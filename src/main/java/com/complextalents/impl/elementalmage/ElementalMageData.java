package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementType;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.elementalmage.ElementalMageSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Utility class for managing Elemental Mage data & attributes.
 * Acts as a helper wrapper around the IPlayerElementalMageData capability.
 */
public class ElementalMageData {

    public static int getEchoCount(Player player) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(IPlayerElementalMageData::getEchoCount)
                .orElse(0);
    }

    public static boolean addEcho(Player player, ElementalReaction reaction, ElementType element) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(cap -> cap.addEcho(reaction, element))
                .orElse(false);
    }

    public static void clearEchoes(Player player) {
        player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(IPlayerElementalMageData::clearEchoes);
    }

    public static float getEffectiveHarmonyMultiplier(Player player) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(IPlayerElementalMageData::getEffectiveHarmonyMultiplier)
                .orElse(1.0f);
    }

    /**
     * Sync stats and Prismatic Echoes to client.
     */
    public static void syncToClient(ServerPlayer player) {
        player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
            PacketHandler.sendTo(new ElementalMageSyncPacket(
                    cap.getEchoCount(),
                    cap.getLastReaction(),
                    cap.getApexElement(),
                    cap.getLockedHarmonyMultiplier(),
                    cap.getConvergenceCritChance(),
                    cap.getConvergenceCritDamage()
            ), player);
        });
    }

    /**
     * Map ElementType to external attribute ResourceLocation (Iron's Spellbooks / Travel Optics).
     */
    public static ResourceLocation getElementalAttributeId(ElementType element) {
        if (element == null) return null;
        return switch (element) {
            case FIRE -> ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire_spell_power");
            case ICE -> ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_spell_power");
            case LIGHTNING -> ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning_spell_power");
            case NATURE -> ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "nature_spell_power");
            case ENDER -> ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ender_spell_power");
            case AQUA -> ResourceLocation.fromNamespaceAndPath("traveloptics", "aqua_spell_power");
            default -> null;
        };
    }
}
