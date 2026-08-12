package com.complextalents.impl.pixie.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.pixie.skills.BondOfTheFaeSkill;
import com.complextalents.impl.pixie.skills.FaeSurgeSkill;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pixie Origin - Invulnerable Support Companion.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class PixieOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "pixie");

    /**
     * Check if a player is a Pixie.
     */
    public static boolean isPixie(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }

    /**
     * Register the Pixie origin.
     */
    public static void register() {
        OriginBuilder.create(ID)
                .displayName(Component.translatable("origin.complextalents.pixie"))
                .description(Component.translatable("origin.complextalents.pixie.desc"))
                .maxLevel(5)
                .baseStat(StatType.MAX_MANA, 10)
                .baseStat(StatType.CDR, 5)
                .baseStat(StatType.MAX_HP, -15)
                .baseStat(StatType.HEAL_AND_SHIELD, -6)
                .passiveSkill("Khế Ước Tiên Linh",
                        "Nhập thể vào đồng minh để trở nên bất tử và tăng 10% đến 35% sức mạnh tấn công cùng 5% đến 25% phòng thủ cho người được bảo vệ.")
                .activeSkill("Bộc Phát Tiên Khí & Dấu Ấn Tiên Linh",
                        "Nhấn để tạo lá chắn và tăng tốc cho đồng minh, đồng thời khắc dấu ấn nổ sát thương phép lên kẻ địch. Nhấn giữ để nhập thể, hủy nhập thể hoặc chuyển đổi mục tiêu bảo vệ.",
                        ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/pixie/fae_surge.png"))
                .activeSkillId(FaeSurgeSkill.ID)
                .scaledStat("physicalAdBuff", "Tăng AD Ký Chủ (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("physicalDefBuff", "Tăng Giáp Ký Chủ (%)", new double[] { 0.05, 0.10, 0.15, 0.20, 0.25 })
                .scaledStat("magicalApBuff", "Tăng AP Ký Chủ (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("magicalManaRegenBuff", "Tăng Hồi Mana (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.30 })
                .scaledStat("tetherRange", "Tầm Dây Trói (Khối)", new double[] { 6.0, 7.0, 8.0, 9.0, 10.0 })
                // Level defensive scaling (Pixie = Fae Companion controlled curve)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.4))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(15.0, Math.pow(lvl - 1, 1.1) * 0.1))
                .levelHealthCalc(lvl -> 0.0)
                .gunConfusionMessages(
                        "origin.complextalents.pixie.gun_msg.1",
                        "origin.complextalents.pixie.gun_msg.2",
                        "origin.complextalents.pixie.gun_msg.3",
                        "origin.complextalents.pixie.gun_msg.4",
                        "origin.complextalents.pixie.gun_msg.5"
                )
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 4)
                .cost(StatType.PERCENT_AD, 4)
                .cost(StatType.AP, 2)
                .cost(StatType.ARMOR_PEN, 4)
                .cost(StatType.LUCK_CRIT, 4)
                .cost(StatType.MAX_HP, 9999)
                .cost(StatType.MAX_MANA, 1)
                .cost(StatType.HEAL_AND_SHIELD, 4)
                .cost(StatType.CDR, 1)
                .spellMasteryCostMultiplier(1.0)
                .schoolSpellMasteryCostMultiplier(com.complextalents.spellmastery.SpellSchool.ELDRITCH, -1.0)
                .weaponMasteryCostMultiplier(10.0);
    }
}
