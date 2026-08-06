package com.complextalents.impl.pixie.skills;

import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import net.minecraft.resources.ResourceLocation;

public class BondOfTheFaeSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "bond_of_the_fae");

    public static void register() {
        SkillBuilder.create("complextalents", "bond_of_the_fae")
                .nature(SkillNature.PASSIVE)
                .displayName("Khế Ước Tiên Linh")
                .description("Nhập thể vào đồng minh để trở nên bất tử và tăng 10% đến 35% sức mạnh tấn công cùng 5% đến 25% phòng thủ cho người được bảo vệ.")
                .setMaxLevel(5)
                .scaledStat("adBuff", "Tăng AD Ký Chủ (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("defBuff", "Tăng Giáp Ký Chủ (%)", new double[] { 0.05, 0.10, 0.15, 0.20, 0.25 })
                .scaledStat("apBuff", "Tăng AP Ký Chủ (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.35 })
                .scaledStat("manaRegenBuff", "Tăng Hồi Mana (%)", new double[] { 0.10, 0.15, 0.20, 0.25, 0.30 })
                .scaledStat("tetherRange", "Tầm Dây Trói (Khối)", new double[] { 6, 7, 8, 9, 10 })
                .register();
    }
}
