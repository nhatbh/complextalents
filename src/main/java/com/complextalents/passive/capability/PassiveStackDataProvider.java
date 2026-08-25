package com.complextalents.passive.capability;

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
 * Forge capability provider for passive stack data.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class PassiveStackDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPassiveStackData> PASSIVE_STACK_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    public static final ResourceLocation IDENTIFIER =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "passive_stack_data");

    private final Player player;
    private IPassiveStackData instance;
    private final LazyOptional<IPassiveStackData> lazy;

    public PassiveStackDataProvider(Player player) {
        this.player = player;
        this.instance = null;
        this.lazy = LazyOptional.of(this::getOrFetchData);
    }

    public PassiveStackDataProvider(IPassiveStackData instance) {
        this.player = null;
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> instance);
    }

    private IPassiveStackData getOrFetchData() {
        if (this.instance == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var psd = PlayerPersistentData.get(serverPlayer.getServer()).getPassiveData(serverPlayer.getGameProfile().getName());
                psd.setPlayer(serverPlayer);
                this.instance = psd;
            } else {
                this.instance = new PassiveStackData();
            }
        }
        return this.instance;
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(IDENTIFIER)) {
                event.addCapability(IDENTIFIER, new PassiveStackDataProvider(player));
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return PASSIVE_STACK_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return ((PassiveStackData) getOrFetchData()).serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ((PassiveStackData) getOrFetchData()).deserializeNBT(nbt);
    }
}
