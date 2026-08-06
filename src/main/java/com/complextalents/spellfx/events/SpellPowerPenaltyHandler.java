package com.complextalents.spellfx.events;

import com.complextalents.TalentsMod;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler for Spell Power Penalty calculations.
 * Dynamically increases cast time and mana cost for spells based on excess spell power.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellPowerPenaltyHandler {

    public static double penaltyThreshold = 0.0;
    public static double spellPenaltyWeight = 1.0;
    public static double instantSpellPenaltyWeight = 1.0;

    /**
     * Calculates penalty multiplier based on general and school-specific spell power above threshold.
     */
    public static double calculatePenaltyMultiplier(AbstractSpell spell, LivingEntity entity, double weight) {
        if (entity == null || spell == null) return 1.0;

        double generalPower = 1.0;
        double schoolPower = 1.0;

        var generalAttr = entity.getAttribute(AttributeRegistry.SPELL_POWER.get());
        if (generalAttr != null) {
            generalPower = generalAttr.getValue();
        }

        if (spell.getSchoolType() != null) {
            schoolPower = spell.getSchoolType().getPowerFor(entity);
        }

        double totalPowerBonus = Math.max(0, generalPower - 1.0) + Math.max(0, schoolPower - 1.0);
        double penalizedPowerBonus = Math.max(0, totalPowerBonus - penaltyThreshold);

        return 1.0 + (penalizedPowerBonus * weight);
    }

    /**
     * Intercepts spell casting event on server to set the penalized mana cost.
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        if (event.getEntity() == null || event.getCastSource() == null || !event.getCastSource().consumesMana()) {
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        double weight = spell.getEffectiveCastTime(event.getSpellLevel(), event.getEntity()) <= 0
                ? instantSpellPenaltyWeight
                : spellPenaltyWeight;

        double multiplier = calculatePenaltyMultiplier(spell, event.getEntity(), weight);
        if (multiplier > 1.0) {
            int originalCost = event.getManaCost();
            int penalizedCost = (int) Math.ceil(originalCost * multiplier);
            event.setManaCost(penalizedCost);
        }
    }
}
