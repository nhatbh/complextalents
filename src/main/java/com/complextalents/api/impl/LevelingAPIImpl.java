package com.complextalents.api.impl;

import com.complextalents.api.leveling.ILevelingAPI;
import com.complextalents.leveling.data.LevelStats;
import com.complextalents.leveling.events.level.PlayerXPResetEvent;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.service.LevelingService;
import net.minecraft.server.level.ServerPlayer;

public class LevelingAPIImpl implements ILevelingAPI {

    @Override
    public int getLevel(ServerPlayer player) {
        return LevelingService.getInstance().getLevel(player);
    }

    @Override
    public double getCurrentXP(ServerPlayer player) {
        return LevelingService.getInstance().getCurrentXP(player);
    }

    @Override
    public double getTotalXP(ServerPlayer player) {
        return LevelingService.getInstance().getTotalXP(player);
    }

    @Override
    public int getTotalSkillPoints(ServerPlayer player) {
        return LevelingService.getInstance().getTotalSkillPoints(player);
    }

    @Override
    public int getConsumedSkillPoints(ServerPlayer player) {
        return LevelingService.getInstance().getConsumedSkillPoints(player);
    }

    @Override
    public int getAvailableSkillPoints(ServerPlayer player) {
        return LevelingService.getInstance().getAvailableSkillPoints(player);
    }

    @Override
    public boolean consumeSkillPoints(ServerPlayer player, int amount) {
        return LevelingService.getInstance().consumeSkillPoints(player, amount);
    }

    @Override
    public boolean awardXP(ServerPlayer player, double amount, XPSource source, XPContext context) {
        return LevelingService.getInstance().awardXP(player, amount, source, context);
    }

    @Override
    public boolean awardXP(ServerPlayer player, double amount, String customReason) {
        XPContext context = XPContext.builder()
                .source(XPSource.API)
                .chunkPos(player.chunkPosition())
                .rawAmount(amount)
                .metadata("reason", customReason != null ? customReason : "API XP Award")
                .build();
        return LevelingService.getInstance().awardXP(player, amount, XPSource.API, context);
    }

    @Override
    public LevelStats getStats(ServerPlayer player) {
        return LevelingService.getInstance().getStats(player);
    }

    @Override
    public void resetCurrentXP(ServerPlayer player, PlayerXPResetEvent.ResetReason reason) {
        LevelingService.getInstance().resetCurrentXP(player, reason);
    }

    @Override
    public void setTotalXP(ServerPlayer player, double newTotalXP) {
        LevelingService.getInstance().setTotalXP(player, newTotalXP);
    }
}
