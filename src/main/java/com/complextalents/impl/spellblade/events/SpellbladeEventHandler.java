package com.complextalents.impl.spellblade.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.spellblade.SpellbladeData;
import com.complextalents.impl.spellblade.origin.SpellbladeOrigin;
import com.complextalents.origin.OriginManager;
import com.complextalents.spellmastery.SpellSchool;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import com.complextalents.util.IronParticleHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import com.complextalents.util.UUIDHelper;

import com.complextalents.impl.spellblade.SpellbladeDataProvider;
import com.complextalents.impl.spellblade.skill.SpellbladeOverchargeSkill;
import com.complextalents.skill.SkillManager;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellbladeEventHandler {

    private static final UUID OVERCHARGE_AD_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("spellblade",
            "overcharge_ad");

    /**
     * Intercept spell casting for Spellblade players:
     * - Swaps active weapon imbue to cast spell's school.
     * - During Overcharge: grants 6 seconds (120 ticks) of enhanced attacks.
     * - Outside Overcharge: grants 1 single-strike imbue charge.
     */
    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;

        if (!SpellbladeOrigin.isSpellblade(serverPlayer))
            return;

        try {
            AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
            if (spell == null || spell.getSchoolType() == null)
                return;

            String schoolPath = spell.getSchoolType().getId().getPath();
            SpellSchool school = SpellSchool.fromString(schoolPath);
            if (school == null)
                return;

            // Set active element to cast spell's school
            SpellbladeData.setActiveElement(serverPlayer, school);

            if (SpellbladeData.isOverchargeActive(serverPlayer)) {
                // Overcharge Active: Grant 6 seconds (120 ticks) of enhanced attacks, resetting
                // timer
                SpellbladeData.setEnhancedAttackTicks(serverPlayer, 120);
            } else {
                // Outside Overcharge: Grant 1 single-strike imbue charge
                SpellbladeData.setHasImbueCharge(serverPlayer, true);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Intercept melee strikes by Spellblade players:
     * 1. Mana Weaver Passive: restores mana inversely proportional to weapon attack
     * speed.
     * 2. Triggers active elemental imbue effects (if in Overcharge 6s window OR
     * holding single-strike charge).
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!SpellbladeOrigin.isSpellblade(player))
            return;

        int originLevel = Math.min(5, Math.max(1, OriginManager.getOriginLevel(player)));
        int idx = originLevel - 1;

        // 1. Mana Weaver Passive: Restore Mana based on inverse attack speed
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.2)
            attackSpeed = 0.2;
        double weightMult = 1.0 / attackSpeed;

        double baseManaPct = SpellbladeOrigin.BASE_MANA_PER_HIT[idx];

        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) {
                double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
                if (maxMana <= 0)
                    maxMana = 100.0;
                float manaRestored = (float) (maxMana * baseManaPct * weightMult);
                float newMana = (float) Math.min(maxMana, magicData.getMana() + manaRestored);
                magicData.setMana(newMana);
                PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Intercept damage dealt by Spellblade players to apply Elemental Imbue
     * enhancements and Eldritch Rift absorption.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Track Eldritch Rift damage absorption on target NBT
        LivingEntity victim = event.getEntity();
        if (victim != null && victim.hasEffect(ModEffects.ELDRITCH_RIFT.get())) {
            double absorbed = victim.getPersistentData().getDouble("EldritchRiftAbsorbedDamage");
            victim.getPersistentData().putDouble("EldritchRiftAbsorbedDamage", absorbed + event.getAmount());
        }

        // Handle attacker Spellblade imbue triggers (Melee hit only)
        if (!(event.getSource().getEntity() instanceof ServerPlayer player))
            return;
        if (!SpellbladeOrigin.isSpellblade(player))
            return;

        // Verify direct melee hit
        if (event.getSource().getDirectEntity() != player)
            return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || event.getSource().isIndirect())
            return;
        if (com.complextalents.elemental.integration.ModIntegrationHandler.isIronSpellbooksLoaded()) {
            if (event.getSource() instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource)
                return;
        }

        boolean isOvercharge = SpellbladeData.isOverchargeActive(player);
        int enhancedTicks = SpellbladeData.getEnhancedAttackTicks(player);
        boolean hasCharge = SpellbladeData.hasImbueCharge(player);

        boolean isImbueActive = (isOvercharge && enhancedTicks > 0) || (!isOvercharge && hasCharge);
        if (!isImbueActive)
            return;

        SpellSchool activeElement = SpellbladeData.getActiveElement(player);
        if (activeElement == null)
            return;

        int originLevel = Math.min(5, Math.max(1, OriginManager.getOriginLevel(player)));
        int idx = originLevel - 1;

        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.2)
            attackSpeed = 0.2;
        double weightMult = 1.0 / attackSpeed;

        // Effective AP scaling with School-Specific Spell Power
        double effectiveAp = SpellbladeData.getEffectiveAP(player, activeElement);

        ServerLevel level = player.serverLevel();

        double imbueWeight = isOvercharge ? weightMult : 1.0;

        switch (activeElement) {
            case FIRE: {
                // Bonus Fire Damage normalized by attack speed during Overcharge
                float bonusDmg = (float) (((event.getAmount() * SpellbladeOrigin.FIRE_DMG_MULT[idx])
                        + (effectiveAp * SpellbladeOrigin.FIRE_AP_RATIO[idx])) * imbueWeight);
                victim.hurt(level.damageSources().inFire(), bonusDmg);
                break;
            }
            case ICE: {
                // Freeze duration normalized by attack speed during Overcharge
                double freezeSec = (SpellbladeOrigin.ICE_FREEZE_BASE_SEC[idx]
                        + (effectiveAp * SpellbladeOrigin.ICE_FREEZE_AP_SCALING[idx])) * imbueWeight;
                int freezeTicks = (int) (freezeSec * 20);
                victim.setTicksFrozen(victim.getTicksFrozen() + freezeTicks);
                break;
            }
            case LIGHTNING: {
                // Radius Splash Dmg + Haste Buff normalized by attack speed during Overcharge
                float splashDmg = (float) ((SpellbladeOrigin.LIGHTNING_SPLASH_BASE_DMG[idx]
                        + (effectiveAp * SpellbladeOrigin.LIGHTNING_AP_RATIO[idx])) * imbueWeight);
                AABB radiusBox = victim.getBoundingBox().inflate(4.0);
                for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, radiusBox,
                        e -> e != player && e != victim)) {
                    nearby.hurt(level.damageSources().lightningBolt(), splashDmg);
                    level.sendParticles(ParticleTypes.FLASH, nearby.getX(), nearby.getY() + 1.0, nearby.getZ(), 1, 0, 0,
                            0, 0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nearby.getX(), nearby.getY() + 1.0, nearby.getZ(),
                            8, 0.3, 0.3, 0.3, 0.1);
                }
                level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0,
                        0);
                // Apply Custom Lightning Haste effect (Attack Speed boost) to player
                player.addEffect(
                        new MobEffectInstance(ModEffects.LIGHTNING_HASTE.get(), 100, idx, false, false, false));
                break;
            }
            case NATURE: {
                // Absorption Shield on hit normalized by attack speed during Overcharge
                float shieldAmount = (float) ((SpellbladeOrigin.NATURE_SHIELD_BASE[idx]
                        + (effectiveAp * SpellbladeOrigin.NATURE_SHIELD_AP_RATIO[idx])) * imbueWeight);
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shieldAmount));
                break;
            }
            case AQUA: {
                // Water Mana Battery normalized by attack speed during Overcharge
                float bonusMana = (float) ((SpellbladeOrigin.WATER_MANA_BASE[idx]
                        + (effectiveAp * SpellbladeOrigin.WATER_MANA_AP_RATIO[idx])) * imbueWeight);
                try {
                    MagicData magicData = MagicData.getPlayerMagicData(player);
                    if (magicData != null) {
                        double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
                        float newMana = (float) Math.min(maxMana, magicData.getMana() + bonusMana);
                        magicData.setMana(newMana);
                        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
                    }
                } catch (Exception ignored) {
                }
                break;
            }
            case EVOCATION: {
                // Shockwave Knockback normalized by attack speed during Overcharge
                double knockbackDist = SpellbladeOrigin.EVOCATION_KNOCKBACK_DIST[idx] * imbueWeight;
                Vec3 vec = victim.position().subtract(player.position()).normalize().scale(knockbackDist * 0.4);
                victim.setDeltaMovement(victim.getDeltaMovement().add(vec.x, 0.3, vec.z));
                level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(), 3, 0.4,
                        0.4, 0.4, 0.1);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
                break;
            }
            case BLOOD: {
                // Bleed DoT & Anti-Heal status normalized by attack speed during Overcharge
                player.addEffect(new MobEffectInstance(ModEffects.BLOOD_BLEED.get(), 80, idx, false, false, false));
                float bleedDmg = (float) ((SpellbladeOrigin.BLOOD_BLEED_DOT_BASE[idx]
                        + effectiveAp * SpellbladeOrigin.BLOOD_BLEED_AP_RATIO[idx]) * imbueWeight);
                victim.hurt(level.damageSources().magic(), bleedDmg);
                break;
            }
            case ENDER: {
                // Armor Pierce % converted directly to True / Void damage + base void dmg + AP
                // scaling
                double piercePct = SpellbladeOrigin.ENDER_ARMOR_PIERCE_PCT[idx];
                float bonusVoid = (float) ((SpellbladeOrigin.ENDER_VOID_BASE_DMG[idx] + event.getAmount() * piercePct
                        + (effectiveAp * SpellbladeOrigin.ENDER_VOID_AP_RATIO[idx])) * imbueWeight);
                event.setAmount((float) (event.getAmount() * (1.0 - piercePct)));
                victim.hurt(level.damageSources().magic(), bonusVoid);
                break;
            }
            case ELDRITCH: {
                // Reality Collapse Rift: duration scaled by attack speed during Overcharge
                int durationTicks = (int) (60 * imbueWeight);
                victim.addEffect(
                        new MobEffectInstance(ModEffects.ELDRITCH_RIFT.get(), durationTicks, idx, false, false, false));
                break;
            }
        }

        // Spawn Iron's Spellbooks impact particles with directional attack velocity
        spawnElementalImpactParticles(level, player, victim, activeElement);

        // Consume single-strike charge if outside Overcharge
        if (!isOvercharge) {
            SpellbladeData.setHasImbueCharge(player, false);
        }
    }

    /**
     * Detonate Eldritch Reality Collapse Rift when effect expires.
     */
    @SubscribeEvent
    public static void onEldritchRiftExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect() == ModEffects.ELDRITCH_RIFT.get()) {
            LivingEntity victim = event.getEntity();
            if (victim != null && victim.level() instanceof ServerLevel level) {
                double absorbedDmg = victim.getPersistentData().getDouble("EldritchRiftAbsorbedDamage");
                victim.getPersistentData().remove("EldritchRiftAbsorbedDamage");
                if (absorbedDmg > 0) {
                    int amp = Math.min(4, Math.max(0, event.getEffectInstance().getAmplifier()));
                    double pct = SpellbladeOrigin.ELDRITCH_ABSORBED_BASE_PCT[amp];
                    if (event.getEntity().getLastHurtByMob() instanceof Player player) {
                        double effectiveAp = SpellbladeData.getEffectiveAP(player, SpellSchool.ELDRITCH);
                        pct += effectiveAp * SpellbladeOrigin.ELDRITCH_ABSORBED_AP_RATIO[amp];
                    }
                    float explosionDmg = (float) (absorbedDmg * pct);

                    victim.hurt(level.damageSources().magic(), explosionDmg);
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 1.2f);
                    level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(), 10,
                            0.5, 0.5, 0.5, 0.1);
                }
            }
        }
    }

    /**
     * Server tick for Spellblade players:
     * - Updates Overcharge stance window ticks.
     * - Updates 6-second enhanced attack ticks.
     * - Manages dynamic AP to AD conversion attribute modifier during Overcharge.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Player player = event.player;
        if (player == null)
            return;

        if (player.level().isClientSide()) {
            if (SpellbladeOrigin.ID.equals(com.complextalents.origin.client.ClientOriginData.getOriginId())) {
                player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> {
                    if (cap.getOverchargeTicks() > 0) {
                        cap.setOverchargeTicks(cap.getOverchargeTicks() - 1);
                    }
                    if (cap.getEnhancedAttackTicks() > 0) {
                        cap.setEnhancedAttackTicks(cap.getEnhancedAttackTicks() - 1);
                    }
                });
            }
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (!SpellbladeOrigin.isSpellblade(serverPlayer))
                return;

            int overchargeTicks = SpellbladeData.getOverchargeTicks(serverPlayer);
            if (overchargeTicks > 0) {
                SpellbladeData.setOverchargeTicks(serverPlayer, overchargeTicks - 1);

                // Apply dynamic AP -> AD conversion modifier during Overcharge based on active
                // skill level
                int skillLevel = Math.min(5,
                        Math.max(1, SkillManager.getSkillLevel(serverPlayer, SpellbladeOverchargeSkill.ID)));
                int idx = skillLevel - 1;

                double ap = serverPlayer.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
                double bonusAp = Math.max(0.0, ap - 1.0);
                double bonusAdPct = bonusAp * SpellbladeOverchargeSkill.AP_TO_AD_CONVERSION[idx];

                AttributeInstance adInst = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                if (adInst != null) {
                    adInst.removeModifier(OVERCHARGE_AD_MODIFIER_UUID);
                    if (bonusAdPct > 0) {
                        adInst.addTransientModifier(new AttributeModifier(OVERCHARGE_AD_MODIFIER_UUID,
                                "Spellblade Overcharge AD", bonusAdPct, AttributeModifier.Operation.MULTIPLY_TOTAL));
                    }
                }
            } else {
                // Remove AD modifier when Overcharge is inactive
                AttributeInstance adInst = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                if (adInst != null && adInst.getModifier(OVERCHARGE_AD_MODIFIER_UUID) != null) {
                    adInst.removeModifier(OVERCHARGE_AD_MODIFIER_UUID);
                }
            }

            int enhancedTicks = SpellbladeData.getEnhancedAttackTicks(serverPlayer);
            if (enhancedTicks > 0) {
                SpellbladeData.setEnhancedAttackTicks(serverPlayer, enhancedTicks - 1);
            }
        }
    }

    private static void spawnElementalImpactParticles(ServerLevel level, ServerPlayer player, LivingEntity victim,
            SpellSchool school) {
        Vec3 startPos = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        Vec3 attackDir = victim.position().subtract(player.position());
        if (attackDir.lengthSqr() < 0.0001) {
            attackDir = player.getLookAngle();
        } else {
            attackDir = attackDir.normalize();
        }

        String particleName = switch (school) {
            case FIRE -> "fire";
            case ICE -> "ice";
            case LIGHTNING -> "electricity";
            case NATURE -> "nature";
            case AQUA -> "acid_bubble";
            case EVOCATION -> "shockwave";
            case BLOOD -> "blood";
            case ENDER -> "unstable_ender";
            case ELDRITCH -> "ender";
            default -> "magic";
        };

        ParticleOptions particle = IronParticleHelper.getIronParticle(particleName);
        if (particle == null)
            return;

        for (int i = 0; i < 16; i++) {
            double spreadX = (level.random.nextDouble() - 0.5) * 0.4;
            double spreadY = (level.random.nextDouble() - 0.2) * 0.3;
            double spreadZ = (level.random.nextDouble() - 0.5) * 0.4;

            double vx = (attackDir.x * 0.35) + spreadX;
            double vy = 0.15 + spreadY;
            double vz = (attackDir.z * 0.35) + spreadZ;

            level.sendParticles(particle, startPos.x, startPos.y, startPos.z, 0, vx, vy, vz, 1.0);
        }

        if (school == SpellSchool.FIRE) {
            ParticleOptions ember = IronParticleHelper.getIronParticle("ember");
            if (ember != null) {
                for (int i = 0; i < 8; i++) {
                    double vx = (attackDir.x * 0.25) + (level.random.nextDouble() - 0.5) * 0.2;
                    double vy = 0.25 + level.random.nextDouble() * 0.2;
                    double vz = (attackDir.z * 0.25) + (level.random.nextDouble() - 0.5) * 0.2;
                    level.sendParticles(ember, startPos.x, startPos.y, startPos.z, 0, vx, vy, vz, 1.0);
                }
            }
        }
    }
}
