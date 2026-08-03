package com.complextalents.dps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class managing an individual player's DPS tracking session.
 */
public class DPSSession {

    public enum State {
        ARMED,
        ACTIVE,
        FINISHED
    }

    public static class DamageRecord {
        public final long timestampMs;
        public final float damage;

        public DamageRecord(long timestampMs, float damage) {
            this.timestampMs = timestampMs;
            this.damage = damage;
        }
    }

    private final Player player;
    private State state = State.ARMED;
    private long startTimeMs = 0;
    private long lastDamageTimeMs = 0;

    private float totalDamage = 0f;
    private int hitCount = 0;
    private float minHit = Float.MAX_VALUE;
    private float maxHit = 0f;

    private final List<DamageRecord> damageRecords = new ArrayList<>();

    public DPSSession(Player player) {
        this.player = player;
    }

    public State getState() {
        return state;
    }

    public Player getPlayer() {
        return player;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getLastDamageTimeMs() {
        return lastDamageTimeMs;
    }

    public float getTotalDamage() {
        return totalDamage;
    }

    public int getHitCount() {
        return hitCount;
    }

    /**
     * Records a hit into the session.
     */
    public synchronized void recordDamage(float amount, long currentTimeMs) {
        if (state == State.FINISHED) {
            return;
        }

        if (state == State.ARMED) {
            state = State.ACTIVE;
            startTimeMs = currentTimeMs;
        }

        lastDamageTimeMs = currentTimeMs;
        totalDamage += amount;
        hitCount++;
        if (amount < minHit) {
            minHit = amount;
        }
        if (amount > maxHit) {
            maxHit = amount;
        }

        damageRecords.add(new DamageRecord(currentTimeMs, amount));
    }

    /**
     * Checks if active session has timed out (3 seconds without damage).
     */
    public boolean isExpired(long currentTimeMs, long timeoutMs) {
        return state == State.ACTIVE && (currentTimeMs - lastDamageTimeMs >= timeoutMs);
    }

    /**
     * Force-finishes the session.
     */
    public synchronized void finish() {
        if (state != State.FINISHED) {
            state = State.FINISHED;
        }
    }

    /**
     * Compiles and sends formatted DPS report to the player and returns formatted string for log.
     */
    public String reportAndSend() {
        finish();

        if (hitCount == 0) {
            MutableComponent msg = Component.literal("[DPS Meter] ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Session ended with 0 damage dealt.").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(msg);
            return "Player " + player.getName().getString() + " ended DPS session with 0 damage.";
        }

        long durationMs = Math.max(1000L, lastDamageTimeMs - startTimeMs);
        float durationSeconds = durationMs / 1000.0f;
        float avgDps = totalDamage / durationSeconds;
        float avgHit = totalDamage / hitCount;
        float actualMinHit = minHit == Float.MAX_VALUE ? 0f : minHit;

        // Calculate 1-second bucket peak DPS
        Map<Integer, Float> secondBuckets = new HashMap<>();
        for (DamageRecord record : damageRecords) {
            int bucket = (int) Math.max(0, (record.timestampMs - startTimeMs) / 1000L);
            secondBuckets.put(bucket, secondBuckets.getOrDefault(bucket, 0f) + record.damage);
        }

        float highPeakDps = 0f;
        float lowPeakDps = Float.MAX_VALUE;

        // Include all buckets from 0 to max bucket
        int maxBucket = (int) Math.max(0, (lastDamageTimeMs - startTimeMs) / 1000L);
        for (int i = 0; i <= maxBucket; i++) {
            float bucketDps = secondBuckets.getOrDefault(i, 0f);
            if (bucketDps > highPeakDps) {
                highPeakDps = bucketDps;
            }
            if (bucketDps < lowPeakDps) {
                lowPeakDps = bucketDps;
            }
        }
        if (lowPeakDps == Float.MAX_VALUE) {
            lowPeakDps = 0f;
        }

        // Build player chat message component
        MutableComponent header = Component.literal("========== [ DPS METER REPORT ] ==========\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        MutableComponent body = Component.empty()
                .append(Component.literal(" Duration: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1fs\n", durationSeconds)).withStyle(ChatFormatting.WHITE))
                
                .append(Component.literal(" Total Damage: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f\n", totalDamage)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                
                .append(Component.literal(" Average DPS: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f\n", avgDps)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                
                .append(Component.literal(" High Peak DPS: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f/s\n", highPeakDps)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                
                .append(Component.literal(" Low Peak DPS: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f/s\n", lowPeakDps)).withStyle(ChatFormatting.GRAY))
                
                .append(Component.literal(" Total Hits: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%d hits\n", hitCount)).withStyle(ChatFormatting.WHITE))
                
                .append(Component.literal(" Avg Hit Damage: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f\n", avgHit)).withStyle(ChatFormatting.WHITE))
                
                .append(Component.literal(" Min / Max Hit: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("%.1f / %.1f\n", actualMinHit, maxHit)).withStyle(ChatFormatting.WHITE));

        MutableComponent footer = Component.literal("===========================================")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        player.sendSystemMessage(header.append(body).append(footer));

        // Format plain text log output
        return String.format(
                "DPS Report for %s | Duration: %.1fs | Total Dmg: %.1f | Avg DPS: %.1f | High Peak: %.1f | Low Peak: %.1f | Hits: %d | Avg Hit: %.1f | Min/Max Hit: %.1f/%.1f",
                player.getName().getString(), durationSeconds, totalDamage, avgDps, highPeakDps, lowPeakDps, hitCount, avgHit, actualMinHit, maxHit
        );
    }
}
