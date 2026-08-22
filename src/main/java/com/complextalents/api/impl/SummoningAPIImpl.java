package com.complextalents.api.impl;

import com.complextalents.api.summoning.ISummoningAPI;
import com.complextalents.summoning.SummoningManager;
import com.complextalents.impl.highpriest.entity.SeraphsEdgeEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class SummoningAPIImpl implements ISummoningAPI {

    @Override
    public boolean isSummon(Entity entity) {
        if (entity instanceof SeraphsEdgeEntity) {
            return true;
        }
        return SummoningManager.isSummon(entity);
    }

    @Override
    public boolean isFriendlySummon(Entity entity) {
        if (entity instanceof SeraphsEdgeEntity echo) {
            return echo.getOwner() instanceof Player;
        }
        return SummoningManager.isPlayerSummon(entity);
    }

    @Override
    public @Nullable Entity getOwner(Entity entity) {
        if (entity instanceof SeraphsEdgeEntity echo) {
            return echo.getOwner();
        }
        if (entity instanceof LivingEntity living) {
            return SummoningManager.getOwner(living);
        }
        return null;
    }
}
