package com.complextalents.refinement;

import com.complextalents.TalentsMod;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.util.UUIDHelper;
import net.minecraft.world.item.ItemStack;
import com.complextalents.item.RefinementGemItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class RefinementEventHandler {

    private static final java.util.UUID REFINEMENT_AD_UUID = UUIDHelper.generateAttributeModifierUUID("refinement",
            "weapon_refinement_attack_damage");
    private static final java.util.UUID GUN_REFINEMENT_DAMAGE_UUID = UUIDHelper.generateAttributeModifierUUID("refinement",
            "gun_refinement_damage_bonus");

    @SubscribeEvent
    public static void onItemAttributeModifier(net.minecraftforge.event.ItemAttributeModifierEvent event) {
        if (event.getSlotType() != net.minecraft.world.entity.EquipmentSlot.MAINHAND)
            return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        // Apply Gun Refinement Mainstat & Substat Bonuses via tacz_attributes native attributes
        if (com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null) {
            com.complextalents.tacz.GunType gunType = com.complextalents.tacz.GunType.fromItemStack(stack);

            // 1. Mainstat Bonus (% Gun Damage)
            int totalXp = com.complextalents.gunmastery.GunRefinementManager.getRefineXp(stack);
            int cumRank = com.complextalents.gunmastery.GunRefinementManager.getRankFromXp(totalXp, 20);
            double mainstatBonus = com.complextalents.gunmastery.GunRefinementManager.getMainstatDamageBonus(cumRank);
            if (mainstatBonus > 0.0) {
                net.minecraft.world.entity.ai.attributes.Attribute gunDamageAttr = com.complextalents.tacz.GunAttributeType.GUN_DAMAGE.get(gunType);
                if (gunDamageAttr != null) {
                    event.addModifier(gunDamageAttr, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            GUN_REFINEMENT_DAMAGE_UUID,
                            "Gun Refinement Damage Bonus",
                            mainstatBonus,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }

            // 2. Substat Bonuses
            com.complextalents.gunmastery.GunRefinementManager.SubstatResult gunSubstats = com.complextalents.gunmastery.GunRefinementManager.calculateSubstats(stack);
            for (java.util.Map.Entry<com.complextalents.gunmastery.GunRefinementManager.GunSubstatType, Double> entry : gunSubstats.values.entrySet()) {
                com.complextalents.gunmastery.GunRefinementManager.GunSubstatType subType = entry.getKey();
                double val = entry.getValue();
                if (val <= 0) continue;

                com.complextalents.tacz.GunAttributeType attrType = switch (subType) {
                    case GUN_DAMAGE -> com.complextalents.tacz.GunAttributeType.GUN_DAMAGE;
                    case HEADSHOT_MULTIPLIER -> com.complextalents.tacz.GunAttributeType.HEADSHOT_MULTIPLIER;
                    case KNOCKBACK_MULTIPLIER -> com.complextalents.tacz.GunAttributeType.KNOCKBACK_MULTIPLIER;
                    case KNOCKBACK_BASE -> com.complextalents.tacz.GunAttributeType.KNOCKBACK_BASE;
                    case PIERCE_MULTIPLIER -> com.complextalents.tacz.GunAttributeType.PIERCE_MULTIPLIER;
                    case RPM_MULTIPLIER -> com.complextalents.tacz.GunAttributeType.RPM_MULTIPLIER;
                    case RELOAD_SPEED -> com.complextalents.tacz.GunAttributeType.RELOAD_SPEED;
                    case DRAW_SPEED -> com.complextalents.tacz.GunAttributeType.DRAW_SPEED;
                    case ADS_SPEED -> com.complextalents.tacz.GunAttributeType.ADS_SPEED;
                    case GUN_MOVEMENT_SPEED -> com.complextalents.tacz.GunAttributeType.GUN_MOVEMENT_SPEED;
                    case RECOIL -> com.complextalents.tacz.GunAttributeType.RECOIL;
                    case VERTICAL_RECOIL -> com.complextalents.tacz.GunAttributeType.VERTICAL_RECOIL;
                    case HORIZONTAL_RECOIL -> com.complextalents.tacz.GunAttributeType.HORIZONTAL_RECOIL;
                    case ADS_RECOIL -> com.complextalents.tacz.GunAttributeType.ADS_RECOIL;
                    case ADS_VERTICAL_RECOIL -> com.complextalents.tacz.GunAttributeType.ADS_VERTICAL_RECOIL;
                    case ADS_HORIZONTAL_RECOIL -> com.complextalents.tacz.GunAttributeType.ADS_HORIZONTAL_RECOIL;
                    case HIP_FIRE_RECOIL -> com.complextalents.tacz.GunAttributeType.HIP_FIRE_RECOIL;
                    case HIP_FIRE_VERTICAL_RECOIL -> com.complextalents.tacz.GunAttributeType.HIP_FIRE_VERTICAL_RECOIL;
                    case HIP_FIRE_HORIZONTAL_RECOIL -> com.complextalents.tacz.GunAttributeType.HIP_FIRE_HORIZONTAL_RECOIL;
                    case ADS_ACCURACY -> com.complextalents.tacz.GunAttributeType.ADS_ACCURACY;
                    case HIP_FIRE_ACCURACY -> com.complextalents.tacz.GunAttributeType.HIP_FIRE_ACCURACY;
                    case ADS_DAMAGE -> com.complextalents.tacz.GunAttributeType.ADS_DAMAGE;
                    case HIP_FIRE_DAMAGE -> com.complextalents.tacz.GunAttributeType.HIP_FIRE_DAMAGE;
                    case AMMO_SAVE_CHANCE -> com.complextalents.tacz.GunAttributeType.AMMO_SAVE_CHANCE;
                    case RELOAD_AMMO_SAVE_CHANCE -> com.complextalents.tacz.GunAttributeType.RELOAD_AMMO_SAVE_CHANCE;
                    case AMMO_RECOVERY_CHANCE -> com.complextalents.tacz.GunAttributeType.AMMO_RECOVERY_CHANCE;
                    case AMMO_RECOVERY_AMOUNT -> com.complextalents.tacz.GunAttributeType.AMMO_RECOVERY_AMOUNT;
                    case AMMO_RECOVERY_PERCENT -> com.complextalents.tacz.GunAttributeType.AMMO_RECOVERY_PERCENT;
                    case BONUS_AMMO_CHANCE -> com.complextalents.tacz.GunAttributeType.BONUS_AMMO_CHANCE;
                    case BONUS_AMMO_AMOUNT -> com.complextalents.tacz.GunAttributeType.BONUS_AMMO_AMOUNT;
                    case BONUS_AMMO_PERCENT -> com.complextalents.tacz.GunAttributeType.BONUS_AMMO_PERCENT;
                    case MAGAZINE_CAPACITY -> com.complextalents.tacz.GunAttributeType.MAGAZINE_CAPACITY;
                    case BOLT_ACTION_SPEED -> com.complextalents.tacz.GunAttributeType.BOLT_ACTION_SPEED;
                    case BURST_SPEED -> com.complextalents.tacz.GunAttributeType.BURST_SPEED;
                    case BURST_DAMAGE -> com.complextalents.tacz.GunAttributeType.BURST_DAMAGE;
                    case BURST_ACCURACY -> com.complextalents.tacz.GunAttributeType.BURST_ACCURACY;
                    case AUTO_DAMAGE -> com.complextalents.tacz.GunAttributeType.AUTO_DAMAGE;
                    case AUTO_ACCURACY -> com.complextalents.tacz.GunAttributeType.AUTO_ACCURACY;
                    case SEMI_DAMAGE -> com.complextalents.tacz.GunAttributeType.SEMI_DAMAGE;
                    case SEMI_ACCURACY -> com.complextalents.tacz.GunAttributeType.SEMI_ACCURACY;
                };

                net.minecraft.world.entity.ai.attributes.Attribute targetAttr = attrType.get(gunType);
                if (targetAttr != null) {
                    java.util.UUID subUuid = UUIDHelper.generateAttributeModifierUUID("refinement", "gun_substat_" + subType.getKey().toLowerCase());
                    boolean isRecoil = subType.name().contains("RECOIL");
                    double modVal = isRecoil ? -val : val;
                    event.addModifier(targetAttr, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            subUuid,
                            "Gun Substat " + subType.getDisplayName(),
                            modVal,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
        }

        int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(stack);
        if (startingTier <= 0)
            return;

        // Apply Base AD% stat (guaranteed)
        double adBonusMultiplier = WeaponMasteryManager.getADBonusMultiplier(stack);
        if (adBonusMultiplier > 0) {
            double baseDamage = 0.0;
            for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : event.getOriginalModifiers()
                    .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                if (mod.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                    baseDamage += mod.getAmount();
                }
            }
            if (baseDamage <= 0) {
                for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : event.getModifiers()
                        .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                    if (mod.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                        baseDamage += mod.getAmount();
                    }
                }
            }
            if (baseDamage <= 0) {
                baseDamage = 4.0;
            }

            double extraDamage = baseDamage * adBonusMultiplier;
            net.minecraft.world.entity.ai.attributes.AttributeModifier modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    REFINEMENT_AD_UUID,
                    "Weapon Refinement AD Bonus",
                    extraDamage,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            event.addModifier(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, modifier);
        }

        // Apply Substats
        java.util.Map<WeaponMasteryManager.SubstatType, Double> substats = WeaponMasteryManager.getCachedSubstats(stack);
        for (java.util.Map.Entry<WeaponMasteryManager.SubstatType, Double> entry : substats.entrySet()) {
            WeaponMasteryManager.SubstatType type = entry.getKey();
            double value = entry.getValue();
            if (value <= 0) continue;

            net.minecraft.world.entity.ai.attributes.Attribute attr = null;
            try {
                switch (type) {
                    case PERCENT_AD:
                        attr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
                        break;
                    case FLAT_AD:
                        attr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
                        break;
                    case ARMOR:
                        attr = net.minecraft.world.entity.ai.attributes.Attributes.ARMOR;
                        break;
                    case MAX_HEALTH:
                        attr = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
                        break;
                    case CRIT_CHANCE:
                        attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_chance"));
                        break;
                    case CRIT_DAMAGE:
                        attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_damage"));
                        break;
                    case ARMOR_SHRED:
                        attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("attributeslib", "armor_shred"));
                        break;
                    case ARMOR_PIERCE:
                        attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("attributeslib", "armor_pierce"));
                        break;
                    case IMPACT:
                        attr = yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes.IMPACT.get();
                        break;
                }
            } catch (Throwable t) {
                // Ignore missing mod attributes
            }

            if (attr != null) {
                java.util.UUID substatUuid = UUIDHelper.generateAttributeModifierUUID("refinement", "weapon_refinement_substat_" + type.getName().toLowerCase());
                net.minecraft.world.entity.ai.attributes.AttributeModifier modifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        substatUuid,
                        "Weapon Refinement Substat " + type.getName(),
                        value,
                        type.getOperation());
                event.addModifier(attr, modifier);
            }
        }
    }

    /**
     * Track mob spawn origin. Mobs spawned via Mob Spawners, Spawn Eggs, Summons,
     * Dispensers, Commands, or Breeding
     * are tagged to prevent gem drop exploits.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        net.minecraft.world.entity.MobSpawnType spawnType = event.getSpawnType();
        if (spawnType == net.minecraft.world.entity.MobSpawnType.SPAWNER ||
                spawnType == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG ||
                spawnType == net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED ||
                spawnType == net.minecraft.world.entity.MobSpawnType.DISPENSER ||
                spawnType == net.minecraft.world.entity.MobSpawnType.BUCKET ||
                spawnType == net.minecraft.world.entity.MobSpawnType.COMMAND ||
                spawnType == net.minecraft.world.entity.MobSpawnType.BREEDING) {
            event.getEntity().getPersistentData().putBoolean("FromSpawnerOrPlayer", true);
        }
    }

    /**
     * Mob Gem Drops:
     * Drops are restricted ONLY to naturally spawned world entities.
     * Excludes:
     * 1. Mobs spawned from Mob Spawners (Dungeons, spawner blocks)
     * 2. Mobs spawned via Spawn Eggs, Dispensers, Commands, or Breeding
     * 3. Player-constructible mobs (Iron Golems, Snow Golems, Withers)
     *
     * Drop Calculations:
     * Part 1: Decide how many gems to drop based on HP.
     * - Base Chance uses an exponential saturation curve: 1.0 - 0.92 * exp(-maxHp /
     * 500.0)
     * (11.6% at 20 HP -> 54.3% at 350 HP -> 87.5% at 1,000 HP -> 99.4% at 2,500
     * HP).
     * - Dynamic Additional Gem Cascade: Retention multiplier scales continuously
     * with HP
     * from 20% (20 HP mobs) up to 75% (50,000 HP World Bosses).
     * Part 2: Roll gem rarity using progression-tuned log-Gaussian wave functions.
     */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();
        if (entity == null || entity instanceof net.minecraft.world.entity.player.Player
                || entity.level().isClientSide()) {
            return;
        }

        // Exclude player-constructible mobs (Iron Golems, Snow Golems, Withers)
        if (entity instanceof net.minecraft.world.entity.animal.IronGolem ||
                entity instanceof net.minecraft.world.entity.animal.SnowGolem ||
                entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            return;
        }

        // Exclude mobs spawned from Mob Spawners, Spawn Eggs, Summons, Dispensers,
        // Commands, or Breeding
        net.minecraft.nbt.CompoundTag persistentData = entity.getPersistentData();
        if (persistentData.getBoolean("FromSpawnerOrPlayer") ||
                persistentData.getBoolean("spawner_spawned") ||
                persistentData.getBoolean("from_spawner") ||
                persistentData.getBoolean("PlayerCreated")) {
            return;
        }

        float maxHp = entity.getMaxHealth();
        net.minecraft.util.RandomSource random = entity.getRandom();

        // ----------------------------------------------------
        // PART 1: Decide how many gems to drop based on HP
        // (Asymptotic saturation curve for 1st gem, HP-scaled dynamic cascade for extra
        // gems)
        // ----------------------------------------------------
        double baseChance = 1.0 - 0.92 * Math.exp(-maxHp / 500.0);

        int gemCount = 0;
        // First roll
        if (random.nextDouble() < baseChance) {
            gemCount = 1;

            // Dynamic Multi-Gem Cascade: Multiplier scales with HP (20% at low HP -> 75% at
            // 50,000 HP)
            double cascadeMultiplier = Math.min(0.75, 0.20 + 0.55 * (1.0 - Math.exp(-maxHp / 15000.0)));
            double cascadeChance = baseChance * cascadeMultiplier;

            while (cascadeChance >= 1e-6 && random.nextDouble() < cascadeChance) {
                gemCount++;
                cascadeChance *= cascadeMultiplier;
            }
        }

        if (gemCount <= 0) {
            return;
        }

        // ----------------------------------------------------
        // PART 2: Roll the gem rarity (Selects ONE gem type)
        // (Log-Gaussian wave functions centered on Expert Gem peak at 10,000 HP, 50,000
        // HP Master cutoff)
        // ----------------------------------------------------
        net.minecraft.world.item.Item chosenGemItem;

        if (maxHp >= 50000.0f) {
            // Pinnacle World Bosses (>= 50,000 HP): Master Gem guaranteed by default cutoff
            chosenGemItem = com.complextalents.item.ModItems.MASTER_WEAPON_GEM.get();
        } else {
            double h = Math.min(50000.0, Math.max(1.0, (double) maxHp));

            // Continuous log-Gaussian wave equations bringing Expert Gem peak to 10,000 HP
            double wNovice = 100.0 * Math.exp(-Math.pow(h / 100.0, 1.2));
            double wApprentice = 100.0 * Math.exp(-Math.pow(Math.log(h / 350.0) / 1.2, 2.0));
            double wAdept = 100.0 * Math.exp(-Math.pow(Math.log(h / 2500.0) / 1.1, 2.0));
            double wExpert = 100.0 * Math.exp(-Math.pow(Math.log(h / 10000.0) / 1.1, 2.0));
            double wMaster = 100.0 * Math.pow(h / 50000.0, 1.5);

            double totalWeight = wMaster + wExpert + wAdept + wApprentice + wNovice;
            double roll = random.nextDouble() * totalWeight;

            if (roll < wMaster) {
                chosenGemItem = com.complextalents.item.ModItems.MASTER_WEAPON_GEM.get();
            } else if (roll < wMaster + wExpert) {
                chosenGemItem = com.complextalents.item.ModItems.EXPERT_WEAPON_GEM.get();
            } else if (roll < wMaster + wExpert + wAdept) {
                chosenGemItem = com.complextalents.item.ModItems.ADEPT_WEAPON_GEM.get();
            } else if (roll < wMaster + wExpert + wAdept + wApprentice) {
                chosenGemItem = com.complextalents.item.ModItems.APPRENTICE_WEAPON_GEM.get();
            } else {
                chosenGemItem = com.complextalents.item.ModItems.NOVICE_WEAPON_GEM.get();
            }
        }

        // Spawn chosen item stack
        ItemStack gemDrop = new ItemStack(chosenGemItem, gemCount);
        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                entity.level(),
                entity.getX(),
                entity.getY() + 0.5,
                entity.getZ(),
                gemDrop);
        itemEntity.setDefaultPickUpDelay();
        event.getDrops().add(itemEntity);
    }
}
