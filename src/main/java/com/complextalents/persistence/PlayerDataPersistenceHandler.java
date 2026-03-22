package com.complextalents.persistence;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.data.SoulData;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.impl.elementalmage.ElementalMageData;
import com.complextalents.impl.elementalmage.origin.ElementalMageOrigin;
import com.complextalents.impl.highpriest.data.FaithData;
import com.complextalents.impl.highpriest.origin.HighPriestOrigin;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.passive.capability.PassiveStackDataProvider;
import com.complextalents.skill.capability.SkillDataProvider;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence of player capability data using global SavedData.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class PlayerDataPersistenceHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataPersistenceHandler.class);

    /**
     * Players whose origin-specific data (Souls, Elemental stats, Faith) needs to be synced to the client.
     * The value is the number of ticks remaining to retry the sync until their origin capability is ready.
     */
    private static final Map<UUID, Integer> PENDING_DATA_SYNC = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Data is already attached and loaded via Provider from PlayerPersistentData.
        // We just need to ensure everything is synced to the client.
        syncAllData(player);
        LOGGER.info("[PERSISTENCE] Player {} logged in, data synced from global storage", player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        savePlayerData(player);
        LOGGER.debug("[PERSISTENCE] Saved player data on logout for {}", player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        savePlayerData(player);
        LOGGER.info("[PERSISTENCE] Saved player data on death for {}", player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();

        if (!event.isWasDeath()) {
            savePlayerData(oldPlayer);
            LOGGER.info("[CLONE] Dimension change for {}, original data saved", newPlayer.getUUID());
        }

        // Capabilities are already attached to the NEW entity via Attachment event,
        // which pulls the SAME instances from global storage. We just need to sync.
        syncAllData(newPlayer);
        LOGGER.info("[CLONE] Restored and synced data for {}", newPlayer.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        player.getCapability(OriginDataProvider.ORIGIN_DATA).ifPresent(com.complextalents.origin.capability.IPlayerOriginData::tick);
        player.getCapability(SkillDataProvider.SKILL_DATA).ifPresent(com.complextalents.skill.capability.IPlayerSkillData::tick);

        // Deliver deferred origin data sync — retry for up to 100 ticks (5 seconds) until origin data is ready.
        UUID playerId = player.getUUID();
        if (PENDING_DATA_SYNC.containsKey(playerId)) {
            int remaining = PENDING_DATA_SYNC.get(playerId);
            boolean synced = false;
            
            if (DarkMageOrigin.isDarkMage(player)) {
                SoulData.syncToClient(player);
                synced = true;
            } else if (ElementalMageOrigin.isElementalMage(player)) {
                ElementalMageData.applyAttributeModifiers(player);
                ElementalMageData.syncToClient(player);
                synced = true;
            } else if (HighPriestOrigin.isHighPriest(player)) {
                FaithData.syncToClient(player);
                synced = true;
            }

            if (synced) {
                PENDING_DATA_SYNC.remove(playerId);
                LOGGER.info("[DATA SYNC] Delivered deferred class data sync for {} ({} ticks after login)", player.getScoreboardName(), 100 - remaining);
            } else if (remaining <= 0) {
                PENDING_DATA_SYNC.remove(playerId);
            } else {
                PENDING_DATA_SYNC.put(playerId, remaining - 1);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Backup save every 60 seconds
        if (event.getServer().getTickCount() % 1200 == 0) {
            saveAllOnlinePlayers(event.getServer());
        }
    }

    public static void syncAllData(ServerPlayer player) {
        player.getCapability(OriginDataProvider.ORIGIN_DATA).ifPresent(com.complextalents.origin.capability.IPlayerOriginData::sync);
        player.getCapability(SkillDataProvider.SKILL_DATA).ifPresent(cap -> {
            cap.sync();
            if (cap instanceof com.complextalents.skill.capability.PlayerSkillData psd) psd.syncCooldowns();
        });
        player.getCapability(PassiveStackDataProvider.PASSIVE_STACK_DATA).ifPresent(com.complextalents.passive.capability.IPassiveStackData::sync);
        
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(cap -> {
            if (cap instanceof com.complextalents.weaponmastery.capability.WeaponMasteryData wmd) {
                wmd.applyStatRewards();
            }
            cap.sync();
        });
        
        player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(cap -> {
            if (cap instanceof com.complextalents.stats.capability.GeneralStatsData gsd) {
                gsd.sync(); // This also applies modifiers
            }
        });
        
        player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(com.complextalents.spellmastery.capability.ISpellMasteryData::sync);

        // Origin specific data handlers
        UUID playerId = player.getUUID();
        PlayerPersistentData persistentData = PlayerPersistentData.get(player.getServer());

        // Origin-specific data (Soul, Elemental, Faith) is now persisted automatically 
        // as live objects in PlayerPersistentData. Queue a deferred sync with a 100-tick retry window.
        PENDING_DATA_SYNC.put(player.getUUID(), 100);

        if (ElementalMageOrigin.isElementalMage(player)) {
            ElementalMageData.applyAttributeModifiers(player);
        }

        // Generic skill-specific persistence
        player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).ifPresent(skillCap -> {
            for (ResourceLocation skillId : skillCap.getAllLearnedSkills()) {
                CompoundTag skillTag = persistentData.getSkillCustomData(playerId, skillId.toString());
                if (skillTag != null && !skillTag.isEmpty()) {
                    // This is where individual skills would register their load hooks
                    if (skillId.equals(com.complextalents.impl.warrior.skills.ChallengersRetribution.ID)) {
                        com.complextalents.impl.warrior.skills.ChallengersRetribution.loadData(player, skillTag);
                    }
                }
            }
        });
    }

    private static void savePlayerData(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerPersistentData persistentData = PlayerPersistentData.get(player.getServer());

        // Soul, Elemental, and Faith data are now persisted automatically as live objects
        // in PlayerPersistentData — no manual snapshot needed here.
        
        // Generic skill-specific persistence
        player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).ifPresent(skillCap -> {
            for (ResourceLocation skillId : skillCap.getAllLearnedSkills()) {
                // Individual skills register their save hooks
                if (skillId.equals(com.complextalents.impl.warrior.skills.ChallengersRetribution.ID)) {
                    CompoundTag skillTag = com.complextalents.impl.warrior.skills.ChallengersRetribution.saveData(player);
                    if (!skillTag.isEmpty()) {
                        persistentData.saveSkillCustomData(playerId, skillId.toString(), skillTag);
                    }
                }
            }
        });
        
        // General Data is saved whenever the world saves via SavedData.setDirty()
        persistentData.setDirty();
    }

    private static void saveAllOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            savePlayerData(player);
        }
        LOGGER.debug("[PERSISTENCE] Periodic save completed");
    }
}
