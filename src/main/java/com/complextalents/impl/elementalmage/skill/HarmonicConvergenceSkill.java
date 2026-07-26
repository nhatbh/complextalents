package com.complextalents.impl.elementalmage.skill;

import com.complextalents.elemental.ElementType;
import com.complextalents.elemental.effects.ElementalEffects;
import com.complextalents.impl.elementalmage.ElementalMageData;
import com.complextalents.impl.elementalmage.ElementalMageDataProvider;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.targeting.TargetType;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
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
    private static final double[] MANA_BASE = { 10.0, 15.0, 20.0, 25.0, 40.0 };
    private static final double[] MANA_MULT = { 5.0, 8.0, 12.0, 16.0, 25.0 };
    private static final double[] RES_BASE = { 15.0, 18.0, 20.0, 22.0, 25.0 }; // Resonance refund per echo
    private static final double[] CRIT_PER_STACK = { 0.10, 0.12, 0.15, 0.17, 0.20 };
    private static final double[] CD_BASE = { 0.25, 0.30, 0.35, 0.40, 0.50 };
    private static final double[] CD_MULT = { 0.15, 0.20, 0.25, 0.30, 0.40 };

    public static void register() {
        SkillBuilder.create("complextalents", "harmonic_convergence")
                .nature(SkillNature.ACTIVE)
                .displayName("Harmonic Convergence")
                .description(
                        "Tiêu hao Prismatic Echoes (cần >=1) để hoàn Mana & Resonance, gia tăng sát thương trong 10s dựa trên số Echoes tiêu thụ và giúp mọi phép dùng trúng kẻ địch lập tức phản ứng với nguyên tố kích hoạt gần nhất.")
                .targeting(TargetType.NONE)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/elementalmage/harmonic_convergence.png"))
                .setMaxLevel(5)
                .scaledCooldown(new double[] { 10.0, 10.0, 10.0, 10.0, 10.0 })
                .scaledStat("base_mana_restore", "Base Mana Restore", MANA_BASE)
                .scaledStat("spell_power_mana_mult", "Spell Power Mana Mult", MANA_MULT)
                .scaledStat("resonance_refund", "Resonance Refund", RES_BASE)
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

                    var cap = serverPlayer.getCapability(ElementalMageDataProvider.ELEMENTAL_DATA).orElseThrow(IllegalStateException::new);

                    // Read consumed Echo count & lock current Harmony Multiplier & Apex element
                    int echoCount = cap.getEchoCount();
                    ElementType apex = cap.getEchoes().isEmpty() ? cap.getApexElement() : cap.getEchoes().get(0);
                    float lockedHarmonyMult = cap.getLiveHarmonyMultiplier();

                    cap.setApexElement(apex);
                    cap.setLockedHarmonyMultiplier(lockedHarmonyMult);
                    cap.clearEchoes(); // Reset combo bar & lock accumulation

                    // Fetch raw Spell Power
                    double spellPower = serverPlayer.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

                    // Phase 1: Engine Refund (Mana + Resonance)
                    double manaRestored = echoCount * (MANA_BASE[level] + (spellPower * MANA_MULT[level]));
                    double resonanceRestored = echoCount * RES_BASE[level];

                    // Apply Mana Recovery
                    try {
                        io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData
                                .getPlayerMagicData(serverPlayer);
                        double maxMana = serverPlayer.getAttributeValue(AttributeRegistry.MAX_MANA.get());
                        magicData.setMana((float) Math.min(maxMana, magicData.getMana() + manaRestored));
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                    } catch (Exception ignored) {}

                    // Apply Resonance Recovery
                    serverPlayer.getCapability(OriginDataProvider.ORIGIN_DATA).ifPresent(originData -> {
                        originData.modifyResource(resonanceRestored);
                        originData.sync();
                    });

                    // Phase 2: 10-Second Convergence Buff Parameters
                    double critChance = Math.min(1.0, echoCount * CRIT_PER_STACK[level]);
                    double rawCritDmg = CD_BASE[level] + Math.max(0.0, (spellPower - 1.0) * CD_MULT[level]);
                    double hardCappedCritDmg = Math.min(1.0, rawCritDmg); // Hard-capped at max +100% bonus

                    cap.setConvergenceCritChance((float) critChance);
                    cap.setConvergenceCritDamage((float) hardCappedCritDmg);
                    cap.sync();

                    // Apply 10s mob effect
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
                            "\u00A7b\u2728 Harmonic Convergence! \u00A7fRefunded \u00A7b%.0f Mana \u00A7f& \u00A79%.0f Resonance\u00A7f. Locked Multiplier at \u00A7e%.1fx\u00A7f (+%.0f%% Crit, +%.0f%% Crit Dmg) for 10s!",
                            manaRestored, resonanceRestored, lockedHarmonyMult, critChance * 100.0, hardCappedCritDmg * 100.0)));
                })
                .register();
    }
}
