package com.complextalents.impl.marksman.effect;

import com.complextalents.util.UUIDHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * Custom MobEffect for Marksman "Dismissed" (Thoát Thân) State.
 * Grants:
 * - +200% Movement Speed boost via Attribute Modifier (Vastly accelerated movement)
 * - Invisibility & Invulnerability while active
 * - Per-tick Hostile Target Clearing
 */
public class DismissedEffect extends MobEffect {

    private static final UUID SPEED_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("effects", "dismissed_speed");

    public DismissedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xAA00FF); // Vibrant Void Purple
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID.toString(), 2.00, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity instanceof Player player) {
            // Continuously clear hostile mob targets within 32 blocks
            AABB searchBox = player.getBoundingBox().inflate(32.0);
            List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, searchBox, mob -> mob.getTarget() == player);
            for (Mob mob : nearbyMobs) {
                mob.setTarget(null);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // Tick every tick to ensure target clearing
    }
}
