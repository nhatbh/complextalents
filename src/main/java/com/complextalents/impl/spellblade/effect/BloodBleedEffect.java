package com.complextalents.impl.spellblade.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BloodBleedEffect extends MobEffect {
    public BloodBleedEffect() {
        super(MobEffectCategory.HARMFUL, 0x880000);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            float maxHp = entity.getMaxHealth();
            float bleedDmg = (float) Math.ceil(maxHp * 0.01f);
            entity.hurt(entity.damageSources().magic(), Math.max(1.0f, bleedDmg));
        }
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(net.minecraft.world.effect.MobEffectInstance instance) {
                return false;
            }

            @Override
            public boolean isVisibleInGui(net.minecraft.world.effect.MobEffectInstance instance) {
                return false;
            }
        });
    }
}
