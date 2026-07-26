package com.complextalents.impl.darkmage.effect;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Harvest Frenzy custom beneficial effect.
 * Increases Iron's Spellbooks Cast Speed attribute (CAST_TIME_REDUCTION).
 */
public class HarvestFrenzyEffect extends MobEffect {

    public HarvestFrenzyEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8B0000);
        if (AttributeRegistry.CAST_TIME_REDUCTION != null && AttributeRegistry.CAST_TIME_REDUCTION.get() != null) {
            this.addAttributeModifier(
                    AttributeRegistry.CAST_TIME_REDUCTION.get(),
                    "f381c810-74a1-4328-98e9-86b6279f1704",
                    0.10, // +10% Cast Speed per amplifier level
                    AttributeModifier.Operation.ADDITION
            );
        }
    }
}
