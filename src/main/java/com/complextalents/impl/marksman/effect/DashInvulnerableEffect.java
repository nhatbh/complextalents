package com.complextalents.impl.marksman.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Custom MobEffect granted during Marksman Relentless Pursuit (Tactical Dash).
 * Negates all incoming damage while active.
 */
public class DashInvulnerableEffect extends MobEffect {

    public DashInvulnerableEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700); // Gold
    }
}
