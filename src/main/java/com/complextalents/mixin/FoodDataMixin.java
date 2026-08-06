package com.complextalents.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    private static final String HEALING_POOL_KEY = "FoodHealingPool";
    private static final String HEALING_TICKS_KEY = "FoodHealingTicks";
    private static final int RESET_INTERVAL_TICKS = 600; // 30 seconds

    private static final ThreadLocal<Player> CURRENT_PLAYER = new ThreadLocal<>();

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"))
    private void capturePlayerAndTickPool(Player player, CallbackInfo ci) {
        CURRENT_PLAYER.set(player);

        if (player == null || player.level().isClientSide) return;

        // Run tick decrement every 10 ticks
        if (player.tickCount % 10 == 0) {
            CompoundTag tag = player.getPersistentData();
            double maxPool = 20.0 + (0.20 * player.getMaxHealth());

            if (!tag.contains(HEALING_TICKS_KEY) || !tag.contains(HEALING_POOL_KEY)) {
                tag.putInt(HEALING_TICKS_KEY, RESET_INTERVAL_TICKS);
                tag.putDouble(HEALING_POOL_KEY, maxPool);
            } else {
                int remainingTicks = tag.getInt(HEALING_TICKS_KEY) - 10;
                if (remainingTicks <= 0) {
                    tag.putInt(HEALING_TICKS_KEY, RESET_INTERVAL_TICKS);
                    double currentPool = tag.getDouble(HEALING_POOL_KEY);
                    tag.putDouble(HEALING_POOL_KEY, Math.min(maxPool, currentPool + (maxPool * 0.5)));
                } else {
                    tag.putInt(HEALING_TICKS_KEY, remainingTicks);
                    double currentPool = tag.getDouble(HEALING_POOL_KEY);
                    if (currentPool > maxPool) {
                        tag.putDouble(HEALING_POOL_KEY, maxPool);
                    }
                }
            }
        }
    }

    /**
     * Modifies the float argument passed into player.heal(float) inside FoodData.tick().
     * Uses @ModifyArg instead of @Redirect so other mods can safely inject or modify
     * natural regeneration without Mixin conflicts.
     */
    @ModifyArg(
        method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"
        ),
        index = 0
    )
    private float modifyNaturalHealAmount(float vanillaAmount) {
        Player player = CURRENT_PLAYER.get();
        if (player == null || player.level().isClientSide) return vanillaAmount;

        CompoundTag tag = player.getPersistentData();
        double maxPool = 20.0 + (0.20 * player.getMaxHealth());

        if (!tag.contains(HEALING_TICKS_KEY) || !tag.contains(HEALING_POOL_KEY)) {
            tag.putInt(HEALING_TICKS_KEY, RESET_INTERVAL_TICKS);
            tag.putDouble(HEALING_POOL_KEY, maxPool);
        } else if (tag.getInt(HEALING_TICKS_KEY) <= 0) {
            tag.putInt(HEALING_TICKS_KEY, RESET_INTERVAL_TICKS);
            double currentPool = tag.getDouble(HEALING_POOL_KEY);
            tag.putDouble(HEALING_POOL_KEY, Math.min(maxPool, currentPool + (maxPool * 0.5)));
        }

        double currentPool = tag.getDouble(HEALING_POOL_KEY);
        if (currentPool <= 0) {
            return 0.0f;
        }

        float maxHealth = player.getMaxHealth();
        // 5% of max health per natural regen tick
        float basePercentageAmount = maxHealth * 0.05f * vanillaAmount;

        double actualHeal = Math.min((double) basePercentageAmount, currentPool);
        currentPool -= actualHeal;

        tag.putDouble(HEALING_POOL_KEY, Math.max(0.0, currentPool));
        return (float) actualHeal;
    }

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void clearPlayer(Player player, CallbackInfo ci) {
        CURRENT_PLAYER.remove();
    }
}
