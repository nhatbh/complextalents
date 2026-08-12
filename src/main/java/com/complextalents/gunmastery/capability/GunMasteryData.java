package com.complextalents.gunmastery.capability;

import com.complextalents.network.PacketHandler;
import com.complextalents.tacz.GunType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class GunMasteryData implements IGunMasteryData {

    private Player player;
    private final Map<GunType, Double> accumulatedDamageMap = new HashMap<>();
    private final Map<GunType, Integer> masteryLevelsMap = new HashMap<>();

    public GunMasteryData() {
        for (GunType type : GunType.values()) {
            if (!type.isGlobal()) {
                accumulatedDamageMap.put(type, 0.0);
                masteryLevelsMap.put(type, 0);
            }
        }
    }

    public GunMasteryData(Player player) {
        this();
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public double getAccumulatedDamage(GunType type) {
        if (type == null) return 0.0;
        return accumulatedDamageMap.getOrDefault(type, 0.0);
    }

    @Override
    public void addAccumulatedDamage(GunType type, double amount) {
        if (type == null || type.isGlobal() || amount <= 0) return;
        double current = accumulatedDamageMap.getOrDefault(type, 0.0);
        accumulatedDamageMap.put(type, current + amount);
        if (player != null && !player.level().isClientSide) {
            sync();
        }
    }

    @Override
    public int getMasteryLevel(GunType type) {
        if (type == null) return 0;
        return masteryLevelsMap.getOrDefault(type, 0);
    }

    @Override
    public void setMasteryLevel(GunType type, int level) {
        if (type == null || type.isGlobal()) return;
        masteryLevelsMap.put(type, Math.max(0, Math.min(20, level)));
        if (player != null && !player.level().isClientSide) {
            sync();
        }
    }

    @Override
    public Map<GunType, Double> getAllAccumulatedDamage() {
        return new HashMap<>(accumulatedDamageMap);
    }

    @Override
    public Map<GunType, Integer> getAllMasteryLevels() {
        return new HashMap<>(masteryLevelsMap);
    }

    @Override
    public void copyFrom(IGunMasteryData other) {
        if (other == null) return;
        this.accumulatedDamageMap.clear();
        this.accumulatedDamageMap.putAll(other.getAllAccumulatedDamage());
        this.masteryLevelsMap.clear();
        this.masteryLevelsMap.putAll(other.getAllMasteryLevels());
    }

    @Override
    public void reset() {
        for (GunType type : GunType.values()) {
            if (!type.isGlobal()) {
                accumulatedDamageMap.put(type, 0.0);
                masteryLevelsMap.put(type, 0);
            }
        }
    }


    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketHandler.sendTo(new com.complextalents.gunmastery.network.GunMasterySyncPacket(serializeNBT()), serverPlayer);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        CompoundTag damageNbt = new CompoundTag();
        for (Map.Entry<GunType, Double> entry : accumulatedDamageMap.entrySet()) {
            damageNbt.putDouble(entry.getKey().name(), entry.getValue());
        }
        nbt.put("AccumulatedDamage", damageNbt);

        CompoundTag levelsNbt = new CompoundTag();
        for (Map.Entry<GunType, Integer> entry : masteryLevelsMap.entrySet()) {
            levelsNbt.putInt(entry.getKey().name(), entry.getValue());
        }
        nbt.put("MasteryLevels", levelsNbt);

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("AccumulatedDamage")) {
            CompoundTag damageNbt = nbt.getCompound("AccumulatedDamage");
            for (String key : damageNbt.getAllKeys()) {
                try {
                    GunType type = GunType.valueOf(key);
                    accumulatedDamageMap.put(type, damageNbt.getDouble(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (nbt.contains("MasteryLevels")) {
            CompoundTag levelsNbt = nbt.getCompound("MasteryLevels");
            for (String key : levelsNbt.getAllKeys()) {
                try {
                    GunType type = GunType.valueOf(key);
                    masteryLevelsMap.put(type, Math.min(20, levelsNbt.getInt(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
