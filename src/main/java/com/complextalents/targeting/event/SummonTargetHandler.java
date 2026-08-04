package com.complextalents.targeting.event;

import com.complextalents.TalentsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
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
     * Safety Net Handler: Cancels incoming damage if a summon somehow bypassing
     * target checks attacks a player.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity livingAttacker && isSummonedEntity(livingAttacker)) {
                event.setCanceled(true); // Block the attack
            }
        }
    }

    /**
     * Checks if an entity is owned by a player (via OwnableEntity or persistent NBT
     * owner UUID).
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
            if (ownable.getOwnerUUID() != null && entity.level().getPlayerByUUID(ownable.getOwnerUUID()) != null) {
                return true;
            }
        }

        // 2. Persistent NBT owner UUID check (covers mods using custom owner NBT keys)
        CompoundTag nbt = entity.getPersistentData();
        if (nbt != null) {
            String[] ownerKeys = { "Owner", "OwnerUUID", "SummonerUUID", "MasterUUID", "CasterUUID", "OwnerId" };
            for (String key : ownerKeys) {
                if (nbt.hasUUID(key)) {
                    if (entity.level().getPlayerByUUID(nbt.getUUID(key)) != null) {
                        return true;
                    }
                } else if (nbt.contains(key, CompoundTag.TAG_STRING)) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(nbt.getString(key));
                        if (entity.level().getPlayerByUUID(uuid) != null) {
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
