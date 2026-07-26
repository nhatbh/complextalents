package com.complextalents.impl.darkmage.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Soul Stasis Potion Effect for Dark Mage.
 * <p>
 * While active, Blood Pact HP drain, damage accumulation, and staggered Bleed ticks are paused.
 * Absorbing additional Soul Orbs adds to the remaining duration of this effect.
 * </p>
 */
public class SoulStasisEffect extends MobEffect {

    public SoulStasisEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x990033); // Deep Crimson / Violet
    }
}
