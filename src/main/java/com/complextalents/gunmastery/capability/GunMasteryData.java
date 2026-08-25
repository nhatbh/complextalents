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
            applyStatRewards();
            sync();
        }
    }

    public void applyStatRewards() {
        if (player == null || player.level().isClientSide) return;

        for (GunType type : GunType.values()) {
            if (type.isGlobal() || type == GunType.RPG) continue;
            int level = getMasteryLevel(type);

            switch (type) {
                case PISTOL -> {
                    double damage = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "gun_damage", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "pistol_damage"), "Pistol Mastery Damage", damage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double reload = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "reload_speed", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.RELOAD_SPEED.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "pistol_reload"), "Pistol Mastery Reload", reload, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                }
                case SNIPER -> {
                    double headshot = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "headshot_multiplier", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.HEADSHOT_MULTIPLIER.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "sniper_headshot"), "Sniper Mastery Headshot", headshot, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double pierce = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "pierce_multiplier", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.PIERCE_MULTIPLIER.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "sniper_pierce"), "Sniper Mastery Pierce", pierce, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                }
                case RIFLE -> {
                    double damage = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "gun_damage", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "rifle_damage"), "Rifle Mastery Damage", damage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double fort = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "fortitude", level);
                    updateModifier(com.complextalents.registry.ModAttributes.FORTITUDE.get(), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "rifle_fortitude"), "Rifle Mastery Fortitude", fort, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
                }
                case SHOTGUN -> {
                    double hipDamage = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "hip_fire_damage", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.HIP_FIRE_DAMAGE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "shotgun_hip_damage"), "Shotgun Mastery Hip Damage", hipDamage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double ammoSave = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "ammo_save_chance", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.AMMO_SAVE_CHANCE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "shotgun_ammo_save"), "Shotgun Mastery Ammo Save", ammoSave, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                }
                case SMG -> {
                    double damage = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "gun_damage", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "smg_damage"), "SMG Mastery Damage", damage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double rpm = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "rpm_multiplier", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.RPM_MULTIPLIER.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "smg_rpm"), "SMG Mastery RPM", rpm, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                }
                case MG -> {
                    double damage = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "gun_damage", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "mg_damage"), "MG Mastery Damage", damage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                    double mag = com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(type, "magazine_capacity", level);
                    updateModifier(com.complextalents.tacz.GunAttributeType.MAGAZINE_CAPACITY.get(type), com.complextalents.util.UUIDHelper.generateAttributeModifierUUID("gun_mastery", "mg_mag_cap"), "MG Mastery Mag Cap", mag, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE);
                }
            }
        }
    }

    private void updateModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, java.util.UUID uuid, String name, double amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        if (attribute == null || player == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
            if (amount != 0) {
                instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(uuid, name, amount, operation));
            }
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

        if (player != null && !player.level().isClientSide) {
            applyStatRewards();
        }
    }
}
