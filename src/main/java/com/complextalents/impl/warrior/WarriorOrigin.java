package com.complextalents.impl.warrior;

import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.ResourceType;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Warrior Origin implementation.
 */
public class WarriorOrigin {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "warrior");
    public static final ResourceLocation STYLE_RESOURCE_ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "style");

    public static void register() {
        // Register Style resource type
        ResourceType styleType = ResourceType.register(STYLE_RESOURCE_ID, "Style", 0, 1000, 0xFFFFD700); // Gold color

        // Build and register Warrior origin
        OriginBuilder.create(ID)
                .displayName(Component.translatable("origin.complextalents.warrior"))
                .description(Component.translatable("origin.complextalents.warrior.desc"))
                .resourceType(styleType)
                .maxLevel(5)
                .baseStat(StatType.PERCENT_AD, 4)
                .baseStat(StatType.MAX_HP, 2)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                // Iron Skin - Base Armor Gain Per Character Level
                .scaledStat("iron_skin_armor", "Giáp Cơ Bản/Cấp", new double[] { 0.30, 0.50, 0.70, 0.90, 1.20 })
                // Weapon Path Specific Parry Bonuses
                .scaledStat("blademaster_parry_ad", "Blademaster AD %", new double[] { 0.15, 0.20, 0.25, 0.30, 0.35 })
                .scaledStat("colossus_parry_cooldown", "Colossus Cooldown (s)", new double[] { 10.0, 9.0, 8.0, 7.0, 6.0 })
                .scaledStat("colossus_parry_kb", "Colossus Knockback", new double[] { 1.2, 1.4, 1.6, 1.8, 2.0 })
                .scaledStat("reaper_parry_crit_dur", "Reaper Crit Duration (s)", new double[] { 3.0, 3.5, 4.0, 4.5, 5.0 })
                .scaledStat("juggernaut_parry_as", "Juggernaut AS %", new double[] { 0.20, 0.25, 0.30, 0.35, 0.40 })
                .scaledStat("vanguard_parry_poise", "Vanguard Poise %", new double[] { 0.25, 0.35, 0.45, 0.55, 0.65 })
                .scaledStat("brawler_parry_speed", "Brawler Speed %", new double[] { 0.20, 0.25, 0.30, 0.35, 0.40 })

                .passiveSkill("Da Thép (Iron Skin)",
                        "Tăng giáp cơ bản nhận được theo cấp độ nhân vật từ +0.30 đến +1.20 giáp cho mỗi cấp (dựa trên cấp Đấu Sĩ).")
                .passiveSkill("Đỡ Đòn Hoàn Hảo & Tinh Thông Vũ Khí",
                        "Khi đỡ đòn hoàn hảo (Perfect Parry), hoàn lại 100% Thể Lực và xóa phạt Thủ. Đồng thời kích hoạt hiệu ứng theo Nhánh Vũ Khí (Kiếm Thánh: +15-35% Sát Thương; Bá Vương: Sóng bộc phá đẩy lùi; Tử Thần: 100% Chí Mạng; Cuồng Chiến: +20-40% Tốc Độ Đánh; Tiên Phong: +25-65% ST Poise; Quyền Thủ: +20-40% Tốc Độ Di Chuyển).")
                .activeSkill("Khiêu Chiến & Phản Trảm",
                        "Giơ khiên hấp thụ sát thương và khiêu khích kẻ địch xung quanh, sau đó nhả khiên để phản lại sát thương diện rộng bằng 100% đến 200% lượng sát thương đã hấp thụ.",
                        null)
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "challengers_retribution"))
                // Reduced base level armor + Iron Skin scaling per level
                .levelArmorCalc((lvl, player) -> {
                    if (lvl <= 1) return 0.0;
                    int originLevel = player != null ? com.complextalents.origin.OriginManager.getOriginLevel(player) : 1;
                    com.complextalents.origin.Origin origin = com.complextalents.origin.OriginRegistry.getInstance().getOrigin(ID);
                    double ironSkinBonus = origin != null ? origin.getScaledStat("iron_skin_armor", originLevel) : 0.30;
                    return (double) Math.round((lvl - 1) * (0.40 + ironSkinBonus));
                })
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(40.0, Math.pow(lvl - 1, 1.1) * 0.4))
                .levelHealthCalc(lvl -> 0.0)
                .renderer(new com.complextalents.impl.warrior.client.WarriorRenderer())
                .gunConfusionMessages(
                        "origin.complextalents.warrior.gun_msg.1",
                        "origin.complextalents.warrior.gun_msg.2",
                        "origin.complextalents.warrior.gun_msg.3",
                        "origin.complextalents.warrior.gun_msg.4",
                        "origin.complextalents.warrior.gun_msg.5"
                )
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 2)
                .cost(StatType.PERCENT_AD, 1)
                .cost(StatType.AP, 4)
                .cost(StatType.LUCK_CRIT, 3)
                .cost(StatType.MAX_HP, 2)
                .cost(StatType.MAX_MANA, 4)
                .cost(StatType.CDR, 3)
                .spellMasteryCostMultiplier(3.0) // Warrior terrible with spells, 300% cost
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .weaponMasteryCostMultiplier(1.0); // Warrior normal with melee weapons, 100% cost
    }
}
