package com.complextalents.impl.darkmage.events;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.data.SoulData;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.events.xp.XPContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handler for Soul Siphon passive.
 * Dark Mages gain souls when they kill enemies.
 * Souls gained = 3 × √(HP/10) - 5 (generous early, harsh late-game brake).
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SoulSiphonHandler {

    private static final Map<UUID, List<VictimData>> deathBuffer = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Long>> spawnTimestamps = new ConcurrentHashMap<>();

    private static class VictimData {
        final LivingEntity entity;
        final double souls;
        VictimData(LivingEntity entity, double souls) {
            this.entity = entity;
            this.souls = souls;
        }
    }

    /**
     * Handle enemy deaths - grant souls to Dark Mage killers.
     * Souls gained = 3 × √(HP/10) - 5 (generous early, harsh late-game brake).
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = event.getEntity();

        // Don't grant souls for player kills (PvP protection)
        if (victim instanceof ServerPlayer) {
            return;
        }

        // Find the killer
        LivingEntity killer = victim.getKillCredit();

        // Try to get killer from damage source if kill credit is null
        if (killer == null && event.getSource().getEntity() instanceof LivingEntity living) {
            killer = living;
        }

        // Must be a player kill
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }

        // Must be a Dark Mage
        if (!DarkMageOrigin.isDarkMage(player)) {
            return;
        }

        // Calculate souls using the offset root formula
        float maxHealth = victim.getMaxHealth();
        double soulsGained = SoulData.calculateSoulsFromKill(maxHealth);

        // Add souls and sync
        SoulData.addSouls(player, soulsGained);

        // Award Soul Hoarder XP
        double soulXP = XPFormula.calculateDarkMageSoulHoarderXP(soulsGained);
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        XPContext soulContext = XPContext.builder()
            .source(XPSource.DARKMAGE_SOUL_HOARDER)
            .chunkPos(chunkPos)
            .rawAmount(soulXP)
            .metadata("soulsHarvested", soulsGained)
            .metadata("victimMaxHealth", victim.getMaxHealth())
            .build();
        LevelingService.getInstance().awardXP(player, soulXP, XPSource.DARKMAGE_SOUL_HOARDER, soulContext);

        // Award Edge of Death XP
        float currentHPPercentage = player.getHealth() / player.getMaxHealth();
        double edgeXP = XPFormula.calculateDarkMageEdgeOfDeathXP(victim.getMaxHealth(), currentHPPercentage);
        ChunkPos chunkPos2 = new ChunkPos(player.blockPosition());
        XPContext edgeContext = XPContext.builder()
            .source(XPSource.DARKMAGE_EDGE)
            .chunkPos(chunkPos2)
            .rawAmount(edgeXP)
            .metadata("killingBlowDamage", victim.getMaxHealth())
            .metadata("playerHPPercentage", currentHPPercentage)
            .metadata("playerCurrentHP", player.getHealth())
            .metadata("playerMaxHP", player.getMaxHealth())
            .build();
        LevelingService.getInstance().awardXP(player, edgeXP, XPSource.DARKMAGE_EDGE, edgeContext);

        // Soul escape particles at the killed mob (more souls = more particles)
        if (victim.level() instanceof ServerLevel serverLevel) {
            int count = Math.max(1, Math.min(40, (int) (soulsGained * 2)));
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    victim.getX(), victim.getY() + victim.getBbHeight() / 2.0, victim.getZ(),
                    count, 0.3, 0.4, 0.3, 0.05);
        }

        // Send chat message to player
        double totalSouls = SoulData.getSouls(player);
        player.sendSystemMessage(Component.literal(
                "\u00A75+" + String.format("%.1f", soulsGained) + " Souls \u00A78(" +
                        String.format("%.1f", totalSouls) + " total)"
        ));

        // Tiered Blood Pact Reactions (Buffered for Batching)
        if (BloodPactTickHandler.isBloodPactActive(player)) {
            deathBuffer.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                    .add(new VictimData(victim, totalSouls));
        }

        TalentsMod.LOGGER.debug("Dark Mage {} gained {:.2f} souls from killing {} (max HP: {}, total souls: {:.2f})",
                player.getName().getString(),
                soulsGained,
                victim.getName().getString(),
                maxHealth,
                totalSouls);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (deathBuffer.isEmpty()) return;

        Map<UUID, List<VictimData>> currentDeaths = new HashMap<>(deathBuffer);
        deathBuffer.clear();

        currentDeaths.forEach((playerId, victims) -> {
            if (victims.isEmpty()) return;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) return;

            // Batching: Find highest max health enemy from simultaneous deaths
            VictimData priorityVictim = victims.stream()
                    .max(Comparator.comparingDouble(v -> v.entity.getMaxHealth()))
                    .orElse(null);

            if (priorityVictim != null) {
                triggerBloodPactReaction(player, priorityVictim.entity, priorityVictim.souls);
            }
        });
        deathBuffer.clear();
    }

    private static void triggerBloodPactReaction(ServerPlayer player, LivingEntity victim, double souls) {
        if (souls >= 6666) {
            spawnBloodOrb(player, victim, 5);
        } else if (souls >= 3000) {
            spawnBloodOrb(player, victim, 4);
        } else if (souls >= 1500) {
            spawnBloodOrb(player, victim, 3);
        } else if (souls >= 500) {
            spawnBloodOrb(player, victim, 2);
        } else if (souls >= 100) {
            // Tier 1: Fledgling - Instant Lifesteal (15% of slain enemy's max health)
            player.heal(victim.getMaxHealth() * 0.15f);
        }
    }

    private static void spawnBloodOrb(ServerPlayer player, LivingEntity victim, int tier) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Rate Limit: 3 per 5 seconds (100 ticks)
        long now = level.getGameTime();
        List<Long> timestamps = spawnTimestamps.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        timestamps.removeIf(t -> now - t > 100);

        if (timestamps.size() >= 3) {
            return;
        }

        timestamps.add(now);
        com.complextalents.impl.darkmage.manager.BloodOrbManager.spawnOrb(player, victim.position().add(0, 1.0, 0), tier);
    }
}
