package com.complextalents.impl.highpriest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent data for the High Priest origin.
 * Stores uncapped Faith stacks.
 */
public class PlayerFaithData implements IPlayerFaithData {

    private Player player;
    private double faith = 0.0;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public double getFaith() {
        return faith;
    }

    @Override
    public void setFaith(double faith) {
        this.faith = Math.max(0.0, faith);
        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public void addFaith(double amount) {
        setFaith(this.faith + amount);
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            FaithData.syncToClient(serverPlayer);
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
        tag.putDouble("faith", faith);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("faith")) {
            this.faith = nbt.getDouble("faith");
        }
    }
}
