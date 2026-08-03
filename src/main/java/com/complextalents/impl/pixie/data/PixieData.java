package com.complextalents.impl.pixie.data;

import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;

public class PixieData {

    private boolean isPixie = false;
    private UUID hostUUID = null;
    private Integer hostEntityId = null;
    private boolean isAttached = false;
    private Vec3 relativeOffset = new Vec3(0, 1.8, 0);
    private int hostCombatTimer = 0; // ticks remaining for in-combat mana regen
    private int silenceTicks = 0;
    private int idleTicks = 0;
    private Vec3 prevPos = Vec3.ZERO;
    private double pixieDust = 100.0; // Resource max 100 for casting Fae Surge
    public static final double MAX_PIXIE_DUST = 100.0;

    public double getPixieDust() {
        return pixieDust;
    }

    public void setPixieDust(double pixieDust) {
        this.pixieDust = Math.min(MAX_PIXIE_DUST, Math.max(0.0, pixieDust));
    }

    public boolean consumePixieDust(double amount) {
        if (this.pixieDust >= amount) {
            this.pixieDust -= amount;
            return true;
        }
        return false;
    }

    public void regenPixieDust(double amount) {
        this.pixieDust = Math.min(MAX_PIXIE_DUST, this.pixieDust + amount);
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    public void incrementIdleTicks() {
        this.idleTicks++;
    }

    public void resetIdleTicks() {
        this.idleTicks = 0;
    }

    public Vec3 getPrevPos() {
        return prevPos;
    }

    public void setPrevPos(Vec3 prevPos) {
        this.prevPos = prevPos;
    }

    public Optional<Integer> getHostEntityId() {
        return Optional.ofNullable(hostEntityId);
    }

    public void setHostEntityId(Integer hostEntityId) {
        this.hostEntityId = hostEntityId;
    }

    public boolean isPixie() {
        return isPixie;
    }

    public void setPixie(boolean pixie) {
        this.isPixie = pixie;
    }

    public Optional<UUID> getHostUUID() {
        return Optional.ofNullable(hostUUID);
    }

    public void setHostUUID(UUID hostUUID) {
        this.hostUUID = hostUUID;
    }

    public boolean isAttached() {
        return isAttached;
    }

    public void setAttached(boolean attached) {
        this.isAttached = attached;
    }

    public Vec3 getRelativeOffset() {
        return relativeOffset;
    }

    public void setRelativeOffset(Vec3 relativeOffset) {
        this.relativeOffset = relativeOffset;
    }

    public int getHostCombatTimer() {
        return hostCombatTimer;
    }

    public void setHostCombatTimer(int hostCombatTimer) {
        this.hostCombatTimer = hostCombatTimer;
    }

    public void tickCombatTimer() {
        if (this.hostCombatTimer > 0) {
            this.hostCombatTimer--;
        }
    }

    public int getSilenceTicks() {
        return silenceTicks;
    }

    public void setSilenceTicks(int silenceTicks) {
        this.silenceTicks = silenceTicks;
    }

    public void tickSilence() {
        if (this.silenceTicks > 0) {
            this.silenceTicks--;
        }
    }

    public void resetAttachment() {
        this.hostUUID = null;
        this.hostEntityId = null;
        this.isAttached = false;
        this.idleTicks = 0;
        this.prevPos = Vec3.ZERO;
        this.relativeOffset = new Vec3(0, 1.8, 0);
    }

    public void resetAll() {
        this.isPixie = false;
        this.hostUUID = null;
        this.hostEntityId = null;
        this.isAttached = false;
        this.hostCombatTimer = 0;
        this.silenceTicks = 0;
        this.idleTicks = 0;
        this.prevPos = Vec3.ZERO;
        this.relativeOffset = new Vec3(0, 1.8, 0);
    }
}
