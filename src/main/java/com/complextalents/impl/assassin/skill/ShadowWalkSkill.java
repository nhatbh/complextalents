package com.complextalents.impl.assassin.skill;

import com.complextalents.impl.assassin.data.AssassinData;
import com.complextalents.impl.assassin.effect.AssassinEffects;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Shadow Walk - Enter infinite stealth after 1s channel.
 */
public class ShadowWalkSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "shadow_walk");

    public static void register() {
        SkillBuilder.create("complextalents", "shadow_walk")
                .nature(SkillNature.ACTIVE)
                .displayName("Dạ Hành")
                .description(
                        "Tiến vào trạng thái tàng hình cho tới khi tấn công hoặc bị phát hiện, tăng tốc độ di chuyển và khiến đòn đánh tiếp theo từ phía sau lưng gây thêm sát thương.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/assassin/shadow_walk.png"))
                .minChannelTime(1.0)
                .maxChannelTime(1.0)
                .scaledCooldown(new double[] { 9999.0, 9999.0, 9999.0, 9999.0, 9999.0 })
                .scaledStat("stealthMoveSpeed", "Tốc Độ Dạ Hành (%)", new double[] { 0.35, 0.40, 0.45, 0.50, 0.60 })
                .scaledStat("stealthGaugeSize", "Thanh Tàng Hình", new double[] { 100.0, 150.0, 200.0, 250.0, 300.0 })
                .scaledStat("stealthGaugeRecovery", "Hồi Thanh Tàng Hình", new double[] { 5.0, 7.0, 10.0, 15.0, 20.0 })
                .scaledStat("stealthBackstabBuff", "Tăng ST Đánh Lén (%)", new double[] { 0.40, 0.50, 0.60, 0.70, 1.0 })
                .scaledStat("stealthBuffDuration", "Thời Gian Tăng ST (s)", new double[] { 5.0, 6.0, 7.0, 8.0, 10.0 })
                .scaledStat("stealthDamageDrain", "Tiêu Thanh Tàng Hình/ST", new double[] { 10.0, 9.0, 8.0, 7.0, 6.0 })
                .scaledStat("visibilityReduction", "Giảm Ẩn Độ", new double[] { 0.1, 0.08, 0.06, 0.04, 0.02 }) // Lower is better
                .setMaxLevel(5)
                .onActive((context, player) -> {
                    if (!(player instanceof ServerPlayer serverPlayer))
                        return;

                    ServerLevel level = serverPlayer.serverLevel();

                    // Ensure gauge is at least 20% of max when starting
                    double currentGauge = AssassinData.getStealthGauge(serverPlayer);
                    double maxGauge = AssassinData.getMaxGauge(serverPlayer);
                    if (currentGauge < maxGauge * 0.2) {
                        AssassinData.setStealthGauge(serverPlayer, maxGauge * 0.2);
                    }

                    // Apply Shadow Walk effect
                    serverPlayer.addEffect(new MobEffectInstance(
                            AssassinEffects.SHADOW_WALK.get(),
                            72000, // 1 hour (Effective infinite)
                            context.skillLevel() - 1,
                            false,
                            false));
                    com.complextalents.impl.assassin.events.ShadowWalkEventHandler
                            .applyUntargetableEffect(serverPlayer);

                    // Play stealth sound and particles
                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.5f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                            serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                            25, 0.3, 0.5, 0.3, 0.05);

                    serverPlayer.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("\u00A78Entering Shadow Walk..."));
                })
                .register();
    }
}
