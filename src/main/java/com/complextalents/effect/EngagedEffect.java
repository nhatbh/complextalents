package com.complextalents.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class EngagedEffect extends MobEffect {

    public EngagedEffect() {
        // Warm crimson/fire color (0xDC382C)
        super(MobEffectCategory.HARMFUL, 0xDC382C);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        // Engaged status cannot be cleansed with Milk or items
        return Collections.emptyList();
    }
}
