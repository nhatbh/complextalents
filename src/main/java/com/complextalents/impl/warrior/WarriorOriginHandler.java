package com.complextalents.impl.warrior;

import com.complextalents.origin.OriginManager;
import com.complextalents.epicfight.event.EpicFightGuardEvent;
import com.complextalents.passive.PassiveManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.events.xp.XPContext;
import net.minecraft.world.level.ChunkPos;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKeys;

import java.util.Random;

/**
 * Handles Style Meter logic for the Warrior origin.
 */
@Mod.EventBusSubscriber(modid = "complextalents")
public class WarriorOriginHandler {
    private static final Random RANDOM = new Random();
    private static final java.util.Map<java.util.UUID, Long> LAST_COMBAT_TIME = new java.util.HashMap<>();
    private static final long COMBAT_TIMEOUT_MS = 5000; // 5 seconds of no activity

    public enum StyleRank {
        D("D", "Dull", 0, 99, 1.0, 5, 0.7, 0x99888888), // Gray (5 pts/sec decay)
        C("C", "Cool", 100, 249, 1.0, 10, 1.0, 0x99AAAAAA), // Light Gray (10 pts/sec decay)
        B("B", "Bravo", 250, 449, 0.9, 5, 1.1, 0x9944DD44), // Green
        A("A", "Awesome", 450, 699, 0.75, 15, 1.2, 0x994444FF), // Blue
        S("S", "Stylish!", 700, 849, 0.5, 30, 1.3, 0x99AA44FF), // Purple
        SS("SS", "Spectacular!", 850, 949, 0.25, 60, 1.4, 0x99FF8844), // Orange
        SSS("SSS", "Smokin' Sexy Style!!", 950, 1000, 0.1, 100, 1.5, 0x99FF4444); // Red

        public final String name;
        public final String fullName;
        public final int min;
        public final int max;
        public final double gainMultiplier;
        public final double decayPerSecond;
        public final double damageMultiplier;
        public final int color;

        StyleRank(String name, String fullName, int min, int max, double gainMultiplier, double decayPerSecond,
                double damageMultiplier, int color) {
            this.name = name;
            this.fullName = fullName;
            this.min = min;
            this.max = max;
            this.gainMultiplier = gainMultiplier;
            this.decayPerSecond = decayPerSecond;
            this.damageMultiplier = damageMultiplier;
            this.color = color;
        }

        public static StyleRank getRank(double points) {
            if (points >= SSS.min)
                return SSS;
            if (points >= SS.min)
                return SS;
            if (points >= S.min)
                return S;
            if (points >= A.min)
                return A;
            if (points >= B.min)
                return B;
            if (points >= C.min)
                return C;
            return D;
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isWarrior(player)) {
                markCombat(player);
                addStylePoints(player, 10 + RANDOM.nextInt(10)); // Base gain on hit
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Handle outgoing damage scaling (Vanguard's Momentum - Origin Passive)
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (isWarrior(player)) {
                double points = OriginManager.getResource(player);
                StyleRank rank = StyleRank.getRank(points);

                String statName = "momentumDamage_" + rank.name();
                double multiplier = OriginManager.getOriginStat(player, statName);
                markCombat(player);

                event.setAmount((float) (event.getAmount() * multiplier));

                // Award Unstoppable Momentum XP
                if (rank == StyleRank.SSS) {
                    double momentumXP = XPFormula.calculateWarriorUnstoppableMomentumXP(event.getAmount());
                    ChunkPos chunkPos = new ChunkPos(player.blockPosition());
                    XPContext context = XPContext.builder()
                            .source(XPSource.WARRIOR_MOMENTUM)
                            .chunkPos(chunkPos)
                            .rawAmount(momentumXP)
                            .metadata("sssDamage", event.getAmount())
                            .metadata("styleRank", "SSS")
                            .build();
                    LevelingService.getInstance().awardXP(player, momentumXP, XPSource.WARRIOR_MOMENTUM, context);
                }
            }
        }

        // Handle incoming damage & damage reduction / Cheat Death
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isWarrior(player)) {
                markCombat(player);
                double points = OriginManager.getResource(player);
                StyleRank rank = StyleRank.getRank(points);

                // 1. Damage Reduction scaling on Style Rank up to maxDR at SSS Rank
                double maxDR = OriginManager.getOriginStat(player, "maxDamageReduction");
                double drFraction = switch (rank) {
                    case D -> 0.0;
                    case C -> 0.15;
                    case B -> 0.30;
                    case A -> 0.50;
                    case S -> 0.70;
                    case SS -> 0.85;
                    case SSS -> 1.00;
                };
                double reduction = maxDR * drFraction;
                float finalDamage = (float) (event.getAmount() * (1.0 - reduction));

                // 2. Cheat Death (The Shatter) at SSS Rank (950+ Style)
                if (rank == StyleRank.SSS && finalDamage >= player.getHealth()) {
                    event.setAmount(0);
                    event.setCanceled(true);

                    // Style Meter Shatters -> reset to B-Rank (250 Points)
                    OriginManager.setResource(player, 250.0);

                    // Remove True Hit Immunity effect immediately on Shatter
                    var stunImmunityEffect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("efn", "sin_stun_immunity"));
                    if (stunImmunityEffect != null && player.hasEffect(stunImmunityEffect)) {
                        player.removeEffect(stunImmunityEffect);
                    }

                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS,
                            1.2f, 0.8f);

                    if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                                player.getX(), player.getY() + 1.0, player.getZ(),
                                15, 0.4, 0.4, 0.4, 0.1);
                    }

                    player.sendSystemMessage(Component.literal("\u00A7cDeath Evasion!"), true);
                    return;
                }

                // Apply reduced damage
                event.setAmount(finalDamage);

                // 3. Unmitigated damage penalty to Style points
                modifyStylePoints(player, -200);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (isWarrior(player)) {
                double stylePoints = OriginManager.getResource(player);
                StyleRank rank = StyleRank.getRank(stylePoints);

                // Apply True Hit Immunity potion effect (efn:sin_stun_immunity) while at SSS
                // Rank
                var stunImmunityEffect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("efn", "sin_stun_immunity"));
                if (stunImmunityEffect != null) {
                    if (rank == StyleRank.SSS) {
                        if (!player.hasEffect(stunImmunityEffect)
                                || player.getEffect(stunImmunityEffect).getDuration() < 20) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(stunImmunityEffect, 40, 0,
                                    false, false, true));
                        }
                    } else if (player.hasEffect(stunImmunityEffect)) {
                        player.removeEffect(stunImmunityEffect);
                    }
                }

                if (rank.decayPerSecond > 0) {
                    long lastCombat = LAST_COMBAT_TIME.getOrDefault(player.getUUID(), 0L);
                    boolean inCombat = (System.currentTimeMillis() - lastCombat) < COMBAT_TIMEOUT_MS;

                    if (inCombat) {
                        return; // No decay while in combat
                    }

                    // Out of combat decay
                    double decay = Math.max(rank.decayPerSecond, 20.0); // Minimum 20 pts/sec when out of combat
                    double decayPerTick = decay / 20.0;
                    modifyStylePoints(player, -decayPerTick);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && isWarrior(player)) {
            markCombat(player);
        }
    }

    private static void markCombat(ServerPlayer player) {
        LAST_COMBAT_TIME.put(player.getUUID(), System.currentTimeMillis());
    }

    private static boolean isWarrior(ServerPlayer player) {
        ResourceLocation origin = OriginManager.getOriginId(player);
        return WarriorOrigin.ID.equals(origin);
    }

    public static void addStylePoints(ServerPlayer player, double amount) {
        double current = OriginManager.getResource(player);
        StyleRank rank = StyleRank.getRank(current);
        modifyStylePoints(player, amount * 2.0 * rank.gainMultiplier);
    }

    public static void modifyStylePoints(ServerPlayer player, double delta) {
        double current = OriginManager.getResource(player);
        double next = Math.max(0, Math.min(1000, current + delta));
        OriginManager.setResource(player, next);

        if (current < StyleRank.SSS.min && next >= StyleRank.SSS.min) {
            player.sendSystemMessage(Component.literal("\u00A76SSSmokin' Sexy Style!"), true);
        }
    }

    @SubscribeEvent
    public static void onGuard(EpicFightGuardEvent event) {
        ServerPlayer player = event.getPlayer();
        if (isWarrior(player)) {
            markCombat(player);
            double points = event.isParry() ? 50 : 20; // More points for perfect parry
            addStylePoints(player, points);

            ServerPlayerPatch playerpatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
            if (playerpatch != null) {
                float refund = event.getStaminaConsumed();
                if (event.isParry()) {
                    float currentStamina = playerpatch.getStamina();
                    playerpatch.resetActionTick();
                    playerpatch.setStamina(currentStamina + refund);

                    // Reset penalty on perfect parry
                    SkillContainer container = event.getContainer();
                    container.getDataManager().setDataSync(SkillDataKeys.PENALTY.get(), 0.0F);
                    container.getDataManager().setDataSync(SkillDataKeys.PENALTY_RESTORE_COUNTER.get(),
                            player.tickCount);
                    player.sendSystemMessage(Component.literal("\u00A7aParried!"), true);
                }
            }
        }
    }

    /**
     * When a player selects the Warrior origin, automatically grant them the
     * epicfight:guard skill.
     */
    @SubscribeEvent
    public static void onOriginChange(com.complextalents.origin.events.OriginChangeEvent event) {
        if (event.getChangeType() == com.complextalents.origin.events.OriginChangeEvent.ChangeType.SET
                && WarriorOrigin.ID.equals(event.getOriginId())) {
            ServerPlayer player = event.getPlayer();
            var server = player.getServer();
            if (server != null) {
                String cmd = "epicfight skill add " + player.getGameProfile().getName() + " guard epicfight:guard";
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), cmd);
            }
        }
    }

}
