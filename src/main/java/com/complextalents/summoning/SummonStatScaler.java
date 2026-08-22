package com.complextalents.summoning;

import com.complextalents.TalentsMod;
import com.complextalents.registry.ModAttributes;
import com.complextalents.util.UUIDHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Component managing attribute stat scaling (Health and Attack Damage) for summoned entities.
 */
public class SummonStatScaler {

    private static final String SUMMON_SCALED_TAG = "CT_SummonScaled";
    private static final UUID HEALTH_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("summoning", "health_modifier");
    private static final UUID DAMAGE_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("summoning", "damage_modifier");
    private static final UUID IRONS_HEALTH_BONUS_UUID = UUIDHelper.generateAttributeModifierUUID("irons_spellbooks", "spell_power_health_bonus");
    private static final UUID IRONS_DAMAGE_BONUS_UUID = UUIDHelper.generateAttributeModifierUUID("irons_spellbooks", "spell_power_damage_bonus");

    public static boolean isIronSpellbooksLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    /**
     * Retrieves the raw linear spell power multiplier calculated by Iron's Spellbooks.
     */
    public static double getRawLinearSpellPower(LivingEntity owner) {
        if (owner == null || !isIronSpellbooksLoaded()) return 1.0;
        try {
            double rawSpellPower = 1.0;
            double rawSchoolPower = 1.0;

            Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
            if (spellPowerAttr != null && owner.getAttributes().hasAttribute(spellPowerAttr)) {
                rawSpellPower = owner.getAttributeValue(spellPowerAttr);
            }

            Attribute summonSchoolAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "summon_spell_power"));
            if (summonSchoolAttr == null) {
                summonSchoolAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "evocation_spell_power"));
            }
            if (summonSchoolAttr != null && owner.getAttributes().hasAttribute(summonSchoolAttr)) {
                rawSchoolPower = owner.getAttributeValue(summonSchoolAttr);
            }

            return Math.max(1.0, rawSpellPower * rawSchoolPower);
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    /**
     * Calculates the Summon Power Factor for an owner.
     * Combines Summoning Power stat, Spell Power, and School Spell Power additively.
     */
    public static double calculateSummonPowerFactor(LivingEntity owner) {
        if (owner == null) return 1.0;

        double summoningPowerBonus = 0.0;
        AttributeInstance summonAttr = owner.getAttribute(ModAttributes.SUMMONING_POWER.get());
        if (summonAttr != null) {
            summoningPowerBonus = summonAttr.getValue() - 1.0;
        }

        // Apply refinement summoning power bonus!
        net.minecraft.world.item.ItemStack contextStack = com.complextalents.refinement.RefinementContext.getCurrentContextStack();
        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = com.complextalents.refinement.RefinementContext.getCurrentContextSpell();
        if (contextStack.isEmpty() && owner instanceof Player player && spell != null) {
            contextStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
        }
        if (spell != null && com.complextalents.refinement.MagicRefinementManager.isSummoningSpell(spell) && !contextStack.isEmpty()) {
            double refinementBonus = com.complextalents.refinement.MagicRefinementManager.getSpellRefinementMainstatBonus(contextStack, spell);
            summoningPowerBonus += refinementBonus;

            // Add the SUMMONING_POWER substat from the casting stack
            summoningPowerBonus += com.complextalents.refinement.MagicRefinementManager.getSpellSubstatValue(
                    contextStack, spell, com.complextalents.refinement.MagicRefinementManager.MagicSubstatType.SUMMONING_POWER
            );
        }

        double spellPowerBonus = 0.0;
        double schoolPowerBonus = 0.0;

        if (isIronSpellbooksLoaded()) {
            try {
                Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
                if (spellPowerAttr != null && owner.getAttributes().hasAttribute(spellPowerAttr)) {
                    double rawSpellPower = owner.getAttributeValue(spellPowerAttr);
                    spellPowerBonus = Math.max(0.0, rawSpellPower - 1.0);
                }

                Attribute summonSchoolAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "summon_spell_power"));
                if (summonSchoolAttr == null) {
                    summonSchoolAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "evocation_spell_power"));
                }
                if (summonSchoolAttr != null && owner.getAttributes().hasAttribute(summonSchoolAttr)) {
                    double rawSchoolPower = owner.getAttributeValue(summonSchoolAttr);
                    schoolPowerBonus = Math.max(0.0, rawSchoolPower - 1.0);
                }
            } catch (Throwable ignored) {}
        }

        double totalFactor = 1.0 + summoningPowerBonus + spellPowerBonus + schoolPowerBonus;
        return Math.max(0.1, totalFactor);
    }

    /**
     * Scales a summon's attributes based on Summon Power Factor.
     */
    public static void applyStatScaling(LivingEntity summon, LivingEntity owner) {
        if (summon == null || owner == null || summon.level().isClientSide()) return;

        CompoundTag nbt = summon.getPersistentData();
        if (nbt.getBoolean(SUMMON_SCALED_TAG)) return;

        double summonPowerFactor = calculateSummonPowerFactor(owner);
        double rawLinearPower = getRawLinearSpellPower(owner);

        // Health Scaling
        AttributeInstance healthAttr = summon.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(IRONS_HEALTH_BONUS_UUID);
            healthAttr.removeModifier(HEALTH_MODIFIER_UUID);

            double baseHealth = healthAttr.getBaseValue();
            if (rawLinearPower > 1.001) {
                baseHealth = baseHealth / rawLinearPower;
                healthAttr.setBaseValue(baseHealth);
            }

            double bonusHealth = baseHealth * (summonPowerFactor - 1.0);
            healthAttr.addPermanentModifier(new AttributeModifier(
                    HEALTH_MODIFIER_UUID, "CT Summon Health Scale", bonusHealth, AttributeModifier.Operation.ADDITION
            ));
            summon.setHealth(summon.getMaxHealth());
        }

        // Damage Scaling
        AttributeInstance damageAttr = summon.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(IRONS_DAMAGE_BONUS_UUID);
            damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);

            double baseDamage = damageAttr.getBaseValue();
            if (rawLinearPower > 1.001) {
                baseDamage = baseDamage / rawLinearPower;
                damageAttr.setBaseValue(baseDamage);
            }

            double bonusDamage = baseDamage * (summonPowerFactor - 1.0);
            damageAttr.addPermanentModifier(new AttributeModifier(
                    DAMAGE_MODIFIER_UUID, "CT Summon Damage Scale", bonusDamage, AttributeModifier.Operation.ADDITION
            ));
        }

        nbt.putBoolean(SUMMON_SCALED_TAG, true);
        TalentsMod.LOGGER.debug("Scaled summon {} for owner {} with power factor {}", summon.getName().getString(), owner.getName().getString(), summonPowerFactor);
    }
}
