package com.complextalents.impl.darkmage.skill;

import com.complextalents.effect.ModEffects;
import com.complextalents.passive.PassiveManager;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class VoidReversalSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "void_reversal");

    // Level scaling arrays for Active Skill (Void Reversal)
    public static final double[] COOLDOWN = { 100.0, 90.0, 80.0, 70.0, 60.0 };
    public static final double[] VOID_SHIELD_PCT = { 0.20, 0.25, 0.30, 0.35, 0.40 };
    public static final double[] SHIELD_DURATION_SEC = { 2.5, 3.0, 3.5, 4.0, 4.5 };
    public static final double[] BLINK_DISTANCE = { 6.0, 7.0, 8.0, 9.0, 10.0 };
    public static final double[] POSSESSED_CRIT_CHANCE = { 0.50, 0.60, 0.75, 0.90, 1.00 };
    public static final double[] POSSESSED_CRIT_DAMAGE = { 0.50, 0.70, 1.00, 1.15, 1.25 };

    public static void register() {
        SkillBuilder.create("complextalents", "void_reversal")
                .nature(SkillNature.ACTIVE)
                .displayName("Hư Không Hoán Chuyển")
                .description(
                        "Tiêu tán toàn bộ Entropy tích tụ để tạo Lá Chắn Hư Không bảo vệ bản thân và lập tức tốc biến lùi về phía sau, thoát khỏi vòng vây kẻ địch.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/darkmage/aspectofthewolf.png"))
                .scaledCooldown(COOLDOWN)
                .setMaxLevel(5)
                .scaledStat("void_shield_pct", "Void Shield (% Max HP)", VOID_SHIELD_PCT)
                .scaledStat("shield_duration", "Shield Duration (s)", SHIELD_DURATION_SEC)
                .scaledStat("blink_distance", "Blink Distance (Blocks)", BLINK_DISTANCE)
                .scaledStat("possessed_crit_chance", "Possessed Spell Crit Chance (%)", POSSESSED_CRIT_CHANCE)
                .scaledStat("possessed_crit_damage", "Possessed Spell Crit Damage (%)", POSSESSED_CRIT_DAMAGE)
                .onActive((context, player) -> {
                    if (!(player instanceof ServerPlayer serverPlayer))
                        return;
                    ServerLevel level = serverPlayer.serverLevel();

                    int skillLevel = Math.min(5, Math.max(1, context.skillLevel()));
                    int idx = skillLevel - 1;

                    // Flush all current Entropy to 0
                    PassiveManager.setPassiveStacks(serverPlayer, "entropy", 0);

                    // End Possessed state immediately (and remove any Silenced effect applied)
                    if (serverPlayer.hasEffect(ModEffects.POSSESSED.get())) {
                        serverPlayer.removeEffect(ModEffects.POSSESSED.get());
                    }
                    if (serverPlayer.hasEffect(ModEffects.SILENCED.get())) {
                        serverPlayer.removeEffect(ModEffects.SILENCED.get());
                    }

                    // Grant Void Shield (Absorption)
                    double maxHp = serverPlayer.getMaxHealth();
                    float shieldAmount = (float) (maxHp * VOID_SHIELD_PCT[idx]);
                    serverPlayer.setAbsorptionAmount(Math.max(serverPlayer.getAbsorptionAmount(), shieldAmount));

                    // Calculate backward velocity impulse away from look direction
                    Vec3 look = serverPlayer.getLookAngle();
                    Vec3 backwardDir = new Vec3(-look.x, 0, -look.z);
                    if (backwardDir.lengthSqr() < 1e-4) {
                        backwardDir = new Vec3(0, 0, 1);
                    } else {
                        backwardDir = backwardDir.normalize();
                    }

                    // Backward velocity magnitude: 1.2 to 2.4 + vertical lift pop of 0.4
                    double impulseSpeed = 1.2 + (idx * 0.3);
                    Vec3 impulse = backwardDir.scale(impulseSpeed).add(0, 0.4, 0);

                    // Apply velocity to player & sync client packet
                    serverPlayer.setDeltaMovement(impulse);
                    serverPlayer.hurtMarked = true;
                    serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(serverPlayer));

                    // Particles & sounds at launch position
                    Vec3 pos = serverPlayer.position();
                    level.sendParticles(ParticleTypes.PORTAL,
                            pos.x, pos.y + 1.0, pos.z,
                            30, 0.4, 0.8, 0.4, 0.1);
                    level.sendParticles(ParticleTypes.DRAGON_BREATH,
                            pos.x, pos.y + 1.0, pos.z,
                            20, 0.4, 0.8, 0.4, 0.05);
                    level.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
                    level.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
                })
                .register();
    }
}
