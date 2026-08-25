package com.complextalents.tacz;

import com.nhatbh.basedefensev2.api.BossAPI;
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
import com.tacz.guns.entity.EntityKineticBullet;
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
 * Handles all combat, accuracy, recoil, speed, magazine, ammo, and Poise API
 * event integrations for TACZ guns.
 */
public class TACZGunEventHandler {

    private static final Map<UUID, Long> LAST_GUN_WARNING_TIME = new ConcurrentHashMap<>();
    private static final ResourceLocation MARKSMAN_ORIGIN_ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "marksman");

    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) return;

        GunType gunType = GunType.fromGunId(event.getGunId());

        ItemStack mainStack = attacker.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainStack);

        float rawHitDamage = event.getAmount();

        // Target Exhaustion Check for LMG suppression mechanic
        LivingEntity victim = event.getHurtEntity() instanceof LivingEntity livingVictim ? livingVictim : null;

        // 2. Poise API Integration & Mitigation
        if (victim != null && !victim.level().isClientSide && PoiseAPI.hasPoise(victim)) {
            // Prevent double-processing by basedefensev2's generic EntityStrengthEventHandler
            victim.getPersistentData().putBoolean("SkipStrengthDamage", true);

            double distance = attacker.distanceTo(victim);
            boolean isTargetExhausted = PoiseAPI.isExhausted(victim);

            // Calculate Pre-mitigated Base Damage (Apotheosis Armor Formula)
            float effectiveArmor = (float) victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            float preMitigatedDamage = rawHitDamage * (50.0f / (50.0f + Math.max(0.0f, effectiveArmor)));

            // Firearm Poise Damage Path
            int finalPierce = 0;
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(event.getGunId()).orElse(null);
            if (index != null && index.getGunData() != null && index.getGunData().getBulletData() != null) {
                finalPierce = index.getGunData().getBulletData().getPierce();
            }
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
                    float shotgunMult = (distance <= 8.0) ? 1.20f : (distance >= 20.0 ? 0.30f : 1.20f - (float) ((distance - 8.0) / 12.0) * 0.90f);
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

            // Firearm Vitality Damage Path
            float vitalityMult = 1.00f;
            if (gunType == GunType.SMG && isTargetExhausted) {
                vitalityMult = 1.20f;
            } else if (gunType == GunType.SNIPER && event.isHeadShot()) {
                vitalityMult = 1.25f;
            } else if (gunType == GunType.SHOTGUN) {
                vitalityMult = (distance <= 8.0) ? 1.10f : (distance >= 20.0 ? 0.85f : 1.10f - (float) ((distance - 8.0) / 12.0) * 0.25f);
            }
            float finalVitalityDamage = preMitigatedDamage * vitalityMult;

            // Scaled boss vitality damage penalty based on gun refinement cumulative level
            if (BossAPI.isBoss(victim)) {
                int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(mainStack);
                int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);
                float progress = Math.min(1.0f, Math.max(0.0f, cumRank / 20.0f));
                float bossVitalityMult = 0.40f + (0.60f * progress);
                finalVitalityDamage *= bossVitalityMult;
            }

            // LMG Suppression Mechanic
            if (gunType == GunType.MG && isTargetExhausted) {
                net.minecraft.world.effect.MobEffect suppressionEffect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                        .getValue(net.minecraft.resources.ResourceLocation.tryParse("basedefensev2:suppression"));
                if (suppressionEffect != null) {
                    victim.addEffect(new MobEffectInstance(suppressionEffect, 20, 0, false, false));
                }
            }

            // Accumulate Gun Mastery Damage from Poise / Vitality Pipeline
            if (attacker instanceof ServerPlayer player) {
                if (gunType != null && !gunType.isGlobal() && gunType != GunType.RPG) {
                    ResourceLocation gunRes = iGun != null ? iGun.getGunId(mainStack) : null;
                    GunClassificationManager.GunEntry entry = gunRes != null ? GunClassificationManager.getGunEntry(gunRes) : null;
                    if (entry == null || entry.tier > 0) {
                        float damageToAccumulate = isTargetExhausted ? finalVitalityDamage : finalPoiseDamage;
                        if (damageToAccumulate > 0) {
                            player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                                data.addAccumulatedDamage(gunType, damageToAccumulate);
                            });
                        }
                    }
                }
            }

            // Single Entry-Point API Call to Poise & Boss Vitality Pipeline with "TACZ" sourceMod identifier
            DamageSource source = event.getDamageSource(GunDamageSourcePart.ARMOR_PIERCING);
            if (source == null) {
                source = victim.damageSources().mobAttack(attacker);
            }

            net.minecraft.world.entity.Entity bulletEntity = event.getBullet();
            String ammoType = null;
            if (bulletEntity instanceof EntityKineticBullet bullet) {
                ResourceLocation ammoLoc = bullet.getAmmoId();
                if (ammoLoc != null) {
                    ammoType = ammoLoc.toString();
                }
            }

            PoiseAPI.damagePoise(victim, finalPoiseDamage, finalVitalityDamage, attacker, source, true, "TACZ", 100, ammoType);
            event.setBaseAmount(0.0001f);
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

        // Preserved Realistic Movement Inaccuracy Penalty System per Gun Archetype
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
        double shotgunSpreadMult = gunType == GunType.SHOTGUN ? 1.5 : 1.0;
        float heartRateInaccMult = shooter instanceof Player player ? HeartRateManager.getInaccuracyMultiplier(player) : 1.0f;

        float baseFactor = (float) (shotgunSpreadMult * heartRateInaccMult);
        Map<InaccuracyType, Float> inaccuracy = cache.getCache(GunProperties.INACCURACY);
        if (inaccuracy != null) {
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
            Map<InaccuracyType, Float> newAimInacc = new EnumMap<>(InaccuracyType.class);
            aimInaccuracy.forEach((k, v) -> newAimInacc.put(k, (float) (v * baseFactor)));
            cache.setCache(GunProperties.AIM_INACCURACY, newAimInacc);
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

        ItemStack mainStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainStack);
        if (iGun == null) return;

        GunType gunType = GunType.fromItemStack(mainStack);

        // ADS Movement Slowness Penalty
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
        if (ServerLifecycleHooks.getCurrentServer() == null)
            return null;
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
            if (stack.isEmpty())
                return;

            IGun iGun = IGun.getIGunOrNull(stack);
            ResourceLocation gunRes = iGun != null ? iGun.getGunId(stack)
                    : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (gunRes == null)
                return;

            GunClassificationManager.GunEntry entry = GunClassificationManager.getGunEntry(gunRes);
            if (entry == null && iGun == null)
                return;
            if (entry != null && entry.tier <= 0) {
                event.getToolTip().add(Component.empty());
                event.getToolTip().add(Component.literal("\u00A7c\u00A7l✦ Creative Only Weapon"));
                event.getToolTip().add(Component.literal("  \u00A77Mastery & Refinement disabled."));
                return;
            }

            GunType gunType = GunType.fromItemStack(stack);
            if ((gunType == null || gunType == GunType.GLOBAL) && entry != null) {
                gunType = entry.getGunType();
            }
            if (gunType == null || gunType == GunType.GLOBAL)
                return;

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
            event.getToolTip().add(
                    Component.literal("\u00A7b\u00A7l" + symbol + " Gun Mastery: \u00A7f" + gunType.getDisplayName()));
            event.getToolTip().add(Component
                    .literal("  \u00A77✦ Weapon Tier: " + rankColor + rankName + " \u00A78(Tier " + tier + ")"));

            if (playerLevel >= requiredLevel) {
                event.getToolTip().add(Component.literal("  \u00A7a✔ Wield Requirement: " + rankColor + "L."
                        + requiredLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
            } else {
                event.getToolTip().add(Component.literal("  \u00A7c✖ Required Gun Mastery: " + rankColor + "L."
                        + requiredLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
            }

            // --- Firearm Refinement Section ---
            boolean isAnvilPreview = false;
            ItemStack inputGunStack = ItemStack.EMPTY;
            if (player != null && player.containerMenu instanceof com.complextalents.menu.RefiningAnvilMenu anvilMenu) {
                net.minecraft.world.inventory.Slot resultSlot = anvilMenu.getSlot(10);
                if (resultSlot != null && resultSlot.hasItem()) {
                    ItemStack res = resultSlot.getItem();
                    if (res == stack || ItemStack.matches(res, stack)
                            || (res.hasTag() && stack.hasTag() && res.getTag().equals(stack.getTag()))) {
                        isAnvilPreview = true;
                        if (anvilMenu.getSlot(0).hasItem()) {
                            inputGunStack = anvilMenu.getSlot(0).getItem();
                        }
                    }
                }
            }

            int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(stack);
            int baseRank = com.complextalents.gunmastery.GunRefinementManager
                    .getBaseCumulativeLevelForStartingTier(tier);
            int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);

            if (cumRank > baseRank) {
                int refineRank = cumRank - baseRank;
                int displayTier = com.complextalents.gunmastery.GunRefinementManager.getTierForCumulativeLevel(cumRank);
                String refColor = com.complextalents.gunmastery.GunRefinementManager.getTierColor(displayTier);
                String refCrest = com.complextalents.gunmastery.GunRefinementManager.getTierCrestIcon(displayTier);
                String refTierName = com.complextalents.gunmastery.GunRefinementManager.getTierNameForTier(displayTier);

                event.getToolTip().add(Component.empty());
                event.getToolTip().add(Component.literal("\u00A7d\u00A7l" + refCrest + " Refinement: " + refColor
                        + refTierName + " (+" + refineRank + ") \u00A78[Lv." + cumRank + "/20]"));

                double mainstatBonus = com.complextalents.gunmastery.GunRefinementManager
                        .getMainstatDamageBonus(cumRank);
                if (mainstatBonus > 0.0) {
                    event.getToolTip().add(Component.literal("  \u00A77└ Base Firearm Damage: \u00A7a+"
                            + String.format("%.1f%%", mainstatBonus * 100.0)));
                }

                if (isAnvilPreview && !inputGunStack.isEmpty()) {
                    var inputSubstatResult = com.complextalents.gunmastery.GunRefinementManager
                            .calculateSubstats(inputGunStack);
                    for (var entrySet : inputSubstatResult.values.entrySet()) {
                        if (entrySet.getValue() > 0.0) {
                            event.getToolTip().add(Component.literal("  \u00A77└ " + entrySet.getKey().getDisplayName()
                                    + ": \u00A7b" + entrySet.getKey().formatValue(entrySet.getValue())));
                        }
                    }
                    event.getToolTip()
                            .add(Component.literal("  \u00A77└ \u00A7e[Random Substat Upgrade]\u00A77: \u00A7b+???"));

                    if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                        event.getToolTip().add(Component.literal("  \u00A7e\u00A7o[ Refinement History Log ]"));
                        for (int i = 0; i < inputSubstatResult.history.size(); i++) {
                            event.getToolTip().add(Component.literal("   " + inputSubstatResult.history.get(i)));
                        }
                        int inputCumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(
                                com.complextalents.gunmastery.GunRefinementManager.getRefineXp(inputGunStack), 20);
                        for (int i = inputCumRank; i < cumRank; i++) {
                            int previewLvl = i + 1;
                            int previewTier = com.complextalents.gunmastery.GunRefinementManager
                                    .getTierForCumulativeLevel(previewLvl);
                            String previewCrest = com.complextalents.gunmastery.GunRefinementManager
                                    .getTierColor(previewTier)
                                    + com.complextalents.gunmastery.GunRefinementManager.getTierCrestIcon(previewTier)
                                    + "\u00A7r\u00A77";
                            event.getToolTip().add(Component.literal("   " + previewCrest + " +??? ???"));
                        }
                    } else {
                        event.getToolTip().add(Component.literal("  \u00A78[Hold CTRL for Refinement History]"));
                    }
                } else {
                    var substatResult = com.complextalents.gunmastery.GunRefinementManager.calculateSubstats(stack);
                    for (var entrySet : substatResult.values.entrySet()) {
                        if (entrySet.getValue() > 0.0) {
                            event.getToolTip().add(Component.literal("  \u00A77└ " + entrySet.getKey().getDisplayName()
                                    + ": \u00A7b" + entrySet.getKey().formatValue(entrySet.getValue())));
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
