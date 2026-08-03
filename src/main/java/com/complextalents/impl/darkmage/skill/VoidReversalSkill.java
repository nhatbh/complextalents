package com.complextalents.impl.darkmage.skill;

import com.complextalents.passive.PassiveManager;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class VoidReversalSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "void_reversal");

    // Level scaling arrays for Active Skill (Void Reversal)
    public static final double[] COOLDOWN = { 50.0, 45.0, 40.0, 35.0, 30.0 };
    public static final double[] VOID_SHIELD_PCT = { 0.20, 0.25, 0.30, 0.35, 0.40 };
    public static final double[] SHIELD_DURATION_SEC = { 2.5, 3.0, 3.5, 4.0, 4.5 };
    public static final double[] BLINK_DISTANCE = { 6.0, 7.0, 8.0, 9.0, 10.0 };
    public static final double[] POSSESSED_CRIT_CHANCE = { 0.50, 0.60, 0.75, 0.90, 1.00 };
    public static final double[] POSSESSED_CRIT_DAMAGE = { 0.50, 0.70, 1.00, 1.15, 1.25 };

    public static void register() {
        SkillBuilder.create("complextalents", "void_reversal")
                .nature(SkillNature.ACTIVE)
                .description("Xóa sạch toàn bộ thanh Entropy tích tụ để nhận Lá Chắn Hư Không bảo vệ và lập tức dịch chuyển lùi về phía sau.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/darkmage/aspectofthewolf.png"))
                .scaledCooldown(COOLDOWN)
                .setMaxLevel(5)
                .scaledStat("void_shield_pct", "Void Shield (% Max HP)", VOID_SHIELD_PCT)
                .scaledStat("shield_duration", "Shield Duration (s)", SHIELD_DURATION_SEC)
                .scaledStat("blink_distance", "Blink Distance (Blocks)", BLINK_DISTANCE)
                .scaledStat("possessed_crit_chance", "Possessed Spell Crit Chance (%)", POSSESSED_CRIT_CHANCE)
                .scaledStat("possessed_crit_damage", "Possessed Spell Crit Damage (%)", POSSESSED_CRIT_DAMAGE)
                .onActive((context, player) -> {
                    if (!(player instanceof ServerPlayer serverPlayer)) return;
                    ServerLevel level = serverPlayer.serverLevel();

                    int skillLevel = Math.min(5, Math.max(1, context.skillLevel()));
                    int idx = skillLevel - 1;

                    // Flush all current Entropy
                    PassiveManager.setPassiveStacks(serverPlayer, "entropy", 0);

                    // Grant Void Shield (Absorption)
                    double maxHp = serverPlayer.getMaxHealth();
                    float shieldAmount = (float) (maxHp * VOID_SHIELD_PCT[idx]);
                    serverPlayer.setAbsorptionAmount(Math.max(serverPlayer.getAbsorptionAmount(), shieldAmount));

                    // Teleport backward away from look direction
                    Vec3 look = serverPlayer.getLookAngle();
                    Vec3 backwardDir = new Vec3(-look.x, 0, -look.z);
                    if (backwardDir.lengthSqr() < 1e-4) {
                        backwardDir = new Vec3(0, 0, 1);
                    } else {
                        backwardDir = backwardDir.normalize();
                    }

                    double maxDistance = BLINK_DISTANCE[idx];
                    Vec3 startPos = serverPlayer.position();
                    Vec3 targetPos = startPos.add(backwardDir.scale(maxDistance));

                    // Wall collision prevention (Raytrace feet & eyes level)
                    Vec3 rayStartFeet = startPos.add(0, 0.2, 0);
                    Vec3 rayEndFeet = startPos.add(backwardDir.scale(maxDistance)).add(0, 0.2, 0);
                    net.minecraft.world.phys.BlockHitResult hitFeet = level.clip(new net.minecraft.world.level.ClipContext(
                            rayStartFeet, rayEndFeet, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, serverPlayer));

                    Vec3 rayStartEye = startPos.add(0, 1.5, 0);
                    Vec3 rayEndEye = startPos.add(backwardDir.scale(maxDistance)).add(0, 1.5, 0);
                    net.minecraft.world.phys.BlockHitResult hitEye = level.clip(new net.minecraft.world.level.ClipContext(
                            rayStartEye, rayEndEye, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, serverPlayer));

                    double actualDist = maxDistance;
                    if (hitFeet.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                        actualDist = Math.min(actualDist, hitFeet.getLocation().distanceTo(rayStartFeet));
                    }
                    if (hitEye.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                        actualDist = Math.min(actualDist, hitEye.getLocation().distanceTo(rayStartEye));
                    }

                    // Stop short before wall surface to avoid clipping into block geometry
                    actualDist = Math.max(0.0, actualDist - 0.6);

                    BlockPos targetBlock = BlockPos.containing(startPos.add(backwardDir.scale(actualDist)));
                    while (!level.getBlockState(targetBlock).isAir() && targetBlock.getY() < level.getMaxBuildHeight() - 1) {
                        targetBlock = targetBlock.above();
                    }
                    if (!level.getBlockState(targetBlock.below()).isSolid()) {
                        while (!level.getBlockState(targetBlock.below()).isSolid() && targetBlock.getY() > level.getMinBuildHeight() + 1) {
                            targetBlock = targetBlock.below();
                        }
                    }

                    double destX = targetBlock.getX() + 0.5;
                    double destY = targetBlock.getY();
                    double destZ = targetBlock.getZ() + 0.5;

                    // Spawn particles at departure
                    level.sendParticles(ParticleTypes.PORTAL,
                            startPos.x, startPos.y + 1.0, startPos.z,
                            40, 0.4, 0.8, 0.4, 0.1);
                    level.playSound(null, startPos.x, startPos.y, startPos.z,
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);

                    // Execute teleport
                    serverPlayer.teleportTo(destX, destY, destZ);

                    // Spawn particles at destination
                    level.sendParticles(ParticleTypes.DRAGON_BREATH,
                            destX, destY + 1.0, destZ,
                            30, 0.4, 0.8, 0.4, 0.05);
                    level.playSound(null, destX, destY, destZ,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
                })
                .register();
    }
}
