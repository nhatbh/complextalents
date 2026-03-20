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

    private final IPlayerSkillData instance;
    private final LazyOptional<IPlayerSkillData> lazy;

    public SkillDataProvider(IPlayerSkillData instance) {
        this.instance = instance;
        this.lazy = LazyOptional.of(() -> instance);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            final PlayerSkillData instance;
            if (player instanceof ServerPlayer serverPlayer) {
                instance = PlayerPersistentData.get(serverPlayer.getServer()).getSkillData(serverPlayer.getUUID());
                instance.setPlayer(serverPlayer);
            } else {
                instance = new PlayerSkillData();
            }

            SkillDataProvider provider = new SkillDataProvider(instance);
            event.addCapability(IDENTIFIER, provider);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return SKILL_DATA.orEmpty(cap, lazy);
    }

    @Override
    public CompoundTag serializeNBT() {
        return ((PlayerSkillData) instance).serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ((PlayerSkillData) instance).deserializeNBT(nbt);
    }
}
