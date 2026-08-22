package com.complextalents.mixin.summoning;

import com.complextalents.summoning.SummoningManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySummonMixin {

    /**
     * Ensures player-summoned entities are always treated as allies by players and ally utility methods
     * (such as Utils.shouldHealEntity in Iron's Spellbooks).
     */
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void ct$isAlliedToPlayer(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (other instanceof Player && SummoningManager.isPlayerSummon(self)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Prevents physical pushing / movement alteration between player-summoned entities and players.
     */
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void ct$preventPushPlayer(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if ((SummoningManager.isPlayerSummon(self) && entity instanceof Player) ||
            (SummoningManager.isPlayerSummon(entity) && self instanceof Player)) {
            ci.cancel();
        }
    }

    /**
     * Disables collision clipping between player-summoned entities and players.
     */
    @Inject(method = "canCollideWith(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void ct$preventCollisionWithPlayer(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if ((SummoningManager.isPlayerSummon(self) && entity instanceof Player) ||
            (SummoningManager.isPlayerSummon(entity) && self instanceof Player)) {
            cir.setReturnValue(false);
        }
    }
}
