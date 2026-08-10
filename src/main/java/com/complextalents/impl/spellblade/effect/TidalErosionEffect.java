package com.complextalents.impl.spellblade.effect;

import com.complextalents.util.UUIDHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Tidal Erosion Effect (Aqua Imbue Debuff)
 * High-pressure aquatic currents erode target defenses, reducing armor by 5% per stack (up to 3 stacks / -15% max).
 */
public class TidalErosionEffect extends MobEffect {

    private static final UUID EROSION_UUID = UUIDHelper.generateAttributeModifierUUID("spellblade", "tidal_erosion");

    public TidalErosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x0088FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(entity, attributeMap, amplifier);
        var armorInstance = attributeMap.getInstance(Attributes.ARMOR);
        if (armorInstance != null) {
            armorInstance.removeModifier(EROSION_UUID);
            // amplifier 0 = 1 stack (-5%), 1 = 2 stacks (-10%), 2 = 3 stacks (-15%)
            int stacks = Math.min(3, amplifier + 1);
            double armorShredPct = -0.05 * stacks;
            armorInstance.addTransientModifier(new AttributeModifier(
                    EROSION_UUID,
                    "Tidal Erosion",
                    armorShredPct,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        var armorInstance = attributeMap.getInstance(Attributes.ARMOR);
        if (armorInstance != null) {
            armorInstance.removeModifier(EROSION_UUID);
        }
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(net.minecraft.world.effect.MobEffectInstance instance) {
                return true;
            }

            @Override
            public boolean isVisibleInGui(net.minecraft.world.effect.MobEffectInstance instance) {
                return true;
            }
        });
    }
}
