package com.complextalents.impl.elementalmage.skill;

import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.effects.ElementalEffects;
import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

public class HarmonicConvergenceSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "harmonic_convergence");

    // Level Scaling Arrays
    private static final double[] CRIT_PER_STACK = { 0.10, 0.12, 0.15, 0.17, 0.20 };
    private static final double[] CD_BASE = { 0.25, 0.30, 0.35, 0.40, 0.50 };
    private static final double[] CD_MULT = { 0.15, 0.20, 0.25, 0.30, 0.40 };

    public static void register() {
        SkillBuilder.create("complextalents", "harmonic_convergence")
                .nature(SkillNature.ACTIVE)
                .displayName("Hội Tụ")
                .description(
                        "Bộc phát Dấu Ấn Nguyên Tố tích tụ để tăng từ 1.15 đến 1.5 lần sát thương phép theo số Dấu Ấn, tăng 30% đến 60% tỷ lệ bạo kích và đến 100% sát thương bạo kích phép trong 10 giây.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/elementalmage/harmonic_convergence.png"))
                .setMaxLevel(5)
                .scaledCooldown(new double[] { 10.0, 10.0, 10.0, 10.0, 10.0 })
                .scaledStat("crit_per_stack", "Crit Per Stack", CRIT_PER_STACK)
                .scaledStat("crit_dmg_base", "Crit Dmg Base", CD_BASE)
                .scaledStat("crit_dmg_mult", "Crit Dmg Mult", CD_MULT)
                .validate((context, player) -> {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    var capOpt = serverPlayer.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).resolve();
                    if (capOpt.isEmpty() || capOpt.get().getEchoCount() <= 0) {
                        serverPlayer.sendSystemMessage(
                                Component.literal("\u00A7cYou have no Prismatic Echoes to converge!"));
                        return false;
                    }
                    return true;
                })
                .onActive((context, player) -> {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    int level = Math.min(Math.max(context.skillLevel() - 1, 0), 4);

                    var cap = serverPlayer.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA)
                            .orElseThrow(IllegalStateException::new);

                    int echoCount = cap.getEchoCount();
                    ElementType apex = cap.getApexElement();

                    // Calculate activated multiplier based on consumed Echo count:
                    // 1 Echo -> 1.15x, 2 Echoes -> 1.30x, 3 Echoes -> 1.50x
                    float activeMultiplier = switch (echoCount) {
                        case 1 -> 1.15f;
                        case 2 -> 1.30f;
                        default -> 1.50f;
                    };

                    cap.setApexElement(apex);
                    cap.setLockedHarmonyMultiplier(activeMultiplier);
                    cap.clearEchoes(); // Clear echo count & lastReaction, lock accumulation

                    // Fetch raw Spell Power
                    double spellPower = serverPlayer.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

                    // 10-Second Convergence Buff Parameters
                    double critChance = Math.min(1.0, echoCount * CRIT_PER_STACK[level]);
                    double rawCritDmg = CD_BASE[level] + Math.max(0.0, (spellPower - 1.0) * CD_MULT[level]);
                    double hardCappedCritDmg = Math.min(1.0, rawCritDmg); // Hard-capped at max +100% bonus

                    cap.setConvergenceCritChance((float) critChance);
                    cap.setConvergenceCritDamage((float) hardCappedCritDmg);
                    cap.sync();

                    // Apply 10s mob effect (HARMONIC_CONVERGENCE effect handles reset on expire)
                    serverPlayer.addEffect(new MobEffectInstance(ElementalEffects.HARMONIC_CONVERGENCE.get(), 200, 0));

                    // Play SFX & Particles
                    ServerLevel sLevel = serverPlayer.serverLevel();
                    sLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7f, 1.5f);

                    sLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            serverPlayer.getX(),
                            serverPlayer.getY() + serverPlayer.getBbHeight() / 2.0,
                            serverPlayer.getZ(),
                            50, 0.5, 0.5, 0.5, 0.2);

                    serverPlayer.sendSystemMessage(Component.literal(String.format(
                            "\u00A7b\u2728 Harmonic Convergence! \u00A7fActivated \u00A7e%.2fx Spell Damage\u00A7f (+%.0f%% Crit, +%.0f%% Crit Dmg) for 10s!",
                            activeMultiplier, critChance * 100.0, hardCappedCritDmg * 100.0)));
                })
                .register();
    }
}
