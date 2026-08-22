package com.complextalents.summoning.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class SummonersFatigueEffect extends MobEffect {

    public SummonersFatigueEffect() {
        // Deep purple/magenta color (0x6A0DAD)
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        // Cannot be cleansed with milk or items
        return Collections.emptyList();
    }
}
