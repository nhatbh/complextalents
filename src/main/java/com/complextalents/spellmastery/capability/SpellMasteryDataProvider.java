package com.complextalents.spellmastery.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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
import com.complextalents.persistence.PlayerPersistentData;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellMasteryDataProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<ISpellMasteryData> MASTERY_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation IDENTIFIER = ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "spell_mastery");

    private final Player player;
    private ISpellMasteryData masteryData;
    private final LazyOptional<ISpellMasteryData> optional;

    public SpellMasteryDataProvider(Player player) {
        this.player = player;
        this.masteryData = null;
        this.optional = LazyOptional.of(this::getOrFetchData);
    }

    public SpellMasteryDataProvider(Player player, ISpellMasteryData data) {
        this.player = player;
        this.masteryData = data;
        this.optional = LazyOptional.of(() -> masteryData);
    }

    private ISpellMasteryData getOrFetchData() {
        if (this.masteryData == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var data = PlayerPersistentData.get(serverPlayer.getServer()).getSpellMasteryData(serverPlayer.getGameProfile().getName());
                data.setPlayer(serverPlayer);
                this.masteryData = data;
            } else {
                this.masteryData = new SpellMasteryData(player);
            }
        }
        return this.masteryData;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MASTERY_DATA) {
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
                event.addCapability(IDENTIFIER, new SpellMasteryDataProvider(player));
            }
        }
    }
}
