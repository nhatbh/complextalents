package com.complextalents.api.impl;

import com.complextalents.api.weaponmastery.IWeaponMasteryAPI;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class WeaponMasteryAPIImpl implements IWeaponMasteryAPI {

    private Optional<IWeaponMasteryData> getCapability(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).resolve();
    }

    @Override
    public WeaponPath getWeaponPath(ResourceLocation itemId) {
        return WeaponMasteryManager.getInstance().getWeaponPath(itemId);
    }

    @Override
    public WeaponPath getWeaponPath(ItemStack stack) {
        return WeaponMasteryManager.getInstance().getWeaponPath(stack);
    }

    @Override
    public int getRequiredRankValue(ResourceLocation itemId) {
        return WeaponMasteryManager.getInstance().getRequiredRankValue(itemId);
    }

    @Override
    public int getWeaponTier(ItemStack stack) {
        return WeaponMasteryManager.getInstance().getWeaponTier(stack);
    }

    @Override
    public double getAccumulatedDamage(Player player, WeaponPath path) {
        return getCapability(player).map(data -> data.getAccumulatedDamage(path)).orElse(0.0);
    }

    @Override
    public void addAccumulatedDamage(Player player, WeaponPath path, double amount) {
        getCapability(player).ifPresent(data -> {
            data.addAccumulatedDamage(path, amount);
            data.sync();
        });
    }

    @Override
    public int getMasteryLevel(Player player, WeaponPath path) {
        return getCapability(player).map(data -> data.getMasteryLevel(path)).orElse(0);
    }

    @Override
    public void setMasteryLevel(Player player, WeaponPath path, int level) {
        getCapability(player).ifPresent(data -> {
            data.setMasteryLevel(path, level);
            data.sync();
        });
    }

    @Override
    public Map<WeaponPath, Double> getAllAccumulatedDamage(Player player) {
        return getCapability(player).map(IWeaponMasteryData::getAllAccumulatedDamage).orElse(Collections.emptyMap());
    }

    @Override
    public Map<WeaponPath, Integer> getAllMasteryLevels(Player player) {
        return getCapability(player).map(IWeaponMasteryData::getAllMasteryLevels).orElse(Collections.emptyMap());
    }

    @Override
    public int getRequiredPlayerLevelForTier(int targetLevel) {
        return WeaponMasteryManager.getInstance().getRequiredPlayerLevelForTier(targetLevel);
    }

    @Override
    public void registerWeaponOverride(ResourceLocation itemId, WeaponPath path, int requiredRankLevel) {
        WeaponMasteryManager.getInstance().registerWeaponMapping(itemId, path, requiredRankLevel);
    }
}
