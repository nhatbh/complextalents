package com.complextalents.api.impl;

import com.complextalents.api.origin.IOriginAPI;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.ResourceType;
import com.complextalents.origin.capability.IPlayerOriginData;
import com.complextalents.origin.capability.OriginDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public class OriginAPIImpl implements IOriginAPI {

    private Optional<IPlayerOriginData> getCapability(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(OriginDataProvider.ORIGIN_DATA).resolve();
    }

    @Override
    public ResourceLocation getActiveOrigin(Player player) {
        return getCapability(player).map(IPlayerOriginData::getActiveOrigin).orElse(null);
    }

    @Override
    public void setActiveOrigin(Player player, ResourceLocation originId) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (originId == null) {
                OriginManager.clearOrigin(serverPlayer);
            } else {
                OriginManager.setOrigin(serverPlayer, originId);
            }
        } else {
            getCapability(player).ifPresent(data -> {
                data.setActiveOrigin(originId);
                data.sync();
            });
        }
    }

    @Override
    public int getOriginLevel(Player player) {
        return getCapability(player).map(IPlayerOriginData::getOriginLevel).orElse(1);
    }

    @Override
    public void setOriginLevel(Player player, int level) {
        if (player instanceof ServerPlayer serverPlayer) {
            OriginManager.setOriginLevel(serverPlayer, Math.max(1, Math.min(5, level)));
        } else {
            getCapability(player).ifPresent(data -> {
                data.setOriginLevel(Math.max(1, Math.min(5, level)));
                data.sync();
            });
        }
    }

    @Override
    public ResourceType getResourceType(Player player) {
        return getCapability(player).map(IPlayerOriginData::getResourceType).orElse(null);
    }

    @Override
    public double getResource(Player player) {
        return getCapability(player).map(IPlayerOriginData::getResource).orElse(0.0);
    }

    @Override
    public void setResource(Player player, double value) {
        getCapability(player).ifPresent(data -> {
            data.setResource(value);
            data.sync();
        });
    }

    @Override
    public void modifyResource(Player player, double delta) {
        getCapability(player).ifPresent(data -> {
            data.modifyResource(delta);
            data.sync();
        });
    }

    @Override
    public void clearOrigin(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            OriginManager.clearOrigin(serverPlayer);
        } else {
            getCapability(player).ifPresent(data -> {
                data.clear();
                data.sync();
            });
        }
    }
}
