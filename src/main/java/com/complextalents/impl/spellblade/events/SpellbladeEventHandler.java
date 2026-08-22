package com.complextalents.impl.spellblade.events;

import com.complextalents.TalentsMod;
import com.nhatbh.basedefensev2.api.PoiseAPI;
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

import com.complextalents.network.PacketHandler;
import com.complextalents.spellfx.network.S2CSpellFXPacket;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import com.complextalents.impl.spellblade.SpellbladeDataProvider;
import com.complextalents.impl.spellblade.skill.SpellbladeOverchargeSkill;
import com.complextalents.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellbladeEventHandler {

    private static final UUID SPELLBLADE_AP_TO_AD_UUID = UUIDHelper.generateAttributeModifierUUID("spellblade",
            "ap_to_ad");
    private static final UUID OVERCHARGE_AD_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("spellblade",
            "overcharge_ad");
    private static final UUID EVOCATION_IMPACT_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("spellblade",
            "evocation_impact");
    private static final ThreadLocal<Boolean> IS_PROCESSING_HURT = ThreadLocal.withInitial(() -> false);

    /**
     * Intercept spell casting for Spellblade players:
     * - Swaps active weapon imbue to cast spell's school.
     * - Imbue Grant: 6 seconds (120 ticks) of elemental imbue.
     * - Overcharge Free Cast Mechanic:
     *   1. If switching element during Overcharge while current imbue timer is > 50% (> 60 ticks), grant Free Cast.
     *   2. If Free Cast is active, consume it for this cast (handled in AbstractSpellMixin for 0 mana).
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
            SpellSchool newSchool = SpellSchool.fromString(schoolPath);

            SpellSchool currentElement = SpellbladeData.getActiveElement(serverPlayer);
            int currentTicks = SpellbladeData.getEnhancedAttackTicks(serverPlayer);
            boolean isOvercharge = SpellbladeData.isOverchargeStance(serverPlayer);

            if (newSchool != null) {
                // Free Cast Trigger: Switching elemental imbue during Overcharge when timer is > 50% (> 60 ticks)
                if (isOvercharge && currentElement != null && currentElement != newSchool && currentTicks > 60) {
                    SpellbladeData.setFreeCast(serverPlayer, true);
                    ServerLevel level = serverPlayer.serverLevel();
                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1);
                }

                // Set active element to cast spell's school and reset 6s imbue duration
                SpellbladeData.setActiveElement(serverPlayer, newSchool);
                SpellbladeData.setEnhancedAttackTicks(serverPlayer, 120);
            }

            // Consume Free Cast charge if active for this spell cast
            if (SpellbladeData.hasFreeCast(serverPlayer)) {
                SpellbladeData.setFreeCast(serverPlayer, false);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Intercept melee strikes by Spellblade players:
     * - Normal Mode: Mana Weaver Passive restores mana based on inverse attack speed.
     * - Overcharge Stance Mode: Calculates AP bonus magic damage when elemental imbue is active.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!SpellbladeOrigin.isSpellblade(player))
            return;

        int originLevel = Math.min(5, Math.max(1, OriginManager.getOriginLevel(player)));
        int originIdx = originLevel - 1;

        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0.2)
            attackSpeed = 0.2;
        double weightMult = 1.0 / attackSpeed;

        boolean isStanceOn = SpellbladeData.isOverchargeStance(player);

        if (!isStanceOn) {
            // Normal Mode: Mana Weaver Passive - Restore Mana based on inverse attack speed
            double baseManaPct = SpellbladeOrigin.BASE_MANA_PER_HIT[originIdx];

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
        } else {
            // Overcharge Stance Mode: Consume Mana per hit and calculate AP bonus magic damage (ONLY if elemental imbue is active)
            SpellSchool activeElement = SpellbladeData.getActiveElement(player);
            boolean isImbueActive = (SpellbladeData.getEnhancedAttackTicks(player) > 0 || SpellbladeData.hasImbueCharge(player)) && activeElement != null;

            if (isImbueActive) {
                int skillLevel = Math.min(5, Math.max(1, SkillManager.getSkillLevel(player, SpellbladeOverchargeSkill.ID)));
                int skillIdx = skillLevel - 1;

                double totalAd = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                double adGain = Math.max(1.0, totalAd - 1.0); // Raw weapon damage gain by subtracting 1.0 unarmed AD
                double effectiveAp = SpellbladeData.getEffectiveAP(player, activeElement);

                double bonusMagicDmg = adGain * effectiveAp * SpellbladeOrigin.AP_DAMAGE_GAIN_RATIO[skillIdx];

                double aquaMult = (activeElement == SpellSchool.AQUA) ? 0.5 : 1.0;
                double manaDrainCost = (SpellbladeOrigin.BASE_MANA_DRAIN_PER_HIT[skillIdx]
                        + (bonusMagicDmg * SpellbladeOrigin.MANA_DRAIN_DAMAGE_SCALING[skillIdx])) * aquaMult;

                try {
                    MagicData magicData = MagicData.getPlayerMagicData(player);
                    if (magicData != null) {
                        boolean insufficientMana = magicData.getMana() < manaDrainCost;

                        // Consume remaining mana (or set to 0 if insufficient)
                        float newMana = (float) Math.max(0.0, magicData.getMana() - manaDrainCost);
                        magicData.setMana(newMana);
                        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));

                        // Register hit as enhanced
                        player.getPersistentData().putDouble("SpellbladeBonusMagicDmg", bonusMagicDmg);
                        player.getPersistentData().putBoolean("SpellbladeWasStanceHit", true);
                        if (activeElement != null) {
                            player.getPersistentData().putString("SpellbladeStanceElement", activeElement.name());
                        }

                        if (insufficientMana) {
                            // Out of Mana -> Deactivate Overcharge Stance AFTER registering the enhanced strike
                            SpellbladeData.setOverchargeStance(player, false);
                            ServerLevel level = player.serverLevel();
                            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
                            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                                    25, 0.3, 0.5, 0.3, 0.05);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Intercept damage dealt by Spellblade players to apply Enhanced Elemental Imbue
     * and Overcharge Stance AP magic damage.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (IS_PROCESSING_HURT.get()) {
            return;
        }
        try {
            IS_PROCESSING_HURT.set(true);

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
            // Trigger Impact & Screen Shake feedback (debounced to once per swing)
            long currentGameTime = player.level().getGameTime();
            long lastImpactTick = player.getPersistentData().getLong("SpellbladeLastImpactTick");

            if (currentGameTime - lastImpactTick > 6) {
                player.getPersistentData().putLong("SpellbladeLastImpactTick", currentGameTime);

                boolean wasStanceHit = player.getPersistentData().getBoolean("SpellbladeWasStanceHit");
                boolean isStanceOn = SpellbladeData.isOverchargeStance(player) || wasStanceHit;
                int enhancedTicks = SpellbladeData.getEnhancedAttackTicks(player);
                boolean hasCharge = SpellbladeData.hasImbueCharge(player);

                SpellSchool activeElement = SpellbladeData.getActiveElement(player);
                if (activeElement == null && wasStanceHit && player.getPersistentData().contains("SpellbladeStanceElement")) {
                    try {
                        activeElement = SpellSchool.valueOf(player.getPersistentData().getString("SpellbladeStanceElement"));
                    } catch (Exception ignored) {}
                }

                boolean isImbued = activeElement != null && (enhancedTicks > 0 || hasCharge);
                int flashColor = isImbued ? getSchoolColorHex(activeElement) : 0;
                
                // Query weapon stun potential / impact attribute from Epic Fight
                double impactStat = 1.0;
                var impactAttr = player.getAttribute(EpicFightAttributes.IMPACT.get());
                if (impactAttr != null) {
                    impactStat = impactAttr.getValue();
                }

                // Impact intensity scales dynamically with weapon stun potential (e.g. Dagger ~22, Sword ~35, Greatsword ~65)
                double stanceMult = isStanceOn ? 1.35 : 1.0;
                int impactIntensity = Math.min(100, Math.max(15, (int) (35.0 * Math.sqrt(impactStat) * stanceMult)));

                PacketHandler.sendTo(new S2CSpellFXPacket(impactIntensity, flashColor, 0.18f), player);
            }

            boolean wasStanceHit = player.getPersistentData().getBoolean("SpellbladeWasStanceHit");
            player.getPersistentData().remove("SpellbladeWasStanceHit");
            boolean isStanceOn = SpellbladeData.isOverchargeStance(player) || wasStanceHit;
            int enhancedTicks = SpellbladeData.getEnhancedAttackTicks(player);
            boolean hasCharge = SpellbladeData.hasImbueCharge(player);

            ServerLevel level = player.serverLevel();

            SpellSchool activeElement = SpellbladeData.getActiveElement(player);
            if (activeElement == null && wasStanceHit && player.getPersistentData().contains("SpellbladeStanceElement")) {
                try {
                    activeElement = SpellSchool.valueOf(player.getPersistentData().getString("SpellbladeStanceElement"));
                } catch (Exception ignored) {
                }
            }
            player.getPersistentData().remove("SpellbladeStanceElement");

            boolean isImbueActive = (enhancedTicks > 0 || hasCharge) && activeElement != null;

            // Apply Overcharge AP bonus magic damage on hit (ONLY when overcharged AND has an active elemental imbue)
            if (isStanceOn && isImbueActive && player.getPersistentData().contains("SpellbladeBonusMagicDmg")) {
                double bonusMagicDmg = player.getPersistentData().getDouble("SpellbladeBonusMagicDmg");
                player.getPersistentData().remove("SpellbladeBonusMagicDmg");
                if (bonusMagicDmg > 0) {
                    victim.hurt(level.damageSources().indirectMagic(null, player), (float) bonusMagicDmg);
                }
            } else {
                player.getPersistentData().remove("SpellbladeBonusMagicDmg");
            }

            // If no active element or imbue, skip elemental imbue effects and particles
            if (!isImbueActive) {
                if (!isStanceOn && enhancedTicks <= 0) {
                    SpellbladeData.setHasImbueCharge(player, false);
                }
                return;
            }

            int originLevel = Math.min(5, Math.max(1, OriginManager.getOriginLevel(player)));
            int originIdx = originLevel - 1;

            int skillLevel = Math.min(5, Math.max(1, SkillManager.getSkillLevel(player, SpellbladeOverchargeSkill.ID)));
            int skillIdx = skillLevel - 1;

            double enhancedMult = isStanceOn ? SpellbladeOrigin.ENHANCED_EFFECT_MULT[skillIdx] : 1.0;

            double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            if (attackSpeed <= 0.2) attackSpeed = 0.2;
            double weightMult = 1.0 / attackSpeed;

            // Effective AP scaling with School-Specific Spell Power
            double effectiveAp = SpellbladeData.getEffectiveAP(player, activeElement);

            switch (activeElement) {
                case FIRE: {
                    // Fire: Deal bonus magic damage AND light target on fire
                    float bonusDmg = (float) (((event.getAmount() * SpellbladeOrigin.FIRE_DMG_MULT[originIdx])
                            + (effectiveAp * SpellbladeOrigin.FIRE_AP_RATIO[originIdx])) * enhancedMult * weightMult);
                    victim.hurt(level.damageSources().indirectMagic(null, player), bonusDmg);
                    if (!(victim instanceof Player)) {
                        int burnSec = (int) Math.max(2, Math.round(5.0 * weightMult * enhancedMult));
                        victim.setSecondsOnFire(burnSec);
                    }
                    break;
                }
                case ICE: {
                    // Ice: Apply freeze stacks (duration scales with AP & weightMult)
                    if (!(victim instanceof Player)) {
                        double freezeSec = (SpellbladeOrigin.ICE_FREEZE_BASE_SEC[originIdx]
                                + (effectiveAp * SpellbladeOrigin.ICE_FREEZE_AP_SCALING[originIdx])) * enhancedMult * weightMult;
                        int freezeTicks = (int) (freezeSec * 20);
                        victim.setTicksFrozen(victim.getTicksFrozen() + freezeTicks);
                    }
                    break;
                }
                case LIGHTNING: {
                    // Lightning: Fixed AoE splash distance (5-8m radius), damage scales with AP, weightMult & enhancedMult
                    double radius = Math.min(8.0, 5.0 + originIdx * 0.5 + effectiveAp * 0.05);
                    float splashDmg = (float) ((SpellbladeOrigin.LIGHTNING_SPLASH_BASE_DMG[originIdx]
                            + (effectiveAp * SpellbladeOrigin.LIGHTNING_AP_RATIO[originIdx])) * enhancedMult * weightMult);
                    AABB radiusBox = victim.getBoundingBox().inflate(radius);
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, radiusBox,
                            e -> e != player && e != victim && !(e instanceof Player))) {
                        nearby.hurt(level.damageSources().indirectMagic(null, player), splashDmg);
                        level.sendParticles(ParticleTypes.FLASH, nearby.getX(), nearby.getY() + 1.0, nearby.getZ(), 1, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nearby.getX(), nearby.getY() + 1.0, nearby.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    }
                    level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
                    break;
                }
                case NATURE: {
                    // Nature: Gain Absorption Shield (shield amount scales with AP & weightMult)
                    float shieldAmount = (float) ((SpellbladeOrigin.NATURE_SHIELD_BASE[originIdx]
                            + (effectiveAp * SpellbladeOrigin.NATURE_SHIELD_AP_RATIO[originIdx])) * enhancedMult * weightMult);
                    player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shieldAmount));
                    break;
                }
                case AQUA: {
                    // Aqua: Gain Attack Speed (Haste) on self AND apply Tidal Erosion (max 3 stacks, -5% armor per stack) to victim
                    int hasteAmp = Math.min(4, (int) (effectiveAp / 25.0));
                    int hasteDuration = (int) ((80 + effectiveAp * 2.0) * weightMult * enhancedMult);
                    player.addEffect(new MobEffectInstance(ModEffects.LIGHTNING_HASTE.get(), hasteDuration, hasteAmp, false, false, false));

                    if (victim != null && !(victim instanceof Player)) {
                        int currentAmp = victim.hasEffect(ModEffects.TIDAL_EROSION.get())
                                ? victim.getEffect(ModEffects.TIDAL_EROSION.get()).getAmplifier()
                                : -1;
                        int newAmp = Math.min(2, currentAmp + 1); // 0 = 1 stack (-5%), 1 = 2 stacks (-10%), 2 = 3 stacks (-15%)
                        victim.addEffect(new MobEffectInstance(ModEffects.TIDAL_EROSION.get(), hasteDuration, newAmp, false, false, true));
                    }
                    break;
                }
                case EVOCATION: {
                    // Evocation: Increase weapon Impact attribute aggressively scaling with weapon weight (weightMult^1.75)
                    try {
                        double aggressiveWeight = Math.pow(weightMult, 1.75);
                        double bonusImpact = (1.5 + effectiveAp * 0.05) * enhancedMult * aggressiveWeight;
                        var impactAttr = player.getAttribute(EpicFightAttributes.IMPACT.get());
                        if (impactAttr != null) {
                            impactAttr.removeModifier(EVOCATION_IMPACT_MODIFIER_UUID);
                            impactAttr.addTransientModifier(new AttributeModifier(EVOCATION_IMPACT_MODIFIER_UUID, "EvocationImpactBonus", bonusImpact, AttributeModifier.Operation.ADDITION));
                        }
                    } catch (Exception ignored) {}
                    level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(), 3, 0.4, 0.4, 0.4, 0.1);
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
                    break;
                }
                case BLOOD: {
                    // Blood: Apply Bleed DoT (deals static 1% Max HP per sec, duration scales with AP, weightMult & enhancedMult)
                    if (!(victim instanceof Player)) {
                        int bleedDuration = (int) ((80 + effectiveAp * 2.0) * weightMult * enhancedMult);
                        victim.addEffect(new MobEffectInstance(ModEffects.BLOOD_BLEED.get(), bleedDuration, 0, false, false, false));
                    }
                    break;
                }
                case ENDER: {
                    // Ender: Bonus Void damage ONLY registers when target's poise/shield is down (using PoiseAPI)
                    boolean isPoiseBroken = PoiseAPI.isExhausted(victim)
                            || (PoiseAPI.hasPoise(victim) && PoiseAPI.getCurrentPoise(victim) <= 0.001f);
                    if (isPoiseBroken) {
                        double shieldMult = 1.5 + effectiveAp * 0.01;
                        float bonusVoid = (float) ((SpellbladeOrigin.ENDER_VOID_BASE_DMG[originIdx] + event.getAmount() * 0.5
                                + (effectiveAp * SpellbladeOrigin.ENDER_VOID_AP_RATIO[originIdx])) * enhancedMult * weightMult * shieldMult);
                        victim.hurt(level.damageSources().indirectMagic(null, player), bonusVoid);
                    }
                    break;
                }
                case ELDRITCH: {
                    // Eldritch: Void Execute — Triggers if target HP <= % max HP threshold AND (Current HP + Current Poise) <= Execute Cap
                    if (!(victim instanceof Player)) {
                        double maxHpPct = 0.05 + (originIdx * 0.025); // 5% at level 1 up to 15% at level 5
                        float hpThreshold = (float) (victim.getMaxHealth() * maxHpPct * enhancedMult);

                        float currentHp = victim.getHealth();
                        float currentPoise = (PoiseAPI.hasPoise(victim) && !PoiseAPI.isExhausted(victim))
                                ? Math.max(0.0f, PoiseAPI.getCurrentPoise(victim))
                                : 0.0f;
                        float totalHpPlusPoise = currentHp + currentPoise;

                        float executeCap = (float) ((20.0 + effectiveAp * 2.5) * weightMult * enhancedMult);

                        if (currentHp <= hpThreshold && totalHpPlusPoise <= executeCap) {
                            // Execute target!
                            victim.hurt(level.damageSources().indirectMagic(null, player), currentHp + currentPoise + 100.0f);
                            level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.5f);
                        }
                    }
                    break;
                }
            }

            // Spawn Iron's Spellbooks impact particles with directional attack velocity
            spawnElementalImpactParticles(level, player, victim, activeElement);

            // Consume single-strike charge if outside Overcharge and enhanced duration expired
            if (!isStanceOn && enhancedTicks <= 0) {
                SpellbladeData.setHasImbueCharge(player, false);
            }
        } finally {
            IS_PROCESSING_HURT.set(false);
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
            if (victim != null && !(victim instanceof Player) && victim.level() instanceof ServerLevel level) {
                double absorbedDmg = victim.getPersistentData().getDouble("EldritchRiftAbsorbedDamage");
                victim.getPersistentData().remove("EldritchRiftAbsorbedDamage");
                if (absorbedDmg > 0) {
                    int amp = Math.min(4, Math.max(0, event.getEffectInstance().getAmplifier()));
                    double pct = SpellbladeOrigin.ELDRITCH_ABSORBED_BASE_PCT[amp];
                    Player player = null;
                    if (event.getEntity().getLastHurtByMob() instanceof Player p) {
                        player = p;
                        double effectiveAp = SpellbladeData.getEffectiveAP(player, SpellSchool.ELDRITCH);
                        pct += effectiveAp * SpellbladeOrigin.ELDRITCH_ABSORBED_AP_RATIO[amp];
                    }
                    float explosionDmg = (float) (absorbedDmg * pct);

                    var dmgSource = player != null ? level.damageSources().indirectMagic(null, player) : level.damageSources().magic();
                    victim.hurt(dmgSource, explosionDmg);
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
     * - Decrements 6-second enhanced attack ticks if active.
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
                    if (cap.getEnhancedAttackTicks() > 0) {
                        cap.setEnhancedAttackTicks(cap.getEnhancedAttackTicks() - 1);
                    }
                });
            }
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            AttributeInstance adAttr = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);

            if (!SpellbladeOrigin.isSpellblade(serverPlayer)) {
                if (adAttr != null && adAttr.getModifier(SPELLBLADE_AP_TO_AD_UUID) != null) {
                    adAttr.removeModifier(SPELLBLADE_AP_TO_AD_UUID);
                }
                return;
            }

            // Update AP-to-AD Attribute Modifier (0.10x to 0.30x of BONUS Spell Power as % AD)
            if (adAttr != null) {
                int originLevel = Math.min(5, Math.max(1, OriginManager.getOriginLevel(serverPlayer)));
                double ratio = SpellbladeOrigin.AP_TO_AD_RATIO[originLevel - 1];
                double spellPower = serverPlayer.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
                double bonusSpellPower = Math.max(0.0, spellPower - 1.0);
                double bonusAd = bonusSpellPower * ratio;

                AttributeModifier currentMod = adAttr.getModifier(SPELLBLADE_AP_TO_AD_UUID);
                if (currentMod == null || Math.abs(currentMod.getAmount() - bonusAd) > 0.001) {
                    if (currentMod != null) {
                        adAttr.removeModifier(SPELLBLADE_AP_TO_AD_UUID);
                    }
                    adAttr.addTransientModifier(new AttributeModifier(SPELLBLADE_AP_TO_AD_UUID, "Spellblade AP to AD", bonusAd, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }

            // Decrement imbue duration and trigger Free Cast when imbue expires naturally in Overcharge
            int enhancedTicks = SpellbladeData.getEnhancedAttackTicks(serverPlayer);
            if (enhancedTicks > 0) {
                int nextTicks = enhancedTicks - 1;
                SpellbladeData.setEnhancedAttackTicks(serverPlayer, nextTicks);
                if (nextTicks == 0 && SpellbladeData.isOverchargeStance(serverPlayer)) {
                    SpellbladeData.setFreeCast(serverPlayer, true);
                    ServerLevel level = serverPlayer.serverLevel();
                    level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
    }

    private static void spawnElementalImpactParticles(ServerLevel level, ServerPlayer player, LivingEntity victim,
            SpellSchool school) {
        if (school == null || victim == null)
            return;
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

    /**
     * Display Overcharge Stance bonus magic damage tooltip on held weapons for Spellblade players.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null)
            return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        if (!(stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem)) {
            if (!stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) {
                return;
            }
        }

        // Check if player is holding this item in mainhand
        if (player.getMainHandItem() != stack)
            return;

        if (!SpellbladeOrigin.ID.equals(com.complextalents.origin.client.ClientOriginData.getOriginId()))
            return;

        var capOpt = player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).resolve();
        if (capOpt.isEmpty())
            return;

        var cap = capOpt.get();
        if (!cap.isOverchargeStance())
            return;

        double totalAd = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double adGain = Math.max(1.0, totalAd - 1.0);
        SpellSchool activeSchool = cap.getActiveElement();
        double effectiveAp = SpellbladeData.getEffectiveAP(player, activeSchool);

        int skillLevel = Math.min(5, Math.max(1, com.complextalents.skill.client.ClientSkillData.getSkillLevel(SpellbladeOverchargeSkill.ID)));
        int skillIdx = skillLevel - 1;

        double bonusMagicDmg = adGain * effectiveAp * SpellbladeOrigin.AP_DAMAGE_GAIN_RATIO[skillIdx];
        double aquaMult = (activeSchool == SpellSchool.AQUA) ? 0.5 : 1.0;
        double manaDrainCost = (SpellbladeOrigin.BASE_MANA_DRAIN_PER_HIT[skillIdx]
                + (bonusMagicDmg * SpellbladeOrigin.MANA_DRAIN_DAMAGE_SCALING[skillIdx])) * aquaMult;

        String schoolName = activeSchool != null ? activeSchool.name() : "RAW MAGIC";
        ChatFormatting schoolColor = activeSchool != null ? getSchoolFormatting(activeSchool) : ChatFormatting.LIGHT_PURPLE;

        Component tooltipLine = Component.literal("  + ")
                .append(Component.literal(String.format("%.1f", bonusMagicDmg)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" Bonus Magic Damage (").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(schoolName).withStyle(schoolColor, ChatFormatting.BOLD))
                .append(Component.literal(" Overcharge)").withStyle(ChatFormatting.DARK_PURPLE));

        Component drainLine = Component.literal("  - ")
                .append(Component.literal(String.format("%.1f", manaDrainCost)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" Mana per hit").withStyle(ChatFormatting.GRAY));

        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.literal("When in Main Hand (Overcharge Stance):").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(tooltipLine);
        event.getToolTip().add(drainLine);
    }

    private static ChatFormatting getSchoolFormatting(SpellSchool school) {
        if (school == null)
            return ChatFormatting.WHITE;
        return switch (school) {
            case FIRE -> ChatFormatting.RED;
            case ICE -> ChatFormatting.AQUA;
            case LIGHTNING -> ChatFormatting.YELLOW;
            case NATURE -> ChatFormatting.GREEN;
            case AQUA -> ChatFormatting.DARK_AQUA;
            case EVOCATION -> ChatFormatting.WHITE;
            case BLOOD -> ChatFormatting.DARK_RED;
            case ENDER -> ChatFormatting.DARK_PURPLE;
            case ELDRITCH -> ChatFormatting.DARK_PURPLE;
            case HOLY -> ChatFormatting.GOLD;
        };
    }

    private static int getSchoolColorHex(SpellSchool school) {
        if (school == null) return 0xFFFFFFFF;
        return switch (school) {
            case FIRE -> 0xE05A47;
            case ICE -> 0x6BBBC9;
            case LIGHTNING -> 0xCFB34A;
            case NATURE -> 0x68A378;
            case AQUA -> 0x5592C2;
            case EVOCATION -> 0xE0E0E0;
            case BLOOD -> 0xB22222;
            case ENDER -> 0x9366BF;
            case ELDRITCH -> 0x8A2BE2;
            case HOLY -> 0xFFFFE0;
        };
    }
}
