package com.complextalents.weaponmastery.capability;

import com.complextalents.network.PacketHandler;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.complextalents.util.UUIDHelper;

public class WeaponMasteryData implements IWeaponMasteryData {

    private Player player;
    private final Map<WeaponPath, Double> accumulatedDamageMap = new HashMap<>(); // Path -> Damage
    private final Map<WeaponPath, Integer> masteryLevelsMap = new HashMap<>(); // Path -> Level (0-25)

    // Modification UUIDs for our weapon mastery bonuses
    private static final UUID BLADEMASTER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_ad");
    private static final UUID BLADEMASTER_MS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_ms");
    private static final UUID BLADEMASTER_AS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "blademaster_as");

    private static final UUID COLOSSUS_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_ad");
    private static final UUID COLOSSUS_HP = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_hp");
    private static final UUID COLOSSUS_SWEEP = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "colossus_sweep");

    private static final UUID VANGUARD_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_ad");
    private static final UUID VANGUARD_CDR = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "vanguard_cdr");

    private static final UUID REAPER_CRIT_CHANCE = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_crit_chance");
    private static final UUID REAPER_CRIT_DAMAGE = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_crit_damage");
    private static final UUID REAPER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "reaper_ad");

    private static final UUID JUGGERNAUT_ARMOR_PEN = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_armor_pen");
    private static final UUID JUGGERNAUT_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_ad");
    private static final UUID JUGGERNAUT_KNOCKBACK = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "juggernaut_knockback");

    private static final UUID BRAWLER_AD = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_ad");
    private static final UUID BRAWLER_AS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_as");
    private static final UUID BRAWLER_MS = UUIDHelper.generateAttributeModifierUUID("weapon_mastery", "brawler_ms");

    public WeaponMasteryData() {
        // Default constructor for global storage
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
            sync(); // Sync after adding damage
        }
    }

    @Override
    public int getMasteryLevel(WeaponPath path) {
        return masteryLevelsMap.getOrDefault(path, 0);
    }

    @Override
    public void setMasteryLevel(WeaponPath path, int level) {
        if (path == null) return;
        masteryLevelsMap.put(path, Math.max(0, Math.min(25, level)));
        
        if (player != null && !player.level().isClientSide) {
            applyStatRewards();
            sync();
        }
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
        int levels = getMasteryLevel(WeaponPath.BLADEMASTER);
        int ranks = levels / 5; // Major rank every 5 levels
        updateModifier(Attributes.ATTACK_DAMAGE, BLADEMASTER_AD, "Blademaster AD", levels * 0.5, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.MOVEMENT_SPEED, BLADEMASTER_MS, "Blademaster MS", ranks * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ATTACK_SPEED, BLADEMASTER_AS, "Blademaster AS", ranks * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyColossusRewards() {
        int levels = getMasteryLevel(WeaponPath.COLOSSUS);
        int ranks = levels / 5;
        updateModifier(Attributes.ATTACK_DAMAGE, COLOSSUS_AD, "Colossus AD", levels * 0.03, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MAX_HEALTH, COLOSSUS_HP, "Colossus HP", ranks * 5.0, AttributeModifier.Operation.ADDITION);
        updateModModifier("epicfight", "sweeping_area", COLOSSUS_SWEEP, "Colossus Sweep", ranks * 0.05, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyVanguardRewards() {
        int levels = getMasteryLevel(WeaponPath.VANGUARD);
        int ranks = levels / 5;
        updateModifier(Attributes.ATTACK_DAMAGE, VANGUARD_AD, "Vanguard AD", levels * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
        // Reach is unsupported, changed to CDR in plan
        double totalCDR = ranks * 0.05; // 5% base CDR equivalent for now per rank
        double cdrPercentage = totalCDR / (1.0 + totalCDR); // simple formula
        updateModModifier("irons_spellbooks", "cooldown_reduction", VANGUARD_CDR, "Vanguard CDR", cdrPercentage, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private void applyReaperRewards() {
        int levels = getMasteryLevel(WeaponPath.REAPER);
        int ranks = levels / 5;
        updateModModifier("attributeslib", "crit_chance", REAPER_CRIT_CHANCE, "Reaper Crit Chance", levels * 0.01, AttributeModifier.Operation.ADDITION);
        updateModModifier("attributeslib", "crit_damage", REAPER_CRIT_DAMAGE, "Reaper Crit Damage", ranks * 0.05, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ATTACK_DAMAGE, REAPER_AD, "Reaper AD", ranks * 0.5, AttributeModifier.Operation.ADDITION);
    }

    private void applyJuggernautRewards() {
        int levels = getMasteryLevel(WeaponPath.JUGGERNAUT);
        int ranks = levels / 5;
        updateModModifier("attributeslib", "armor_pierce", JUGGERNAUT_ARMOR_PEN, "Juggernaut Armor Pen", levels * 1.0, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ATTACK_DAMAGE, JUGGERNAUT_AD, "Juggernaut AD", ranks * 0.05, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.ATTACK_KNOCKBACK, JUGGERNAUT_KNOCKBACK, "Juggernaut Knockback", ranks * 0.1, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private void applyBrawlerRewards() {
        int levels = getMasteryLevel(WeaponPath.BRAWLER);
        int ranks = levels / 5;
        updateModifier(Attributes.ATTACK_DAMAGE, BRAWLER_AD, "Brawler AD", levels * 0.5, AttributeModifier.Operation.ADDITION);
        updateModifier(Attributes.ATTACK_SPEED, BRAWLER_AS, "Brawler AS", ranks * 0.03, AttributeModifier.Operation.MULTIPLY_BASE);
        updateModifier(Attributes.MOVEMENT_SPEED, BRAWLER_MS, "Brawler MS", ranks * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
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

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("AccumulatedDamage")) {
            CompoundTag damageNbt = nbt.getCompound("AccumulatedDamage");
            for (String key : damageNbt.getAllKeys()) {
                WeaponPath path = WeaponPath.fromString(key);
                if (path != null) {
                    accumulatedDamageMap.put(path, damageNbt.getDouble(key));
                }
            }
        }
        
        if (nbt.contains("MasteryLevels")) {
            CompoundTag levelsNbt = nbt.getCompound("MasteryLevels");
            for (String key : levelsNbt.getAllKeys()) {
                WeaponPath path = WeaponPath.fromString(key);
                if (path != null) {
                    masteryLevelsMap.put(path, levelsNbt.getInt(key));
                }
            }
        }
        
        if (!player.level().isClientSide) {
            applyStatRewards();
        }
    }
}
