package com.complextalents.weaponmastery.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.complextalents.TalentsMod;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class WeaponMasteryDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IWeaponMasteryData> WEAPON_MASTERY_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation IDENTIFIER = ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "weapon_mastery");

    private IWeaponMasteryData weaponMasteryData = null;
    private final LazyOptional<IWeaponMasteryData> optional = LazyOptional.of(this::createWeaponMasteryData);

    private final Player player;

    public WeaponMasteryDataProvider(Player player) {
        this.player = player;
    }

    private IWeaponMasteryData createWeaponMasteryData() {
        if (this.weaponMasteryData == null) {
            this.weaponMasteryData = new WeaponMasteryData(player);
        }
        return this.weaponMasteryData;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == WEAPON_MASTERY_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return createWeaponMasteryData().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createWeaponMasteryData().deserializeNBT(nbt);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(IDENTIFIER)) {
                event.addCapability(IDENTIFIER, new WeaponMasteryDataProvider(player));
            }
        }
    }
}
