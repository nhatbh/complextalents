package com.complextalents.mixin.tacz;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InaccuracyType.class, remap = false)
public abstract class InaccuracyTypeMixin {

    @Inject(
        method = "getInaccuracyType",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void forceMoveInaccuracyOnADS(LivingEntity livingEntity, CallbackInfoReturnable<InaccuracyType> cir) {
        if (livingEntity == null) return;

        IGunOperator operator = IGunOperator.fromLivingEntity(livingEntity);
        float aimingProgress = operator != null ? operator.getSynAimingProgress() : 0.0f;

        double distance = Math.abs(livingEntity.walkDist - livingEntity.walkDistO);
        if (livingEntity instanceof Player player) {
            distance = HitboxHelper.getPlayerVelocity(player).length();
        }
        boolean isMoving = distance > 0.02f;

        // Moving while aiming down sights: Force MOVE inaccuracy instead of pinpoint AIM
        if (isMoving && aimingProgress > 0.2f) {
            cir.setReturnValue(InaccuracyType.MOVE);
            return;
        }
    }
}
