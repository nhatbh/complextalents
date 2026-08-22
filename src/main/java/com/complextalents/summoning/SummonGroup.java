package com.complextalents.summoning;

import java.util.UUID;

/**
 * Data structure tracking a single summoning spell cast and its associated resource reservation.
 * Stored entirely in the owning player's persistent NBT — no mob NBT required.
 */
public class SummonGroup {
    public final UUID groupId;
    public final UUID ownerUUID;
    public final String spellId;         // ResourceLocation string of the spell that created this reservation
    public final double initialManaCost;
    public final double reservedMaxMana;
    public final double reservedMaxHP;
    public final long spawnGameTime;
    public final boolean isDarkMage;
    public double extraDecayAccrued = 0.0;

    public SummonGroup(UUID groupId, UUID ownerUUID, String spellId, double initialManaCost,
                       double reservedMaxMana, double reservedMaxHP, long spawnGameTime, boolean isDarkMage) {
        this.groupId = groupId;
        this.ownerUUID = ownerUUID;
        this.spellId = spellId != null ? spellId : "unknown";
        this.initialManaCost = initialManaCost;
        this.reservedMaxMana = reservedMaxMana;
        this.reservedMaxHP = reservedMaxHP;
        this.spawnGameTime = spawnGameTime;
        this.isDarkMage = isDarkMage;
    }

    public double getTotalPenalty() {
        return (isDarkMage ? reservedMaxHP : reservedMaxMana) + extraDecayAccrued;
    }
}
