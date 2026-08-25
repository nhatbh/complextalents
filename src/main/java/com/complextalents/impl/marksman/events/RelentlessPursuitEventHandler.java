package com.complextalents.impl.marksman.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.marksman.data.MarksmanResourceData;
import com.complextalents.impl.marksman.skill.RelentlessPursuitSkill;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.skill.capability.SkillDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event Handler for Marksman Active Skill: Relentless Pursuit (Tactical Dash & Mobility System).
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class RelentlessPursuitEventHandler {

    private static final String NBT_MOBILITY_TICK = "marksman_mobility_tick_counter";

    /**
     * 100% Damage Negation while Dash Invulnerability effect is active.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && entity.hasEffect(ModEffects.DASH_INVULNERABLE.get())) {
            event.setCanceled(true);
        }
    }

    /**
     * Server tick handler for Marksman Mobility passive regeneration.
     * Regen rate is scaled by Relentless Pursuit skill rank:
     * R1: 48 ticks (+1 pt / 2.4s) -> R5: 24 ticks (+1 pt / 1.2s)
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        var originOpt = player.getCapability(OriginDataProvider.ORIGIN_DATA);
        if (!originOpt.isPresent() || originOpt.orElse(null).getActiveOrigin() == null) {
            return;
        }

        ResourceLocation originId = originOpt.orElse(null).getActiveOrigin();
        if (!com.complextalents.impl.marksman.origin.MarksmanOrigin.ID.equals(originId)) {
            return;
        }

        // Get Relentless Pursuit skill rank
        int skillRank = 1;
        var skillDataOpt = player.getCapability(SkillDataProvider.SKILL_DATA);
        if (skillDataOpt.isPresent()) {
            int level = skillDataOpt.resolve().get().getSkillLevel(RelentlessPursuitSkill.ID);
            skillRank = Math.min(Math.max(1, level), 5);
        }

        int[] intervalTicks = new int[]{ 48, 42, 36, 30, 24 };
        int requiredTicks = intervalTicks[skillRank - 1];

        CompoundTag nbt = player.getPersistentData();
        int counter = nbt.getInt(NBT_MOBILITY_TICK) + 1;
        if (counter >= requiredTicks) {
            counter = 0;
            MarksmanResourceData.addMobility(player, 1.0f);
        }
        nbt.putInt(NBT_MOBILITY_TICK, counter);
    }
}
