package com.complextalents.impl.darkmage.effect;

import com.complextalents.impl.darkmage.util.BloodParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class BleedEffect extends MobEffect {

    public BleedEffect() {
        // Dark red color
        super(MobEffectCategory.HARMFUL, 0x8A0303);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Tick every 10 ticks (0.5 seconds)
        return duration % 10 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }

        ServerLevel level = (ServerLevel) entity.level();

        // Deal 1.5 constant magic damage
        entity.hurt(level.damageSources().magic(), 1.5f);

        // Spawn bleed particles
        Vec3 pos = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        level.sendParticles(BloodParticleHelper.BLOOD_MIST, pos.x, pos.y, pos.z, 5, 0.2, 0.4, 0.2, 0.05);
    }
}
