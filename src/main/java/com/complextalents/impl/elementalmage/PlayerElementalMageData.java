package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent data for the Elemental Mage origin.
 * Stores elemental power levels for all schools.
 */
public class PlayerElementalMageData implements IPlayerElementalMageData {

    private Player player;
    private final Map<ElementType, Float> stats = new ConcurrentHashMap<>();

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public float getStat(ElementType element) {
        return stats.getOrDefault(element, 0.0f);
    }

    @Override
    public void setStat(ElementType element, float value) {
        stats.put(element, Math.max(0.0f, value));
        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public Map<ElementType, Float> getAllStats() {
        return new EnumMap<>(stats);
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            ElementalMageData.syncToClient(serverPlayer);
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
        for (Map.Entry<ElementType, Float> entry : stats.entrySet()) {
            tag.putFloat(entry.getKey().name(), entry.getValue());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        stats.clear();
        for (ElementType type : ElementType.values()) {
            if (nbt.contains(type.name())) {
                stats.put(type, nbt.getFloat(type.name()));
            }
        }
    }
}
