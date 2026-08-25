package com.complextalents.stats.capability;

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
 * Provider for the General Stats capability.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class GeneralStatsDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IGeneralStatsData> STATS_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    private static final ResourceLocation CAPABILITY_ID =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "general_stats");

    private final Player player;
    private IGeneralStatsData instance;
    private final LazyOptional<IGeneralStatsData> lazy;

    public GeneralStatsDataProvider(Player player) {
        this.player = player;
        this.instance = null;
        this.lazy = LazyOptional.of(this::getOrFetchData);
    }

    public GeneralStatsDataProvider(Player player, IGeneralStatsData instance) {
        this.player = player;
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> this.instance);
    }

    private IGeneralStatsData getOrFetchData() {
        if (this.instance == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var data = PlayerPersistentData.get(serverPlayer.getServer()).getGeneralStatsData(serverPlayer.getGameProfile().getName());
                data.setPlayer(serverPlayer);
                this.instance = data;
            } else {
                this.instance = new GeneralStatsData(player);
            }
        }
        return this.instance;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return STATS_DATA.orEmpty(cap, lazy);
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
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(CAPABILITY_ID)) {
                event.addCapability(CAPABILITY_ID, new GeneralStatsDataProvider(player));
            }
        }
    }
}
