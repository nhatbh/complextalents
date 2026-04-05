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
    private float convergenceCritChance = 0.0f;
    private float convergenceCritDamage = 0.0f;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public float getStat(ElementType element) {
        return stats.getOrDefault(element, 1.0f);
    }


    @Override
    public void setStat(ElementType element, float value) {
        stats.put(element, Math.max(1.0f, value));

        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public Map<ElementType, Float> getAllStats() {
        EnumMap<ElementType, Float> map = new EnumMap<>(ElementType.class);
        map.putAll(stats);
        return map;
    }


    @Override
    public float getConvergenceCritChance() {
        return convergenceCritChance;
    }

    @Override
    public void setConvergenceCritChance(float chance) {
        this.convergenceCritChance = chance;
        markDirty();
    }

    @Override
    public float getConvergenceCritDamage() {
        return convergenceCritDamage;
    }

    @Override
    public void setConvergenceCritDamage(float damage) {
        this.convergenceCritDamage = damage;
        markDirty();
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
        tag.putFloat("convergence_crit_chance", convergenceCritChance);
        tag.putFloat("convergence_crit_damage", convergenceCritDamage);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        stats.clear();
        for (ElementType type : ElementType.values()) {
            if (nbt.contains(type.name())) {
                stats.put(type, Math.max(1.0f, nbt.getFloat(type.name())));
            } else {
                stats.put(type, 1.0f);
            }
        }
        this.convergenceCritChance = nbt.getFloat("convergence_crit_chance");
        this.convergenceCritDamage = nbt.getFloat("convergence_crit_damage");
    }

}
