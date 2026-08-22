package com.complextalents.mixin.summoning;

import com.complextalents.summoning.SummoningManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsSummonMixin {

    /**
     * Prevents AI targeting goals from acquiring players as targets when the attacker is a player summon.
     */
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void ct$ignorePlayerTargeting(LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (attacker != null && target instanceof Player && SummoningManager.isPlayerSummon(attacker)) {
            cir.setReturnValue(false);
        }
    }
}
