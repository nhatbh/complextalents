package com.complextalents.tacz;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mirror enum of tacz_attributes attribute keys.
 * Implements Supplier<Attribute> following standard Forge registry lookup patterns.
 * Hardcodes every single attribute path for every GunType statically based on the tacz_attributes registry dump.
 * Zero dynamic string resolution or concatenation at runtime.
 */
public enum GunAttributeType implements Supplier<Attribute> {
    GUN_DAMAGE("gun_damage", "pistol_damage", "sniper_damage", "rifle_damage", "shotgun_damage", "smg_damage", "rpg_damage", "mg_damage"),
    RELOAD_SPEED("reload_speed", "pistol_reload_speed", "sniper_reload_speed", "rifle_reload_speed", "shotgun_reload_speed", "smg_reload_speed", "rpg_reload_speed", "mg_reload_speed"),
    BOLT_ACTION_SPEED("bolt_action_speed", "pistol_bolt_action_speed", "sniper_bolt_action_speed", "rifle_bolt_action_speed", "shotgun_bolt_action_speed", "smg_bolt_action_speed", "rpg_bolt_action_speed", "mg_bolt_action_speed"),
    MAGAZINE_CAPACITY("magazine_capacity", "pistol_magazine_capacity", "sniper_magazine_capacity", "rifle_magazine_capacity", "shotgun_magazine_capacity", "smg_magazine_capacity", "rpg_magazine_capacity", "mg_magazine_capacity"),
    AMMO_SAVE_CHANCE("ammo_save_chance", "pistol_ammo_save_chance", "sniper_ammo_save_chance", "rifle_ammo_save_chance", "shotgun_ammo_save_chance", "smg_ammo_save_chance", "rpg_ammo_save_chance", "mg_ammo_save_chance"),
    AMMO_RECOVERY_CHANCE("ammo_recovery_chance", "pistol_ammo_recovery_chance", "sniper_ammo_recovery_chance", "rifle_ammo_recovery_chance", "shotgun_ammo_recovery_chance", "smg_ammo_recovery_chance", "rpg_ammo_recovery_chance", "mg_ammo_recovery_chance"),
    AMMO_RECOVERY_AMOUNT("ammo_recovery_amount", "pistol_ammo_recovery_amount", "sniper_ammo_recovery_amount", "rifle_ammo_recovery_amount", "shotgun_ammo_recovery_amount", "smg_ammo_recovery_amount", "rpg_ammo_recovery_amount", "mg_ammo_recovery_amount"),
    AMMO_RECOVERY_PERCENT("ammo_recovery_percent", "pistol_ammo_recovery_percent", "sniper_ammo_recovery_percent", "rifle_ammo_recovery_percent", "shotgun_ammo_recovery_percent", "smg_ammo_recovery_percent", "rpg_ammo_recovery_percent", "mg_ammo_recovery_percent"),
    RELOAD_AMMO_SAVE_CHANCE("reload_ammo_save_chance", "pistol_reload_ammo_save_chance", "sniper_reload_ammo_save_chance", "rifle_reload_ammo_save_chance", "shotgun_reload_ammo_save_chance", "smg_reload_ammo_save_chance", "rpg_reload_ammo_save_chance", "mg_reload_ammo_save_chance"),
    BONUS_AMMO_CHANCE("bonus_ammo_chance", "pistol_bonus_ammo_chance", "sniper_bonus_ammo_chance", "rifle_bonus_ammo_chance", "shotgun_bonus_ammo_chance", "smg_bonus_ammo_chance", "rpg_bonus_ammo_chance", "mg_bonus_ammo_chance"),
    BONUS_AMMO_AMOUNT("bonus_ammo_amount", "pistol_bonus_ammo_amount", "sniper_bonus_ammo_amount", "rifle_bonus_ammo_amount", "shotgun_bonus_ammo_amount", "smg_bonus_ammo_amount", "rpg_bonus_ammo_amount", "mg_bonus_ammo_amount"),
    BONUS_AMMO_PERCENT("bonus_ammo_percent", "pistol_bonus_ammo_percent", "sniper_bonus_ammo_percent", "rifle_bonus_ammo_percent", "shotgun_bonus_ammo_percent", "smg_bonus_ammo_percent", "rpg_bonus_ammo_percent", "mg_bonus_ammo_percent"),
    HIP_FIRE_ACCURACY("hip_fire_accuracy", "pistol_hip_fire_accuracy", "sniper_hip_fire_accuracy", "rifle_hip_fire_accuracy", "shotgun_hip_fire_accuracy", "smg_hip_fire_accuracy", "rpg_hip_fire_accuracy", "mg_hip_fire_accuracy"),
    ADS_ACCURACY("ads_accuracy", "pistol_ads_accuracy", "sniper_ads_accuracy", "rifle_ads_accuracy", "shotgun_ads_accuracy", "smg_ads_accuracy", "rpg_ads_accuracy", "mg_ads_accuracy"),
    HIP_FIRE_DAMAGE("hip_fire_damage", "pistol_hip_fire_damage", "sniper_hip_fire_damage", "rifle_hip_fire_damage", "shotgun_hip_fire_damage", "smg_hip_fire_damage", "rpg_hip_fire_damage", "mg_hip_fire_damage"),
    ADS_DAMAGE("ads_damage", "pistol_ads_damage", "sniper_ads_damage", "rifle_ads_damage", "shotgun_ads_damage", "smg_ads_damage", "rpg_ads_damage", "mg_ads_damage"),
    AUTO_DAMAGE("auto_damage", "pistol_auto_damage", "sniper_auto_damage", "rifle_auto_damage", "shotgun_auto_damage", "smg_auto_damage", "rpg_auto_damage", "mg_auto_damage"),
    SEMI_DAMAGE("semi_damage", "pistol_semi_damage", "sniper_semi_damage", "rifle_semi_damage", "shotgun_semi_damage", "smg_semi_damage", "rpg_semi_damage", "mg_semi_damage"),
    BURST_DAMAGE("burst_damage", "pistol_burst_damage", "sniper_burst_damage", "rifle_burst_damage", "shotgun_burst_damage", "smg_burst_damage", "rpg_burst_damage", "mg_burst_damage"),
    AUTO_ACCURACY("auto_accuracy", "pistol_auto_accuracy", "sniper_auto_accuracy", "rifle_auto_accuracy", "shotgun_auto_accuracy", "smg_auto_accuracy", "rpg_auto_accuracy", "mg_auto_accuracy"),
    SEMI_ACCURACY("semi_accuracy", "pistol_semi_accuracy", "sniper_semi_accuracy", "rifle_semi_accuracy", "shotgun_semi_accuracy", "smg_semi_accuracy", "rpg_semi_accuracy", "mg_semi_accuracy"),
    BURST_ACCURACY("burst_accuracy", "pistol_burst_accuracy", "sniper_burst_accuracy", "rifle_burst_accuracy", "shotgun_burst_accuracy", "smg_burst_accuracy", "rpg_burst_accuracy", "mg_burst_accuracy"),
    RECOIL("recoil", "pistol_recoil", "sniper_recoil", "rifle_recoil", "shotgun_recoil", "smg_recoil", "rpg_recoil", "mg_recoil"),
    VERTICAL_RECOIL("vertical_recoil", "pistol_vertical_recoil", "sniper_vertical_recoil", "rifle_vertical_recoil", "shotgun_vertical_recoil", "smg_vertical_recoil", "rpg_vertical_recoil", "mg_vertical_recoil"),
    HORIZONTAL_RECOIL("horizontal_recoil", "pistol_horizontal_recoil", "sniper_horizontal_recoil", "rifle_horizontal_recoil", "shotgun_horizontal_recoil", "smg_horizontal_recoil", "rpg_horizontal_recoil", "mg_horizontal_recoil"),
    ADS_RECOIL("ads_recoil", "pistol_ads_recoil", "sniper_ads_recoil", "rifle_ads_recoil", "shotgun_ads_recoil", "smg_ads_recoil", "rpg_ads_recoil", "mg_ads_recoil"),
    ADS_VERTICAL_RECOIL("ads_vertical_recoil", "pistol_ads_vertical_recoil", "sniper_ads_vertical_recoil", "rifle_ads_vertical_recoil", "shotgun_ads_vertical_recoil", "smg_ads_vertical_recoil", "rpg_ads_vertical_recoil", "mg_ads_vertical_recoil"),
    ADS_HORIZONTAL_RECOIL("ads_horizontal_recoil", "pistol_ads_horizontal_recoil", "sniper_ads_horizontal_recoil", "rifle_ads_horizontal_recoil", "shotgun_ads_horizontal_recoil", "smg_ads_horizontal_recoil", "rpg_ads_horizontal_recoil", "mg_ads_horizontal_recoil"),
    HIP_FIRE_RECOIL("hip_fire_recoil", "pistol_hip_fire_recoil", "sniper_hip_fire_recoil", "rifle_hip_fire_recoil", "shotgun_hip_fire_recoil", "smg_hip_fire_recoil", "rpg_hip_fire_recoil", "mg_hip_fire_recoil"),
    HIP_FIRE_VERTICAL_RECOIL("hip_fire_vertical_recoil", "pistol_hip_fire_vertical_recoil", "sniper_hip_fire_vertical_recoil", "rifle_hip_fire_vertical_recoil", "shotgun_hip_fire_vertical_recoil", "smg_hip_fire_vertical_recoil", "rpg_hip_fire_vertical_recoil", "mg_hip_fire_vertical_recoil"),
    HIP_FIRE_HORIZONTAL_RECOIL("hip_fire_horizontal_recoil", "pistol_hip_fire_horizontal_recoil", "sniper_hip_fire_horizontal_recoil", "rifle_hip_fire_horizontal_recoil", "shotgun_hip_fire_horizontal_recoil", "smg_hip_fire_horizontal_recoil", "rpg_hip_fire_horizontal_recoil", "mg_hip_fire_horizontal_recoil"),
    GUN_MOVEMENT_SPEED("gun_movement_speed", "pistol_gun_movement_speed", "sniper_gun_movement_speed", "rifle_gun_movement_speed", "shotgun_gun_movement_speed", "smg_gun_movement_speed", "rpg_gun_movement_speed", "mg_gun_movement_speed"),
    HEADSHOT_MULTIPLIER("headshot_multiplier", "pistol_headshot_multiplier", "sniper_headshot_multiplier", "rifle_headshot_multiplier", "shotgun_headshot_multiplier", "smg_headshot_multiplier", "rpg_headshot_multiplier", "mg_headshot_multiplier"),
    KNOCKBACK_MULTIPLIER("knockback_multiplier", "pistol_knockback_multiplier", "sniper_knockback_multiplier", "rifle_knockback_multiplier", "shotgun_knockback_multiplier", "smg_knockback_multiplier", "rpg_knockback_multiplier", "mg_knockback_multiplier"),
    KNOCKBACK_BASE("knockback_base", "pistol_knockback_base", "sniper_knockback_base", "rifle_knockback_base", "shotgun_knockback_base", "smg_knockback_base", "rpg_knockback_base", "mg_knockback_base"),
    PIERCE_MULTIPLIER("pierce_multiplier", "pistol_pierce_multiplier", "sniper_pierce_multiplier", "rifle_pierce_multiplier", "shotgun_pierce_multiplier", "smg_pierce_multiplier", "rpg_pierce_multiplier", "mg_pierce_multiplier"),
    RPM_MULTIPLIER("rpm_multiplier", "pistol_rpm_multiplier", "sniper_rpm_multiplier", "rifle_rpm_multiplier", "shotgun_rpm_multiplier", "smg_rpm_multiplier", "rpg_rpm_multiplier", "mg_rpm_multiplier"),
    ADS_SPEED("ads_speed", "pistol_ads_speed", "sniper_ads_speed", "rifle_ads_speed", "shotgun_ads_speed", "smg_ads_speed", "rpg_ads_speed", "mg_ads_speed"),
    SEMI_BULLET_AMOUNT("semi_bullet_amount", "pistol_semi_bullet_amount", "sniper_semi_bullet_amount", "rifle_semi_bullet_amount", "shotgun_semi_bullet_amount", "smg_semi_bullet_amount", "rpg_semi_bullet_amount", "mg_semi_bullet_amount"),
    AUTO_BULLET_AMOUNT("auto_bullet_amount", "pistol_auto_bullet_amount", "sniper_auto_bullet_amount", "rifle_auto_bullet_amount", "shotgun_auto_bullet_amount", "smg_auto_bullet_amount", "rpg_auto_bullet_amount", "mg_auto_bullet_amount"),
    BURST_BULLET_AMOUNT("burst_bullet_amount", "pistol_burst_bullet_amount", "sniper_burst_bullet_amount", "rifle_burst_bullet_amount", "shotgun_burst_bullet_amount", "smg_burst_bullet_amount", "rpg_burst_bullet_amount", "mg_burst_bullet_amount"),
    DRAW_SPEED("draw_speed", "pistol_draw_speed", "sniper_draw_speed", "rifle_draw_speed", "shotgun_draw_speed", "smg_draw_speed", "rpg_draw_speed", "mg_draw_speed"),
    BURST_SPEED("burst_speed", "pistol_burst_speed", "sniper_burst_speed", "rifle_burst_speed", "shotgun_burst_speed", "smg_burst_speed", "rpg_burst_speed", "mg_burst_speed");

    public static final String MODID_TACZ_ATTRIBUTES = "tacz_attributes";

    private final String id;
    private final Supplier<Attribute> globalSupplier;
    private final Map<GunType, Supplier<Attribute>> typeSuppliers = new EnumMap<>(GunType.class);

    GunAttributeType(String globalPath, String pistolPath, String sniperPath, String riflePath, String shotgunPath, String smgPath, String rpgPath, String mgPath) {
        this.id = globalPath;
        this.globalSupplier = () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, globalPath)
        );

        typeSuppliers.put(GunType.GLOBAL, globalSupplier);
        typeSuppliers.put(GunType.PISTOL, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, pistolPath)
        ));
        typeSuppliers.put(GunType.SNIPER, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, sniperPath)
        ));
        typeSuppliers.put(GunType.RIFLE, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, riflePath)
        ));
        typeSuppliers.put(GunType.SHOTGUN, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, shotgunPath)
        ));
        typeSuppliers.put(GunType.SMG, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, smgPath)
        ));
        typeSuppliers.put(GunType.RPG, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, rpgPath)
        ));
        typeSuppliers.put(GunType.MG, () -> ForgeRegistries.ATTRIBUTES.getValue(
            ResourceLocation.fromNamespaceAndPath(MODID_TACZ_ATTRIBUTES, mgPath)
        ));
    }

    public String getId() {
        return id;
    }

    @Override
    public Attribute get() {
        return globalSupplier.get();
    }

    public Attribute get(GunType gunType) {
        if (gunType == null) {
            return get();
        }
        Supplier<Attribute> supplier = typeSuppliers.get(gunType);
        return supplier != null ? supplier.get() : get();
    }
}
