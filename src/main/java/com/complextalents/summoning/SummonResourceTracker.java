package com.complextalents.summoning;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.impl.mage.MageManaRegenHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.*;

/**
 * Tracks resource reservations inside player NBT.
 *
 * <p>Rule: On summoning spell cast, inflicts {@link ModEffects#SUMMONERS_FATIGUE} for 10 seconds (200 ticks).
 * As long as the player has active summons, the effect is continuously refreshed to 10 seconds.
 * When summons are gone, the effect ticks down and expires naturally over 10 seconds.
 * Only when {@link ModEffects#SUMMONERS_FATIGUE} expires do active reservations transition into 60-second recovery instances.</p>
 */
public class SummonResourceTracker {

    private static final String MAIN_TAG = "CT_SummonData";
    private static final String GROUPS_TAG = "SummonGroups";
    private static final String RECOVERIES_TAG = "Recoveries";

    public static final int FATIGUE_DURATION_TICKS = 200; // 10 seconds

    public static boolean isIronSpellbooksLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    // -------------------------------------------------------------------------
    // NBT Load / Save
    // -------------------------------------------------------------------------

    public static List<SummonGroup> loadGroups(Player player) {
        List<SummonGroup> list = new ArrayList<>();
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(MAIN_TAG)) return list;

        CompoundTag data = persistent.getCompound(MAIN_TAG);
        if (!data.contains(GROUPS_TAG)) return list;

        ListTag groupList = data.getList(GROUPS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            CompoundTag gTag = groupList.getCompound(i);
            try {
                UUID groupId = UUID.fromString(gTag.getString("GroupId"));
                String spellId = gTag.contains("SpellId") ? gTag.getString("SpellId") : "unknown";
                double initialManaCost = gTag.getDouble("InitialManaCost");
                double reservedMaxMana = gTag.getDouble("ReservedMaxMana");
                double reservedMaxHP = gTag.getDouble("ReservedMaxHP");
                long spawnGameTime = gTag.getLong("SpawnGameTime");
                boolean isDarkMage = gTag.getBoolean("IsDarkMage");
                double extraDecayAccrued = gTag.getDouble("ExtraDecayAccrued");

                SummonGroup group = new SummonGroup(groupId, player.getUUID(), spellId,
                        initialManaCost, reservedMaxMana, reservedMaxHP, spawnGameTime, isDarkMage);
                group.extraDecayAccrued = extraDecayAccrued;
                list.add(group);
            } catch (Exception e) {
                TalentsMod.LOGGER.error("Failed to parse SummonGroup NBT for player {}", player.getName().getString(), e);
            }
        }
        return list;
    }

    public static void saveGroups(Player player, List<SummonGroup> groups) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag mainCompound = persistent.contains(MAIN_TAG)
                ? persistent.getCompound(MAIN_TAG) : new CompoundTag();

        ListTag groupList = new ListTag();
        for (SummonGroup g : groups) {
            CompoundTag gTag = new CompoundTag();
            gTag.putString("GroupId", g.groupId.toString());
            gTag.putString("SpellId", g.spellId);
            gTag.putDouble("InitialManaCost", g.initialManaCost);
            gTag.putDouble("ReservedMaxMana", g.reservedMaxMana);
            gTag.putDouble("ReservedMaxHP", g.reservedMaxHP);
            gTag.putLong("SpawnGameTime", g.spawnGameTime);
            gTag.putBoolean("IsDarkMage", g.isDarkMage);
            gTag.putDouble("ExtraDecayAccrued", g.extraDecayAccrued);
            groupList.add(gTag);
        }

        mainCompound.put(GROUPS_TAG, groupList);
        persistent.put(MAIN_TAG, mainCompound);
    }

    public static List<ResourceRecoveryInstance> loadRecoveries(Player player) {
        List<ResourceRecoveryInstance> list = new ArrayList<>();
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(MAIN_TAG)) return list;

        CompoundTag data = persistent.getCompound(MAIN_TAG);
        if (!data.contains(RECOVERIES_TAG)) return list;

        ListTag recList = data.getList(RECOVERIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < recList.size(); i++) {
            CompoundTag rTag = recList.getCompound(i);
            try {
                boolean isDarkMage = rTag.getBoolean("IsDarkMage");
                double totalAmountToRecover = rTag.getDouble("TotalAmountToRecover");
                int remainingTicks = rTag.getInt("RemainingTicks");
                int totalTicks = rTag.getInt("TotalTicks");

                ResourceRecoveryInstance rec = new ResourceRecoveryInstance(player.getUUID(), isDarkMage, totalAmountToRecover, totalTicks);
                rec.remainingTicks = remainingTicks;
                list.add(rec);
            } catch (Exception e) {
                TalentsMod.LOGGER.error("Failed to parse ResourceRecoveryInstance NBT for player {}", player.getName().getString(), e);
            }
        }
        return list;
    }

    public static void saveRecoveries(Player player, List<ResourceRecoveryInstance> recoveries) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag mainCompound = persistent.contains(MAIN_TAG)
                ? persistent.getCompound(MAIN_TAG) : new CompoundTag();

        ListTag recList = new ListTag();
        for (ResourceRecoveryInstance r : recoveries) {
            CompoundTag rTag = new CompoundTag();
            rTag.putBoolean("IsDarkMage", r.isDarkMage);
            rTag.putDouble("TotalAmountToRecover", r.totalAmountToRecover);
            rTag.putInt("RemainingTicks", r.remainingTicks);
            rTag.putInt("TotalTicks", r.totalTicks);
            recList.add(rTag);
        }

        mainCompound.put(RECOVERIES_TAG, recList);
        persistent.put(MAIN_TAG, mainCompound);
    }

    // -------------------------------------------------------------------------
    // Core Logic
    // -------------------------------------------------------------------------

    /**
     * Called when a summoning spell is cast.
     * Creates a per-spell reservation record in player NBT and applies Summoner's Fatigue effect for 10 seconds.
     *
     * @param spellId       Spell identifier.
     * @param spellManaCost The fully-adjusted mana cost.
     */
    public static void registerSpellSummon(ServerPlayer owner, String spellId, int spellManaCost) {
        if (owner == null) return;

        boolean isDark = DarkMageOrigin.isDarkMage(owner);
        boolean isStandardMage = MageManaRegenHandler.isMage(owner);

        // Non-mage origins don't use resource reservations
        if (!isDark && !isStandardMage) return;

        double reservedMana = isDark ? 0.0 : StandardMageSummonHandler.calculateReservedMana(spellManaCost);
        double reservedHP   = isDark ? DarkMageSummonHandler.calculateReservedHP(owner, spellManaCost) : 0.0;

        UUID groupId = UUID.randomUUID();
        long now = owner.level().getGameTime();

        SummonGroup group = new SummonGroup(groupId, owner.getUUID(), spellId,
                spellManaCost, reservedMana, reservedHP, now, isDark);

        List<SummonGroup> groups = loadGroups(owner);
        groups.add(group);
        saveGroups(owner, groups);

        recalculateAndApplyModifiers(owner);

        TalentsMod.LOGGER.debug("Registered spell summon record [{}] for player {} (Dark={}, ReservedMana={}, ReservedHP={})",
                spellId, owner.getName().getString(), isDark, reservedMana, reservedHP);
    }

    /**
     * Called when a summoned entity dies or leaves the level.
     */
    public static void unregisterSummonEntity(LivingEntity entity) {
        // Handled via tickSummonMaintenance and Summoner's Fatigue effect expiry
    }

    /**
     * Server-side tick (every 20 ticks) for maintenance decay and recovery ticking.
     */
    public static void tickSummonMaintenance(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.tickCount % 20 != 0) return;

        List<SummonGroup> activeGroups = loadGroups(player);
        List<ResourceRecoveryInstance> recoveries = loadRecoveries(player);

        boolean downed = SummoningManager.isDowned(player);
        boolean isDark = DarkMageOrigin.isDarkMage(player);

        // 1. Downed state check: unsummon all active summons if downed
        if (downed && !activeGroups.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDowned! Ritual severed, summons unsummoned."), true);
            SummoningManager.dismissAllSummons(player);
            return;
        }

        boolean groupsModified = false;
        long currentTime = player.level().getGameTime();

        if (!activeGroups.isEmpty()) {
            List<LivingEntity> activeSummons = SummoningManager.getSummons(player);

            if (!activeSummons.isEmpty()) {
                // Player still has active summons — refresh Summoner's Fatigue effect for 10 seconds (200 ticks)
                player.addEffect(new MobEffectInstance(ModEffects.SUMMONERS_FATIGUE.get(), FATIGUE_DURATION_TICKS, 0, false, true, true));

                // Maintenance decay while summons are alive (starts after 30s)
                boolean stdModified  = StandardMageSummonHandler.tickMaintenance(player, activeGroups, downed, currentTime);
                boolean darkModified = DarkMageSummonHandler.tickMaintenance(player, activeGroups, downed, currentTime);
                groupsModified = stdModified || darkModified;

                // Threshold forced despawn
                if (isDark) {
                    if (DarkMageSummonHandler.checkDespawnThreshold(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cHealth Critical! Dark summons collapsed."), true);
                        SummoningManager.dismissAllSummons(player);
                        return;
                    }
                } else {
                    if (StandardMageSummonHandler.checkDespawnThreshold(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cMax Mana Depleted! Summons despawned."), true);
                        SummoningManager.dismissAllSummons(player);
                        return;
                    }
                }
            } else {
                // No active summons — check if Summoner's Fatigue effect has expired
                if (!player.hasEffect(ModEffects.SUMMONERS_FATIGUE.get())) {
                    // Effect has expired — transition all active groups to 60-second recovery instances
                    for (SummonGroup g : activeGroups) {
                        double totalPenalty = g.getTotalPenalty();
                        if (totalPenalty > 0.01) {
                            recoveries.add(new ResourceRecoveryInstance(player.getUUID(), g.isDarkMage, totalPenalty, 1200));
                        }
                    }
                    activeGroups.clear();
                    saveGroups(player, activeGroups);
                    saveRecoveries(player, recoveries);
                    recalculateAndApplyModifiers(player);
                    TalentsMod.LOGGER.debug("Summoner's Fatigue expired for {} — moved all reservation records to 60-second recovery.",
                            player.getName().getString());
                    return;
                }
            }
        }

        // 5. Tick recovery instances (60-second recovery) — ONLY if Summoner's Fatigue is NOT active on the player
        boolean recoveriesModified = false;
        if (!player.hasEffect(ModEffects.SUMMONERS_FATIGUE.get()) && !recoveries.isEmpty()) {
            Iterator<ResourceRecoveryInstance> it = recoveries.iterator();
            while (it.hasNext()) {
                ResourceRecoveryInstance rec = it.next();
                rec.remainingTicks -= 20;
                recoveriesModified = true;
                if (rec.remainingTicks <= 0) it.remove();
            }
        }

        if (groupsModified) saveGroups(player, activeGroups);
        if (recoveriesModified) saveRecoveries(player, recoveries);

        // 6. Re-apply updated attribute modifiers
        recalculateAndApplyModifiers(player);
    }

    /**
     * Recalculates and applies active Max Mana / Max HP attribute modifier penalties.
     */
    public static void recalculateAndApplyModifiers(ServerPlayer player) {
        if (player == null) return;

        List<SummonGroup> groups = loadGroups(player);
        List<ResourceRecoveryInstance> recoveries = loadRecoveries(player);

        double totalManaPenalty = 0.0;
        double totalHPPenalty   = 0.0;

        for (SummonGroup g : groups) {
            if (g.isDarkMage) totalHPPenalty   += g.getTotalPenalty();
            else              totalManaPenalty += g.getTotalPenalty();
        }
        for (ResourceRecoveryInstance r : recoveries) {
            if (r.isDarkMage) totalHPPenalty   += r.getCurrentPenalty();
            else              totalManaPenalty += r.getCurrentPenalty();
        }

        StandardMageSummonHandler.applyManaModifiers(player, totalManaPenalty);
        DarkMageSummonHandler.applyHPModifiers(player, totalHPPenalty);
    }

    /**
     * Immediately transitions all active records to 60-second recovery on explicit player dismissal.
     */
    public static void handleDismissal(ServerPlayer player) {
        if (player == null) return;

        // Remove fatigue effect on explicit dismissal
        if (player.hasEffect(ModEffects.SUMMONERS_FATIGUE.get())) {
            player.removeEffect(ModEffects.SUMMONERS_FATIGUE.get());
        }

        List<SummonGroup> groups = loadGroups(player);
        List<ResourceRecoveryInstance> recoveries = loadRecoveries(player);

        for (SummonGroup g : groups) {
            double totalPenalty = g.getTotalPenalty();
            if (totalPenalty > 0.01) {
                recoveries.add(new ResourceRecoveryInstance(player.getUUID(), g.isDarkMage, totalPenalty, 1200));
            }
        }

        groups.clear();
        saveGroups(player, groups);
        saveRecoveries(player, recoveries);
        recalculateAndApplyModifiers(player);
    }
}
