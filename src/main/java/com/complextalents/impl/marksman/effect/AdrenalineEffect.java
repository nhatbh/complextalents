package com.complextalents.impl.marksman.effect;

import com.complextalents.tacz.HeartRateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Custom MobEffect for Marksman Adrenaline State.
 * The effect amplifier represents the active Overclock stack count.
 */
public class AdrenalineEffect extends MobEffect {

    public AdrenalineEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xDC143C); // Crimson Red
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            // Lock heart rate to resting state (60 BPM) while Adrenaline is active
            HeartRateManager.setHeartRate(player, HeartRateManager.RESTING_BPM);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // Tick every tick to maintain heart rate stabilization
    }
}
