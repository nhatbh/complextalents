package com.complextalents.weaponmastery.persistence;

import com.complextalents.TalentsMod;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class WeaponMasteryPersistenceHandler {

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player clone = event.getEntity();

        original.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(oldData -> {
            clone.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(newData -> {
                CompoundTag nbt = oldData.serializeNBT();
                newData.deserializeNBT(nbt);
                
                // Re-apply stats on clone (since deserialization might not do it depending on timing if clientSide)
                if (newData instanceof com.complextalents.weaponmastery.capability.WeaponMasteryData wmd) {
                    wmd.applyStatRewards();
                }
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(IWeaponMasteryData::sync);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(IWeaponMasteryData::sync);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(IWeaponMasteryData::sync);
        }
    }
}
