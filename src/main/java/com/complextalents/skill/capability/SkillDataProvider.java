package com.complextalents.skill.capability;

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
 * Forge capability provider for player skill data.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SkillDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPlayerSkillData> SKILL_DATA = CapabilityManager.get(
            new CapabilityToken<>() {}
    );

    public static final ResourceLocation IDENTIFIER =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "skill_data");

    private final Player player;
    private IPlayerSkillData instance;
    private final LazyOptional<IPlayerSkillData> lazy;

    public SkillDataProvider(Player player) {
        this.player = player;
        this.instance = null;
        this.lazy = LazyOptional.of(this::getOrFetchData);
    }

    public SkillDataProvider(IPlayerSkillData instance) {
        this.player = null;
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> instance);
    }

    private IPlayerSkillData getOrFetchData() {
        if (this.instance == null && this.player != null) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                var psd = PlayerPersistentData.get(serverPlayer.getServer()).getSkillData(serverPlayer.getGameProfile().getName());
                psd.setPlayer(serverPlayer);
                this.instance = psd;
            } else {
                this.instance = new PlayerSkillData();
            }
        }
        return this.instance;
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            if (!event.getCapabilities().containsKey(IDENTIFIER)) {
                event.addCapability(IDENTIFIER, new SkillDataProvider(player));
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return SKILL_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return ((PlayerSkillData) getOrFetchData()).serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ((PlayerSkillData) getOrFetchData()).deserializeNBT(nbt);
    }
}
