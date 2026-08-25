package com.complextalents.gunmastery.capability;

import com.complextalents.TalentsMod;
import com.complextalents.persistence.PlayerPersistentData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class GunMasteryDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IGunMasteryData> GUN_MASTERY_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation IDENTIFIER = ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "gun_mastery");

    private final Player player;
    private IGunMasteryData gunMasteryData;
    private final LazyOptional<IGunMasteryData> optional;

    public GunMasteryDataProvider(Player player) {
        this.player = player;
        this.gunMasteryData = null;
        this.optional = LazyOptional.of(this::getOrFetchData);
    }

    public GunMasteryDataProvider(Player player, IGunMasteryData data) {
        this.player = player;
        this.gunMasteryData = data;
        this.optional = LazyOptional.of(() -> this.gunMasteryData);
    }

    private IGunMasteryData getOrFetchData() {
        if (this.gunMasteryData == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var data = PlayerPersistentData.get(serverPlayer.getServer()).getGunMasteryData(serverPlayer.getGameProfile().getName());
                data.setPlayer(serverPlayer);
                this.gunMasteryData = data;
            } else {
                this.gunMasteryData = new GunMasteryData(player);
            }
        }
        return this.gunMasteryData;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == GUN_MASTERY_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return getOrFetchData().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getOrFetchData().deserializeNBT(nbt);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(IDENTIFIER)) {
                event.addCapability(IDENTIFIER, new GunMasteryDataProvider(player));
            }
        }
    }
}
