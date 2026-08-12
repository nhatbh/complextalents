package com.complextalents.elemental.integration;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.elemental.ElementType;
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
        ElementType targetElement = MobElementService.getElement(entity);

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

        float poiseDamage = damage;
        String reactionMsg = null;

        // 3. Elemental Counter / Resistance Matrix
        if (isElemental) {
            float multiplier = getElementalMultiplier(sourceElement, targetElement);
            poiseDamage = damage * multiplier;
            if (multiplier > 1.0f) {
                reactionMsg = "§aCounter (2.0x): -%.0f Poise";
            } else if (multiplier < 1.0f) {
                reactionMsg = "§cResisted (0.5x): -%.0f Poise";
            }
        }
        // 4. Arcane School Unique Mechanics
        else if (isArcane) {
            switch (sourceElement) {
                case HOLY -> {
                    if (maxPoise > 0) {
                        float scaleFactor = 1.0f + (currentPoise / maxPoise);
                        poiseDamage = damage * scaleFactor;
                        reactionMsg = String.format("§eSmite (%.1fx): -%%.0f Poise", scaleFactor);
                    }
                }
                case EVOCATION -> {
                    if (maxPoise > 0) {
                        float missingPoise = maxPoise - currentPoise;
                        float scaleFactor = 1.0f + (missingPoise / maxPoise);
                        poiseDamage = damage * scaleFactor;

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
                    boolean darkMageBonus = false;
                    if (caster instanceof ServerPlayer player
                            && com.complextalents.impl.darkmage.origin.DarkMageOrigin.isDarkMage(player)) {
                        int originLevel = Math.min(4,
                                Math.max(0, com.complextalents.origin.OriginManager.getOriginLevel(player) - 1));
                        boolean isDowned = isExhausted || entity.getHealth() <= entity.getMaxHealth() * 0.3f;
                        if (isDowned) {
                            damage = originalDamage
                                    * (float) com.complextalents.impl.darkmage.origin.DarkMageOrigin.ENDER_FLANK_DOWNED_MULT[originLevel];
                            darkMageBonus = true;
                        }
                    }

                    if (isExhausted) {
                        if (!darkMageBonus)
                            damage = originalDamage * 2.0f;
                        poiseDamage = 0;
                        reactionMsg = String.format("§5Execution (%.1fx): %.0f Damage", damage / originalDamage,
                                damage);
                    } else {
                        poiseDamage = damage * 0.5f;
                        reactionMsg = "§8Ender (0.5x): -%.0f Poise";
                    }
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
                            damage *= nukeMult;
                            poiseDamage = damage * 0.5f;
                            reactionMsg = String.format("§dCosmic Redline (%.1fx): %.0f Damage [POSSESSED]", nukeMult,
                                    damage);
                        } else {
                            float backfireHp = (float) (player.getMaxHealth()
                                    * com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_BACKFIRE_SELF_DMG[originLevel]);
                            player.hurt(player.damageSources().magic(), backfireHp);
                            int silenceTicks = (int) (com.complextalents.impl.darkmage.origin.DarkMageOrigin.ELDRITCH_BACKFIRE_SILENCE_SEC[originLevel]
                                    * 20);
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    com.complextalents.effect.ModEffects.SILENCED.get(), silenceTicks, 0));
                            poiseDamage = 0;
                            reactionMsg = "§cEldritch Backfire!";
                        }
                    } else {
                        poiseDamage = damage * 0.5f;
                        reactionMsg = String.format("§4Decay: %.0f Damage", damage);
                    }
                }
                case BLOOD -> {
                    float lifestealPct = 0.25f;
                    if (caster instanceof ServerPlayer player) {
                        // Decrease lifesteal for consecutive casts (Blood Exhaustion effect)
                        net.minecraft.world.effect.MobEffectInstance exhaustion = player
                                .getEffect(com.complextalents.effect.ModEffects.BLOOD_EXHAUSTION.get());
                        if (exhaustion != null) {
                            int stacks = Math.min(3, exhaustion.getAmplifier() + 1);
                            // Reduces lifesteal by 33.3% per stack (1 stack = 0.66x, 2 stacks = 0.33x, 3
                            // stacks = 0.0x)
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
                    float lifestealAmount = damage * lifestealPct;
                    if (caster != null && lifestealAmount > 0) {
                        caster.heal(lifestealAmount);
                        reactionMsg = String.format("§cLifesteal: +%.1f HP", lifestealAmount);
                    }
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

        // 6. Inflict Poise Damage & Calculate Mitigated Damage
        if (!isExhausted) {
            // Use damagePoise to trigger events, exhaustion, and recovery timers properly
            PoiseAPI.damagePoise(entity, poiseDamage, caster, damageSource, true);

            // Re-check if this exact hit broke poise
            if (PoiseAPI.isExhausted(entity)) {
                // Poise broke on this hit! Target receives unmitigated spell damage
                damage = originalDamage;
            } else {
                damage = PoiseAPI.calculateMitigatedDamage(entity, damage);
            }
        }

        // Prevent vanilla/default hurt listener from double-damaging poise
        entity.getPersistentData().putBoolean("SkipStrengthDamage", true);
        event.setAmount(damage);
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
            case HOLY, EVOCATION, ENDER, ELDRITCH, BLOOD -> true;
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
            default -> null;
        };
    }
}
