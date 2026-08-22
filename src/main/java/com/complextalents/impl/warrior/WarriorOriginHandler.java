package com.complextalents.impl.warrior;

import com.complextalents.epicfight.event.EpicFightGuardEvent;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.util.UUIDHelper;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKeys;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.Random;
import java.util.UUID;

/**
 * Handles Style Meter and Weapon-Path Perfect Parry mechanics for the Warrior origin.
 */
@Mod.EventBusSubscriber(modid = "complextalents")
public class WarriorOriginHandler {
    private static final Random RANDOM = new Random();
    private static final java.util.Map<UUID, Long> LAST_COMBAT_TIME = new java.util.HashMap<>();
    private static final long COMBAT_TIMEOUT_MS = 5000; // 5 seconds of no activity

    private static final UUID BLADEMASTER_PARRY_AD_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "blademaster_parry_ad");
    private static final UUID JUGGERNAUT_PARRY_AS_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "juggernaut_parry_as");
    private static final UUID BRAWLER_PARRY_SPEED_UUID = UUIDHelper.generateAttributeModifierUUID("warrior", "brawler_parry_speed");

    private static final java.util.Map<UUID, Long> LAST_COLOSSUS_PARRY_TIME = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> REAPER_CRIT_BUFF_EXPIRY = new java.util.HashMap<>();

    public record VanguardPoiseBuff(int expiryTick, double poisePct) {}
    private static final java.util.Map<UUID, VanguardPoiseBuff> VANGUARD_POISE_BUFF = new java.util.HashMap<>();

    public record TimedModifier(UUID playerUuid, Attribute attribute, UUID modifierUuid, int expireTick) {}
    private static final java.util.List<TimedModifier> ACTIVE_TIMED_MODIFIERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public enum StyleRank {
        D("D", "Dull", 0, 99, 1.0, 5, 0.60, 0x99888888), // Gray (5 pts/sec decay)
        C("C", "Cool", 100, 249, 1.0, 10, 0.80, 0x99AAAAAA), // Light Gray (10 pts/sec decay)
        B("B", "Bravo", 250, 449, 0.9, 5, 0.95, 0x9944DD44), // Green
        A("A", "Awesome", 450, 699, 0.75, 15, 1.01, 0x994444FF), // Blue
        S("S", "Stylish!", 700, 849, 0.5, 30, 1.03, 0x99AA44FF), // Purple
        SS("SS", "Spectacular!", 850, 949, 0.25, 60, 1.06, 0x99FF8844), // Orange
        SSS("SSS", "Smokin' Sexy Style!!", 950, 1000, 0.1, 100, 1.10, 0x99FF4444); // Red

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
            if (points >= SSS.min) return SSS;
            if (points >= SS.min) return SS;
            if (points >= S.min) return S;
            if (points >= A.min) return A;
            if (points >= B.min) return B;
            if (points >= C.min) return C;
            return D;
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isWarrior(player)) {
                if (event.getTarget() instanceof net.minecraft.world.entity.player.Player) {
                    return; // Do not gain style points or trigger combat on hitting players
                }
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
                if (multiplier <= 0.0) multiplier = rank.damageMultiplier;
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
                if (maxDR <= 0.0) maxDR = 0.40;
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
                            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                            1.2f, 0.8f);

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
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
            // Clean up expired timed attribute modifiers
            for (TimedModifier tm : ACTIVE_TIMED_MODIFIERS) {
                if (player.getUUID().equals(tm.playerUuid()) && player.tickCount >= tm.expireTick()) {
                    var instance = player.getAttribute(tm.attribute());
                    if (instance != null) {
                        instance.removeModifier(tm.modifierUuid());
                    }
                    ACTIVE_TIMED_MODIFIERS.remove(tm);
                }
            }

            if (isWarrior(player)) {
                double stylePoints = OriginManager.getResource(player);
                StyleRank rank = StyleRank.getRank(stylePoints);

                // Apply True Hit Immunity potion effect (efn:sin_stun_immunity) while at SSS Rank
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

                    // Activate weapon path bonus ONLY on Perfect Parry
                    triggerWeaponPathParryBonus(player);
                }
            }
        }
    }

    private static void triggerWeaponPathParryBonus(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
        if (path == null) return;

        int originLevel = OriginManager.getOriginLevel(player);
        Origin origin = OriginRegistry.getInstance().getOrigin(WarriorOrigin.ID);
        if (origin == null) return;

        switch (path) {
            case BLADEMASTER -> {
                double adPct = origin.getScaledStat("blademaster_parry_ad", originLevel);
                applyTimedAttributeModifier(player, Attributes.ATTACK_DAMAGE, BLADEMASTER_PARRY_AD_UUID,
                        "Blademaster Parry AD Bonus", adPct, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
                player.sendSystemMessage(Component.literal(String.format("\u00A76⚔ Perfect Parry: +%.0f%% Sát Thương (5s)", adPct * 100)), true);
            }
            case COLOSSUS -> {
                double cooldownSec = origin.getScaledStat("colossus_parry_cooldown", originLevel);
                double kbForce = origin.getScaledStat("colossus_parry_kb", originLevel);

                long now = System.currentTimeMillis();
                long lastTrigger = LAST_COLOSSUS_PARRY_TIME.getOrDefault(player.getUUID(), 0L);
                if (now - lastTrigger >= (long) (cooldownSec * 1000.0)) {
                    LAST_COLOSSUS_PARRY_TIME.put(player.getUUID(), now);
                    triggerColossusExplosion(player, originLevel, kbForce);
                    player.sendSystemMessage(Component.literal("\u00A7e💥 Perfect Parry: Sóng bộc phá đẩy lùi!"), true);
                }
            }
            case REAPER -> {
                double durSec = origin.getScaledStat("reaper_parry_crit_dur", originLevel);
                int durationTicks = (int) (durSec * 20.0);
                REAPER_CRIT_BUFF_EXPIRY.put(player.getUUID(), player.tickCount + durationTicks);
                player.sendSystemMessage(Component.literal(String.format("\u00A7c💀 Perfect Parry: 100%% Chí Mạng (%.1fs)", durSec)), true);
            }
            case JUGGERNAUT -> {
                double asPct = origin.getScaledStat("juggernaut_parry_as", originLevel);
                applyTimedAttributeModifier(player, Attributes.ATTACK_SPEED, JUGGERNAUT_PARRY_AS_UUID,
                        "Juggernaut Parry AS Bonus", asPct, AttributeModifier.Operation.MULTIPLY_BASE, 100);
                player.sendSystemMessage(Component.literal(String.format("\u00A7a⚡ Perfect Parry: +%.0f%% Tốc Độ Đánh (5s)", asPct * 100)), true);
            }
            case VANGUARD -> {
                double poisePct = origin.getScaledStat("vanguard_parry_poise", originLevel);
                int durationTicks = 100; // 5.0 seconds
                VANGUARD_POISE_BUFF.put(player.getUUID(), new VanguardPoiseBuff(player.tickCount + durationTicks, poisePct));
                player.sendSystemMessage(Component.literal(String.format("\u00A79🛡 Perfect Parry: +%.0f%% ST Poise (5s)", poisePct * 100)), true);
            }
            case BRAWLER -> {
                double speedPct = origin.getScaledStat("brawler_parry_speed", originLevel);
                applyTimedAttributeModifier(player, Attributes.MOVEMENT_SPEED, BRAWLER_PARRY_SPEED_UUID,
                        "Brawler Parry Speed Bonus", speedPct, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
                player.sendSystemMessage(Component.literal(String.format("\u00A7d👟 Perfect Parry: +%.0f%% Tốc Độ Di Chuyển (5s)", speedPct * 100)), true);
            }
        }
    }

    private static void applyTimedAttributeModifier(ServerPlayer player, Attribute attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation, int durationTicks) {
        if (attribute == null) return;
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
            instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            ACTIVE_TIMED_MODIFIERS.add(new TimedModifier(player.getUUID(), attribute, uuid, player.tickCount + durationTicks));
        }
    }

    private static void triggerColossusExplosion(ServerPlayer player, int originLevel, double kbForce) {
        Vec3 pos = player.position();
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.2);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);

            double radius = 4.0;
            AABB area = player.getBoundingBox().inflate(radius);
            float damageAmount = 5.0f + (float) originLevel * 4.0f;

            level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isAlliedTo(player)).forEach(entity -> {
                entity.hurt(player.damageSources().mobAttack(player), damageAmount);

                Vec3 dir = entity.position().subtract(pos);
                double dist = Math.max(0.1, dir.length());
                Vec3 normDir = dir.normalize();
                entity.knockback(kbForce, -normDir.x, -normDir.z);

                if (com.nhatbh.basedefensev2.api.PoiseAPI.hasPoise(entity)) {
                    com.nhatbh.basedefensev2.api.PoiseAPI.damagePoise(entity, damageAmount * 1.5f, 0.0f, player, player.damageSources().mobAttack(player), true, "ComplexTalents");
                }
            });
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isWarrior(player)) {
            Integer expiry = REAPER_CRIT_BUFF_EXPIRY.get(player.getUUID());
            if (expiry != null && player.tickCount <= expiry) {
                event.setResult(Event.Result.ALLOW);
                event.setDamageModifier(Math.max(1.5f, event.getDamageModifier()));
            }
        }
    }

    @SubscribeEvent
    public static void onPoiseDamage(com.nhatbh.basedefensev2.api.event.PoiseDamageEvent event) {
        if (event.getAttacker() instanceof ServerPlayer player && isWarrior(player)) {
            VanguardPoiseBuff buff = VANGUARD_POISE_BUFF.get(player.getUUID());
            if (buff != null && player.tickCount <= buff.expiryTick) {
                event.setAmount((float) (event.getAmount() * (1.0 + buff.poisePct)));
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
