package com.complextalents.mixin.summoning;

import com.complextalents.summoning.SummoningManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySummonMixin {

    /**
     * Prevents player-summoned entities from attacking players.
     */
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void ct$preventSummonAttackingPlayer(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (target instanceof Player && SummoningManager.isPlayerSummon(self)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Complete Damage Immunity for Players against Player-Summoned Entities and Summon-Owned Projectiles.
     * Intercepts all incoming damage at HEAD before any health or knockback changes occur.
     */
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void ct$preventPlayerDamageFromSummon(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof Player) {
            Entity attacker = source.getEntity();
            Entity directAttacker = source.getDirectEntity();

            if (SummoningManager.isPlayerSummonOrOwnedByPlayerSummon(attacker) ||
                SummoningManager.isPlayerSummonOrOwnedByPlayerSummon(directAttacker)) {
                cir.setReturnValue(false);
            }
        }
    }
}
