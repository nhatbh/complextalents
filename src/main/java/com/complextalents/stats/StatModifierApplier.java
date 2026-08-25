package com.complextalents.stats;

import com.complextalents.registry.ModAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Logic for applying stat rank effects to player attributes.
 */
public class StatModifierApplier {

    /**
     * Re-applies all stat modifiers to the player based on their ranks.
     */
    public static void applyAll(Player player, java.util.Map<StatType, Integer> ranks) {
        for (StatType type : StatType.values()) {
            int rank = ranks.getOrDefault(type, 0);
            applyStatModifier(player, type, rank);
        }
    }

    private static void applyStatModifier(Player player, StatType type, int rank) {
        double totalValue = type.getYieldPerRank() * rank;
        UUID uuid = type.getModifierUuid();

        switch (type) {
            case FLAT_AD:
                updateModifier(player, Attributes.ATTACK_DAMAGE, totalValue, AttributeModifier.Operation.ADDITION,
                        uuid);
                break;
            case PERCENT_AD:
                updateModifier(player, Attributes.ATTACK_DAMAGE, totalValue,
                        AttributeModifier.Operation.MULTIPLY_TOTAL,
                        uuid);
                break;
            case AP:
                updateModAttribute(player, "irons_spellbooks", "spell_power", totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case MAGIC_EFFECTIVENESS:
                updateModifier(player, com.complextalents.registry.ModAttributes.MAGIC_EFFECTIVENESS.get(), totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case LUCK_CRIT:
                // +2.5% Crit Chance, +0.2 Luck, +2.5% Spell Crit Chance per rank
                updateModifier(player, Attributes.LUCK, rank * 0.2, AttributeModifier.Operation.ADDITION, uuid);
                updateModAttribute(player, "attributeslib", "crit_chance", rank * 0.025,
                        AttributeModifier.Operation.ADDITION, uuid);
                updateModifier(player, ModAttributes.SPELL_CRIT_CHANCE.get(), rank * 0.025,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case MAX_HP:
                updateModifier(player, Attributes.MAX_HEALTH, totalValue, AttributeModifier.Operation.ADDITION, uuid);
                break;
            case MAX_MANA:
                double oldMax = 0;
                var attr = AttributeRegistry.MAX_MANA.get();
                if (attr != null) {
                    oldMax = player.getAttributeValue(attr);
                }

                updateModAttribute(player, "irons_spellbooks", "max_mana", totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);

                if (attr != null && player instanceof ServerPlayer serverPlayer) {
                    double newMax = player.getAttributeValue(attr);
                    if (newMax > oldMax) {
                        try {
                            MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                            magicData.setMana(magicData.getMana() + (float) (newMax - oldMax));
                            PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                        } catch (Exception ignored) {
                        }
                    }
                }
                break;
            case HEAL_AND_SHIELD:
                updateModifier(player, ModAttributes.HEAL_AND_SHIELD_POWER.get(), totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case CDR:
                // Ability Haste formula: Haste / (100 + Haste) = Cooldown & Cast Time Reduction %
                double hasteFraction = totalValue / (100.0 + totalValue);
                updateModAttribute(player, "irons_spellbooks", "cooldown_reduction", hasteFraction,
                        AttributeModifier.Operation.ADDITION, uuid);
                updateModAttribute(player, "irons_spellbooks", "cast_time_reduction", hasteFraction,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case SUMMONING_POWER:
                updateModifier(player, ModAttributes.SUMMONING_POWER.get(), totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case GUN_DAMAGE:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(), totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
            case RELOAD_SPEED:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.RELOAD_SPEED.get(), totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
            case FORTITUDE:
                updateModifier(player, ModAttributes.FORTITUDE.get(), totalValue,
                        AttributeModifier.Operation.ADDITION, uuid);
                break;
            case HEADSHOT_DAMAGE:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.HEADSHOT_MULTIPLIER.get(), totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
            case RECOIL_CONTROL:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.RECOIL.get(), -totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
            case BULLET_PENETRATION:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.PIERCE_MULTIPLIER.get(), totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
            case FIRE_RATE:
                updateModifier(player, com.complextalents.tacz.GunAttributeType.RPM_MULTIPLIER.get(), totalValue,
                        AttributeModifier.Operation.MULTIPLY_BASE, uuid);
                break;
        }
    }

    private static void updateModifier(Player player, Attribute attribute, double amount,
            AttributeModifier.Operation operation, UUID uuid) {
        if (attribute == null)
            return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null)
            return;

        instance.removeModifier(uuid);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, "General Stat Modifier", amount, operation));
        }
    }

    private static void updateModAttribute(Player player, String modId, String attrName, double amount,
            AttributeModifier.Operation operation, UUID uuid) {
        Attribute attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(modId, attrName));
        if (attribute != null) {
            updateModifier(player, attribute, amount, operation, uuid);
        }
    }
}
