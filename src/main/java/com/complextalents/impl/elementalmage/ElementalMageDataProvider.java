package com.complextalents.impl.elementalmage;

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
 * Provider for the Elemental Mage stats data capability.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ElementalMageDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPlayerElementalMageData> ELEMENTAL_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    private static final ResourceLocation CAPABILITY_ID =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "elemental_mage_data");

    private final Player player;
    private IPlayerElementalMageData instance;
    private final LazyOptional<IPlayerElementalMageData> lazy;

    public ElementalMageDataProvider(Player player) {
        this.player = player;
        this.instance = null;
        this.lazy = LazyOptional.of(this::getOrFetchData);
    }

    public ElementalMageDataProvider(IPlayerElementalMageData instance) {
        this.player = null;
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> this.instance);
    }

    private IPlayerElementalMageData getOrFetchData() {
        if (this.instance == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var data = PlayerPersistentData.get(serverPlayer.getServer()).getElementalData(serverPlayer.getGameProfile().getName());
                data.setPlayer(serverPlayer);
                this.instance = data;
            } else {
                var data = new PlayerElementalMageData();
                data.setPlayer(player);
                this.instance = data;
            }
        }
        return this.instance;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return ELEMENTAL_DATA.orEmpty(cap, lazy);
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
                event.addCapability(CAPABILITY_ID, new ElementalMageDataProvider(player));
            }
        }
    }
}
