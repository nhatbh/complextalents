package com.complextalents.elemental.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Buff effect for Harmonic Convergence.
 * While active, the player has increased crit chance and damage for all spells and attacks.
 * Multipliers are stored in the player's ElementalMageData capability.
 */
public class HarmonicConvergenceEffect extends MobEffect {
    public HarmonicConvergenceEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void removeAttributeModifiers(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.complextalents.impl.elementalmage.ElementalMageData.clearConvergence(serverPlayer);
        }
    }
}
