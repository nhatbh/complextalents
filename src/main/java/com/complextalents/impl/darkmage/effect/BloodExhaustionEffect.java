package com.complextalents.impl.darkmage.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Blood Corruption / Hemorrhage Effect:
 * Applied when casting consecutive Blood spells.
 * Stacks up to 7 times (amplifier 0 to 6).
 * Each stack increases HP cost multiplier and decreases Lifesteal yield.
 */
public class BloodExhaustionEffect extends MobEffect {
    public BloodExhaustionEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000); // Dark Red
    }
}
