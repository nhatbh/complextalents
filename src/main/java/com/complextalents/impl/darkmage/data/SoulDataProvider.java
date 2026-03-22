package com.complextalents.impl.darkmage.data;

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
 * Provider for the Dark Mage soul data capability.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SoulDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPlayerSoulData> SOUL_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    private static final ResourceLocation CAPABILITY_ID =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "soul_data");

    private final IPlayerSoulData instance;
    private final LazyOptional<IPlayerSoulData> lazy;

    public SoulDataProvider(IPlayerSoulData instance) {
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> this.instance);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return SOUL_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.deserializeNBT(nbt);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof ServerPlayer player) {
            var data = PlayerPersistentData.get(player.getServer()).getSoulData(player.getUUID());
            data.setPlayer(player);
            event.addCapability(CAPABILITY_ID, new SoulDataProvider(data));
        } else if (event.getObject() instanceof Player player) {
            var data = new PlayerSoulData();
            data.setPlayer(player);
            event.addCapability(CAPABILITY_ID, new SoulDataProvider(data));
        }
    }
}
