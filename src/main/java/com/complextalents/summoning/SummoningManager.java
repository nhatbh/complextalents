package com.complextalents.summoning;

import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * Central facade manager for controlling and querying summoned entities across ComplexTalents.
 * Delegates stat scaling to {@link SummonStatScaler} and resource/maintenance tracking to {@link SummonResourceTracker}.
 */
public class SummoningManager {

    public static boolean isIronSpellbooksLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    /**
     * Checks if an entity is a downed player (via basedefensev2:downed MobEffect).
     */
    public static boolean isDowned(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getActiveEffects().stream().anyMatch(effect -> {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
            return id != null && "basedefensev2".equals(id.getNamespace()) && "downed".equals(id.getPath());
        });
    }

    /**
     * Checks if an entity is a summoned or owned entity.
     */
    public static boolean isSummon(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return getOwner(living) != null;
    }

    /**
     * Checks if an entity is a summon owned specifically by a player.
     */
    public static boolean isPlayerSummon(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return getOwner(living) instanceof Player;
    }

    /**
     * Checks if an entity is either a player summon OR owned by a player summon.
     */
    public static boolean isPlayerSummonOrOwnedByPlayerSummon(Entity entity) {
        if (entity == null) return false;
        if (isPlayerSummon(entity)) return true;
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner != null && isPlayerSummon(owner)) return true;
        }
        return false;
    }

    /**
     * Retrieves the owner of a given entity.
     */
    public static @Nullable Entity getOwner(LivingEntity entity) {
        if (entity == null) return null;

        // 1. Check Iron's Spellbooks IMagicSummon interface & SummonManager
        if (isIronSpellbooksLoaded()) {
            try {
                if (entity instanceof IMagicSummon magicSummon) {
                    Entity summoner = magicSummon.getSummoner();
                    if (summoner != null) return summoner;
                }
                Entity ironOwner = SummonManager.getOwner(entity);
                if (ironOwner != null) return ironOwner;
            } catch (Throwable ignored) {}
        }

        // 2. Check Vanilla OwnableEntity
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null) return owner;

            UUID ownerUUID = ownable.getOwnerUUID();
            if (ownerUUID != null && entity.level() instanceof ServerLevel serverLevel) {
                Entity found = serverLevel.getEntity(ownerUUID);
                if (found != null) return found;
                if (serverLevel.getServer() != null) {
                    Player player = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
                    if (player != null) return player;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves all active summoned entities belonging to an owner in the level.
     */
    public static List<LivingEntity> getSummons(LivingEntity owner) {
        List<LivingEntity> result = new ArrayList<>();
        if (owner == null || !(owner.level() instanceof ServerLevel serverLevel)) return result;

        if (isIronSpellbooksLoaded()) {
            try {
                Set<UUID> summonUUIDs = SummonManager.getSummons(owner);
                if (summonUUIDs != null) {
                    for (UUID uuid : summonUUIDs) {
                        Entity ent = serverLevel.getEntity(uuid);
                        if (ent instanceof LivingEntity living && living.isAlive()) {
                            result.add(living);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        double radius = 64.0;
        AABB box = owner.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner);

        for (LivingEntity entity : nearby) {
            if (!result.contains(entity)) {
                Entity foundOwner = getOwner(entity);
                if (foundOwner != null && foundOwner.getUUID().equals(owner.getUUID())) {
                    result.add(entity);
                }
            }
        }

        return result;
    }

    /**
     * Registers a single summoned entity to an owner.
     */
    public static void registerSummon(LivingEntity summon, LivingEntity owner, int durationTicks) {
        if (summon == null || owner == null) return;

        if (isIronSpellbooksLoaded()) {
            try {
                SummonManager.setOwner(summon, owner);
                if (durationTicks > 0) {
                    SummonManager.setDuration(summon, durationTicks);
                }
            } catch (Throwable ignored) {}
        }

        applyStatScaling(summon, owner);
    }

    // --- Delegate Methods for Stat Scaling ---

    public static double getRawLinearSpellPower(LivingEntity owner) {
        return SummonStatScaler.getRawLinearSpellPower(owner);
    }

    public static double calculateSummonPowerFactor(LivingEntity owner) {
        return SummonStatScaler.calculateSummonPowerFactor(owner);
    }

    public static void applyStatScaling(LivingEntity summon, LivingEntity owner) {
        SummonStatScaler.applyStatScaling(summon, owner);
    }

    // --- Delegate Methods for Resource Tracking & Maintenance ---

    public static void registerSpellSummon(ServerPlayer owner, String spellId, int spellManaCost) {
        SummonResourceTracker.registerSpellSummon(owner, spellId, spellManaCost);
    }

    public static void unregisterSummonEntity(LivingEntity entity) {
        SummonResourceTracker.unregisterSummonEntity(entity);
    }

    public static void tickSummonMaintenance(ServerPlayer player) {
        SummonResourceTracker.tickSummonMaintenance(player);
    }

    public static void recalculateAndApplyModifiers(ServerPlayer player) {
        SummonResourceTracker.recalculateAndApplyModifiers(player);
    }

    /**
     * Despawns / dismisses all active summons belonging to an owner and transitions their penalties into 60s recoveries.
     */
    public static int dismissAllSummons(LivingEntity owner) {
        List<LivingEntity> summons = getSummons(owner);
        int dismissedCount = 0;

        for (LivingEntity summon : summons) {
            if (isIronSpellbooksLoaded() && summon instanceof IMagicSummon magicSummon) {
                magicSummon.onUnSummon();
            } else {
                summon.discard();
            }
            dismissedCount++;
        }

        if (owner instanceof ServerPlayer serverPlayer) {
            SummonResourceTracker.handleDismissal(serverPlayer);
        }

        return dismissedCount;
    }
}
