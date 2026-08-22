package com.complextalents.summoning;

import com.complextalents.util.UUIDHelper;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.UUID;

/**
 * Encapsulates Dark Mage summoning resource rules: Max HP reservation scaled with Max Mana,
 * 30s HP corrosion, Downed state instant unsummon, and critical HP forced despawning.
 */
public class DarkMageSummonHandler {

    public static final UUID HP_RESERVE_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("summoning", "hp_reserve");

    public static double calculateReservedHP(ServerPlayer owner, int spellManaCost) {
        double maxMana = 100.0;
        if (SummoningManager.isIronSpellbooksLoaded()) {
            try {
                Attribute manaAttr = AttributeRegistry.MAX_MANA.get();
                if (owner.getAttributes().hasAttribute(manaAttr)) {
                    maxMana = Math.max(10.0, owner.getAttributeValue(manaAttr));
                }
            } catch (Throwable ignored) {}
        }
        double hpRatio = Math.min(0.90, (double) spellManaCost / maxMana);
        return owner.getMaxHealth() * hpRatio;
    }

    public static boolean tickMaintenance(ServerPlayer player, List<SummonGroup> activeGroups, boolean downed, long currentTime) {
        boolean modified = false;
        for (SummonGroup group : activeGroups) {
            if (group.isDarkMage) {
                long ageTicks = currentTime - group.spawnGameTime;
                if (ageTicks > 600) { // Past 30 seconds
                    double baseDecayRate = group.reservedMaxHP / 30.0;
                    group.extraDecayAccrued += baseDecayRate;
                    modified = true;
                }
            }
        }
        return modified;
    }

    public static boolean checkDespawnThreshold(ServerPlayer player) {
        // Dark Mage summons are no longer dismissed on low HP; HP can drop down to 6 HP safely.
        return false;
    }

    public static void applyHPModifiers(ServerPlayer player, double totalHPPenalty) {
        AttributeInstance hpInstance = player.getAttribute(Attributes.MAX_HEALTH);
        if (hpInstance != null) {
            hpInstance.removeModifier(HP_RESERVE_MODIFIER_UUID);
            if (totalHPPenalty > 0.01) {
                double baseMaxHP = hpInstance.getBaseValue();
                double maxAllowedPenalty = Math.max(0.0, baseMaxHP - 6.0); // Leaves at least 6.0 HP
                double actualPenalty = Math.min(totalHPPenalty, maxAllowedPenalty);

                hpInstance.addTransientModifier(new AttributeModifier(
                        HP_RESERVE_MODIFIER_UUID, "CT Summon HP Reserve", -actualPenalty, AttributeModifier.Operation.ADDITION
                ));
            }
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }
}
