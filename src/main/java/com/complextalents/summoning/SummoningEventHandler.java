package com.complextalents.summoning;

import com.complextalents.TalentsMod;
import com.complextalents.classification.SpellClassificationManager;
import com.complextalents.effect.ModEffects;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;



@Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SummoningEventHandler {

    /**
     * Auto-detects summoned entities joining the world and applies stat scaling & origin buffs.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof LivingEntity livingSummon) {
            Entity owner = SummoningManager.getOwner(livingSummon);
            if (owner instanceof LivingEntity livingOwner) {
                SummoningManager.applyStatScaling(livingSummon, livingOwner);
            }
        }
    }

    /**
     * Intercepts SpellOnCastEvent for SUMMONING-classified spells to apply immediate resource
     * reservations (max mana reduction for Standard Mage, max HP reduction for Dark Mage).
     * Runs at LOW priority so SpellPowerPenaltyHandler (NORMAL) and MagicRefinementEventHandler (NORMAL)
     * have already applied their penalty/reduction to event.getManaCost(), giving us the true
     * effective cost including spell-power penalty and magic effectiveness.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSummoningSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.level().isClientSide()) return;

        String spellId = event.getSpellId();
        if (spellId == null) return;

        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return;

        if (SpellClassificationManager.getOrAutoClassify(spell) != SpellClassificationManager.SpellType.SUMMONING) return;

        // Apply Summoner's Fatigue effect on cast for 10 seconds (200 ticks)
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.SUMMONERS_FATIGUE.get(), 200, 0, false, true, true));

        // Use the event's already-mutated mana cost: it has been adjusted by
        // SpellPowerPenaltyHandler (penalty + magic effectiveness) and
        // MagicRefinementEventHandler (refinement discount) before this handler runs.
        int manaCost = event.getManaCost();

        SummoningManager.registerSpellSummon(serverPlayer, spellId, manaCost);

        TalentsMod.LOGGER.debug("Summoning spell {} (level {}, effective cost {}) cast by {} — registered resource reservation.",
                spellId, event.getSpellLevel(), manaCost, serverPlayer.getName().getString());
    }

    /**
     * Player tick handler to manage active summon maintenance decay and 60-second recovery timers.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            SummoningManager.tickSummonMaintenance(serverPlayer);
        }
    }

    /**
     * Detects entity death to unregister summons and initiate post-summon recovery.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        SummoningManager.unregisterSummonEntity(event.getEntity());
    }

    /**
     * Detects entity removal/despawn from level to unregister summons and initiate post-summon recovery.
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            SummoningManager.unregisterSummonEntity(livingEntity);
        }
    }

    /**
     * Prevents player-owned summons from selecting players or friendly summons as targets.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        LivingEntity attacker = event.getEntity();

        if (newTarget == null || attacker == null) return;

        // If attacker is a player-owned summon targeting a player
        if (newTarget instanceof Player && SummoningManager.isSummon(attacker)) {
            event.setCanceled(true);
            event.setNewTarget(null);
            return;
        }

        // If attacker is a summon targeting another summon with the same owner
        if (SummoningManager.isSummon(attacker) && SummoningManager.isSummon(newTarget)) {
            Entity ownerAttacker = SummoningManager.getOwner(attacker);
            Entity ownerTarget = SummoningManager.getOwner(newTarget);
            if (ownerAttacker != null && ownerTarget != null && ownerAttacker.getUUID().equals(ownerTarget.getUUID())) {
                event.setCanceled(true);
                event.setNewTarget(null);
            }
        }
    }

    /**
     * Prevents players from left-clicking or targeting any player-owned summon.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof LivingEntity livingTarget) {
            if (SummoningManager.isPlayerSummon(livingTarget)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Complete Friendly Fire Protection: Cancels all attacks between players and player-summoned entities under any circumstances.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        Entity attacker = event.getSource().getEntity();
        Entity directAttacker = event.getSource().getDirectEntity();

        // 1. Prevents summons from attacking players
        if (victim instanceof Player) {
            if (isPlayerOrSummon(attacker) || isPlayerOrSummon(directAttacker)) {
                if (SummoningManager.isPlayerSummon(attacker) || SummoningManager.isPlayerSummon(directAttacker)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 2. Prevents players or other player-summons from attacking any player-summoned entity under any circumstances
        if (SummoningManager.isPlayerSummon(victim)) {
            if (isPlayerOrSummon(attacker) || isPlayerOrSummon(directAttacker)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Secondary safety check on LivingHurtEvent to ensure zero damage from players to player-owned summons under any circumstances.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        Entity attacker = event.getSource().getEntity();
        Entity directAttacker = event.getSource().getDirectEntity();

        // Block all damage to player-owned summons caused by players or player summons
        if (SummoningManager.isPlayerSummon(victim)) {
            if (isPlayerOrSummon(attacker) || isPlayerOrSummon(directAttacker)) {
                event.setCanceled(true);
                event.setAmount(0.0f);
                return;
            }
        }

        // Scale damage dealt by summons when attacking hostile targets
        if (attacker instanceof LivingEntity livingAttacker && SummoningManager.isSummon(livingAttacker)) {
            Entity owner = SummoningManager.getOwner(livingAttacker);
            if (owner instanceof Player) {
                float multiplier = 1.10f;
                event.setAmount(event.getAmount() * multiplier);
            }
        }
    }

    /**
     * Intercepts PoiseDamageEvent at HIGHEST priority to cancel all poise damage
     * between players and player-summoned entities or between friendly summons.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPoiseDamage(com.nhatbh.basedefensev2.api.event.PoiseDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        Entity attacker = event.getAttacker();
        Entity directAttacker = event.getSource() != null ? event.getSource().getDirectEntity() : null;

        // 1. Prevents summons from dealing poise damage to players
        if (victim instanceof Player) {
            if (isPlayerOrSummon(attacker) || isPlayerOrSummon(directAttacker)) {
                if (SummoningManager.isPlayerSummon(attacker) || SummoningManager.isPlayerSummon(directAttacker)) {
                    event.setCanceled(true);
                    event.setAmount(0.0f);
                    return;
                }
            }
        }

        // 2. Prevents players or player-summons from dealing poise damage to any player-summoned entity
        if (SummoningManager.isPlayerSummon(victim)) {
            if (isPlayerOrSummon(attacker) || isPlayerOrSummon(directAttacker)) {
                event.setCanceled(true);
                event.setAmount(0.0f);
                return;
            }
        }

        // 3. Prevents summons with the same owner from dealing poise damage to each other
        if (SummoningManager.isSummon(victim) && attacker instanceof LivingEntity livingAttacker && SummoningManager.isSummon(livingAttacker)) {
            Entity ownerVictim = SummoningManager.getOwner(victim);
            Entity ownerAttacker = SummoningManager.getOwner(livingAttacker);
            if (ownerVictim != null && ownerAttacker != null && ownerVictim.getUUID().equals(ownerAttacker.getUUID())) {
                event.setCanceled(true);
                event.setAmount(0.0f);
            }
        }
    }

    private static boolean isPlayerOrSummon(Entity entity) {
        if (entity == null) return false;
        return entity instanceof Player || SummoningManager.isPlayerSummon(entity);
    }
}
