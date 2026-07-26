package com.complextalents.elemental.effects;

import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * Buff effect for Harmonic Convergence.
 * While active, the player has locked Harmony Multiplier, instant Apex Catalyst reactions,
 * and guaranteed spell critical hits.
 */
public class HarmonicConvergenceEffect extends MobEffect {
    public HarmonicConvergenceEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).ifPresent(cap -> {
                cap.setLockedHarmonyMultiplier(0.0f);
                cap.setApexElement(null);
                cap.setConvergenceCritChance(0.0f);
                cap.setConvergenceCritDamage(0.0f);
                cap.sync();
            });
        }
    }
}
