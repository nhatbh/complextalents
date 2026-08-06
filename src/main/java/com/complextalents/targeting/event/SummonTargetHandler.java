package com.complextalents.targeting.event;

import com.complextalents.TalentsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SummonTargetHandler {

    /**
     * Primary Handler: Prevents summoned entities from selecting players as attack
     * targets.
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();

        // Check if the target being acquired is any Player
        if (newTarget instanceof Player) {
            LivingEntity attacker = event.getEntity();

            // If the attacker is a summoned/owned entity, cancel the targeting
            if (isSummonedEntity(attacker)) {
                event.setCanceled(true); // Prevents the AI goal from setting target
                event.setNewTarget(null); // Forces target to null
            }
        }
    }

    /**
     * Prevents players from manually left-clicking player-summoned entities.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof LivingEntity livingTarget && isSummonedEntity(livingTarget)) {
            event.setCanceled(true); // Cancels player left-click melee attack
        }
    }

    /**
     * Safety Net Handler: Cancels damage between players and player-summoned entities in both directions.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        // 1. Prevents summons from attacking players
        if (victim instanceof Player) {
            if (attacker instanceof LivingEntity livingAttacker && isSummonedEntity(livingAttacker)) {
                event.setCanceled(true); // Block summon -> player damage
            }
        }

        // 2. Prevents players or player-summons from attacking player-summoned mobs
        if (isSummonedEntity(victim)) {
            if (attacker instanceof Player || (attacker instanceof LivingEntity livingAttacker && isSummonedEntity(livingAttacker))) {
                event.setCanceled(true); // Block player/summon -> summon damage
            }
        }
    }

    /**
     * Checks if an entity is owned by a player (via OwnableEntity or persistent NBT owner UUID).
     * Prevents player-summoned entities from targeting players while allowing
     * hostile mob summons to work normally.
     */
    public static boolean isSummonedEntity(LivingEntity entity) {
        if (entity == null)
            return false;

        // 1. Standard OwnableEntity interface check
        if (entity instanceof OwnableEntity ownable) {
            if (ownable.getOwner() instanceof Player) {
                return true;
            }
            if (ownable.getOwnerUUID() != null) {
                if (entity.level().getPlayerByUUID(ownable.getOwnerUUID()) != null) {
                    return true;
                }
                if (entity.getServer() != null && entity.getServer().getPlayerList().getPlayer(ownable.getOwnerUUID()) != null) {
                    return true;
                }
            }
        }

        // 2. Persistent NBT owner UUID check (covers mods using custom owner NBT keys)
        CompoundTag nbt = entity.getPersistentData();
        if (nbt != null) {
            String[] ownerKeys = { 
                "Owner", "OwnerUUID", "SummonerUUID", "MasterUUID", "CasterUUID", "OwnerId",
                "casterUUID", "caster_uuid", "summoner_uuid", "owner_uuid"
            };
            for (String key : ownerKeys) {
                if (nbt.hasUUID(key)) {
                    java.util.UUID uuid = nbt.getUUID(key);
                    if (entity.level().getPlayerByUUID(uuid) != null) {
                        return true;
                    }
                    if (entity.getServer() != null && entity.getServer().getPlayerList().getPlayer(uuid) != null) {
                        return true;
                    }
                } else if (nbt.contains(key, CompoundTag.TAG_STRING)) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(nbt.getString(key));
                        if (entity.level().getPlayerByUUID(uuid) != null) {
                            return true;
                        }
                        if (entity.getServer() != null && entity.getServer().getPlayerList().getPlayer(uuid) != null) {
                            return true;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        return false;
    }
}
