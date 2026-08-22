package com.complextalents.summoning;

import com.complextalents.util.UUIDHelper;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.UUID;

/**
 * Encapsulates Standard Mage summoning resource rules: Max Mana reservation,
 * 30s maintenance decay (2x speed when Downed), and 0 Mana forced despawning.
 */
public class StandardMageSummonHandler {

    public static final UUID MANA_RESERVE_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("summoning", "mana_reserve");

    public static double calculateReservedMana(int spellManaCost) {
        return Math.max(10.0, spellManaCost);
    }

    public static boolean tickMaintenance(ServerPlayer player, List<SummonGroup> activeGroups, boolean downed, long currentTime) {
        boolean modified = false;
        for (SummonGroup group : activeGroups) {
            if (!group.isDarkMage) {
                long ageTicks = currentTime - group.spawnGameTime;
                if (ageTicks > 600) { // Past 30 seconds
                    double baseDecayRate = group.initialManaCost / 30.0;
                    double decayMultiplier = downed ? 2.0 : 1.0;
                    group.extraDecayAccrued += baseDecayRate * decayMultiplier;
                    modified = true;
                }
            }
        }
        return modified;
    }

    public static boolean checkDespawnThreshold(ServerPlayer player) {
        if (!SummoningManager.isIronSpellbooksLoaded()) return false;
        try {
            Attribute manaAttr = AttributeRegistry.MAX_MANA.get();
            if (player.getAttributes().hasAttribute(manaAttr)) {
                double effectiveMaxMana = player.getAttributeValue(manaAttr);
                return effectiveMaxMana <= 0.0;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void applyManaModifiers(ServerPlayer player, double totalManaPenalty) {
        if (!SummoningManager.isIronSpellbooksLoaded()) return;
        try {
            Attribute manaAttr = AttributeRegistry.MAX_MANA.get();
            AttributeInstance instance = player.getAttribute(manaAttr);
            if (instance != null) {
                instance.removeModifier(MANA_RESERVE_MODIFIER_UUID);
                if (totalManaPenalty > 0.01) {
                    instance.addTransientModifier(new AttributeModifier(
                            MANA_RESERVE_MODIFIER_UUID, "CT Summon Mana Reserve", -totalManaPenalty, AttributeModifier.Operation.ADDITION
                    ));
                }
                MagicData magicData = MagicData.getPlayerMagicData(player);
                if (magicData != null) {
                    double newMaxMana = player.getAttributeValue(manaAttr);
                    if (magicData.getMana() > newMaxMana) {
                        magicData.setMana((float) newMaxMana);
                        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
