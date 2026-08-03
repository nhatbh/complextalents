package com.complextalents.weaponmastery.capability;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.network.PacketHandler;
import com.complextalents.util.UUIDHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WeaponMasteryData implements IWeaponMasteryData {

    public static final int CURRENT_MASTERY_VERSION = 2; // Schema version for 15-level progression

    private Player player;
    private final Map<WeaponPath, Double> accumulatedDamageMap = new HashMap<>();
    private final Map<WeaponPath, Integer> masteryLevelsMap = new HashMap<>(); // Path -> Level (0-15)

    // Attribute Modifier UUIDs
    private static final UUID BLADEMASTER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_ad");
    private static final UUID BLADEMASTER_MS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_ms");
    private static final UUID BLADEMASTER_AS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_as");

    private static final UUID COLOSSUS_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_ad");
    private static final UUID COLOSSUS_HP = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_hp");
    private static final UUID COLOSSUS_SWEEP = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_sweep");

    private static final UUID VANGUARD_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_ad");
    private static final UUID VANGUARD_MS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_ms");
    private static final UUID VANGUARD_HP = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_hp");
    private static final UUID VANGUARD_ARMOR = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_armor");

    private static final UUID REAPER_CRIT_CHANCE = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_crit_chance");
    private static final UUID REAPER_CRIT_DAMAGE = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_crit_damage");
    private static final UUID REAPER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_ad");
    private static final UUID REAPER_ARMOR_PEN = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_armor_pen");

    private static final UUID JUGGERNAUT_ARMOR_PEN = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_armor_pen");
    private static final UUID JUGGERNAUT_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_ad");
    private static final UUID JUGGERNAUT_ARMOR = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_armor");
    private static final UUID JUGGERNAUT_KNOCKBACK = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_knockback");

    private static final UUID BRAWLER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_ad");
    private static final UUID BRAWLER_AS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_as");
    private static final UUID BRAWLER_MS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_ms");

    public WeaponMasteryData() {
        for (WeaponPath path : WeaponPath.values()) {
            accumulatedDamageMap.put(path, 0.0);
            masteryLevelsMap.put(path, 0);
        }
    }

    public WeaponMasteryData(Player player) {
        this();
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public double getAccumulatedDamage(WeaponPath path) {
        return accumulatedDamageMap.getOrDefault(path, 0.0);
    }

    @Override
    public void addAccumulatedDamage(WeaponPath path, double amount) {
        if (path == null || amount <= 0) return;
        double current = accumulatedDamageMap.getOrDefault(path, 0.0);
        accumulatedDamageMap.put(path, current + amount);
        if (player != null && !player.level().isClientSide) {
            sync();
        }
    }

    @Override
    public int getMasteryLevel(WeaponPath path) {
        return masteryLevelsMap.getOrDefault(path, 0);
    }

    @Override
    public void setMasteryLevel(WeaponPath path, int level) {
        if (path == null) return;
        masteryLevelsMap.put(path, Math.max(0, Math.min(15, level)));
        
        if (player != null && !player.level().isClientSide) {
            applyStatRewards();
            sync();
        }
    }

    public static int getRanksCompleted(int level) {
        if (level < 2) return 0;  // Novice in progress
        if (level < 5) return 1;  // Novice Complete
        if (level < 9) return 2;  // Apprentice Complete
        if (level < 14) return 3; // Adept Complete
        if (level < 15) return 4; // Expert Complete
        return 5;                 // Master Pinnacle Complete
    }

    public void applyStatRewards() {
        if (player == null || player.level().isClientSide) return;
        
        applyBlademasterRewards();
        applyColossusRewards();
        applyVanguardRewards();
        applyReaperRewards();
        applyJuggernautRewards();
        applyBrawlerRewards();
    }

    private void updateModifier(Attribute attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
            if (amount != 0) {
                instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            }
        }
    }

    private void updateModModifier(String modId, String attrName, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath(modId, attrName));
        if (attribute != null) {
            updateModifier(attribute, uuid, name, amount, operation);
        }
    }

    private void applyBlademasterRewards() {
        int level = getMasteryLevel(WeaponPath.BLADEMASTER);
        int ranks = getRanksCompleted(level);

        double totalAd = (level * 0.75) + (level >= 15 ? 2.5 : 0);
        double totalMs = (ranks * 0.015) + (level >= 15 ? 0.05 : 0);
        double totalAs = (ranks * 0.02) + (level >= 15 ? 0.075 : 0);

        updateModifier(Attributes.ATTACK_DAMAGE, BLADEMASTER_AD, "Blademaster AD", totalAd, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.MOVEMENT_SPEED, BLADEMASTER_MS, "Blademaster MS", totalMs, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ATTACK_SPEED, BLADEMASTER_AS, "Blademaster AS", totalAs, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyColossusRewards() {
        int level = getMasteryLevel(WeaponPath.COLOSSUS);
        int ranks = getRanksCompleted(level);

        double totalAd = (level * 0.03) + (level >= 15 ? 0.125 : 0);
        double totalHp = (ranks * 2.0) + (level >= 15 ? 5.0 : 0);
        double totalSweep = (ranks * 0.03) + (level >= 15 ? 0.075 : 0);

        updateModifier(Attributes.ATTACK_DAMAGE, COLOSSUS_AD, "Colossus AD", totalAd, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MAX_HEALTH, COLOSSUS_HP, "Colossus HP", totalHp, AttributeModifier.Operation.ADDITION);
        updateModModifier("epicfight", "sweeping_area", COLOSSUS_SWEEP, "Colossus Sweep", totalSweep, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyVanguardRewards() {
        int level = getMasteryLevel(WeaponPath.VANGUARD);
        int ranks = getRanksCompleted(level);

        double totalAd = (level * 0.02) + (level >= 15 ? 0.10 : 0);
        double totalMs = (level * 0.0125) + (level >= 15 ? 0.05 : 0);
        double totalHp = (ranks * 2.0);
        double totalArmor = (level >= 15 ? 2.5 : 0);

        updateModifier(Attributes.ATTACK_DAMAGE, VANGUARD_AD, "Vanguard AD", totalAd, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MOVEMENT_SPEED, VANGUARD_MS, "Vanguard MS", totalMs, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MAX_HEALTH, VANGUARD_HP, "Vanguard HP", totalHp, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ARMOR, VANGUARD_ARMOR, "Vanguard Armor", totalArmor, AttributeModifier.Operation.ADDITION);
    }

    private void applyReaperRewards() {
        int level = getMasteryLevel(WeaponPath.REAPER);
        int ranks = getRanksCompleted(level);

        double totalCritChance = (level * 0.015) + (level >= 15 ? 0.075 : 0);
        double totalCritDamage = (ranks * 0.06) + (level >= 15 ? 0.175 : 0);
        double totalAd = (ranks * 0.5);
        double totalArmorPen = (level >= 15 ? 5.0 : 0);

        updateModModifier("attributeslib", "crit_chance", REAPER_CRIT_CHANCE, "Reaper Crit Chance", totalCritChance, AttributeModifier.Operation.ADDITION);
        updateModModifier("attributeslib", "crit_damage", REAPER_CRIT_DAMAGE, "Reaper Crit Damage", totalCritDamage, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ATTACK_DAMAGE, REAPER_AD, "Reaper AD", totalAd, AttributeModifier.Operation.ADDITION);
        updateModModifier("attributeslib", "armor_pierce", REAPER_ARMOR_PEN, "Reaper Armor Pen", totalArmorPen, AttributeModifier.Operation.ADDITION);
    }

    private void applyJuggernautRewards() {
        int level = getMasteryLevel(WeaponPath.JUGGERNAUT);
        int ranks = getRanksCompleted(level);

        double totalArmorPen = (level * 1.5) + (level >= 15 ? 10.0 : 0);
        double totalAd = (ranks * 0.03);
        double totalArmor = (level >= 15 ? 4.0 : 0);
        double totalKb = (ranks * 0.06) + (level >= 15 ? 0.125 : 0);

        updateModModifier("attributeslib", "armor_pierce", JUGGERNAUT_ARMOR_PEN, "Juggernaut Armor Pen", totalArmorPen, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ATTACK_DAMAGE, JUGGERNAUT_AD, "Juggernaut AD", totalAd, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ARMOR, JUGGERNAUT_ARMOR, "Juggernaut Armor", totalArmor, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ATTACK_KNOCKBACK, JUGGERNAUT_KNOCKBACK, "Juggernaut Knockback", totalKb, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyBrawlerRewards() {
        int level = getMasteryLevel(WeaponPath.BRAWLER);
        int ranks = getRanksCompleted(level);

        double totalAd = (level * 0.5) + (level >= 15 ? 3.0 : 0);
        double totalAs = (ranks * 0.02) + (level >= 15 ? 0.10 : 0);
        double totalMs = (ranks * 0.015) + (level >= 15 ? 0.075 : 0);

        updateModifier(Attributes.ATTACK_DAMAGE, BRAWLER_AD, "Brawler AD", totalAd, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ATTACK_SPEED, BRAWLER_AS, "Brawler AS", totalAs, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MOVEMENT_SPEED, BRAWLER_MS, "Brawler MS", totalMs, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    @Override
    public Map<WeaponPath, Integer> getAllMasteryLevels() {
        return new HashMap<>(masteryLevelsMap);
    }

    @Override
    public Map<WeaponPath, Double> getAllAccumulatedDamage() {
        return new HashMap<>(accumulatedDamageMap);
    }

    @Override
    public void setAllMasteryLevels(Map<WeaponPath, Integer> levels) {
        for (Map.Entry<WeaponPath, Integer> entry : levels.entrySet()) {
            masteryLevelsMap.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void setAllAccumulatedDamage(Map<WeaponPath, Double> damageMap) {
        for (Map.Entry<WeaponPath, Double> entry : damageMap.entrySet()) {
            accumulatedDamageMap.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketHandler.sendTo(new com.complextalents.weaponmastery.network.WeaponMasterySyncPacket(serializeNBT()), serverPlayer);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("MasteryVersion", CURRENT_MASTERY_VERSION);
        
        CompoundTag damageNbt = new CompoundTag();
        for (Map.Entry<WeaponPath, Double> entry : accumulatedDamageMap.entrySet()) {
            damageNbt.putDouble(entry.getKey().name(), entry.getValue());
        }
        nbt.put("AccumulatedDamage", damageNbt);
        
        CompoundTag levelsNbt = new CompoundTag();
        for (Map.Entry<WeaponPath, Integer> entry : masteryLevelsMap.entrySet()) {
            levelsNbt.putInt(entry.getKey().name(), entry.getValue());
        }
        nbt.put("MasteryLevels", levelsNbt);
        
        return nbt;
    }

    private static int calculateOldSPSpentForLevel(int oldLevel) {
        int total = 0;
        for (int i = 0; i < oldLevel && i < 25; i++) {
            if (i < 5) total += 1;
            else if (i < 15) total += 2;
            else if (i < 20) total += 3;
            else total += 4;
        }
        return total;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        int version = nbt.contains("MasteryVersion") ? nbt.getInt("MasteryVersion") : 1;

        if (nbt.contains("AccumulatedDamage")) {
            CompoundTag damageNbt = nbt.getCompound("AccumulatedDamage");
            for (String key : damageNbt.getAllKeys()) {
                WeaponPath path = WeaponPath.fromString(key);
                if (path != null) {
                    accumulatedDamageMap.put(path, damageNbt.getDouble(key));
                }
            }
        }
        
        int totalOldSPRefund = 0;
        if (nbt.contains("MasteryLevels")) {
            CompoundTag levelsNbt = nbt.getCompound("MasteryLevels");
            for (String key : levelsNbt.getAllKeys()) {
                WeaponPath path = WeaponPath.fromString(key);
                if (path != null) {
                    int rawLvl = levelsNbt.getInt(key);
                    if (version < CURRENT_MASTERY_VERSION) {
                        totalOldSPRefund += calculateOldSPSpentForLevel(rawLvl);
                        masteryLevelsMap.put(path, 0); // Reset for migration
                    } else {
                        masteryLevelsMap.put(path, Math.min(15, rawLvl));
                    }
                }
            }
        }

        // Handle SP Refund for Old Save Files (Version 1 -> Version 2 migration)
        if (version < CURRENT_MASTERY_VERSION && totalOldSPRefund > 0 && player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            final int refundAmount = totalOldSPRefund;
            serverPlayer.getServer().execute(() -> {
                PlayerLevelingData levelingData = PlayerLevelingData.get(serverPlayer.getServer());
                int currentConsumed = levelingData.getConsumedSkillPoints(serverPlayer.getUUID());
                levelingData.setConsumedSkillPoints(serverPlayer.getUUID(), Math.max(0, currentConsumed - refundAmount));
                LevelingSyncHandler.syncPlayerLevelData(serverPlayer);
                serverPlayer.sendSystemMessage(Component.literal("\u00A7a[Weapon Mastery] Progression updated! Reset old levels and refunded \u00A7b" + refundAmount + " SP\u00A7a to your pool.")
                        .withStyle(ChatFormatting.GREEN));
            });
        }
        
        if (player != null && !player.level().isClientSide) {
            applyStatRewards();
        }
    }
}
