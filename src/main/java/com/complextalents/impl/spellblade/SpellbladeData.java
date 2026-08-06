package com.complextalents.impl.spellblade;

import com.complextalents.network.PacketHandler;
import com.complextalents.network.spellblade.SpellbladeDataSyncPacket;
import com.complextalents.spellmastery.SpellSchool;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

public class SpellbladeData {

    public static SpellSchool getActiveElement(Player player) {
        return player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA)
                .map(IPlayerSpellbladeData::getActiveElement)
                .orElse(null);
    }

    public static void setActiveElement(Player player, SpellSchool school) {
        player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> cap.setActiveElement(school));
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static int getEnhancedAttackTicks(Player player) {
        return player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA)
                .map(IPlayerSpellbladeData::getEnhancedAttackTicks)
                .orElse(0);
    }

    public static void setEnhancedAttackTicks(Player player, int ticks) {
        player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> cap.setEnhancedAttackTicks(ticks));
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static boolean hasImbueCharge(Player player) {
        return player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA)
                .map(IPlayerSpellbladeData::hasImbueCharge)
                .orElse(false);
    }

    public static void setHasImbueCharge(Player player, boolean charge) {
        player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> cap.setHasImbueCharge(charge));
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static int getOverchargeTicks(Player player) {
        return player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA)
                .map(IPlayerSpellbladeData::getOverchargeTicks)
                .orElse(0);
    }

    public static void setOverchargeTicks(Player player, int ticks) {
        player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> cap.setOverchargeTicks(ticks));
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static boolean isOverchargeActive(Player player) {
        return player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA)
                .map(IPlayerSpellbladeData::isOverchargeActive)
                .orElse(false);
    }

    public static void syncToClient(ServerPlayer player) {
        player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> {
            PacketHandler.sendTo(new SpellbladeDataSyncPacket(
                    cap.getActiveElement(),
                    cap.getEnhancedAttackTicks(),
                    cap.hasImbueCharge(),
                    cap.getOverchargeTicks()
            ), player);
        });
    }

    /**
     * Calculates the player's Effective Ability Power (AP) for a specific school:
     * Effective AP = Base AP * School Specific Spell Power Attribute Multiplier.
     */
    public static double getEffectiveAP(Player player, SpellSchool school) {
        if (player == null) return 0.0;
        double baseAp = player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        if (school == null) return baseAp;

        Attribute schoolAttribute = getSchoolAttribute(school);
        double schoolMult = 1.0;
        if (schoolAttribute != null && player.getAttribute(schoolAttribute) != null) {
            schoolMult = player.getAttributeValue(schoolAttribute);
        }

        return baseAp * schoolMult;
    }

    public static Attribute getSchoolAttribute(SpellSchool school) {
        if (school == null) return null;
        return switch (school) {
            case FIRE -> AttributeRegistry.FIRE_SPELL_POWER.get();
            case ICE -> AttributeRegistry.ICE_SPELL_POWER.get();
            case LIGHTNING -> AttributeRegistry.LIGHTNING_SPELL_POWER.get();
            case NATURE -> AttributeRegistry.NATURE_SPELL_POWER.get();
            case EVOCATION -> AttributeRegistry.EVOCATION_SPELL_POWER.get();
            case ENDER -> AttributeRegistry.ENDER_SPELL_POWER.get();
            case BLOOD -> AttributeRegistry.BLOOD_SPELL_POWER.get();
            case ELDRITCH -> AttributeRegistry.ELDRITCH_SPELL_POWER.get();
            case AQUA -> ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("traveloptics", "aqua_spell_power"));
            case HOLY -> AttributeRegistry.HOLY_SPELL_POWER.get();
        };
    }
}
