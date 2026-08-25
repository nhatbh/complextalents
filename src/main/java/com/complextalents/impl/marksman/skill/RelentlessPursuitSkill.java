package com.complextalents.impl.marksman.skill;

import com.complextalents.effect.ModEffects;
import com.complextalents.impl.marksman.data.MarksmanResourceData;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Marksman Active Skill: Relentless Pursuit (Tactical Dash & Mobility System)
 */
public class RelentlessPursuitSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "relentless_pursuit");

    public static void register() {
        SkillBuilder.create(ID)
                .displayName("Relentless Pursuit")
                .description("Dash rapidly in your movement direction and become briefly invulnerable. Costs 50 Mobility. Upgrading this skill accelerates Mobility regeneration and extends invulnerability duration.")
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/relentless_pursuit.png"))
                .nature(SkillNature.ACTIVE)
                .targeting(TargetType.NONE)
                .minChannelTime(0.0)
                .maxChannelTime(0.0)
                .setMaxLevel(5)
                .scaledCooldown(new double[] { 3.0, 3.0, 3.0, 3.0, 3.0 }) // 3s static cooldown
                
                // Stat Matrix for Ranks 1 to 5
                .scaledStat("invulnerableTicks", "Thời Gian Bất Tử (ticks)", new double[] { 10.0, 12.0, 14.0, 16.0, 18.0 })
                .scaledStat("passiveTicksPerPoint", "Hồi Chiêu Điểm (ticks)", new double[] { 48.0, 42.0, 36.0, 30.0, 24.0 })
                
                .onActive((context, playerObj) -> {
                    if (!(playerObj instanceof ServerPlayer player)) return;

                    float currentMobility = MarksmanResourceData.getMobility(player);
                    if (currentMobility < 50.0f) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7cKhông đủ Điểm Cơ Động! (Cần 50)"));
                        return;
                    }

                    // Deduct 50 Mobility
                    MarksmanResourceData.consumeMobility(player, 50.0f);

                    ServerLevel level = player.serverLevel();
                    int invulnTicks = context != null ? (int) context.getStat("invulnerableTicks") : 10;

                    // Apply Dash invulnerability potion effect
                    player.addEffect(new MobEffectInstance(ModEffects.DASH_INVULNERABLE.get(), invulnTicks, 0, false, false, true));

                    // Horizontal Dash Vector
                    Vec3 look = player.getLookAngle();
                    Vec3 horiz = new Vec3(look.x, 0, look.z).normalize();
                    if (horiz.lengthSqr() < 0.001) {
                        horiz = new Vec3(0, 0, 1);
                    }
                    double dashSpeed = 2.4;
                    player.setDeltaMovement(horiz.x * dashSpeed, 0.15, horiz.z * dashSpeed);
                    player.hurtMarked = true;

                    // FX: Flap sound & Cloud particle trail
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f);
                    level.sendParticles(ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.5, player.getZ(),
                            15, 0.3, 0.2, 0.3, 0.1);
                })
                .register();
    }
}
