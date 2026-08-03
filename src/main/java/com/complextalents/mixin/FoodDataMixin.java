package com.complextalents.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    private static final Map<UUID, HealingPoolTracker> HEALING_POOLS = new ConcurrentHashMap<>();

    private static class HealingPoolTracker {
        double currentPool;
        long lastUpdateTick;
        boolean initialized = false;

        void update(Player player, long currentTick) {
            float maxHealth = player.getMaxHealth();
            double maxPool = 20.0 + (0.20 * maxHealth);

            if (!initialized) {
                currentPool = maxPool;
                lastUpdateTick = currentTick;
                initialized = true;
                return;
            }

            long elapsedTicks = currentTick - lastUpdateTick;
            if (elapsedTicks > 0) {
                // Recover half of max pool every 30 seconds (600 ticks) -> maxPool / 1200 per
                // tick
                double recovery = elapsedTicks * (maxPool / 1200.0);
                currentPool = Math.min(maxPool, currentPool + recovery);
                lastUpdateTick = currentTick;
            }
        }

        float consume(Player player, float requestedAmount) {
            float maxHealth = player.getMaxHealth();
            double maxPool = 20.0 + (0.20 * maxHealth);

            if (currentPool > maxPool) {
                currentPool = maxPool;
            }

            if (currentPool <= 0) {
                return 0.0f;
            }

            double actualHeal = Math.min((double) requestedAmount, currentPool);
            currentPool -= actualHeal;
            return (float) actualHeal;
        }
    }

    private static final ThreadLocal<Player> CURRENT_PLAYER = new ThreadLocal<>();

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"))
    private void capturePlayer(Player player, CallbackInfo ci) {
        CURRENT_PLAYER.set(player);
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
        if (player == null) return vanillaAmount;

        long currentTick = player.level().getGameTime();
        UUID uuid = player.getUUID();

        HealingPoolTracker tracker = HEALING_POOLS.computeIfAbsent(uuid, k -> new HealingPoolTracker());
        tracker.update(player, currentTick);

        float maxHealth = player.getMaxHealth();
        // 5% of max health per natural regen tick
        float basePercentageAmount = maxHealth * 0.05f * vanillaAmount;

        return tracker.consume(player, basePercentageAmount);
    }

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void clearPlayer(Player player, CallbackInfo ci) {
        CURRENT_PLAYER.remove();
    }
}
