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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge capability provider for player origin data.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class OriginDataProvider implements ICapabilitySerializable<CompoundTag> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginDataProvider.class);

    public static final Capability<IPlayerOriginData> ORIGIN_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    public static final ResourceLocation IDENTIFIER =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "origin_data");

    private final Player player;
    private IPlayerOriginData instance;
    private final LazyOptional<IPlayerOriginData> lazy;

    public OriginDataProvider(Player player) {
        this.player = player;
        this.instance = null;
        this.lazy = LazyOptional.of(this::getOrFetchData);
    }

    public OriginDataProvider(IPlayerOriginData instance) {
        this.player = null;
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> instance);
    }

    private IPlayerOriginData getOrFetchData() {
        if (this.instance == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var pod = PlayerPersistentData.get(serverPlayer.getServer()).getOriginData(serverPlayer.getGameProfile().getName());
                pod.setPlayer(serverPlayer);
                this.instance = pod;
                LOGGER.info("[ORIGIN LAZY-LOAD] Player {} ({}) - retrieved origin: activeOrigin={}, level={}",
                        serverPlayer.getGameProfile().getName(),
                        serverPlayer.getUUID(),
                        pod.getActiveOrigin(),
                        pod.getOriginLevel());
            } else {
                this.instance = new PlayerOriginData();
            }
        }
        return this.instance;
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(IDENTIFIER)) {
                event.addCapability(IDENTIFIER, new OriginDataProvider(player));
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return ORIGIN_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return ((PlayerOriginData) getOrFetchData()).serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ((PlayerOriginData) getOrFetchData()).deserializeNBT(nbt);
    }
}
