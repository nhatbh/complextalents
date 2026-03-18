package com.complextalents.stats;

import net.minecraft.network.chat.Component;

/**
 * Represents a stat that scales with level.
 * Separates the internal key from the display name.
 */
public record ScaledStat(Component displayName, double[] values) {
    public ScaledStat(String displayName, double[] values) {
        this(Component.literal(displayName), values);
    }

    public double getValue(int level) {
        if (values == null || values.length == 0) return 0.0;
        int index = Math.min(Math.max(level - 1, 0), values.length - 1);
        return values[index];
    }
}
