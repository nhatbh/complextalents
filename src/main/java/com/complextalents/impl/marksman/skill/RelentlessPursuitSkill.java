package com.complextalents.impl.marksman.skill;

import com.complextalents.impl.marksman.data.MarksmanAdrenalineData;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;

/**
 * Marksman Active Skill: Relentless Pursuit (Adrenaline Mode & Dismiss Resource System)
 * 
 * <p>Active Cast:</p>
 * <ul>
 *   <li>Static 300s Cooldown (unaffected by Ability Haste)</li>
 *   <li>Enters Adrenaline Mode: Faster reloading (+60%), +75% Headshot Damage</li>
 *   <li>Headshots build Dismiss Resource (0 - 100) based on gun archetype</li>
 *   <li>Re-activating skill at 100 Dismiss Resource grants Dismissed State (1s Invisibility, Invulnerability, Speed IV & Target Reset)</li>
 *   <li>On-kill: Extends duration up to max double base duration (+3s per kill)</li>
 *   <li>Body shot penalty applies (-1.5s)</li>
 * </ul>
 */
public class RelentlessPursuitSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "relentless_pursuit");

    public static void register() {
        SkillBuilder.create(ID)
                .displayName("Truy Cùng Diệt Tận")
                .description("Hồi chiêu cố định 300 giây. Kích hoạt Trạng Thái Adrenaline: Nạp đạn nhanh hơn, tăng mạnh sát thương Headshot. Bắn trúng Headshot tích lũy thanh tài nguyên Thoát Thân (Dismiss, 0-100) tùy theo dòng súng. Khi thanh tài nguyên đạt 100, kích hoạt lại kỹ năng để tiến vào trạng thái Thoát Thân (Tàng Hình, Vô Địch, Xóa Mục Tiêu kẻ địch trong 1s). Hạ gục kéo dài thời gian Adrenaline (tối đa gấp đôi nền).")
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/relentless_pursuit.png"))
                .nature(SkillNature.ACTIVE)
                .targeting(TargetType.NONE)
                .minChannelTime(0.0)
                .maxChannelTime(0.0)
                .setMaxLevel(5)
                .scaledCooldown(new double[] { 300.0, 300.0, 300.0, 300.0, 300.0 }) // 300s static cooldown
                
                // Stat Matrix
                .scaledStat("baseDuration", "Thời Gian Nền (s)", new double[] { 15.0, 16.5, 18.0, 19.5, 21.0 })
                .scaledStat("reloadSpeedBoost", "Tốc Độ Nạp Đạn (%)", new double[] { 0.40, 0.45, 0.50, 0.55, 0.60 })
                .scaledStat("headshotBonus", "ST Headshot Thưởng (%)", new double[] { 0.50, 0.55, 0.60, 0.65, 0.75 })
                
                .onActive((context, playerObj) -> {
                    if (!(playerObj instanceof ServerPlayer player)) return;

                    ServerLevel level = player.serverLevel();
                    int levelIndex = context != null ? context.skillLevel() : 1;
                    double baseDuration = context != null ? context.getStat("baseDuration") : 15.0;

                    // 1. Clear Slowness / Soft CC
                    player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

                    // 2. Activate Server Adrenaline Data & Reset Dismiss Resource
                    MarksmanAdrenalineData.activate(player, levelIndex, (float) baseDuration);

                    // FX: Initial adrenaline sound swell & particle burst
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.2f);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            30, 0.4, 0.6, 0.4, 0.15);
                })
                .register();
    }
}
