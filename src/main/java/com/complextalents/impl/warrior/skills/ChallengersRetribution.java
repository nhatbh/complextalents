package com.complextalents.impl.warrior.skills;

import com.complextalents.TalentsMod;
import com.complextalents.impl.warrior.WarriorOriginHandler;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.S2CSpawnAAAParticlePacket;
import com.complextalents.origin.OriginManager;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.skill.Skill;
import com.complextalents.util.UUIDHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3f;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import net.minecraft.world.InteractionHand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charge skill: Challenger's Retribution.
 * Holds a shield, taunts enemies, and reflects absorbed damage.
 */
@Mod.EventBusSubscriber(modid = "complextalents")
public class ChallengersRetribution {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "challengers_retribution");
    private static final UUID SLOWNESS_UUID = UUIDHelper.generateAttributeModifierUUID("warrior",
            "retribution_slowness");
    private static final UUID KB_RESIST_UUID = UUIDHelper.generateAttributeModifierUUID("warrior",
            "retribution_kb_resist");

    private static final Map<UUID, ShieldData> ACTIVE_SHIELDS = new HashMap<>();

    public static class ShieldData {
        public double health;
        public double maxHealth;
        public double absorbedDamage;
        public final long startTime;

        public ShieldData(double health) {
            this.health = health;
            this.maxHealth = health;
            this.absorbedDamage = 0;
            this.startTime = System.currentTimeMillis();
        }

        public ShieldData(double health, double maxHealth, double absorbedDamage, long startTime) {
            this.health = health;
            this.maxHealth = maxHealth;
            this.absorbedDamage = absorbedDamage;
            this.startTime = startTime;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("health", health);
            tag.putDouble("maxHealth", maxHealth);
            tag.putDouble("absorbedDamage", absorbedDamage);
            tag.putLong("startTime", startTime);
            return tag;
        }

        public static ShieldData deserializeNBT(CompoundTag tag) {
            return new ShieldData(
                    tag.getDouble("health"),
                    tag.getDouble("maxHealth"),
                    tag.getDouble("absorbedDamage"),
                    tag.getLong("startTime"));
        }
    }

    public static void register() {
        SkillBuilder.create(ID)
                .displayName("Challenger's Retribution")
                .description(
                        "Vào thế đỡ khiên khiêu khích kẻ địch lân cận 5-10 khối, nhận 100% KB Resistance (kháng đẩy lùi tuyệt đối) và hấp thụ sát thương nhận vào. Nhả phím để phản lại 50%-160% sát thương đã hấp thụ thành đòn đánh AoE.")
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/warrior/challengers_retribution.png"))
                .nature(SkillNature.CHARGE)
                .setMaxLevel(5)
                .maxChannelTime(5.0) // Maximum 5 second charge
                .scaledCooldown(new double[] { 45.0, 40.0, 35.0, 30.0, 30.0 })
                .scaledStat("tauntRange", new double[] { 5, 6, 7, 8, 10 })
                .scaledStat("baseHp", new double[] { 25.0, 35.0, 45.0, 55.0, 65.0 })
                .scaledStat("reflectPercent", new double[] { 0.5, 0.75, 1.0, 1.25, 1.6 })
                .onActive(ChallengersRetribution::startCharge)
                .onRelease(ChallengersRetribution::releaseCharge)
                .register();
    }

    private static void startCharge(Skill.ExecutionContext context, Object playerObj) {
        if (!(playerObj instanceof ServerPlayer player))
            return;

        // Calculate Shield HP
        double points = OriginManager.getResource(player);
        WarriorOriginHandler.StyleRank rank = WarriorOriginHandler.StyleRank.getRank(points);

        double baseHp = context != null ? context.getStat("baseHp") : 25.0;
        // Rank Multipliers: D (1.0x - No penalty), C (1.1x), B (1.2x), A (1.3x), S
        // (1.4x), SS (1.5x), SSS (1.6x)
        double rankMult = switch (rank) {
            case D -> 1.0;
            case C -> 1.1;
            case B -> 1.2;
            case A -> 1.3;
            case S -> 1.4;
            case SS -> 1.5;
            case SSS -> 1.6;
        };

        // Base Max HP is 20. Shield scales with player's Max HP ratio.
        double maxHpRatio = Math.max(1.0, player.getMaxHealth() / 20.0);
        double shieldHp = (baseHp * maxHpRatio) * rankMult;

        ACTIVE_SHIELDS.put(player.getUUID(), new ShieldData(shieldHp));

        // Sync to HUD
        OriginManager.getCapability(player).ifPresent(data -> {
            data.setShieldMax(shieldHp);
            data.setShieldValue(shieldHp);
        });

        // Apply Immobilization and Knockback Resistance
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SLOWNESS_UUID);
            speedAttr.addTransientModifier(new AttributeModifier(SLOWNESS_UUID, "Retribution Slowness", -0.9,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.removeModifier(KB_RESIST_UUID);
            kbAttr.addTransientModifier(new AttributeModifier(KB_RESIST_UUID, "Retribution KB Resist", 1.0,
                    AttributeModifier.Operation.ADDITION));
        }

        // Taunt nearby enemies
        double tauntRange = context != null ? context.getStat("tauntRange") : 5.0;
        AABB area = player.getBoundingBox().inflate(tauntRange);
        player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player).forEach(entity -> {
            entity.setLastHurtByMob(player);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(player);
            }
        });

        // FX: Roar, Shield Block, and Taunt Particle
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.7f, 1.2f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0f, 0.8f);

        PacketHandler.sendToNearby(new S2CSpawnAAAParticlePacket(
                ResourceLocation.fromNamespaceAndPath("complextalents", "taunt"),
                player.position().add(0, 1.0, 0)), (net.minecraft.server.level.ServerLevel) player.level(),
                player.position());

        // Epic Fight Animation: Play GUARD animation
        ServerPlayerPatch playerPatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (playerPatch != null) {
            CapabilityItem itemCap = playerPatch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
            AnimationAccessor<? extends StaticAnimation> anim = itemCap.getGuardMotion(null,
                    yesman.epicfight.skill.guard.GuardSkill.BlockType.GUARD, playerPatch);
            if (anim != null) {
                playerPatch.playAnimationSynchronized(anim, 0.0F);
            }
        }
    }

    private static void releaseCharge(Skill.ExecutionContext context, Object playerObj, double chargeTime) {
        if (!(playerObj instanceof ServerPlayer player))
            return;

        ShieldData data = ACTIVE_SHIELDS.remove(player.getUUID());

        // Clear HUD
        OriginManager.getCapability(player).ifPresent(cap -> {
            cap.setShieldValue(0);
            cap.setShieldMax(0);
        });

        if (data != null) {
            // Remove Attributes
            var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null)
                speedAttr.removeModifier(SLOWNESS_UUID);
            var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null)
                kbAttr.removeModifier(KB_RESIST_UUID);

            // Award XP based on total absorbed damage
            double parryXP = XPFormula.calculateWarriorPerfectParryXP(data.absorbedDamage);
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            XPContext xpContext = XPContext.builder()
                    .source(XPSource.WARRIOR_PARRY)
                    .chunkPos(chunkPos)
                    .rawAmount(parryXP)
                    .metadata("damageAbsorbed", data.absorbedDamage)
                    .metadata("shieldMaxHealth", data.maxHealth)
                    .metadata("shieldRemainingHealth", data.health)
                    .build();
            LevelingService.getInstance().awardXP(player, parryXP, XPSource.WARRIOR_PARRY, xpContext);

            // Reflect Damage
            double reflectMult = 1.0;
            if (context != null) {
                reflectMult = context.getStat("reflectPercent");
            } else {
                int level = com.complextalents.skill.SkillManager.getSkillLevel(player, ID);
                double[] scale = new double[] { 0.5, 0.75, 1.0, 1.25, 1.6 };
                reflectMult = scale[Math.min(4, Math.max(0, level - 1))];
            }
            double damage = data.absorbedDamage * reflectMult;

            // Release AoE burst
            double burstRange = 6.0;
            AABB area = player.getBoundingBox().inflate(burstRange);
            player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player).forEach(entity -> {
                entity.hurt(player.damageSources().mobAttack(player), (float) damage);

                // Knockback
                double kbMagnitude = 1.5 + (data.absorbedDamage * 0.1);
                Vec3 dir = entity.position().subtract(player.position()).normalize();
                entity.knockback(kbMagnitude, -dir.x, -dir.z);
            });

            // Epic Fight Animation: Play current weapon attack animation
            ServerPlayerPatch playerPatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
            if (playerPatch != null) {
                CapabilityItem itemCap = playerPatch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
                List<AnimationAccessor<? extends AttackAnimation>> anim = itemCap.getAutoAttackMotion(playerPatch);
                if (anim != null && !anim.isEmpty()) {
                    playerPatch.playAnimationSynchronized(anim.get(0), 0.0F);
                }
            }

            // FX: Reflect Sounds (Clang + Explode) and Particle
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath("complextalents", "grandmaster.gate_hit")),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.2f);

            Vector3f rotation = new Vector3f(0, (float) Math.toRadians(-player.getYRot() + 180), 0);
            PacketHandler.sendToNearby(new S2CSpawnAAAParticlePacket(
                    ResourceLocation.fromNamespaceAndPath("complextalents", "reflect"),
                    player.position().add(0, 1.2, 0),
                    rotation,
                    0.4f), (net.minecraft.server.level.ServerLevel) player.level(), player.position());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShieldData data = ACTIVE_SHIELDS.get(player.getUUID());
            if (data != null) {
                double amount = event.getAmount();
                data.absorbedDamage += amount;
                data.health -= amount;

                // Award style points for blocking
                WarriorOriginHandler.addStylePoints(player, 20.0);

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 0.8f);

                if (data.health <= 0) {
                    releaseCharge(null, player, 0.0);
                } else {
                    // Update HUD
                    OriginManager.getCapability(player).ifPresent(cap -> {
                        cap.setShieldValue(data.health);
                    });
                }

                // Absorb damage
                event.setAmount(0);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ACTIVE_SHIELDS.containsKey(player.getUUID())) {
                player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ACTIVE_SHIELDS.containsKey(player.getUUID())) {
                if (player.tickCount % 2 == 0) {
                    ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
                            ParticleTypes.ELECTRIC_SPARK,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            2, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }
    }

    // --- Persistence Hooks ---

    public static CompoundTag saveData(ServerPlayer player) {
        ShieldData data = ACTIVE_SHIELDS.get(player.getUUID());
        return data != null ? data.serializeNBT() : new CompoundTag();
    }

    public static void loadData(ServerPlayer player, CompoundTag tag) {
        if (tag.contains("health")) {
            ShieldData data = ShieldData.deserializeNBT(tag);
            ACTIVE_SHIELDS.put(player.getUUID(), data);

            // Re-apply HUD
            OriginManager.getCapability(player).ifPresent(cap -> {
                cap.setShieldMax(data.maxHealth);
                cap.setShieldValue(data.health);
            });

            // Re-apply attributes if still valid
            applyRetributionAttributes(player);

            TalentsMod.LOGGER.info("[SKILL PERSIST] Restored Challenger's Retribution for {}", player.getUUID());
        }
    }

    private static void applyRetributionAttributes(ServerPlayer player) {
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SLOWNESS_UUID);
            speedAttr.addTransientModifier(new AttributeModifier(SLOWNESS_UUID, "Retribution Slowness", -0.9,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.removeModifier(KB_RESIST_UUID);
            kbAttr.addTransientModifier(new AttributeModifier(KB_RESIST_UUID, "Retribution KB Resist", 1.0,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
