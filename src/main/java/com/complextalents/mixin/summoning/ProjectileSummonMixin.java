package com.complextalents.mixin.summoning;

import com.complextalents.summoning.SummoningManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Projectile.class)
public abstract class ProjectileSummonMixin {

    @Shadow
    @Nullable
    public abstract Entity getOwner();

    /**
     * Ensures projectiles fired by player-summoned entities pass through players without colliding or dealing damage.
     */
    @Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void ct$passThroughPlayers(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player) {
            Entity owner = this.getOwner();
            if (owner != null && SummoningManager.isPlayerSummonOrOwnedByPlayerSummon(owner)) {
                cir.setReturnValue(false);
            }
        }
    }
}
