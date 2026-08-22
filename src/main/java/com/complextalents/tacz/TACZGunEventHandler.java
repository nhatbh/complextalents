package com.complextalents.tacz;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import com.nhatbh.basedefensev2.registry.ModEffects;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.EnumMap;
import java.util.Map;

import com.complextalents.origin.OriginManager;
import com.complextalents.origin.capability.IPlayerOriginData;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.gunmastery.classification.GunClassificationManager;
import com.complextalents.gunmastery.capability.GunMasteryDataProvider;
import com.complextalents.gunmastery.capability.IGunMasteryData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all combat, accuracy, recoil, speed, magazine, ammo, and Poise API event integrations for TACZ guns.
 */
public class TACZGunEventHandler {

    private static final Map<UUID, Long> LAST_GUN_WARNING_TIME = new ConcurrentHashMap<>();
    private static final ResourceLocation MARKSMAN_ORIGIN_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "marksman");

    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) return;

        GunType gunType = GunType.fromGunId(event.getGunId());

        IGunOperator operator = IGunOperator.fromLivingEntity(attacker);
        boolean isAiming = operator != null && operator.getSynIsAiming();

        ItemStack mainStack = attacker.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainStack);
        FireMode fireMode = iGun != null ? iGun.getFireMode(mainStack) : FireMode.UNKNOWN;

        // 1. Base Damage Multipliers & Refinement Mainstat Bonus
        double damageMult = GunAttributes.getValue(attacker, GunAttributeType.GUN_DAMAGE, gunType);
        if (mainStack != null && !mainStack.isEmpty()) {
            int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(mainStack);
            int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);
            double mainstatBonus = com.complextalents.gunmastery.GunRefinementManager.getMainstatDamageBonus(cumRank);
            damageMult *= (1.0 + mainstatBonus);
        }

        if (isAiming) {
            damageMult *= GunAttributes.getValue(attacker, GunAttributeType.ADS_DAMAGE, gunType);
        } else {
            damageMult *= GunAttributes.getValue(attacker, GunAttributeType.HIP_FIRE_DAMAGE, gunType);
        }

        if (fireMode == FireMode.SEMI) {
            damageMult *= GunAttributes.getValue(attacker, GunAttributeType.SEMI_DAMAGE, gunType);
        } else if (fireMode == FireMode.AUTO) {
            damageMult *= GunAttributes.getValue(attacker, GunAttributeType.AUTO_DAMAGE, gunType);
        } else if (fireMode == FireMode.BURST) {
            damageMult *= GunAttributes.getValue(attacker, GunAttributeType.BURST_DAMAGE, gunType);
        }

        float newBaseAmount = (float) (event.getBaseAmount() * damageMult);

        float rawHitDamage = newBaseAmount * (event.isHeadShot() ? event.getHeadshotMultiplier() : 1.0f);
        event.setBaseAmount(newBaseAmount);

        // Target Exhaustion Check for LMG suppression mechanic
        LivingEntity victim = event.getHurtEntity() instanceof LivingEntity livingVictim ? livingVictim : null;

        // 3. Poise API Integration & Mitigation
        if (victim != null && !victim.level().isClientSide && PoiseAPI.hasPoise(victim)) {
            // Prevent double-processing by basedefensev2's generic EntityStrengthEventHandler
            victim.getPersistentData().putBoolean("SkipStrengthDamage", true);

            double distance = attacker.distanceTo(victim);
            boolean isTargetExhausted = PoiseAPI.isExhausted(victim);

            // 1. Calculate Pre-mitigated Base Damage (Apotheosis Armor Formula)
            float effectiveArmor = (float) victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            float preMitigatedDamage = rawHitDamage * (50.0f / (50.0f + Math.max(0.0f, effectiveArmor)));

            // 2. Firearm Poise Damage Path
            int basePierce = 0;
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(event.getGunId()).orElse(null);
            if (index != null && index.getGunData() != null && index.getGunData().getBulletData() != null) {
                basePierce = index.getGunData().getBulletData().getPierce();
            }
            double pierceMult = GunAttributes.getValue(attacker, GunAttributeType.PIERCE_MULTIPLIER, gunType);
            int finalPierce = (int) Math.round(basePierce * pierceMult);
            float penetrationFactor = (float) (1.0 + (finalPierce * 0.10));

            float basePoiseDamage = preMitigatedDamage * 0.40f * penetrationFactor;
            float headshotMult = 1.0f;
            float bodyMult = 0.4f;

            switch (gunType) {
                case PISTOL -> {
                    headshotMult = 0.70f;
                    bodyMult = 0.35f;
                }
                case RIFLE -> {
                    headshotMult = 0.80f;
                    bodyMult = 0.40f;
                }
                case SHOTGUN -> {
                    float shotgunMult = (distance <= 8.0) ? 0.80f : (distance >= 20.0 ? 0.30f : 0.80f - (float) ((distance - 8.0) / 12.0) * 0.50f);
                    headshotMult = shotgunMult;
                    bodyMult = shotgunMult;
                }
                case MG -> {
                    headshotMult = 0.30f;
                    bodyMult = 0.15f;
                }
                case SMG -> {
                    headshotMult = 0.30f;
                    bodyMult = 0.15f;
                }
                case SNIPER -> {
                    float sniperDistFactor = distance < 10.0 ? 0.3f : 1.0f;
                    headshotMult = 2.50f * sniperDistFactor;
                    bodyMult = 0.30f * sniperDistFactor;
                }
                default -> {
                    headshotMult = 1.00f;
                    bodyMult = 0.40f;
                }
            }

            float finalPoiseDamage = basePoiseDamage * (event.isHeadShot() ? headshotMult : bodyMult);

            // 3. Firearm Vitality Damage Path (Subtle Scaled Vitality Multiplier)
            float vitalityMult = 1.00f;
            if (gunType == GunType.SMG && isTargetExhausted) {
                vitalityMult = 1.20f; // SMG: +20% subtle execution boost when exhausted
            } else if (gunType == GunType.SNIPER && event.isHeadShot()) {
                vitalityMult = 1.25f; // Sniper: +25% subtle headshot execution boost
            } else if (gunType == GunType.SHOTGUN) {
                vitalityMult = (distance <= 8.0) ? 1.10f : (distance >= 20.0 ? 0.85f : 1.10f - (float) ((distance - 8.0) / 12.0) * 0.25f);
            }
            float finalVitalityDamage = preMitigatedDamage * vitalityMult;

            // 4. LMG Suppression Mechanic
            if (gunType == GunType.MG && isTargetExhausted) {
                net.minecraft.world.effect.MobEffect suppressionEffect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.tryParse("basedefensev2:suppression"));
                if (suppressionEffect != null) {
                    victim.addEffect(new MobEffectInstance(suppressionEffect, 20, 0, false, false));
                }
            }

            // 5. Accumulate Gun Mastery Damage from Poise / Vitality Pipeline
            if (attacker instanceof ServerPlayer player) {
                if (gunType != null && !gunType.isGlobal() && gunType != GunType.RPG) {
                    float damageToAccumulate = isTargetExhausted ? finalVitalityDamage : finalPoiseDamage;
                    if (damageToAccumulate > 0) {
                        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                            data.addAccumulatedDamage(gunType, damageToAccumulate);
                        });
                    }
                }
            }

            // 6. Single Entry-Point API Call to Poise & Boss Vitality Pipeline with "TACZ" sourceMod identifier
            DamageSource source = event.getDamageSource(GunDamageSourcePart.ARMOR_PIERCING);
            if (source == null) {
                source = victim.damageSources().mobAttack(attacker);
            }

            PoiseAPI.damagePoise(victim, finalPoiseDamage, finalVitalityDamage, attacker, source, true, "TACZ");
            event.setBaseAmount(0.0001f);
        }
    }

    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null || attacker.level().isClientSide) return;

        GunType gunType = GunType.fromGunId(event.getGunId());

        double recoveryChance = GunAttributes.getValue(attacker, GunAttributeType.AMMO_RECOVERY_CHANCE, gunType);
        if (recoveryChance > 0.0 && attacker.getRandom().nextDouble() < recoveryChance) {
            ItemStack mainStack = attacker.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(mainStack);
            if (iGun != null) {
                int flatAmount = (int) Math.round(GunAttributes.getValue(attacker, GunAttributeType.AMMO_RECOVERY_AMOUNT, gunType));
                double percentAmount = GunAttributes.getValue(attacker, GunAttributeType.AMMO_RECOVERY_PERCENT, gunType);

                CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(mainStack)).orElse(null);
                int maxAmmo = gunIndex != null ? gunIndex.getGunData().getAmmoAmount() : 30;

                int recovered = flatAmount + (int) Math.round(maxAmmo * percentAmount);
                if (recovered > 0) {
                    int currentAmmo = iGun.getCurrentAmmoCount(mainStack);
                    iGun.setCurrentAmmoCount(mainStack, Math.min(maxAmmo, currentAmmo + recovered));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter == null) return;

        ItemStack stack = event.getGunItemStack();
        IGun iGun = IGun.getIGunOrNull(stack);
        GunType gunType = GunType.fromItemStack(stack);

        // Marksman Lore Restriction: Firearms are unknown technology in this fantasy world.
        // Only the transported Marksman hero knows how to operate firearms.
        if (shooter instanceof Player player) {
            ResourceLocation originId = null;
            if (player.level().isClientSide()) {
                originId = com.complextalents.origin.client.ClientOriginData.getOriginId();
            } else if (player instanceof ServerPlayer serverPlayer) {
                originId = OriginManager.getOriginId(serverPlayer);
            } else {
                originId = player.getCapability(OriginDataProvider.ORIGIN_DATA)
                        .resolve()
                        .map(IPlayerOriginData::getActiveOrigin)
                        .orElse(null);
            }

            boolean isMarksman = originId != null && (originId.equals(MARKSMAN_ORIGIN_ID) || "marksman".equals(originId.getPath()));
            if (!isMarksman) {
                event.setCanceled(true);

                long now = System.currentTimeMillis();
                Long lastTime = LAST_GUN_WARNING_TIME.get(player.getUUID());
                if (lastTime == null || now - lastTime > 2000) {
                    LAST_GUN_WARNING_TIME.put(player.getUUID(), now);

                    // Trigger inspect animation when confused non-Marksman attempts to fire
                    triggerInspectAnimation(player);

                    String msgKey = getRandomOriginGunMessage(originId, player);
                    player.displayClientMessage(Component.translatable(msgKey), true);
                }
                return;
            }

            // RPG Restriction: Marksman players cannot fire RPG / Launcher weapons due to International Law
            if (gunType == GunType.RPG) {
                event.setCanceled(true);

                long now = System.currentTimeMillis();
                Long lastTime = LAST_GUN_WARNING_TIME.get(player.getUUID());
                if (lastTime == null || now - lastTime > 2000) {
                    LAST_GUN_WARNING_TIME.put(player.getUUID(), now);
                    triggerInspectAnimation(player);
                    player.displayClientMessage(Component.translatable("origin.complextalents.marksman.rpg_msg"), true);
                }
                return;
            }

            // Gun Mastery Tier Requirement Restriction
            ResourceLocation gunRes = iGun != null ? iGun.getGunId(stack) : null;
            if (gunRes != null) {
                GunClassificationManager.GunEntry entry = GunClassificationManager.getGunEntry(gunRes);
                if (entry != null) {
                    int requiredLevel = GunClassificationManager.getRequiredMasteryLevel(stack);
                    IGunMasteryData masteryData = player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).orElse(null);
                    int currentLevel = masteryData != null ? masteryData.getMasteryLevel(gunType) : 0;
                    if (currentLevel < requiredLevel) {
                        event.setCanceled(true);

                        long now = System.currentTimeMillis();
                        Long lastTime = LAST_GUN_WARNING_TIME.get(player.getUUID());
                        if (lastTime == null || now - lastTime > 2000) {
                            LAST_GUN_WARNING_TIME.put(player.getUUID(), now);
                            triggerInspectAnimation(player);
                            String rankColor = getTierColor(entry.tier);
                            player.displayClientMessage(Component.literal("\u00A7cYou need \u00A7lGun Mastery Level " + requiredLevel + " (" + rankColor + entry.rank + "\u00A7c)\u00A7r\u00A7c to operate this firearm!"), true);
                        }
                        return;
                    }
                }
            }
        }

        if (event.getLogicalSide().isClient()) return;
        if (iGun == null) return;

        // Server-side Ammo Save Chance
        double saveChance = GunAttributes.getValue(shooter, GunAttributeType.AMMO_SAVE_CHANCE, gunType);
        if (saveChance > 0.0 && shooter.getRandom().nextDouble() < saveChance) {
            iGun.setCurrentAmmoCount(stack, iGun.getCurrentAmmoCount(stack) + 1);
        }
    }




    private static String getRandomOriginGunMessage(ResourceLocation originId, Player player) {
        if (originId != null) {
            com.complextalents.origin.Origin origin = com.complextalents.origin.OriginRegistry.getInstance().getOrigin(originId);
            if (origin != null) {
                return origin.getRandomGunConfusionMessage(player.getRandom());
            }
        }
        return com.complextalents.origin.Origin.getDefaultGunConfusionMessage(player.getRandom());
    }

    private static void triggerInspectAnimation(LivingEntity shooter) {
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator == null) return;
        try {
            Class<?> clazz = operator.getClass();
            java.lang.reflect.Method inspectMethod = null;
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("inspect")) {
                    inspectMethod = m;
                    break;
                }
            }
            if (inspectMethod != null) {
                if (inspectMethod.getParameterCount() == 0) {
                    inspectMethod.invoke(operator);
                } else if (inspectMethod.getParameterCount() == 1 && inspectMethod.getParameterTypes()[0] == java.util.function.Supplier.class) {
                    inspectMethod.invoke(operator, (java.util.function.Supplier<ItemStack>) shooter::getMainHandItem);
                }
            } else {
                java.lang.reflect.Method drawMethod = null;
                for (java.lang.reflect.Method m : clazz.getMethods()) {
                    if (m.getName().toLowerCase().contains("draw")) {
                        drawMethod = m;
                        break;
                    }
                }
                if (drawMethod != null) {
                    if (drawMethod.getParameterCount() == 0) {
                        drawMethod.invoke(operator);
                    } else if (drawMethod.getParameterCount() == 1 && drawMethod.getParameterTypes()[0] == java.util.function.Supplier.class) {
                        drawMethod.invoke(operator, (java.util.function.Supplier<ItemStack>) shooter::getMainHandItem);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onGunFinishReload(GunFinishReloadEvent event) {
        if (event.getLogicalSide().isClient()) return;

        ItemStack stack = event.getGunItemStack();
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return;

        LivingEntity shooter = getShooterFromStack(stack);
        if (shooter == null) return;

        GunType gunType = GunType.fromItemStack(stack);

        double bonusChance = GunAttributes.getValue(shooter, GunAttributeType.BONUS_AMMO_CHANCE, gunType);
        if (bonusChance > 0.0 && shooter.getRandom().nextDouble() < bonusChance) {
            int flatBonus = (int) Math.round(GunAttributes.getValue(shooter, GunAttributeType.BONUS_AMMO_AMOUNT, gunType));
            double percentBonus = GunAttributes.getValue(shooter, GunAttributeType.BONUS_AMMO_PERCENT, gunType);

            CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack)).orElse(null);
            int baseCapacity = gunIndex != null ? gunIndex.getGunData().getAmmoAmount() : 30;

            int bonusCount = flatBonus + (int) Math.round(baseCapacity * percentBonus);
            if (bonusCount > 0) {
                iGun.setCurrentAmmoCount(stack, iGun.getCurrentAmmoCount(stack) + bonusCount);
            }
        }
    }

    @SubscribeEvent
    public static void onAttachmentProperty(AttachmentPropertyEvent event) {
        ItemStack stack = event.getGunItem();
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return;

        LivingEntity shooter = getShooterFromStack(stack);
        if (shooter == null && net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            shooter = net.minecraft.client.Minecraft.getInstance().player;
        }
        if (shooter == null) return;

        GunType gunType = GunType.fromItemStack(stack);
        AttachmentCacheProperty cache = event.getCacheProperty();

        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        boolean isAiming = operator != null && operator.getSynIsAiming();
        FireMode fireMode = iGun.getFireMode(stack);

        // 0. Damage Multiplier (Refinement Mainstat + Mastery Gun Damage Bonus)
        java.util.LinkedList<com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair> damageList = cache.getCache(GunProperties.DAMAGE);
        if (damageList != null && !damageList.isEmpty()) {
            double damageMult = GunAttributes.getValue(shooter, GunAttributeType.GUN_DAMAGE, gunType);
            if (stack != null && !stack.isEmpty()) {
                int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(stack);
                int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);
                double mainstatBonus = com.complextalents.gunmastery.GunRefinementManager.getMainstatDamageBonus(cumRank);
                damageMult *= (1.0 + mainstatBonus);
            }
            if (damageMult != 1.0) {
                java.util.LinkedList<com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair> newDamageList = new java.util.LinkedList<>();
                for (com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair pair : damageList) {
                    newDamageList.add(new com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair(pair.getDistance(), (float) (pair.getDamage() * damageMult)));
                }
                cache.setCache(GunProperties.DAMAGE, newDamageList);
            }
        }

        // 0b. Headshot Multiplier
        Float headshotMult = cache.getCache(GunProperties.HEADSHOT_MULTIPLIER);
        if (headshotMult != null) {
            double hsBonus = GunAttributes.getValue(shooter, GunAttributeType.HEADSHOT_MULTIPLIER, gunType);
            if (hsBonus != 1.0) {
                cache.setCache(GunProperties.HEADSHOT_MULTIPLIER, (float) (headshotMult * hsBonus));
            }
        }

        double shotgunSpreadMult = gunType == GunType.SHOTGUN ? 1.5 : 1.0;
        float heartRateInaccMult = shooter instanceof Player player ? HeartRateManager.getInaccuracyMultiplier(player) : 1.0f;

        // Realistic movement inaccuracy penalties
        double movePenalty = switch (gunType) {
            case SNIPER -> 7.0;  // 7.0x penalty for moving with Sniper Rifle
            case MG -> 4.5;      // 4.5x penalty for Machine Gun (LMG)
            case RIFLE -> 3.5;   // 3.5x penalty for Assault Rifle
            case SMG -> 2.2;     // 2.2x penalty for SMG
            case SHOTGUN -> 1.5; // 1.5x penalty for Shotgun
            case PISTOL -> 1.2;  // 1.2x penalty for Pistol
            default -> 3.0;
        };

        final double finalMovePenalty = shooter.isSprinting() ? movePenalty * 1.5 : movePenalty;

        Map<InaccuracyType, Float> inaccuracy = cache.getCache(GunProperties.INACCURACY);
        if (inaccuracy != null) {
            double hipAcc = GunAttributes.getValue(shooter, GunAttributeType.HIP_FIRE_ACCURACY, gunType);
            double modeAcc = 1.0;
            if (fireMode == FireMode.SEMI) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.SEMI_ACCURACY, gunType);
            else if (fireMode == FireMode.AUTO) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.AUTO_ACCURACY, gunType);
            else if (fireMode == FireMode.BURST) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.BURST_ACCURACY, gunType);

            double baseFactor = (1.0 / Math.max(0.0001, (hipAcc * modeAcc))) * shotgunSpreadMult * heartRateInaccMult;
            Map<InaccuracyType, Float> newInacc = new EnumMap<>(InaccuracyType.class);
            inaccuracy.forEach((k, v) -> {
                double mult = baseFactor;
                if (k == InaccuracyType.MOVE) {
                    mult *= finalMovePenalty;
                }
                newInacc.put(k, (float) (v * mult));
            });
            if (!newInacc.containsKey(InaccuracyType.MOVE)) {
                float standVal = newInacc.getOrDefault(InaccuracyType.STAND, 1.0f);
                newInacc.put(InaccuracyType.MOVE, (float) (standVal * finalMovePenalty));
            }
            cache.setCache(GunProperties.INACCURACY, newInacc);
        }

        Map<InaccuracyType, Float> aimInaccuracy = cache.getCache(GunProperties.AIM_INACCURACY);
        if (aimInaccuracy != null) {
            double adsAcc = GunAttributes.getValue(shooter, GunAttributeType.ADS_ACCURACY, gunType);
            double modeAcc = 1.0;
            if (fireMode == FireMode.SEMI) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.SEMI_ACCURACY, gunType);
            else if (fireMode == FireMode.AUTO) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.AUTO_ACCURACY, gunType);
            else if (fireMode == FireMode.BURST) modeAcc = GunAttributes.getValue(shooter, GunAttributeType.BURST_ACCURACY, gunType);

            double baseFactor = (1.0 / Math.max(0.0001, (adsAcc * modeAcc))) * shotgunSpreadMult * heartRateInaccMult;
            Map<InaccuracyType, Float> newAimInacc = new EnumMap<>(InaccuracyType.class);
            aimInaccuracy.forEach((k, v) -> {
                newAimInacc.put(k, (float) (v * baseFactor));
            });
            cache.setCache(GunProperties.AIM_INACCURACY, newAimInacc);
        }

        // 2. Recoil
        ParameterizedCachePair<Float, Float> recoil = cache.getCache(GunProperties.RECOIL);
        if (recoil != null) {
            double genRecoil = GunAttributes.getValue(shooter, GunAttributeType.RECOIL, gunType);
            double pitchRecoil = GunAttributes.getValue(shooter, GunAttributeType.RECOIL_PITCH, gunType);
            double yawRecoil = GunAttributes.getValue(shooter, GunAttributeType.RECOIL_YAW, gunType);

            if (isAiming) {
                genRecoil *= GunAttributes.getValue(shooter, GunAttributeType.ADS_RECOIL, gunType);
                pitchRecoil *= GunAttributes.getValue(shooter, GunAttributeType.ADS_RECOIL_PITCH, gunType);
                yawRecoil *= GunAttributes.getValue(shooter, GunAttributeType.ADS_RECOIL_YAW, gunType);
            } else {
                genRecoil *= GunAttributes.getValue(shooter, GunAttributeType.HIP_FIRE_RECOIL, gunType);
                pitchRecoil *= GunAttributes.getValue(shooter, GunAttributeType.HIP_FIRE_RECOIL_PITCH, gunType);
                yawRecoil *= GunAttributes.getValue(shooter, GunAttributeType.HIP_FIRE_RECOIL_YAW, gunType);
            }

            float pitchFactor = (float) (1.0 / Math.max(0.0001, (genRecoil * pitchRecoil)));
            float yawFactor = (float) (1.0 / Math.max(0.0001, (genRecoil * yawRecoil)));

            float defaultPitch = recoil.left() != null ? recoil.left().getDefaultValue() : 1.0f;
            float defaultYaw = recoil.right() != null ? recoil.right().getDefaultValue() : 1.0f;

            cache.setCache(GunProperties.RECOIL, ParameterizedCachePair.of(defaultPitch * pitchFactor, defaultYaw * yawFactor));
        }

        // 3. RPM
        Integer rpm = cache.getCache(GunProperties.ROUNDS_PER_MINUTE);
        if (rpm != null) {
            double rpmMult = GunAttributes.getValue(shooter, GunAttributeType.RPM_MULTIPLIER, gunType);
            cache.setCache(GunProperties.ROUNDS_PER_MINUTE, (int) Math.round(rpm * rpmMult));
        }

        // 4. ADS Speed
        Float adsTime = cache.getCache(GunProperties.ADS_TIME);
        if (adsTime != null) {
            double adsSpeedMult = GunAttributes.getValue(shooter, GunAttributeType.ADS_SPEED, gunType);
            cache.setCache(GunProperties.ADS_TIME, (float) (adsTime / Math.max(0.0001, adsSpeedMult)));
        }

        // 5. Pierce
        Integer pierce = cache.getCache(GunProperties.PIERCE);
        if (pierce != null) {
            double pierceMult = GunAttributes.getValue(shooter, GunAttributeType.PIERCE_MULTIPLIER, gunType);
            cache.setCache(GunProperties.PIERCE, (int) Math.round(pierce * pierceMult));
        }

        // 6. Knockback (-80% for non-shotgun weapons to prevent kiting)
        Float knockback = cache.getCache(GunProperties.KNOCKBACK);
        if (knockback != null) {
            double kbMult = GunAttributes.getValue(shooter, GunAttributeType.KNOCKBACK_MULTIPLIER, gunType);
            double kbBase = GunAttributes.getValue(shooter, GunAttributeType.KNOCKBACK_BASE, gunType);
            double kitingPenalty = gunType == GunType.SHOTGUN ? 1.0 : 0.20;
            cache.setCache(GunProperties.KNOCKBACK, (float) (((knockback + kbBase) * kbMult) * kitingPenalty));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            HeartRateManager.onDamageTaken(player, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        HeartRateManager.tickHeartRate(player);

        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator == null) return;
        ShooterDataHolder data = operator.getDataHolder();
        if (data == null) return;

        ItemStack mainStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainStack);
        if (iGun == null) return;

        GunType gunType = GunType.fromItemStack(mainStack);

        // 1. Reload Speed
        if (data.reloadStateType != null && data.reloadStateType.isReloading()) {
            double reloadSpeed = GunAttributes.getValue(player, GunAttributeType.RELOAD_SPEED, gunType);
            if (reloadSpeed != 1.0 && reloadSpeed > 0.0) {
                long extraElapsedMs = (long) ((reloadSpeed - 1.0) * 50.0);
                data.reloadTimestamp -= extraElapsedMs;
            }
        }

        // 2. Bolt Action Speed
        if (data.isBolting) {
            double boltSpeed = GunAttributes.getValue(player, GunAttributeType.BOLT_ACTION_SPEED, gunType);
            if (boltSpeed != 1.0 && boltSpeed > 0.0) {
                long extraElapsedMs = (long) ((boltSpeed - 1.0) * 50.0);
                data.boltTimestamp -= extraElapsedMs;
            }
        }

        // 3. Draw Speed
        if (data.drawTimestamp > 0) {
            double drawSpeed = GunAttributes.getValue(player, GunAttributeType.DRAW_SPEED, gunType);
            if (drawSpeed != 1.0 && drawSpeed > 0.0) {
                long extraElapsedMs = (long) ((drawSpeed - 1.0) * 50.0);
                data.drawTimestamp -= extraElapsedMs;
            }
        }

        // 4. ADS Movement Slowness Penalty
        if (operator.getSynIsAiming() && !player.level().isClientSide) {
            int slownessAmplifier = switch (gunType) {
                case SNIPER -> 3; // Slowness IV (~75% movement speed reduction)
                case MG -> 2;     // Slowness III (~45% movement speed reduction)
                case RPG -> 2;    // Slowness III (~45% movement speed reduction)
                case RIFLE -> 1;  // Slowness II (~30% movement speed reduction)
                case SMG -> 0;    // Slowness I (~15% movement speed reduction)
                case SHOTGUN -> 0;// Slowness I (~15% movement speed reduction)
                case PISTOL -> -1;// No slowness penalty for sidearms
                default -> -1;
            };
            if (slownessAmplifier >= 0) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 3, slownessAmplifier, false, false, false));
            }
        }
    }

    public static String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "\u00A7f"; // Recruit: White
            case 2 -> "\u00A7a"; // Trooper: Emerald Green
            case 3 -> "\u00A79"; // Sergeant: Indigo Blue
            case 4 -> "\u00A75"; // Captain: Purple
            case 5 -> "\u00A76"; // General: Gold
            default -> "\u00A7f";
        };
    }

    public static String getTierSymbol(int tier) {
        return switch (tier) {
            case 1 -> "✧";
            case 2 -> "✦";
            case 3 -> "❖";
            case 4 -> "❂";
            case 5 -> "⚜";
            default -> "✧";
        };
    }

    private static LivingEntity getShooterFromStack(ItemStack stack) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return null;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                return player;
            }
        }
        return null;
    }

    @Mod.EventBusSubscriber(modid = com.complextalents.TalentsMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) return;

            IGun iGun = IGun.getIGunOrNull(stack);
            ResourceLocation gunRes = iGun != null ? iGun.getGunId(stack) : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (gunRes == null) return;

            GunClassificationManager.GunEntry entry = GunClassificationManager.getGunEntry(gunRes);
            if (entry == null && iGun == null) return;

            GunType gunType = GunType.fromItemStack(stack);
            if ((gunType == null || gunType == GunType.GLOBAL) && entry != null) {
                gunType = entry.getGunType();
            }
            if (gunType == null || gunType == GunType.GLOBAL) return;

            int tier = entry != null ? entry.tier : 1;
            String rankName = entry != null ? entry.rank : "Recruit";
            int requiredLevel = GunClassificationManager.getRequiredMasteryLevel(stack);

            Player player = event.getEntity();
            int playerLevel = 0;
            if (player != null) {
                var data = player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).orElse(null);
                if (data != null) {
                    playerLevel = data.getMasteryLevel(gunType);
                }
            }

            String rankColor = getTierColor(tier);
            String symbol = getTierSymbol(tier);

            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal("\u00A7b\u00A7l" + symbol + " Gun Mastery: \u00A7f" + gunType.getDisplayName()));
            event.getToolTip().add(Component.literal("  \u00A77✦ Weapon Tier: " + rankColor + rankName + " \u00A78(Tier " + tier + ")"));

            if (playerLevel >= requiredLevel) {
                event.getToolTip().add(Component.literal("  \u00A7a✔ Wield Requirement: " + rankColor + "L." + requiredLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
            } else {
                event.getToolTip().add(Component.literal("  \u00A7c✖ Required Gun Mastery: " + rankColor + "L." + requiredLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
            }

            // --- Firearm Refinement Section ---
            boolean isAnvilPreview = false;
            ItemStack inputGunStack = ItemStack.EMPTY;
            if (player != null && player.containerMenu instanceof com.complextalents.menu.RefiningAnvilMenu anvilMenu) {
                net.minecraft.world.inventory.Slot resultSlot = anvilMenu.getSlot(10);
                if (resultSlot != null && resultSlot.hasItem()) {
                    ItemStack res = resultSlot.getItem();
                    if (res == stack || ItemStack.matches(res, stack) || (res.hasTag() && stack.hasTag() && res.getTag().equals(stack.getTag()))) {
                        isAnvilPreview = true;
                        if (anvilMenu.getSlot(0).hasItem()) {
                            inputGunStack = anvilMenu.getSlot(0).getItem();
                        }
                    }
                }
            }

            int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(stack);
            int baseRank = com.complextalents.gunmastery.GunRefinementManager.getBaseCumulativeLevelForStartingTier(tier);
            int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);

            if (cumRank > baseRank) {
                int refineRank = cumRank - baseRank;
                int displayTier = com.complextalents.gunmastery.GunRefinementManager.getTierForCumulativeLevel(cumRank);
                String refColor = com.complextalents.gunmastery.GunRefinementManager.getTierColor(displayTier);
                String refCrest = com.complextalents.gunmastery.GunRefinementManager.getTierCrestIcon(displayTier);
                String refTierName = com.complextalents.gunmastery.GunRefinementManager.getTierNameForTier(displayTier);

                event.getToolTip().add(Component.empty());
                event.getToolTip().add(Component.literal("\u00A7d\u00A7l" + refCrest + " Refinement: " + refColor + refTierName + " (+" + refineRank + ") \u00A78[Lv." + cumRank + "/20]"));

                double mainstatBonus = com.complextalents.gunmastery.GunRefinementManager.getMainstatDamageBonus(cumRank);
                if (mainstatBonus > 0.0) {
                    event.getToolTip().add(Component.literal("  \u00A77└ Base Firearm Damage: \u00A7a+" + String.format("%.1f%%", mainstatBonus * 100.0)));
                }

                if (isAnvilPreview && !inputGunStack.isEmpty()) {
                    var inputSubstatResult = com.complextalents.gunmastery.GunRefinementManager.calculateSubstats(inputGunStack);
                    for (var entrySet : inputSubstatResult.values.entrySet()) {
                        if (entrySet.getValue() > 0.0) {
                            event.getToolTip().add(Component.literal("  \u00A77└ " + entrySet.getKey().getDisplayName() + ": \u00A7b" + entrySet.getKey().formatValue(entrySet.getValue())));
                        }
                    }
                    event.getToolTip().add(Component.literal("  \u00A77└ \u00A7e[Random Substat Upgrade]\u00A77: \u00A7b+???"));

                    if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                        event.getToolTip().add(Component.literal("  \u00A7e\u00A7o[ Refinement History Log ]"));
                        for (int i = 0; i < inputSubstatResult.history.size(); i++) {
                            event.getToolTip().add(Component.literal("   " + inputSubstatResult.history.get(i)));
                        }
                        int inputCumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(com.complextalents.gunmastery.GunRefinementManager.getRefineXp(inputGunStack), 20);
                        for (int i = inputCumRank; i < cumRank; i++) {
                            int previewLvl = i + 1;
                            int previewTier = com.complextalents.gunmastery.GunRefinementManager.getTierForCumulativeLevel(previewLvl);
                            String previewCrest = com.complextalents.gunmastery.GunRefinementManager.getTierColor(previewTier) + com.complextalents.gunmastery.GunRefinementManager.getTierCrestIcon(previewTier) + "\u00A7r\u00A77";
                            event.getToolTip().add(Component.literal("   " + previewCrest + " +??? ???"));
                        }
                    } else {
                        event.getToolTip().add(Component.literal("  \u00A78[Hold CTRL for Refinement History]"));
                    }
                } else {
                    var substatResult = com.complextalents.gunmastery.GunRefinementManager.calculateSubstats(stack);
                    for (var entrySet : substatResult.values.entrySet()) {
                        if (entrySet.getValue() > 0.0) {
                            event.getToolTip().add(Component.literal("  \u00A77└ " + entrySet.getKey().getDisplayName() + ": \u00A7b" + entrySet.getKey().formatValue(entrySet.getValue())));
                        }
                    }

                    if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                        event.getToolTip().add(Component.literal("  \u00A7e\u00A7o[ Refinement History Log ]"));
                        for (String histLine : substatResult.history) {
                            event.getToolTip().add(Component.literal("   " + histLine));
                        }
                    } else if (!substatResult.history.isEmpty()) {
                        event.getToolTip().add(Component.literal("  \u00A78[Hold CTRL for Refinement History]"));
                    }
                }
            }
        }
    }
}
