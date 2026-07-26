package com.complextalents.impl.darkmage.events;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.manager.BloodOrbManager;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.util.XPFormula;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler for Soul Siphon passive.
 * Spawns physical Soul Orbs with Density V = max(1, floor(3 * sqrt(EnemyMaxHP / 10) - 5)).
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SoulSiphonHandler {

    /**
     * Handle enemy deaths - spawn physical Soul Orb for Dark Mage killer.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = event.getEntity();

        // Don't spawn orbs for player kills (PvP protection)
        if (victim instanceof ServerPlayer) {
            return;
        }

        // Find the killer
        LivingEntity killer = victim.getKillCredit();
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

        float maxHealth = victim.getMaxHealth();

        // Calculate Soul Density V: max(0.1, 3 * sqrt(HP / 10) - 2) allowing sub-1 density for low HP mobs
        double densityV = Math.max(0.1, Math.round((3.0 * Math.sqrt(maxHealth / 10.0) - 2.0) * 10.0) / 10.0);

        // Spawn physical Soul Orb
        BloodOrbManager.spawnOrb(player, victim.position().add(0, 0.5, 0), densityV);

        // Award Soul Hoarder XP
        double soulXP = XPFormula.calculateDarkMageSoulHoarderXP(densityV);
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        XPContext soulContext = XPContext.builder()
                .source(XPSource.DARKMAGE_SOUL_HOARDER)
                .chunkPos(chunkPos)
                .rawAmount(soulXP)
                .metadata("soulsHarvested", densityV)
                .metadata("victimMaxHealth", maxHealth)
                .build();
        LevelingService.getInstance().awardXP(player, soulXP, XPSource.DARKMAGE_SOUL_HOARDER, soulContext);

        // Award Edge of Death XP
        float currentHPPercentage = player.getHealth() / player.getMaxHealth();
        double edgeXP = XPFormula.calculateDarkMageEdgeOfDeathXP(maxHealth, currentHPPercentage);
        ChunkPos chunkPos2 = new ChunkPos(player.blockPosition());
        XPContext edgeContext = XPContext.builder()
                .source(XPSource.DARKMAGE_EDGE)
                .chunkPos(chunkPos2)
                .rawAmount(edgeXP)
                .metadata("killingBlowDamage", maxHealth)
                .metadata("playerHPPercentage", currentHPPercentage)
                .metadata("playerCurrentHP", player.getHealth())
                .metadata("playerMaxHP", player.getMaxHealth())
                .build();
        LevelingService.getInstance().awardXP(player, edgeXP, XPSource.DARKMAGE_EDGE, edgeContext);

        // Soul particles at the killed mob
        if (victim.level() instanceof ServerLevel serverLevel) {
            int count = Math.max(3, Math.min(50, (int) (densityV * 3)));
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    victim.getX(), victim.getY() + victim.getBbHeight() / 2.0, victim.getZ(),
                    count, 0.3, 0.4, 0.3, 0.05);
        }

        player.displayClientMessage(Component.literal(
                "\u00A75+Soul Orb dropped \u00A78(Density: " + String.format("%.1f", densityV) + ")"
        ), true);

        TalentsMod.LOGGER.debug("Dark Mage {} spawned Soul Orb (Density V={}) from killing {} (max HP: {})",
                player.getName().getString(),
                densityV,
                victim.getName().getString(),
                maxHealth);
    }
}
