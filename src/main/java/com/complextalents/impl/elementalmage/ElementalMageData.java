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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing Elemental Mage stats.
 * Now acts as a wrapper around the IPlayerElementalMageData capability.
 */
public class ElementalMageData {

    private static final UUID ELEMENTAL_MODIFIER_UUID = UUID.fromString("f4702164-323b-4573-b6d8-c682705a6e84");
    
    // Non-persistent session state for Harmonic Convergence
    private static final ConcurrentHashMap<UUID, ConvergenceBuff> CONVERGENCE_BUFFS = new ConcurrentHashMap<>();

    public static float getStat(Player player, ElementType element) {
        return player.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                .map(cap -> cap.getStat(element))
                .orElse(0.0f);
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
        PacketHandler.sendTo(new ElementalMageSyncPacket(stats), player);
    }

    /**
     * Re-apply all attribute modifiers based on current elemental stats.
     */
    public static void applyAttributeModifiers(Player player) {
        for (ElementType element : ElementType.values()) {
            float value = getStat(player, element);
            ResourceLocation attrId = getElementalAttributeId(element);
            if (attrId != null) {
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrId);
                if (attr != null) {
                    AttributeInstance inst = player.getAttribute(attr);
                    if (inst != null) {
                        inst.removeModifier(ELEMENTAL_MODIFIER_UUID);
                        if (value > 0) {
                            inst.addPermanentModifier(new AttributeModifier(ELEMENTAL_MODIFIER_UUID, 
                                "Elemental Power Modifier", value, AttributeModifier.Operation.ADDITION));
                        }
                    }
                }
            }
        }
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
     * Process elemental damage dealt by a player to potentially increase their power.
     */
    public static void processElementalDamage(ServerPlayer player, ElementType element, float damage) {
        float current = getStat(player, element);
        float increase = damage * 0.01f; // 1% of damage as power
        setStat(player, element, current + increase);
    }

    public static ConvergenceBuff getConvergenceBuff(UUID playerId) {
        return CONVERGENCE_BUFFS.computeIfAbsent(playerId, k -> new ConvergenceBuff());
    }

    /**
     * Inner class for tracking session state for Harmonic Convergence buffs.
     */
    public static class ConvergenceBuff {
        public boolean waitingForNextSpell = false;
        public String buffedSpellId = null;
        public int buffWindowTicks = 0;
        public double cachedCritChanceOffset = 0.0;
        public double cachedCritDamageBonus = 0.0;
        
        // Fields for basic convergence logic (active element tracking)
        public ElementType activeElement = null;
        public long expirationTime = 0;
        public float multiplier = 1.0f;

        public boolean isActive(long currentTime) {
            return activeElement != null && currentTime < expirationTime;
        }
    }

    // --- Legacy / Context-less helpers ---

    public static float getStat(UUID playerUuid, ElementType element) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0.0f;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) return getStat(player, element);
        
        return PlayerPersistentData.get(server).getElementalData(playerUuid).getStat(element);
    }

    @Deprecated
    public static void setStat(UUID playerUuid, ElementType element, float value) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            setStat(player, element, value);
        } else {
            var data = PlayerPersistentData.get(server);
            data.getElementalData(playerUuid).setStat(element, value);
            data.setDirty();
        }
    }
}
