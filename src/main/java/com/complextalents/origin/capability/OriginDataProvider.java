package com.complextalents.origin.capability;

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

/**
 * Forge capability provider for player origin data.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class OriginDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPlayerOriginData> ORIGIN_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    public static final ResourceLocation IDENTIFIER =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "origin_data");

    private final IPlayerOriginData instance;
    private final LazyOptional<IPlayerOriginData> lazy;

    public OriginDataProvider(IPlayerOriginData instance) {
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> instance);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            final PlayerOriginData instance;
            if (player instanceof ServerPlayer serverPlayer) {
                instance = PlayerPersistentData.get(serverPlayer.getServer()).getOriginData(serverPlayer.getUUID());
                instance.setPlayer(serverPlayer);
            } else {
                instance = new PlayerOriginData();
            }

            LazyOptional<IPlayerOriginData> lazyOptional = LazyOptional.of(() -> instance);
            OriginDataProvider provider = new OriginDataProvider(instance);
            event.addCapability(IDENTIFIER, provider);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return ORIGIN_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return ((PlayerOriginData) instance).serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ((PlayerOriginData) instance).deserializeNBT(nbt);
    }
}
