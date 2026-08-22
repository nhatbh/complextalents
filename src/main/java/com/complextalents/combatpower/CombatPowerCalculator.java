package com.complextalents.combatpower;

import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import com.complextalents.skill.capability.SkillDataProvider;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class CombatPowerCalculator {

    public static int calculateCombatPower(Player player) {
        if (player == null) return 0;

        double cpTotal = 0;

        // 1. Attribute Stats CP (Base + Allocated Stats + Passives + Equipment)
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double maxHealth = player.getMaxHealth();
        double armor = player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

        cpTotal += (attackDamage * 12.0);
        cpTotal += (maxHealth * 2.0);
        cpTotal += (armor * 6.0);
        cpTotal += (toughness * 8.0);

        // Movement Speed above base (0.10)
        double speedBonus = Math.max(0, (speed - 0.10) / 0.10);
        cpTotal += (speedBonus * 400.0);

        // Dynamic General & Per-School Spell Power attributes (Iron's Spells 'n Spellbooks integration)
        for (var entry : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getEntries()) {
            net.minecraft.resources.ResourceLocation loc = entry.getKey().location();
            if ("irons_spellbooks".equals(loc.getNamespace())) {
                String path = loc.getPath();
                if (path.endsWith("_spell_power")) {
                    net.minecraft.world.entity.ai.attributes.Attribute attr = entry.getValue();
                    if (attr != null && player.getAttributes().hasAttribute(attr)) {
                        double val = player.getAttributeValue(attr);
                        double bonusPower = Math.max(0, val - 1.0);

                        if ("spell_power".equals(path)) {
                            // General Spell Power: +10 CP per 1% (+1000 CP per 1.0 bonus)
                            cpTotal += (bonusPower * 1000.0);
                        } else {
                            // Per-School Spell Power: +4 CP per 1% (+400 CP per 1.0 bonus)
                            cpTotal += (bonusPower * 400.0);
                        }
                    }
                }
            }
        }

        // Custom stats from GeneralStats capability
        var statsCap = player.getCapability(GeneralStatsDataProvider.STATS_DATA);
        if (statsCap.isPresent()) {
            var statsData = statsCap.orElseThrow(IllegalStateException::new);
            for (StatType type : StatType.values()) {
                int rank = statsData.getStatRank(type);
                if (rank <= 0) continue;

                if (type == StatType.LUCK_CRIT) {
                    cpTotal += (rank * type.getYieldPerRank() * 10.0);
                } else if (type == StatType.MAGIC_EFFECTIVENESS) {
                    cpTotal += (rank * type.getYieldPerRank() * 100.0 * 8.0);
                } else if (type == StatType.AP) {
                    cpTotal += (rank * type.getYieldPerRank() * 100.0 * 10.0);
                } else if (type == StatType.CDR) {
                    cpTotal += (rank * type.getYieldPerRank() * 8.0);
                } else if (type == StatType.MAX_HP) {
                    cpTotal += (rank * type.getYieldPerRank() * 2.0);
                } else if (type == StatType.FLAT_AD || type == StatType.PERCENT_AD) {
                    cpTotal += (rank * type.getYieldPerRank() * 12.0);
                } else {
                    cpTotal += (rank * 15.0);
                }
            }
        }

        // 2. Weapon Mastery CP
        var weaponCap = player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA);
        if (weaponCap.isPresent()) {
            var weaponData = weaponCap.orElseThrow(IllegalStateException::new);
            for (IWeaponMasteryData.WeaponPath path : IWeaponMasteryData.WeaponPath.values()) {
                int level = weaponData.getMasteryLevel(path);
                if (level <= 0) continue;

                int baseLevels = Math.min(level, 14);
                cpTotal += (baseLevels * 25.0);

                // Level 15 Master Pinnacle Rank Bonus
                if (level >= 15) {
                    cpTotal += 300.0;
                }
            }
        }

        // 3. Origin Skill CP
        var skillCap = player.getCapability(SkillDataProvider.SKILL_DATA);
        if (skillCap.isPresent()) {
            var skillData = skillCap.orElseThrow(IllegalStateException::new);
            for (ResourceLocation skillId : skillData.getAllLearnedSkills()) {
                int lvl = skillData.getSkillLevel(skillId);
                cpTotal += (lvl * 45.0);
            }
        }

        // 4. Spell Mastery CP
        var spellCap = player.getCapability(SpellMasteryDataProvider.MASTERY_DATA);
        if (spellCap.isPresent()) {
            var spellData = spellCap.orElseThrow(IllegalStateException::new);
            for (var entry : spellData.getAllMasteryLevels().entrySet()) {
                int level = entry.getValue();
                cpTotal += (level * 35.0);
            }
        }

        return (int) Math.round(cpTotal);
    }

    public static int getHighestCombatPower(Player player) {
        if (player == null) return 0;
        int currentCP = calculateCombatPower(player);
        int highestCP = currentCP;

        var statsCap = player.getCapability(GeneralStatsDataProvider.STATS_DATA);
        if (statsCap.isPresent()) {
            var statsData = statsCap.orElseThrow(IllegalStateException::new);
            if (currentCP > statsData.getHighestCombatPower()) {
                statsData.setHighestCombatPower(currentCP);
            }
            highestCP = statsData.getHighestCombatPower();
        }

        return highestCP;
    }

    public static KnightRank getKnightRank(Player player) {
        int highestCP = getHighestCombatPower(player);
        return KnightRank.fromCP(highestCP);
    }
}
