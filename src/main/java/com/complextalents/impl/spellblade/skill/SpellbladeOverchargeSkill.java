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
                        "Bộc phát trạng thái Quá Tải trong 30s: Chuyển hóa Sức Mạnh Ma Thuật (AP) thành Sức Mạnh Công Kích (AD). Mỗi phép thuật thi triển sẽ duy trì Cường Hóa Nguyên Tố liên tục trong 6s trên mọi đòn đánh mà không bị mất đi sau 1 lần chém.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/spellblade/spellblade.png"))
                .scaledCooldown(SpellbladeOrigin.ACTIVE_COOLDOWN)
                .scaledStat("ap_to_ad_conversion", "AP to AD Conversion (%)", AP_TO_AD_CONVERSION)
                .setMaxLevel(5)
                .onActive((context, player) -> {
                    if (!(player instanceof ServerPlayer serverPlayer))
                        return;
                    ServerLevel level = serverPlayer.serverLevel();

                    // Activate 30 seconds Overcharge stance window (600 ticks)
                    SpellbladeData.setOverchargeTicks(serverPlayer, 600);

                    // Sound & Particle visual effects
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
                })
                .register();
    }
}
