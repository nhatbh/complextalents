package com.complextalents.summoning;

import java.util.UUID;

/**
 * Data structure tracking post-summon 60-second resource recovery.
 */
public class ResourceRecoveryInstance {
    public final UUID ownerUUID;
    public final boolean isDarkMage;
    public final double totalAmountToRecover;
    public int remainingTicks;
    public final int totalTicks;

    public ResourceRecoveryInstance(UUID ownerUUID, boolean isDarkMage, double totalAmountToRecover, int totalTicks) {
        this.ownerUUID = ownerUUID;
        this.isDarkMage = isDarkMage;
        this.totalAmountToRecover = totalAmountToRecover;
        this.remainingTicks = totalTicks;
        this.totalTicks = totalTicks;
    }

    public double getCurrentPenalty() {
        if (remainingTicks <= 0 || totalTicks <= 0) return 0.0;
        return totalAmountToRecover * ((double) remainingTicks / totalTicks);
    }
}
