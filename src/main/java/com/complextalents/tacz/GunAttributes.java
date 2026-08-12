package com.complextalents.tacz;

import com.complextalents.TalentsMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Manages the registration and computation for all 264 TACZ gun attributes.
 * (43 base categories across 8 scopes: Global + 7 Gun Archetypes)
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GunAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, TalentsMod.MODID);

    private static final Map<GunAttributeType, Map<GunType, RegistryObject<Attribute>>> ATTRIBUTE_MAP =
            new EnumMap<>(GunAttributeType.class);


    static {
        for (GunAttributeType attrType : GunAttributeType.values()) {
            Map<GunType, RegistryObject<Attribute>> scopeMap = new EnumMap<>(GunType.class);
            for (GunType gunType : GunType.values()) {
                String regName = gunType.isGlobal() ?
                        attrType.getId() :
                        gunType.getId() + "_" + attrType.getId();

                String descriptionId = "attribute.complextalents." + regName;

                RegistryObject<Attribute> regObj = ATTRIBUTES.register(regName, () ->
                        new RangedAttribute(
                                descriptionId,
                                attrType.getDefaultValue(),
                                attrType.getMinValue(),
                                attrType.getMaxValue()
                        ).setSyncable(true)
                );
                scopeMap.put(gunType, regObj);
            }
            ATTRIBUTE_MAP.put(attrType, scopeMap);
        }
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
    }

    /**
     * Gets the RegistryObject for a specific attribute type and scope.
     */
    public static RegistryObject<Attribute> getRegistryObject(GunAttributeType attrType, GunType gunType) {
        Map<GunType, RegistryObject<Attribute>> scopeMap = ATTRIBUTE_MAP.get(attrType);
        if (scopeMap == null) return null;
        return scopeMap.get(gunType != null ? gunType : GunType.GLOBAL);
    }

    /**
     * Gets the Attribute instance for a specific attribute type and scope.
     */
    public static Attribute getAttribute(GunAttributeType attrType, GunType gunType) {
        RegistryObject<Attribute> reg = getRegistryObject(attrType, gunType);
        return reg != null ? reg.get() : null;
    }

    /**
     * Computes the combined value of an attribute for an entity (Global * Archetype or Global + Archetype).
     */
    public static double getValue(LivingEntity entity, GunAttributeType attrType, GunType gunType) {
        if (entity == null || attrType == null) {
            return attrType != null ? attrType.getDefaultValue() : 1.0;
        }

        Attribute globalAttr = getAttribute(attrType, GunType.GLOBAL);
        double globalVal = (globalAttr != null && entity.getAttributes().hasAttribute(globalAttr)) ?
                entity.getAttributeValue(globalAttr) : attrType.getDefaultValue();

        double masteryBonus = 0.0;
        if (entity instanceof Player player) {
            // Marksman Adrenaline Mode Skill Bonuses: Faster reload & increased headshot damage
            if (com.complextalents.impl.marksman.data.MarksmanAdrenalineData.isActive(player)) {
                if (attrType == GunAttributeType.RELOAD_SPEED) {
                    masteryBonus += 0.60; // 60% faster reload speed during Adrenaline
                } else if (attrType == GunAttributeType.HEADSHOT_MULTIPLIER) {
                    masteryBonus += 0.75; // +75% bonus headshot damage multiplier during Adrenaline
                }
            }

            // 1. General Stats bonus
            var statsOpt = player.getCapability(com.complextalents.stats.capability.GeneralStatsDataProvider.STATS_DATA);
            if (statsOpt.isPresent()) {
                var stats = statsOpt.orElse(null);
                if (stats != null) {
                    switch (attrType) {
                        case GUN_DAMAGE -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.GUN_DAMAGE) * com.complextalents.stats.StatType.GUN_DAMAGE.getYieldPerRank();
                        case RELOAD_SPEED -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.RELOAD_SPEED) * com.complextalents.stats.StatType.RELOAD_SPEED.getYieldPerRank();
                        case FORTITUDE -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.FORTITUDE) * com.complextalents.stats.StatType.FORTITUDE.getYieldPerRank();
                        case HEADSHOT_MULTIPLIER -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.HEADSHOT_DAMAGE) * com.complextalents.stats.StatType.HEADSHOT_DAMAGE.getYieldPerRank();
                        case RECOIL -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.RECOIL_CONTROL) * com.complextalents.stats.StatType.RECOIL_CONTROL.getYieldPerRank();
                        case PIERCE_MULTIPLIER -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.BULLET_PENETRATION) * com.complextalents.stats.StatType.BULLET_PENETRATION.getYieldPerRank();

                        case RPM_MULTIPLIER -> masteryBonus += stats.getStatRank(com.complextalents.stats.StatType.FIRE_RATE) * com.complextalents.stats.StatType.FIRE_RATE.getYieldPerRank();
                        default -> {}

                    }
                }
            }

            // 2. Gun Mastery Archetype bonus
            var dataOpt = player.getCapability(com.complextalents.gunmastery.capability.GunMasteryDataProvider.GUN_MASTERY_DATA);
            if (dataOpt.isPresent()) {
                var data = dataOpt.orElse(null);
                if (data != null) {
                    if (gunType != null && !gunType.isGlobal()) {
                        int level = data.getMasteryLevel(gunType);
                        masteryBonus += com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(gunType, attrType, level);
                    } else if (gunType == null || gunType.isGlobal()) {
                        // For global scope (e.g., FORTITUDE from Rifle), sum across all archetypes
                        for (GunType gt : GunType.values()) {
                            if (!gt.isGlobal()) {
                                int level = data.getMasteryLevel(gt);
                                masteryBonus += com.complextalents.gunmastery.GunMasteryManager.getInstance().getStatBonus(gt, attrType, level);
                            }
                        }
                    }
                }
            }

            // 3. Held Firearm Refinement Substat bonus
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand != null && mainHand.hasTag() && mainHand.getTag().contains("RefineSubstats")) {
                net.minecraft.nbt.CompoundTag substatsTag = mainHand.getTag().getCompound("RefineSubstats");
                switch (attrType) {
                    case GUN_DAMAGE -> { if (substatsTag.contains("GUN_DAMAGE")) masteryBonus += substatsTag.getDouble("GUN_DAMAGE"); }
                    case HEADSHOT_MULTIPLIER -> { if (substatsTag.contains("HEADSHOT_MULTIPLIER")) masteryBonus += substatsTag.getDouble("HEADSHOT_MULTIPLIER"); }
                    case RPM_MULTIPLIER -> { if (substatsTag.contains("RPM_MULTIPLIER")) masteryBonus += substatsTag.getDouble("RPM_MULTIPLIER"); }
                    case RELOAD_SPEED -> { if (substatsTag.contains("RELOAD_SPEED")) masteryBonus += substatsTag.getDouble("RELOAD_SPEED"); }
                    case RECOIL -> { if (substatsTag.contains("RECOIL")) masteryBonus -= substatsTag.getDouble("RECOIL"); }
                    case ADS_SPEED -> { if (substatsTag.contains("ADS_SPEED")) masteryBonus += substatsTag.getDouble("ADS_SPEED"); }
                    case ADS_ACCURACY -> { if (substatsTag.contains("ADS_ACCURACY")) masteryBonus += substatsTag.getDouble("ADS_ACCURACY"); }
                    case HIP_FIRE_ACCURACY -> { if (substatsTag.contains("HIP_FIRE_ACCURACY")) masteryBonus += substatsTag.getDouble("HIP_FIRE_ACCURACY"); }
                    case DRAW_SPEED -> { if (substatsTag.contains("DRAW_SPEED")) masteryBonus += substatsTag.getDouble("DRAW_SPEED"); }
                    case BOLT_ACTION_SPEED -> { if (substatsTag.contains("BOLT_ACTION_SPEED")) masteryBonus += substatsTag.getDouble("BOLT_ACTION_SPEED"); }
                    default -> {}
                }
            }
        }




        if (gunType == null || gunType.isGlobal()) {
            if (attrType.getOperation() == GunAttributeType.Operation.MULTIPLICATIVE) {
                return globalVal * (1.0 + masteryBonus);
            } else {
                return globalVal + masteryBonus;
            }
        }

        Attribute typeAttr = getAttribute(attrType, gunType);
        double typeVal = (typeAttr != null && entity.getAttributes().hasAttribute(typeAttr)) ?
                entity.getAttributeValue(typeAttr) : attrType.getDefaultValue();

        if (attrType.getOperation() == GunAttributeType.Operation.MULTIPLICATIVE) {
            return globalVal * (typeVal * (1.0 + masteryBonus));
        } else {
            return globalVal + typeVal + masteryBonus;
        }
    }

    /**
     * Helper to compute combined value directly using TACZ raw archetype string.
     */
    public static double getCombinedValue(LivingEntity entity, GunAttributeType attrType, String rawGunType) {
        return getValue(entity, attrType, GunType.fromId(rawGunType));
    }

    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (Map<GunType, RegistryObject<Attribute>> scopeMap : ATTRIBUTE_MAP.values()) {
            for (RegistryObject<Attribute> attrReg : scopeMap.values()) {
                if (attrReg != null && attrReg.isPresent()) {
                    event.add(EntityType.PLAYER, attrReg.get());
                }
            }
        }
    }
}
