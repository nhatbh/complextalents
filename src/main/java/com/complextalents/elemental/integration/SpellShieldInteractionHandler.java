package com.complextalents.elemental.integration;

import com.complextalents.elemental.ElementType;
import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.elemental.MobElementService;
import com.nhatbh.basedefensev2.strength.ModAttributes;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles element-poise interaction for spell damage in ComplexTalents,
 * delegating strength/poise queries and mutations to basedefensev2's PoiseAPI.
 */
public class SpellShieldInteractionHandler {

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide)
            return;

        // 1. Check if target entity has a Poise / Shield system
        if (!PoiseAPI.hasPoise(entity))
            return;

        SpellDamageSource damageSource = event.getSpellDamageSource();
        if (damageSource == null)
            return;

        AbstractSpell spell = damageSource.spell();
        if (spell == null)
            return;

        ElementType sourceElement = mapSchoolTypeToElement(spell.getSchoolType());
        if (sourceElement == null)
            return;

        LivingEntity caster = damageSource.getEntity() instanceof LivingEntity living ? living : entity.getKillCredit();
        com.nhatbh.basedefensev2.elemental.ElementType baseTarget = MobElementService.getElement(entity);
        ElementType targetElement = null;
        if (baseTarget != null) {
            try {
                targetElement = ElementType.valueOf(baseTarget.name());
            } catch (Exception ignored) {}
        }

        float originalDamage = event.getAmount();
        float damage = originalDamage;

        boolean isExhausted = PoiseAPI.isExhausted(entity);
        float currentPoise = PoiseAPI.getCurrentPoise(entity);
        float maxPoise = PoiseAPI.getMaxPoise(entity);

        boolean isArcane = isArcaneSchool(sourceElement);
        boolean isElemental = isElementalSchool(sourceElement);

        // 2. Arcane Backfire (Self-elemental absorbs and heals poise)
        if (isArcane && targetElement != null && sourceElement == targetElement) {
            PoiseAPI.healPoise(entity, damage);
            event.setCanceled(true);
            notifyCaster(caster, Component.literal(String.format("§cBackfire: +%.0f Poise", damage)));
            return;
        }

        // 1. Calculate Pre-mitigated Base Damage (Apotheosis Armor Formula)
        float effectiveArmor = (float) entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        float preMitigatedDamage = originalDamage * (50.0f / (50.0f + Math.max(0.0f, effectiveArmor)));

        float poiseDamage = preMitigatedDamage;
        float vitalityDamage = preMitigatedDamage;
        String reactionMsg = null;

        // 3. Elemental Counter / Resistance Matrix
        if (isElemental) {
            float multiplier = getElementalMultiplier(sourceElement, targetElement);
            poiseDamage = preMitigatedDamage * multiplier;
            if (multiplier > 1.0f) {
                vitalityDamage = preMitigatedDamage * 1.50f; // Counter deals 1.5x Vitality damage to exposed targets
                reactionMsg = "§aCounter (2.0x): -%.0f Poise";
            } else if (multiplier < 1.0f) {
                vitalityDamage = preMitigatedDamage * 0.50f;
                reactionMsg = "§cResisted (0.5x): -%.0f Poise";
            }
        }
        // 4. Arcane School Unique Mechanics
        else if (isArcane) {
            switch (sourceElement) {
                case HOLY -> {
                    if (maxPoise > 0) {
                        float scaleFactor = 1.0f + (currentPoise / maxPoise);
                        poiseDamage = preMitigatedDamage * scaleFactor;
                        vitalityDamage = preMitigatedDamage;
                        reactionMsg = String.format("§eSmite (%.1fx): -%%.0f Poise", scaleFactor);
                    }
                }
                case EVOCATION -> {
                    if (maxPoise > 0) {
                        float missingPoise = maxPoise - currentPoise;
                        float scaleFactor = 1.0f + (missingPoise / maxPoise);
                        poiseDamage = preMitigatedDamage * scaleFactor;
                        vitalityDamage = preMitigatedDamage;

                        // Dark Mage Evocation Synergy: Bonus Shield & Poise Damage
                        if (caster instanceof ServerPlayer player
                                && com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                            int originLevel = Math.min(4,
                                    Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                            double bonusPct = com.complextalents.impl.darkmage.origin.DarkMageOrigin.EVOCATION_SHIELD_POISE_BONUS[originLevel];
                            poiseDamage *= (1.0f + (float) bonusPct);
                        }

                        reactionMsg = String.format("§dSoul Rend (%.1fx): -%%.0f Poise", scaleFactor);
                    }
                }
                case ENDER -> {
                    float voidVitMult = 2.0f;
                    if (caster instanceof ServerPlayer player
                            && com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                        int originLevel = Math.min(4,
                                Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                        boolean isDowned = isExhausted || entity.getHealth() <= entity.getMaxHealth() * 0.3f;
                        if (isDowned) {
                            voidVitMult = (float) com.complextalents.impl.darkmage.origin.DarkMageOrigin.ENDER_FLANK_DOWNED_MULT[originLevel];
                        }
                    }

                    poiseDamage = preMitigatedDamage * 0.5f;
                    vitalityDamage = preMitigatedDamage * voidVitMult;
                    reactionMsg = String.format("§5Execution (%.1fx): %.0f Damage", voidVitMult, vitalityDamage);
                }
                case ELDRITCH -> {
                    if (caster instanceof ServerPlayer player
                            && com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                        int originLevel = Math.min(4,
                                Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                        int currentEntropy = com.complextalents.passive.PassiveManager.getPassiveStacks(player,
                                "entropy");
                        double requiredThreshold = com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_REQUIRED_THRESHOLD[originLevel];

                        if (currentEntropy >= requiredThreshold) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    com.complextalents.effect.ModEffects.POSSESSED.get(), 300, 0));
                            float nukeMult = (float) com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_MAX_NUKE_MULT[originLevel];
                            vitalityDamage = preMitigatedDamage * nukeMult;
                            poiseDamage = preMitigatedDamage * 0.5f;
                            reactionMsg = String.format("§dCosmic Redline (%.1fx): %.0f Damage [POSSESSED]", nukeMult,
                                    vitalityDamage);
                        } else {
                            float backfireHp = (float) (player.getMaxHealth()
                                     * com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_BACKFIRE_SELF_DMG[originLevel]);
                            player.hurt(player.damageSources().magic(), backfireHp);
                            int silenceTicks = (int) (com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_BACKFIRE_SILENCE_SEC[originLevel]
                                     * 20);
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    com.complextalents.effect.ModEffects.SILENCED.get(), silenceTicks, 0));
                            poiseDamage = 0;
                            vitalityDamage = 0;
                            reactionMsg = "§cEldritch Backfire!";
                        }
                    } else {
                        poiseDamage = preMitigatedDamage * 0.5f;
                        vitalityDamage = preMitigatedDamage;
                        reactionMsg = String.format("§4Decay: %.0f Damage", vitalityDamage);
                    }
                }
                case BLOOD -> {
                    float lifestealPct = 0.25f;
                    if (caster instanceof ServerPlayer player) {
                        net.minecraft.world.effect.MobEffectInstance exhaustion = player
                                .getEffect(com.complextalents.effect.ModEffects.BLOOD_EXHAUSTION.get());
                        if (exhaustion != null) {
                            int stacks = Math.min(3, exhaustion.getAmplifier() + 1);
                            float lifestealReduction = Math.max(0.0f, 1.0f - (stacks / 3.0f));
                            lifestealPct *= lifestealReduction;
                        }

                        if (com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                            int originLevel = Math.min(4,
                                    Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                            boolean isDowned = isExhausted || entity.getHealth() <= entity.getMaxHealth() * 0.3f;
                            if (isDowned) {
                                lifestealPct *= (float) com.complextalents.impl.darkmage.origin.DarkMageOrigin.BLOOD_DOWNED_LIFESTEAL_MULT[originLevel];
                            }
                        }
                    }

                    poiseDamage = preMitigatedDamage * 0.5f;
                    vitalityDamage = preMitigatedDamage;

                    float lifestealAmount = vitalityDamage * lifestealPct;
                    if (caster != null && lifestealAmount > 0) {
                        caster.heal(lifestealAmount);
                        reactionMsg = String.format("§cLifesteal: +%.1f HP", lifestealAmount);
                    }
                }
                case ABYSSAL -> {
                    poiseDamage = preMitigatedDamage * 0.25f;
                    float pressureMult = 1.25f;
                    if (caster instanceof ServerPlayer player
                            && com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                        int originLevel = Math.min(4,
                                Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                        int currentEntropy = com.complextalents.passive.PassiveManager.getPassiveStacks(player, "entropy");
                        if (currentEntropy > 0) {
                            pressureMult += (currentEntropy * 0.05f); // +5% per entropy stack
                        }
                    }
                    vitalityDamage = preMitigatedDamage * pressureMult;
                    reactionMsg = String.format("§1Depth Pressure (%.2fx): %.0f Damage", pressureMult, vitalityDamage);
                }
                case TECHNOMANCY -> {
                    poiseDamage = preMitigatedDamage * 1.40f;
                    vitalityDamage = preMitigatedDamage;
                    reactionMsg = String.format("§6Kinetic Overcharge (1.4x): -%.0f Poise", poiseDamage);
                }
                default -> {
                }
            }
        }

        // 5. Apply Attribute Multipliers Safely
        boolean isCounter = isElemental && getElementalMultiplier(sourceElement, targetElement) > 1.0f;
        AttributeInstance attr = entity.getAttribute(isCounter 
                ? ModAttributes.SPECIAL_STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get() 
                : ModAttributes.STRENGTH_DAMAGE_TAKEN_MULTIPLIER.get());
        if (attr != null) {
            poiseDamage *= (float) attr.getValue();
        }

        // Notify Actionbar
        if (reactionMsg != null) {
            if (reactionMsg.contains("%")) {
                notifyCaster(caster, Component.literal(String.format(reactionMsg, poiseDamage)));
            } else {
                notifyCaster(caster, Component.literal(reactionMsg));
            }
        }

        // 6. Inflict Poise & Vitality Damage via Single Entry Point API with "IronsSpells" sourceMod identifier
        PoiseAPI.damagePoise(entity, poiseDamage, vitalityDamage, caster, damageSource, true, "IronsSpells");

        // Prevent vanilla/default hurt listener from double-damaging poise
        entity.getPersistentData().putBoolean("SkipStrengthDamage", true);
        event.setAmount(0.0001f);
    }

    private static void notifyCaster(LivingEntity caster, Component message) {
        if (caster instanceof ServerPlayer player) {
            player.sendSystemMessage(message, true);
        }
    }

    private static boolean isElementalSchool(ElementType element) {
        if (element == null)
            return false;
        return switch (element) {
            case FIRE, NATURE, AQUA, LIGHTNING, ICE -> true;
            default -> false;
        };
    }

    private static boolean isArcaneSchool(ElementType element) {
        if (element == null)
            return false;
        return switch (element) {
            case HOLY, EVOCATION, ENDER, ELDRITCH, BLOOD, ABYSSAL, TECHNOMANCY -> true;
            default -> false;
        };
    }

    private static float getElementalMultiplier(ElementType source, ElementType target) {
        if (target == null || source == null)
            return 1.0f;
        if (!isElementalSchool(source) || !isElementalSchool(target))
            return 1.0f;

        if (source == ElementType.FIRE && target == ElementType.NATURE)
            return 2.0f;
        if (source == ElementType.NATURE && target == ElementType.AQUA)
            return 2.0f;
        if (source == ElementType.AQUA && target == ElementType.LIGHTNING)
            return 2.0f;
        if (source == ElementType.LIGHTNING && target == ElementType.ICE)
            return 2.0f;
        if (source == ElementType.ICE && target == ElementType.FIRE)
            return 2.0f;

        if (source == ElementType.NATURE && target == ElementType.FIRE)
            return 0.5f;
        if (source == ElementType.AQUA && target == ElementType.NATURE)
            return 0.5f;
        if (source == ElementType.LIGHTNING && target == ElementType.AQUA)
            return 0.5f;
        if (source == ElementType.ICE && target == ElementType.LIGHTNING)
            return 0.5f;
        if (source == ElementType.FIRE && target == ElementType.ICE)
            return 0.5f;

        return 1.0f;
    }

    private static ElementType mapSchoolTypeToElement(SchoolType schoolType) {
        if (schoolType == null)
            return null;
        String schoolPath = schoolType.getId().getPath();
        return switch (schoolPath) {
            case "fire" -> ElementType.FIRE;
            case "ice" -> ElementType.ICE;
            case "lightning" -> ElementType.LIGHTNING;
            case "ender" -> ElementType.ENDER;
            case "nature" -> ElementType.NATURE;
            case "blood" -> ElementType.BLOOD;
            case "holy" -> ElementType.HOLY;
            case "evocation" -> ElementType.EVOCATION;
            case "eldritch" -> ElementType.ELDRITCH;
            case "aqua" -> ElementType.AQUA;
            case "abyssal" -> ElementType.ABYSSAL;
            case "technomancy" -> ElementType.TECHNOMANCY;
            default -> null;
        };
    }
}
