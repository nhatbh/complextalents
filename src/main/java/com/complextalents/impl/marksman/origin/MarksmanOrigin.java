package com.complextalents.impl.marksman.origin;

import com.complextalents.TalentsMod;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;

/**
 * Marksman Origin — The Outworlder Hero who masters modern firearm technology.
 * Currently implemented as a functional placeholder to allow selecting the origin
 * and operating TACZ firearms.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class MarksmanOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "marksman");

    /**
     * Register the Marksman origin.
     */
    public static void register() {
        OriginBuilder.create(ID)
                .displayName(Component.translatable("origin.complextalents.marksman"))
                .description(Component.translatable("origin.complextalents.marksman.desc"))
                .maxLevel(5)
                .baseStat(StatType.GUN_DAMAGE, 2)
                .baseStat(StatType.FORTITUDE, 2)
                .baseStat(StatType.LUCK_CRIT, 5)
                .upgradableStats(
                        StatType.GUN_DAMAGE,
                        StatType.RELOAD_SPEED,
                        StatType.FORTITUDE,
                        StatType.HEADSHOT_DAMAGE,
                        StatType.RECOIL_CONTROL,
                        StatType.BULLET_PENETRATION,
                        StatType.FIRE_RATE,
                        StatType.MAX_HP,
                        StatType.LUCK_CRIT
                )
                .passiveSkill("Hỏa Lực Hiện Đại",
                        "Bậc thầy duy nhất có khả năng hiểu và vận hành các loại súng hỏa lực modern (TACZ). Giảm 50% tổng Máu Tối Đa nhận được từ mọi nguồn.")
                .activeSkill("Truy Cùng Diệt Tận",
                        "Lướt giải khống chế, ổn định nhịp tim 60 BPM và vào trạng thái Adrenaline hỗ trợ định vị Reticle đỏ.",
                        ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/marksman/relentless_pursuit.png"))
                .activeSkillId(com.complextalents.impl.marksman.skill.RelentlessPursuitSkill.ID)


                // Level defensive scaling (Frail modern human archetype — halved armor & toughness growth)
                .levelArmorCalc(lvl -> lvl <= 1 ? 0.0 : Math.round(Math.pow(lvl - 1, 1.15) * 0.25))
                .levelToughnessCalc(lvl -> lvl <= 1 ? 0.0 : Math.min(7.5, Math.pow(lvl - 1, 1.1) * 0.06))
                .levelHealthCalc(lvl -> 0.0)


                .renderer(new com.complextalents.impl.marksman.client.MarksmanRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.GUN_DAMAGE, 1)
                .cost(StatType.RELOAD_SPEED, 1)
                .cost(StatType.FORTITUDE, 1)
                .cost(StatType.HEADSHOT_DAMAGE, 2)
                .cost(StatType.RECOIL_CONTROL, 1)
                .cost(StatType.BULLET_PENETRATION, 2)
                .cost(StatType.FIRE_RATE, 2)
                .cost(StatType.MAX_HP, 4)
                .cost(StatType.LUCK_CRIT, 2)

                .spellMasteryCostMultiplier(-1.0)
                .weaponMasteryCostMultiplier(3.0)
                .gunMasteryCostMultiplier(1.0);




        TalentsMod.LOGGER.info("Marksman origin registered");
    }

    /**
     * Check if a player is a Marksman.
     */
    public static boolean isMarksman(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }
}
