package com.complextalents.tacz;

/**
 * Defines the base attribute definitions for TACZ gun combat systems.
 */
public enum GunAttributeType {
    // --- Damage & Combat ---
    GUN_DAMAGE("gun_damage", "Gun Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    HIP_FIRE_DAMAGE("hip_fire_damage", "Hip Fire Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    ADS_DAMAGE("ads_damage", "ADS Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    SEMI_DAMAGE("semi_damage", "Semi Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    AUTO_DAMAGE("auto_damage", "Auto Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    BURST_DAMAGE("burst_damage", "Burst Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    HEADSHOT_MULTIPLIER("headshot_multiplier", "Headshot Damage Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    KNOCKBACK_MULTIPLIER("knockback_multiplier", "Knockback Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    KNOCKBACK_BASE("knockback_base", "Base Knockback", 0.0, 0.0, 100.0, Operation.ADDITIVE),
    PIERCE_MULTIPLIER("pierce_multiplier", "Pierce Count Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),

    // --- Accuracy & Recoil ---
    HIP_FIRE_ACCURACY("hip_fire_accuracy", "Hip Fire Accuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    ADS_ACCURACY("ads_accuracy", "ADS Accuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    SEMI_ACCURACY("semi_accuracy", "Semi Accuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    AUTO_ACCURACY("auto_accuracy", "Auto Accuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    BURST_ACCURACY("burst_accuracy", "Burst Accuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    MOVEMENT_INACCURACY("movement_inaccuracy", "Movement Inaccuracy Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    FORTITUDE("fortitude", "Heart Rate Fortitude Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),

    RECOIL("recoil", "Recoil Control Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    RECOIL_PITCH("recoil_pitch", "Vertical Recoil Control Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    RECOIL_YAW("recoil_yaw", "Horizontal Recoil Control Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),

    ADS_RECOIL("ads_recoil", "ADS Recoil Control Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    ADS_RECOIL_PITCH("ads_recoil_pitch", "ADS Vertical Recoil Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    ADS_RECOIL_YAW("ads_recoil_yaw", "ADS Horizontal Recoil Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),

    HIP_FIRE_RECOIL("hip_fire_recoil", "Hip Fire Recoil Control Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    HIP_FIRE_RECOIL_PITCH("hip_fire_recoil_pitch", "Hip Fire Vertical Recoil Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),
    HIP_FIRE_RECOIL_YAW("hip_fire_recoil_yaw", "Hip Fire Horizontal Recoil Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),

    // --- Speed & Handling ---
    RELOAD_SPEED("reload_speed", "Reload Speed Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    BOLT_ACTION_SPEED("bolt_action_speed", "Bolt Action Speed Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    RPM_MULTIPLIER("rpm_multiplier", "Fire Rate Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    ADS_SPEED("ads_speed", "ADS Speed Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    DRAW_SPEED("draw_speed", "Draw Speed Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    BURST_SPEED("burst_speed", "Burst Speed Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    GUN_MOVEMENT_SPEED("gun_movement_speed", "Gun Movement Speed Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE),

    // --- Ammo & Magazine ---
    MAGAZINE_CAPACITY("magazine_capacity", "Magazine Capacity Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    SEMI_BULLET_AMOUNT("semi_bullet_amount", "Semi Bullets Per Shot Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    AUTO_BULLET_AMOUNT("auto_bullet_amount", "Auto Bullets Per Shot Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    BURST_BULLET_AMOUNT("burst_bullet_amount", "Burst Bullets Per Shot Multiplier", 1.0, 0.01, 100.0, Operation.MULTIPLICATIVE),
    AMMO_SAVE_CHANCE("ammo_save_chance", "Ammo Save Chance", 0.0, 0.0, 1.0, Operation.ADDITIVE),
    AMMO_RECOVERY_CHANCE("ammo_recovery_chance", "Ammo Recovery Chance", 0.0, 0.0, 1.0, Operation.ADDITIVE),
    AMMO_RECOVERY_AMOUNT("ammo_recovery_amount", "Ammo Recovery Flat Amount", 0.0, 0.0, 1000.0, Operation.ADDITIVE),
    AMMO_RECOVERY_PERCENT("ammo_recovery_percent", "Ammo Recovery Percent", 0.0, 0.0, 1.0, Operation.ADDITIVE),
    RELOAD_AMMO_SAVE_CHANCE("reload_ammo_save_chance", "Reload Ammo Save Chance", 0.0, 0.0, 1.0, Operation.ADDITIVE),
    BONUS_AMMO_CHANCE("bonus_ammo_chance", "Bonus Loaded Ammo Chance", 0.0, 0.0, 1.0, Operation.ADDITIVE),
    BONUS_AMMO_AMOUNT("bonus_ammo_amount", "Bonus Loaded Ammo Amount", 0.0, 0.0, 1000.0, Operation.ADDITIVE),
    BONUS_AMMO_PERCENT("bonus_ammo_percent", "Bonus Loaded Ammo Percent", 0.0, 0.0, 5.0, Operation.ADDITIVE),
    AMMO_CRAFTING_YIELD("ammo_crafting_yield", "Ammo Crafting Yield Multiplier", 1.0, 0.0, 100.0, Operation.MULTIPLICATIVE);

    public enum Operation {
        MULTIPLICATIVE,
        ADDITIVE
    }

    private final String id;
    private final String displayName;
    private final double defaultValue;
    private final double minValue;
    private final double maxValue;
    private final Operation operation;

    GunAttributeType(String id, String displayName, double defaultValue, double minValue, double maxValue, Operation operation) {
        this.id = id;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.operation = operation;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public Operation getOperation() {
        return operation;
    }
}
