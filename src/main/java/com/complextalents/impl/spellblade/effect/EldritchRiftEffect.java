package com.complextalents.impl.spellblade.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class EldritchRiftEffect extends MobEffect {
    public EldritchRiftEffect() {
        super(MobEffectCategory.HARMFUL, 0x660099);
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
