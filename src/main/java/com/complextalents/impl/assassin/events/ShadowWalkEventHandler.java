package com.complextalents.impl.assassin.events;

import com.complextalents.TalentsMod;
import com.complextalents.impl.assassin.data.AssassinData;
import com.complextalents.impl.assassin.effect.AssassinEffects;
import com.complextalents.impl.assassin.origin.AssassinOrigin;
import com.complextalents.impl.assassin.skill.ShadowWalkSkill;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.events.xp.XPContext;
import net.minecraft.world.level.ChunkPos;
import com.complextalents.skill.SkillManager;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Handles all events related to the Shadow Walk skill.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class ShadowWalkEventHandler {

    private static final ResourceLocation UNTARGETABLE_ID = ResourceLocation.fromNamespaceAndPath("basedefensev2", "untargetable");

    public static void applyUntargetableEffect(ServerPlayer player) {
        MobEffect untargetable = ForgeRegistries.MOB_EFFECTS.getValue(UNTARGETABLE_ID);
        if (untargetable != null) {
            MobEffectInstance current = player.getEffect(untargetable);
            if (current == null || current.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(untargetable, 100, 0, false, false));
            }
        }
    }

    public static void removeUntargetableEffect(ServerPlayer player) {
        MobEffect untargetable = ForgeRegistries.MOB_EFFECTS.getValue(UNTARGETABLE_ID);
        if (untargetable != null && player.hasEffect(untargetable)) {
            player.removeEffect(untargetable);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side.isClient())
            return;
        if (!(event.player instanceof ServerPlayer player))
            return;
        if (!AssassinOrigin.isAssassin(player))
            return;

        boolean isStealthed = player.hasEffect(AssassinEffects.SHADOW_WALK.get());

        // 1. Handle Speed Scaling
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (isStealthed) {
                double speedBonus = SkillManager.getSkillStat(player, ShadowWalkSkill.ID, "stealthMoveSpeed");
                AttributeModifier existing = speedAttr.getModifier(AssassinOrigin.STEALTH_SPEED_UUID);
                if (existing == null || existing.getAmount() != speedBonus) {
                    if (existing != null)
                        speedAttr.removeModifier(AssassinOrigin.STEALTH_SPEED_UUID);
                    speedAttr.addTransientModifier(new AttributeModifier(AssassinOrigin.STEALTH_SPEED_UUID, "Assassin Stealth Speed",
                            speedBonus, AttributeModifier.Operation.MULTIPLY_BASE));
                }
            } else {
                if (speedAttr.getModifier(AssassinOrigin.STEALTH_SPEED_UUID) != null) {
                    speedAttr.removeModifier(AssassinOrigin.STEALTH_SPEED_UUID);
                }
            }
        }

        // 2. Handle Gauge Drain/Recovery
        handleStealthGaugeAndAggro(player, isStealthed);

        // 3. Sync gauge to client periodically
        if (player.tickCount % 10 == 0) {
            AssassinData.syncToClient(player);
        }

        // 4. Safety Rail: Check every 100 ticks if CD needs manual reset
        if (player.tickCount % 100 == 0 && !isStealthed) {
            double currentGauge = AssassinData.getStealthGauge(player);
            double maxGauge = AssassinData.getMaxGauge(player);
            if (currentGauge >= maxGauge) {
                player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).ifPresent(data -> {
                    if (data.isOnCooldown(ShadowWalkSkill.ID)) {
                        data.clearCooldown(ShadowWalkSkill.ID);
                    }
                });
            }
        }
    }

    private static void handleStealthGaugeAndAggro(ServerPlayer player, boolean isStealthed) {
        double currentGauge = AssassinData.getStealthGauge(player);
        double recovery = SkillManager.getSkillStat(player, ShadowWalkSkill.ID, "stealthGaugeRecovery");

        if (isStealthed) {
            // Decreased depletion range: 5.0 blocks (down from 10.0)
            List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(5.0));
            if (!nearbyMobs.isEmpty()) {
                double minDistance = 5.0;
                for (Mob mob : nearbyMobs) {
                    double dist = mob.distanceTo(player);
                    if (dist < minDistance) {
                        minDistance = dist;
                    }
                }

                int exposureTicks = player.getPersistentData().getInt("StealthExposureTicks") + 1;
                player.getPersistentData().putInt("StealthExposureTicks", exposureTicks);

                boolean wasDraining = player.getPersistentData().getBoolean("StealthDrainActive");
                if (!wasDraining && exposureTicks > 15) {
                    // Play drain warning sound when grace period ends
                    player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
                    player.getPersistentData().putBoolean("StealthDrainActive", true);
                }

                // Grace Period: First 15 ticks (0.75s) of brief exposure cause 0 drain
                if (exposureTicks > 15) {
                    // Distance scaling: Full rate 1.5 at <= 2.0 blocks, linear scale to 5.0 blocks
                    double distanceFactor;
                    if (minDistance <= 2.0) {
                        distanceFactor = 1.5;
                    } else {
                        distanceFactor = ((5.0 - minDistance) / 3.0) * 1.5;
                    }

                    // Accelerating time multiplier: ramps up from 1.0x to 5.0x the longer player lingers
                    double timeRamp = 1.0 + Math.min(4.0, (exposureTicks - 15) * 0.05);
                    double drainRate = distanceFactor * timeRamp;

                    currentGauge -= drainRate;
                }
            } else {
                // Recover gauge when away from mobs while stealthed
                player.getPersistentData().putBoolean("StealthDrainActive", false);
                player.getPersistentData().putInt("StealthExposureTicks", 0);
                currentGauge += recovery / 20.0;
            }

            if (currentGauge <= 0) {
                player.removeEffect(AssassinEffects.SHADOW_WALK.get());
                removeUntargetableEffect(player);
                player.setInvisible(false);
                currentGauge = 0;
                player.getPersistentData().putBoolean("StealthDrainActive", false);
                player.getPersistentData().putInt("StealthExposureTicks", 0);
                alertNearbyMobs(player);

                // Discovery FX
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.5f, 1.2f);
                player.serverLevel().sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        15, 0.2, 0.5, 0.2, 0.05);
            } else {
                if (!player.isInvisible()) {
                    player.setInvisible(true);
                }
                applyUntargetableEffect(player);
                // Force mobs already targeting us to drop target
                List<Mob> targetingMe = player.level().getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(16.0),
                        mob -> mob.getTarget() == player);
                for (Mob mob : targetingMe) {
                    mob.setTarget(null);
                }
            }
        } else {
            removeUntargetableEffect(player);
            // Recover gauge when not stealthed - Slowed by half
            currentGauge += (recovery / 20.0) / 2.0;

            // Reset CD when not in stealth and have full gauge
            double maxGauge = AssassinData.getMaxGauge(player);
            if (currentGauge >= maxGauge) {
                currentGauge = maxGauge;
                player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).ifPresent(data -> {
                    if (data.isOnCooldown(ShadowWalkSkill.ID)) {
                        data.clearCooldown(ShadowWalkSkill.ID);
                    }
                });
            }
        }

        AssassinData.setStealthGauge(player, currentGauge);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && AssassinOrigin.isAssassin(victim)) {
            if (victim.hasEffect(AssassinEffects.SHADOW_WALK.get())) {
                double penalty = SkillManager.getSkillStat(victim, ShadowWalkSkill.ID, "stealthDamagePenalty");
                event.setAmount((float) (event.getAmount() * penalty));

                // Break stealth on damage and aggro nearby mobs
                victim.removeEffect(AssassinEffects.SHADOW_WALK.get());
                removeUntargetableEffect(victim);
                victim.setInvisible(false);
                alertNearbyMobs(victim);

                // Discovery FX
                victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8f, 1.0f);
                victim.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        10, 0.3, 0.5, 0.3, 0.05);
            }
        }

        // Handle Assassin Attack Effects
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && AssassinOrigin.isAssassin(attacker)) {
            // Apply Ambush Damage Scaling (Multiplicative)
            if (attacker.hasEffect(AssassinEffects.AMBUSH.get())) {
                double apBuff = SkillManager.getSkillStat(attacker, ShadowWalkSkill.ID, "stealthBackstabBuff");
                float ambushDamage = (float) (event.getAmount() * (1.0 + apBuff));
                event.setAmount(ambushDamage);
                
                // Award Ambush XP
                double ambushXP = XPFormula.calculateAssassinAmbushXP(ambushDamage);
                ChunkPos chunkPos = new ChunkPos(attacker.blockPosition());
                XPContext context = XPContext.builder()
                    .source(XPSource.ASSASSIN_AMBUSH)
                    .chunkPos(chunkPos)
                    .rawAmount(ambushXP)
                    .metadata("totalDamage", ambushDamage)
                    .metadata("apBuffMultiplier", apBuff)
                    .build();
                LevelingService.getInstance().awardXP(attacker, ambushXP, XPSource.ASSASSIN_AMBUSH, context);
            }
            if (attacker.hasEffect(AssassinEffects.SHADOW_WALK.get())) {
                attacker.removeEffect(AssassinEffects.SHADOW_WALK.get());
                removeUntargetableEffect(attacker);
                attacker.setInvisible(false);

                // Consume stealth gauge based on attack type
                double maxGauge = AssassinData.getMaxGauge(attacker);
                double currentGauge = AssassinData.getStealthGauge(attacker);
                double gaugeConsumption;

                if (com.complextalents.impl.assassin.util.AssassinUtils.isBackstab(attacker, event.getEntity())) {
                    // Backstab: consume 25% of max gauge
                    gaugeConsumption = maxGauge * 0.25;
                } else {
                    // Normal strike: consume 50% of max gauge
                    gaugeConsumption = maxGauge * 0.50;
                }

                AssassinData.setStealthGauge(attacker, currentGauge - gaugeConsumption);

                if (com.complextalents.impl.assassin.util.AssassinUtils.isBackstab(attacker, event.getEntity())) {
                    double apBuff = SkillManager.getSkillStat(attacker, ShadowWalkSkill.ID, "stealthBackstabBuff");
                    double buffDuration = SkillManager.getSkillStat(attacker, ShadowWalkSkill.ID, "stealthBuffDuration");
                    int durationTicks = (int) (buffDuration * 20);

                    // Apply custom Ambush effect for correct multiplicative damage scaling
                    attacker.addEffect(new MobEffectInstance(AssassinEffects.AMBUSH.get(), durationTicks, 0));

                    // 1. Apply True Hit Immunity (efn:sin_stun_immunity)
                    var stunImmunityEffect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.fromNamespaceAndPath("efn", "sin_stun_immunity"));
                    if (stunImmunityEffect != null) {
                        attacker.addEffect(new MobEffectInstance(stunImmunityEffect, durationTicks, 0, false, false, true));
                    }

                    // 2. Apply Shield (100% Max HP Absorption for durationTicks)
                    float shieldAmount = (float) (attacker.getMaxHealth() * 1.00);
                    int amp = Math.max(0, (int) (shieldAmount / 4.0f) - 1);
                    attacker.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, durationTicks, amp, false, true, true));
                    attacker.setAbsorptionAmount(Math.max(attacker.getAbsorptionAmount(), shieldAmount));

                    // Apply scaling to the initial backstab hit immediately
                    float finalBackstabDamage = (float) (event.getAmount() * (1.0 + apBuff));
                    event.setAmount(finalBackstabDamage);

                    // Award Ghost XP
                    double ghostXP = XPFormula.calculateAssassinGhostXP(finalBackstabDamage, currentGauge, maxGauge);
                    ChunkPos chunkPos = new ChunkPos(attacker.blockPosition());
                    double gaugeEfficiency = maxGauge > 0 ? currentGauge / maxGauge : 0;
                    XPContext ghostContext = XPContext.builder()
                        .source(XPSource.ASSASSIN_GHOST)
                        .chunkPos(chunkPos)
                        .rawAmount(ghostXP)
                        .metadata("backstabDamage", finalBackstabDamage)
                        .metadata("currentGauge", currentGauge)
                        .metadata("maxGauge", maxGauge)
                        .metadata("gaugeEfficiency", gaugeEfficiency)
                        .build();
                    LevelingService.getInstance().awardXP(attacker, ghostXP, XPSource.ASSASSIN_GHOST, ghostContext);
                    
                    // Backstab FX (Blood Splatter + Fine Mist)
                    ServerLevel serverLevel = attacker.serverLevel();
                    double x = event.getEntity().getX();
                    double y = event.getEntity().getY() + event.getEntity().getBbHeight() / 2.0;
                    double z = event.getEntity().getZ();

                    // Sound
                    serverLevel.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);

                    // OPTION 1: Chunky Splatter (Redstone Block)
                    BlockParticleOption bloodSplatter = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    serverLevel.sendParticles(bloodSplatter, x, y, z, 15, 0.2, 0.3, 0.2, 0.15);

                    // OPTION 2: Fine Mist (Red Dust)
                    DustParticleOptions bloodMist = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.0f), 1.2f);
                    serverLevel.sendParticles(bloodMist, x, y, z, 10, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssassinOrigin.isAssassin(player)) {
            if (player.hasEffect(AssassinEffects.SHADOW_WALK.get())) {
                double reduction = SkillManager.getSkillStat(player, ShadowWalkSkill.ID, "visibilityReduction");
                event.modifyVisibility(reduction);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player) {
            if (player.hasEffect(AssassinEffects.SHADOW_WALK.get())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssassinOrigin.isAssassin(player)) {
            AssassinData.syncToClient(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AssassinOrigin.isAssassin(player)) {
            AssassinData.setStealthGauge(player, AssassinData.getMaxGauge(player));
            player.getPersistentData().putBoolean("StealthDrainActive", false);
            AssassinData.syncToClient(player);
        }
    }

    private static void alertNearbyMobs(ServerPlayer player) {
        List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0));
        for (Mob mob : nearbyMobs) {
            mob.setTarget(player);
        }
    }
}
