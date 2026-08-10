package com.complextalents.api.impl;

import com.complextalents.api.stats.IStatsAPI;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatModifierApplier;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.stats.capability.IGeneralStatsData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StatsAPIImpl implements IStatsAPI {

    private Optional<IGeneralStatsData> getCapability(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(GeneralStatsDataProvider.STATS_DATA).resolve();
    }

    @Override
    public int getStatRank(Player player, StatType statType) {
        return getCapability(player).map(data -> data.getStatRank(statType)).orElse(0);
    }

    @Override
    public void setStatRank(Player player, StatType statType, int rank) {
        getCapability(player).ifPresent(data -> {
            data.setStatRank(statType, Math.max(0, rank));
            data.sync();
        });
    }

    @Override
    public int getOriginStatRank(Player player, StatType statType) {
        return getCapability(player).map(data -> data.getOriginStatRank(statType)).orElse(0);
    }

    @Override
    public void setOriginStatRank(Player player, StatType statType, int rank) {
        getCapability(player).ifPresent(data -> {
            data.setOriginStatRank(statType, Math.max(0, rank));
            data.sync();
        });
    }

    @Override
    public int getTotalStatRank(Player player, StatType statType) {
        return getStatRank(player, statType) + getOriginStatRank(player, statType);
    }

    @Override
    public Map<StatType, Integer> getAllStatRanks(Player player) {
        return getCapability(player).map(IGeneralStatsData::getAllRanks).orElse(Collections.emptyMap());
    }

    @Override
    public Map<StatType, Integer> getAllOriginStatRanks(Player player) {
        return getCapability(player).map(IGeneralStatsData::getAllOriginRanks).orElse(Collections.emptyMap());
    }

    @Override
    public int getHighestCombatPower(Player player) {
        return getCapability(player).map(IGeneralStatsData::getHighestCombatPower).orElse(0);
    }

    @Override
    public void setHighestCombatPower(Player player, int combatPower) {
        getCapability(player).ifPresent(data -> {
            data.setHighestCombatPower(Math.max(0, combatPower));
            data.sync();
        });
    }

    @Override
    public int getSPCostPerRank(Player player, StatType statType) {
        if (player instanceof ServerPlayer serverPlayer) {
            ResourceLocation originId = OriginManager.getOriginId(serverPlayer);
            if (originId != null) {
                return ClassCostMatrix.getCost(originId, statType);
            }
        }
        return 4; // Default cost if no origin
    }

    @Override
    public void reapplyAllModifiers(Player player) {
        getCapability(player).ifPresent(data -> {
            Map<StatType, Integer> combined = new HashMap<>();
            for (StatType type : StatType.values()) {
                combined.put(type, data.getStatRank(type) + data.getOriginStatRank(type));
            }
            StatModifierApplier.applyAll(player, combined);
        });
    }

    @Override
    public void resetStatsToOrigin(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ResourceLocation originId = OriginManager.getOriginId(serverPlayer);
            resetStatsToOrigin(player, originId);
        } else {
            resetStatsToOrigin(player, (ResourceLocation) null);
        }
    }

    @Override
    public void resetStatsToOrigin(Player player, ResourceLocation originId) {
        getCapability(player).ifPresent(data -> {
            // 1. Reset all purchased ranks and origin base ranks to 0
            for (StatType type : StatType.values()) {
                data.setStatRank(type, 0);
                data.setOriginStatRank(type, 0);
            }

            // 2. Apply baseline origin ranks if origin exists
            if (originId != null) {
                Origin origin = OriginRegistry.getInstance().getOrigin(originId);
                if (origin != null) {
                    origin.getBaseStats().forEach((type, rank) -> data.setOriginStatRank(type, rank));
                }
            }

            // 3. Sync to client and reapply attribute modifiers
            data.sync();
            reapplyAllModifiers(player);
        });
    }
}
