package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementType;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.elementalmage.ElementalMageSyncPacket;
import com.complextalents.persistence.PlayerPersistentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing Elemental Mage stats.
 * Now acts as a wrapper around the IPlayerElementalMageData capability.
 */
public class ElementalMageData {

    @SuppressWarnings("null")
    private static final UUID ELEMENTAL_MODIFIER_UUID = UUID.fromString("f4702164-323b-4573-b6d8-c682705a6e84");
    

    public static float getStat(Player player, ElementType element) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(cap -> cap.getStat(element))
                .orElse(1.0f);
    }

    public static void setStat(Player player, ElementType element, float value) {
        player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
            cap.setStat(element, value);
        });
    }

    public static Map<ElementType, Float> getAllStats(Player player) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(IPlayerElementalMageData::getAllStats)
                .orElse(java.util.Collections.emptyMap());
    }

    /**
     * Sync stats to client.
     */
    public static void syncToClient(ServerPlayer player) {
        Map<ElementType, Float> stats = getAllStats(player);
        player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
            PacketHandler.sendTo(new ElementalMageSyncPacket(stats, cap.getConvergenceCritChance(), cap.getConvergenceCritDamage()), player);
        });
    }

    /**
     * Map ElementType to external attribute ResourceLocation (Iron's Spellbooks / Travel Optics).
     */
    public static ResourceLocation getElementalAttributeId(ElementType element) {
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

    /**
     * Activate Harmonic Convergence: Apply Accumulated Power as temporary attributes.
     * Formula: bonus = accumulated_value - 1.0
     */
    public static void activateConvergence(ServerPlayer player) {
        for (ElementType element : ElementType.values()) {
            float value = getStat(player, element);
            float bonus = Math.max(0, value - 1.0f);
            
            ResourceLocation attrId = getElementalAttributeId(element);
            if (attrId != null && bonus > 0) {
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrId);
                if (attr != null) {
                    AttributeInstance inst = player.getAttribute(attr);
                    if (inst != null) {
                        inst.removeModifier(ELEMENTAL_MODIFIER_UUID);
                        inst.addTransientModifier(new AttributeModifier(ELEMENTAL_MODIFIER_UUID, 
                            "Harmonic Convergence Bonus", bonus, AttributeModifier.Operation.ADDITION));
                    }
                }
            }
        }
        
        // Sync to client to update UI
        syncToClient(player);
    }

    /**
     * Clear Harmonic Convergence: Remove all temporary attributes.
     */
    public static void clearConvergence(ServerPlayer player) {
        for (ElementType element : ElementType.values()) {
            ResourceLocation attrId = getElementalAttributeId(element);
            if (attrId != null) {
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrId);
                if (attr != null) {
                    AttributeInstance inst = player.getAttribute(attr);
                    if (inst != null) {
                        inst.removeModifier(ELEMENTAL_MODIFIER_UUID);
                    }
                }
            }
        }
        
        // Sync to client to update UI
        syncToClient(player);
    }

    // --- Legacy / Context-less helpers ---

    public static float getStat(UUID playerUuid, ElementType element) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 1.0f;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) return getStat(player, element);
        
        return PlayerPersistentData.get(server).getElementalData(playerUuid).getStat(element);
    }
}
