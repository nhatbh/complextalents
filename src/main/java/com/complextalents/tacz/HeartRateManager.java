package com.complextalents.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Manages player Heart Rate (BPM) dynamics for TACZ gun combat.
 * Heart rate increases when sprinting, taking damage, or suffering low health,
 * causing weapon inaccuracy to scale dynamically up to +100% (2.0x spread).
 */
public class HeartRateManager {
    public static final String NBT_HEART_RATE = "tacz_heart_rate";
    public static final float RESTING_BPM = 60.0f;
    public static final float MAX_BPM = 180.0f;

    /**
     * Retrieves the player's current heart rate in BPM.
     */
    public static float getHeartRate(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_HEART_RATE)) {
            tag.putFloat(NBT_HEART_RATE, RESTING_BPM);
            return RESTING_BPM;
        }
        return tag.getFloat(NBT_HEART_RATE);
    }

    /**
     * Sets the player's heart rate in BPM.
     */
    public static void setHeartRate(Player player, float bpm) {
        float clamped = Math.max(RESTING_BPM, Math.min(MAX_BPM, bpm));
        player.getPersistentData().putFloat(NBT_HEART_RATE, clamped);
    }

    /**
     * Retrieves the heart rate gain factor based on player Fortitude.
     * Higher Fortitude reduces BPM increases from sprinting, damage, and low
     * health.
     */
    public static float getGainFactor(Player player) {
        double fortitude = GunAttributes.getValue(player, GunAttributeType.FORTITUDE, GunType.GLOBAL);
        return (float) (1.0 / Math.max(0.01, fortitude));
    }

    public static final String NBT_REST_TICKS = "tacz_rest_ticks";

    public static int getRestTicks(Player player) {
        return player.getPersistentData().getInt(NBT_REST_TICKS);
    }

    public static void setRestTicks(Player player, int ticks) {
        player.getPersistentData().putInt(NBT_REST_TICKS, ticks);
    }

    /**
     * Called every tick to update player heart rate based on physical state and
     * health.
     */
    public static void tickHeartRate(Player player) {
        float currentBpm = getHeartRate(player);
        float healthRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        float gainFactor = getGainFactor(player);

        // 1. Determine baseline floor based on health loss (Lower HP = Higher stress
        // floor)
        float healthFloor = RESTING_BPM;
        if (healthRatio < 0.5f) {
            // At 50% HP -> 60 BPM floor; At 0% HP -> 140 BPM floor (mitigated by fortitude)
            healthFloor = RESTING_BPM + ((0.5f - healthRatio) * 160.0f * gainFactor);
        }

        float targetBpm = healthFloor;

        // 2. Check movement state: Sprinting gains heart rate; walking allows half
        // recovery; standing allows full recovery
        double horizDistSqr = player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z;
        boolean isSprinting = player.isSprinting();
        boolean isWalking = !isSprinting && horizDistSqr > 0.0005;

        if (isSprinting) {
            // Reset rest duration while sprinting
            setRestTicks(player, 0);

            targetBpm = Math.max(targetBpm, RESTING_BPM + (90.0f * gainFactor));
            currentBpm = Math.min(MAX_BPM, currentBpm + (0.35f * gainFactor)); // ~7 BPM per sec while sprinting
        } else {
            // Player is standing or walking. Increment rest ticks.
            int restTicks = getRestTicks(player) + 1;
            setRestTicks(player, restTicks);

            if (currentBpm > targetBpm) {
                // S-Curve Recovery Model:
                // a) Time Ramp-up: Slow at first (parasympathetic delay), accelerating over
                // 2.5s of rest
                float restSecs = restTicks / 20.0f;
                float timeRamp = Math.min(1.0f, (restSecs / 2.5f) * (restSecs / 2.5f)); // Quadratic ramp

                // b) Baseline Deceleration: Eases up smoothly as it approaches baseline
                // targetBpm
                float diff = currentBpm - targetBpm;
                float distanceEase = Math.min(1.0f, diff / 30.0f); // Ease factor slows down when diff < 30 BPM

                // c) Cardiac Pulse Modulation: Rhythmic pulsed recovery synchronized with the
                // heartbeat frequency
                double pulseFreq = (currentBpm / 60.0) * Math.PI * 2.0 / 20.0; // Radians per tick
                double pulseWave = Math.sin(player.tickCount * pulseFreq);
                float pulseModulation = (float) (0.25 + 1.50 * Math.max(0.0, Math.pow(Math.max(0.0, pulseWave), 3.0)));

                float maxDecayRate = 0.90f; // Max recovery speed ~18 BPM drop per sec
                float decay = maxDecayRate * timeRamp * Math.max(0.05f, distanceEase) * pulseModulation;

                // Allow HALF rate (50%) of recovery when walking
                if (isWalking) {
                    decay *= 0.5f;
                }

                currentBpm = Math.max(targetBpm, currentBpm - decay);
            } else if (currentBpm < targetBpm) {
                currentBpm = Math.min(targetBpm, currentBpm + 0.5f);
            }
        }

        // 3. Realistic HRV (Heart Rate Variability) organic micro-fluctuations (+/- 1.3
        // BPM)
        float hrvJitter = (float) (Math.sin(player.tickCount * 0.18) * 0.85 + Math.cos(player.tickCount * 0.42) * 0.45);
        currentBpm = Math.max(RESTING_BPM - 1.5f, Math.min(MAX_BPM, currentBpm + (hrvJitter * 0.12f)));

        setHeartRate(player, currentBpm);
    }

    /**
     * Spikes player heart rate when taking damage.
     */
    public static void onDamageTaken(Player player, float damageAmount) {
        float currentBpm = getHeartRate(player);
        float gainFactor = getGainFactor(player);
        float spike = (15.0f + (damageAmount * 1.5f)) * gainFactor;
        setHeartRate(player, currentBpm + spike);
    }

    /**
     * Calculates inaccuracy multiplier based on current heart rate.
     * 60 BPM -> 1.0x (No extra inaccuracy)
     * 180 BPM -> 3.5x (+250% extra spread)
     */
    public static float getInaccuracyMultiplier(Player player) {
        float currentBpm = getHeartRate(player);
        float stressRatio = Math.max(0.0f, (currentBpm - RESTING_BPM) / (MAX_BPM - RESTING_BPM));
        return 1.0f + (stressRatio * 3.5f); // Smoothly scales from 1.0x to 4.5x
    }
}
