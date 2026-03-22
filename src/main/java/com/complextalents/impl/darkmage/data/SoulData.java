package com.complextalents.impl.darkmage.data;

import com.complextalents.TalentsMod;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.darkmage.SoulSyncPacket;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.integration.SpellCritAttributes;
import com.complextalents.persistence.PlayerPersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side tracking for Dark Mage Soul stacks.
 * Souls are UNCAPPED - no maximum limit.
 * Gained from kills: 3 × √(HP/10) - 5 with ±10% variance
 * Lost: 30% on actual death (Phylactery trigger is free)
 *
 * <p>Soul counts are stored in {@link PlayerSoulData} instances inside {@link PlayerPersistentData},
 * using the same live-object pattern as {@link com.complextalents.stats.capability.GeneralStatsData}.
 * This means Minecraft's world autosave always serializes the latest value — no manual snapshot
 * / flush required.</p>
 *
 * <p>Non-persistent session state (Phylactery cooldown, Blood Pact active flag, multipliers)
 * is still held in static maps as before — these are intentionally ephemeral.</p>
 */
public class SoulData {

    // --- Non-persistent session state (intentionally ephemeral) ---

    // Phylactery cooldown tracking (game time when cooldown expires)
    private static final ConcurrentHashMap<UUID, Long> PHYLACTERY_COOLDOWN = new ConcurrentHashMap<>();

    // Blood Pact active state tracking
    private static final ConcurrentHashMap<UUID, Boolean> BLOOD_PACT_ACTIVE = new ConcurrentHashMap<>();

    // Multipliers for Blood Pact (Drain and Soul Effect)
    private static final ConcurrentHashMap<UUID, Float> BLOOD_PACT_DRAIN_MULTIPLIER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Float> BLOOD_PACT_SOUL_MULTIPLIER = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Soul accessors — delegate to PlayerPersistentData (same pattern as stats)
    // -------------------------------------------------------------------------

    /**
     * Get the current soul count for a player.
     * Checks the PlayerSoulData capability first (new robust system).
     */
    public static double getSouls(Player player) {
        return player.getCapability(SoulDataProvider.SOUL_DATA)
                .map(IPlayerSoulData::getSouls)
                .orElse(0.0);
    }

    /**
     * Get the current soul count as a double via UUID.
     * Convenience read-only overload — only use when a ServerPlayer reference is unavailable.
     */
    public static double getSouls(UUID playerUuid) {
        // This overload is kept for call sites that only have a UUID (e.g. BloodPactSkill helpers).
        // Since we no longer maintain a static map, we need the server here.
        // MinecraftServer is always accessible in a server-side context via ServerLifecycleHooks.
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0.0;
        // Fallback to PlayerPersistentData for UUID-only access if capability isn't directly accessible
        // (e.g., if player is offline or capability not yet attached/loaded for some reason).
        // This should ideally be replaced with a proper capability lookup via PlayerList.
        return PlayerPersistentData.get(server).getSoulData(playerUuid).getSouls();
    }

    /**
     * Get the current soul count floored to integer (for display/packet purposes).
     */
    public static int getSoulsInt(UUID playerUuid) {
        return (int) getSouls(playerUuid);
    }

    /**
     * Get the current soul count floored to integer (for display/packet purposes).
     */
    public static int getSoulsInt(Player player) {
        return (int) getSouls(player);
    }

    /**
     * Set soul count for a player and sync to client.
     */
    public static void setSouls(Player player, double souls) {
        player.getCapability(SoulDataProvider.SOUL_DATA).ifPresent(cap -> {
            cap.setSouls(souls);
            TalentsMod.LOGGER.info("[SOUL DATA] Souls set to {} for player {}", souls, player.getScoreboardName());
            if (player instanceof ServerPlayer serverPlayer) {
                syncToClient(serverPlayer);
            }
        });
    }

    /**
     * Set soul count for a player by UUID.
     * This is a fallback and should be used with caution, as it requires fetching the player.
     */
    public static void setSouls(UUID playerUuid, double souls) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            setSouls(player, souls);
        } else {
            // If player is offline, update persistent data directly (capability won't be loaded)
            PlayerPersistentData persistentData = PlayerPersistentData.get(server);
            persistentData.getSoulData(playerUuid).setSouls(souls);
            persistentData.setDirty();
            TalentsMod.LOGGER.info("[SOUL DATA] Souls set to {} for offline player {}", souls, playerUuid);
        }
    }

    /**
     * Add souls to a player's count and sync to client.
     */
    public static void addSouls(Player player, double amount) {
        player.getCapability(SoulDataProvider.SOUL_DATA).ifPresent(cap -> {
            cap.addSouls(amount);
            TalentsMod.LOGGER.info("[SOUL DATA] Added {} souls to {}, new total: {}",
                    amount, player.getScoreboardName(), cap.getSouls());
            if (player instanceof ServerPlayer serverPlayer) {
                syncToClient(serverPlayer);
            }
        });
    }

    /**
     * Add souls to a player's count by UUID.
     */
    public static void addSouls(UUID playerUuid, double amount) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            addSouls(player, amount);
        } else {
            // If player is offline, update persistent data directly
            PlayerPersistentData persistentData = PlayerPersistentData.get(server);
            persistentData.getSoulData(playerUuid).addSouls(amount);
            persistentData.setDirty();
            TalentsMod.LOGGER.info("[SOUL DATA] Added {} souls to offline player {}, new total: {}",
                    amount, playerUuid, persistentData.getSoulData(playerUuid).getSouls());
        }
    }

    /**
     * Calculate souls gained from killing a mob based on its max health.
     * Two-phase formula with ±10% variance:
     * - Phase 1 (HP < 40): Linear scaling HP/40 to ensure weak mobs give souls
     * - Phase 2 (HP >= 40): Root formula 3 × √(HP/10) - 5
     *   - Anchored at 40 HP ≈ 1 soul (0.9-1.1)
     *   - Crossover with HP/40 at 1000 HP ≈ 25 souls (22.5-27.5)
     *   - Generous curve between 40-1000 HP (more than HP/40)
     *   - Harsh brake for high HP mobs (10000 HP ≈ 80-98 souls instead of 250)
     *
     * @param mobMaxHealth The mob's maximum health
     * @return Souls gained (decimal value with ±10% variance, minimum 0)
     */
    public static double calculateSoulsFromKill(double mobMaxHealth) {
        double baseSouls;
        if (mobMaxHealth < 40) {
            // Phase 1: Linear scaling prevents negative numbers for weak mobs
            baseSouls = mobMaxHealth / 40.0;
        } else {
            // Phase 2: The diminishing curve handles the mid-to-endgame economy
            baseSouls = (3.0 * Math.sqrt(mobMaxHealth / 10.0)) - 5.0;
        }
        // Apply ±10% variance (multiplier between 0.9 and 1.1)
        double variance = 0.9 + (ThreadLocalRandom.current().nextDouble() * 0.2);
        return Math.max(0.0, baseSouls * variance);
    }

    /**
     * Lose a percentage of souls and sync to client.
     *
     * @param percentage 0.0–1.0 (e.g. 0.3 = 30%)
     * @return The number of souls lost
     */
    public static double loseSouls(ServerPlayer player, double percentage) {
        PlayerPersistentData persistentData = PlayerPersistentData.get(player.getServer());
        double lost = persistentData.getSoulData(player.getUUID()).loseSouls(percentage);
        persistentData.setDirty();
        TalentsMod.LOGGER.info("[SOUL DATA] Player {} lost {} souls ({}%), remaining: {}", 
            player.getScoreboardName(), lost, percentage * 100, persistentData.getSoulData(player.getUUID()).getSouls());
        syncToClient(player);
        return lost;
    }

    // -------------------------------------------------------------------------
    // Phylactery cooldown (ephemeral — resets on server restart, intentional)
    // -------------------------------------------------------------------------

    /**
     * Check if Phylactery is on cooldown.
     */
    public static boolean isPhylacteryOnCooldown(UUID playerUuid, long currentGameTime) {
        Long cooldownEnd = PHYLACTERY_COOLDOWN.get(playerUuid);
        if (cooldownEnd == null) {
            return false;
        }
        return currentGameTime < cooldownEnd;
    }

    /**
     * Set Phylactery cooldown end time.
     */
    public static void setPhylacteryCooldown(UUID playerUuid, long cooldownEndGameTime) {
        PHYLACTERY_COOLDOWN.put(playerUuid, cooldownEndGameTime);
        TalentsMod.LOGGER.debug("Dark Mage Phylactery cooldown set until game time {} for player {}",
                cooldownEndGameTime, playerUuid);
    }

    /**
     * Get remaining Phylactery cooldown in ticks.
     */
    public static long getPhylacteryCooldownRemaining(UUID playerUuid, long currentGameTime) {
        Long cooldownEnd = PHYLACTERY_COOLDOWN.get(playerUuid);
        if (cooldownEnd == null) {
            return 0;
        }
        return Math.max(0, cooldownEnd - currentGameTime);
    }

    // -------------------------------------------------------------------------
    // Blood Pact session state (ephemeral)
    // -------------------------------------------------------------------------

    /**
     * Track Blood Pact active state (for damage bonus application).
     */
    public static void setBloodPactActive(UUID playerUuid, boolean active) {
        if (active) {
            BLOOD_PACT_ACTIVE.put(playerUuid, true);
        } else {
            BLOOD_PACT_ACTIVE.remove(playerUuid);
        }
    }

    /**
     * Check if Blood Pact is active for a player.
     */
    public static boolean isBloodPactActive(UUID playerUuid) {
        return BLOOD_PACT_ACTIVE.getOrDefault(playerUuid, false);
    }

    /**
     * Update Blood Pact multipliers for a player.
     */
    public static void setBloodPactMultipliers(UUID playerUuid, float drainMult, float soulMult) {
        BLOOD_PACT_DRAIN_MULTIPLIER.put(playerUuid, drainMult);
        BLOOD_PACT_SOUL_MULTIPLIER.put(playerUuid, soulMult);
    }

    public static float getDrainMultiplier(UUID playerUuid) {
        return BLOOD_PACT_DRAIN_MULTIPLIER.getOrDefault(playerUuid, 1.0f);
    }

    public static float getSoulMultiplier(UUID playerUuid) {
        return BLOOD_PACT_SOUL_MULTIPLIER.getOrDefault(playerUuid, 1.0f);
    }

    // -------------------------------------------------------------------------
    // Client sync
    // -------------------------------------------------------------------------

    /**
     * Sync soul data to a specific client.
     */
    public static void syncToClient(ServerPlayer player) {
        double souls = getSouls(player);
        boolean bloodPactActive = isBloodPactActive(player.getUUID());
        long currentGameTime = player.level().getGameTime();
        long cooldownRemaining = getPhylacteryCooldownRemaining(player.getUUID(), currentGameTime);

        TalentsMod.LOGGER.info("[SOUL SYNC] Sending {} souls to client {}", souls, player.getScoreboardName());

        // Get total cooldown in ticks from origin stat (seconds * 20)
        double cooldownSeconds = OriginManager.getOriginStat(player, "phylacteryCooldown");
        long totalCooldownTicks = (long) (cooldownSeconds * 20);

        // Read Blood Pact combat stats from player attributes
        float spellPower = readAttribute(player, ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
        float critChance = readAttribute(player, SpellCritAttributes.SPELL_CRIT_CHANCE.get());
        float critDamage = readAttribute(player, SpellCritAttributes.SPELL_CRIT_DAMAGE.get());

        // Get multipliers
        float drainMult = getDrainMultiplier(player.getUUID());
        float soulMult = getSoulMultiplier(player.getUUID());

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new SoulSyncPacket(souls, bloodPactActive, cooldownRemaining, totalCooldownTicks,
                        spellPower, critChance, critDamage, drainMult, soulMult));
    }

    private static float readAttribute(ServerPlayer player, ResourceLocation id) {
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(id);
        if (attr == null) return 0f;
        var inst = player.getAttribute(attr);
        return inst != null ? (float) inst.getValue() : 0f;
    }

    private static float readAttribute(ServerPlayer player, Attribute attr) {
        if (attr == null) return 0f;
        var inst = player.getAttribute(attr);
        return inst != null ? (float) inst.getValue() : 0f;
    }

    // -------------------------------------------------------------------------
    // Session cleanup (ephemeral state only — soul count is in PlayerPersistentData)
    // -------------------------------------------------------------------------

    /**
     * Clean up ephemeral session data for a player (on logout/origin change).
     * Soul count is NOT removed here — it lives in PlayerPersistentData.
     */
    public static void cleanup(UUID playerUuid) {
        PHYLACTERY_COOLDOWN.remove(playerUuid);
        BLOOD_PACT_ACTIVE.remove(playerUuid);
        BLOOD_PACT_DRAIN_MULTIPLIER.remove(playerUuid);
        BLOOD_PACT_SOUL_MULTIPLIER.remove(playerUuid);
        TalentsMod.LOGGER.debug("Cleaned up Dark Mage session data for player {}", playerUuid);
    }

    /**
     * Clean up ephemeral session data for a player.
     */
    public static void cleanup(ServerPlayer player) {
        cleanup(player.getUUID());
    }

    // -------------------------------------------------------------------------
    // Legacy NBT methods — kept for compatibility with PlayerDataPersistenceHandler.
    // These are now no-ops / thin wrappers since persistence is handled automatically.
    // -------------------------------------------------------------------------

    /**
     * @deprecated Soul data is now persisted automatically via PlayerPersistentData.
     *             This method is kept for backward compatibility only.
     */
    @Deprecated
    public static CompoundTag serializeNBT(UUID playerUuid) {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new CompoundTag();
        return PlayerPersistentData.get(server).getSoulData(playerUuid).serializeNBT();
    }

    /**
     * @deprecated Soul data is now persisted automatically via PlayerPersistentData.
     *             This method is kept for backward compatibility only.
     */
    @Deprecated
    public static CompoundTag serializeNBT(ServerPlayer player) {
        return serializeNBT(player.getUUID());
    }

    /**
     * @deprecated Soul data is now persisted automatically via PlayerPersistentData.
     *             deserialization is handled in {@link PlayerPersistentData#load(CompoundTag)}.
     */
    @Deprecated
    public static void deserializeNBT(UUID playerUuid, CompoundTag tag) {
        // No-op: soul data is now loaded directly from PlayerPersistentData on world load.
        // This method is kept so existing callers don't break.
        TalentsMod.LOGGER.debug("[SoulData] deserializeNBT called (no-op) for player {}", playerUuid);
    }

    /**
     * @deprecated Soul data is now persisted automatically via PlayerPersistentData.
     */
    @Deprecated
    public static void deserializeNBT(ServerPlayer player, CompoundTag tag) {
        deserializeNBT(player.getUUID(), tag);
        // Still sync client so HUD updates immediately on login
        syncToClient(player);
    }
}
