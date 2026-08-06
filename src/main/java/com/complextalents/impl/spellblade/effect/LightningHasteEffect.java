package com.complextalents.impl.spellblade.effect;

import com.complextalents.util.UUIDHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class LightningHasteEffect extends MobEffect {

    public LightningHasteEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFCFB34A);
        // Grants +10% Attack Speed per amplifier level (+10% at Lvl 1 up to +50% at Lvl 5)
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                UUIDHelper.generateAttributeModifierUUID("spellblade", "lightning_haste").toString(),
                0.10,
                AttributeModifier.Operation.MULTIPLY_BASE
        );
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
