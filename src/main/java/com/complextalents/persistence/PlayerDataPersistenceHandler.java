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

import java.util.UUID;

/**
 * Handles persistence of player capability data using global SavedData.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class PlayerDataPersistenceHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataPersistenceHandler.class);

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

        if (DarkMageOrigin.isDarkMage(player)) {
            CompoundTag soulTag = persistentData.getDarkMageData(playerId);
            if (soulTag != null) {
                SoulData.deserializeNBT(player, soulTag);
                TalentsMod.LOGGER.info("[SOUL PERSIST] Deserialized {} souls for player {}", 
                    soulTag.getDouble("souls"), playerId);
            }
        }

        if (ElementalMageOrigin.isElementalMage(player)) {
            CompoundTag elementalTag = persistentData.getElementalMageData(playerId);
            if (elementalTag != null) ElementalMageData.deserializeNBT(playerId, elementalTag);
            ElementalMageData.applyAttributeModifiers(player);
            ElementalMageData.syncToClient(player);
        }

        if (HighPriestOrigin.isHighPriest(player)) {
            CompoundTag faithTag = persistentData.getFaithData(playerId);
            if (faithTag != null) FaithData.deserializeNBT(player, faithTag);
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

        // Origin-specific data persistence
        if (DarkMageOrigin.isDarkMage(player)) {
            persistentData.saveDarkMageData(playerId, SoulData.serializeNBT(player));
        }
        if (ElementalMageOrigin.isElementalMage(player)) {
            persistentData.saveElementalMageData(playerId, ElementalMageData.serializeNBT(playerId));
        }
        if (HighPriestOrigin.isHighPriest(player)) {
            persistentData.saveFaithData(playerId, FaithData.serializeNBT(player));
        }
        
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
