package com.complextalents.impl.darkmage.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player soul data stored as a live instance in {@link com.complextalents.persistence.PlayerPersistentData}.
 * Uses the same pattern as GeneralStatsData so that world autosave always serializes the current state.
 */
public class PlayerSoulData implements IPlayerSoulData {

    private Player player;
    private double souls = 0.0;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public double getSouls() {
        return souls;
    }

    @Override
    public void setSouls(double souls) {
        this.souls = Math.max(0.0, souls);
        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public void addSouls(double amount) {
        setSouls(this.souls + amount);
    }

    @Override
    public double loseSouls(double percentage) {
        double current = this.souls;
        double toLose = current * percentage;
        setSouls(current - toLose);
        return toLose;
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            SoulData.syncToClient(serverPlayer);
        }
    }

    private void markDirty() {
        if (player instanceof ServerPlayer serverPlayer) {
            com.complextalents.persistence.PlayerPersistentData.get(serverPlayer.getServer()).setDirty();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("souls", souls);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("souls")) {
            souls = Math.max(0.0, tag.getDouble("souls"));
        }
    }
}
