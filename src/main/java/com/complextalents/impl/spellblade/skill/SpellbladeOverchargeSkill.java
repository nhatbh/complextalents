package com.complextalents.impl.spellblade.skill;

import com.complextalents.impl.spellblade.SpellbladeData;
import com.complextalents.impl.spellblade.origin.SpellbladeOrigin;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SpellbladeOverchargeSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "spellblade_overcharge");
    public static final double[] AP_TO_AD_CONVERSION = { 0.15, 0.20, 0.25, 0.30, 0.35 };

    public static void register() {
        SkillBuilder.create("complextalents", "spellblade_overcharge")
                .nature(SkillNature.ACTIVE)
                .displayName("Quá Tải")
                .description(
                        "Trạng Thái Quá Tải (hồi chiêu 5s): Phép thi triển ≤ 5s trở thành tức thì (0s). Mỗi đòn chém tiêu hao Mana gây thêm sát thương phép theo AP & AD gain và cường hóa hiệu ứng nguyên tố 1.25x-1.90x. Khi yểm nguyên tố hết hạn hoặc đổi nguyên tố khi duration >50%, phép tiếp theo thi triển miễn phí (0 Mana).")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/spellblade/spellblade.png"))
                .scaledCooldown(SpellbladeOrigin.ACTIVE_COOLDOWN)
                .scaledStat("ap_damage_ratio", "Tỷ Lệ AP (%)", SpellbladeOrigin.AP_DAMAGE_GAIN_RATIO)
                .scaledStat("enhanced_mult", "Bội Số Nguyên Tố", SpellbladeOrigin.ENHANCED_EFFECT_MULT)
                .setMaxLevel(5)
                .onActive((context, player) -> {
                    if (!(player instanceof ServerPlayer serverPlayer))
                        return;
                    ServerLevel level = serverPlayer.serverLevel();

                    // Toggle Overcharge Stance
                    SpellbladeData.toggleOverchargeStance(serverPlayer);
                    boolean isStanceOn = SpellbladeData.isOverchargeStance(serverPlayer);

                    // Sound & Particle visual effects
                    if (isStanceOn) {
                        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.5f);
                        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);

                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                                40, 0.5, 0.8, 0.5, 0.2);
                        level.sendParticles(ParticleTypes.FLAME,
                                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                                25, 0.4, 0.6, 0.4, 0.1);
                    } else {
                        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
                        level.sendParticles(ParticleTypes.SMOKE,
                                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                                20, 0.3, 0.5, 0.3, 0.05);
                    }
                })
                .register();
    }
}
