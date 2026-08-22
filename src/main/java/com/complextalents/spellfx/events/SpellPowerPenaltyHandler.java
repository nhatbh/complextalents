package com.complextalents.spellfx.events;

import com.complextalents.TalentsMod;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Event handler for Spell Power Penalty calculations.
 * Dynamically increases cast time and mana cost for spells based on excess
 * spell power.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellPowerPenaltyHandler {

    public static double penaltyThreshold = 1.0; // Grace threshold: +100% bonus spell power before penalty starts
    public static double spellPenaltyWeight = 0.40; // Reduced from 1.0 to 0.25 (25% weight)
    public static double instantSpellPenaltyWeight = 0.50; // Reduced from 1.0 to 0.15 (15% weight)

    private static final ThreadLocal<LivingEntity> CURRENT_CASTER = new ThreadLocal<>();

    public static void setCasterContext(LivingEntity entity) {
        CURRENT_CASTER.set(entity);
    }

    public static void clearCasterContext() {
        CURRENT_CASTER.remove();
    }

    public static LivingEntity getCasterContext() {
        LivingEntity entity = CURRENT_CASTER.get();
        if (entity == null && FMLEnvironment.dist.isClient()) {
            entity = getClientPlayer();
        }
        return entity;
    }

    private static LivingEntity getClientPlayer() {
        return net.minecraft.client.Minecraft.getInstance().player;
    }

    /**
     * Calculates penalty multiplier based on general and school-specific spell
     * power above threshold.
     */
    public static double calculatePenaltyMultiplier(AbstractSpell spell, LivingEntity entity, double weight) {
        if (entity == null || spell == null)
            return 1.0;

        double generalPower = 1.0;
        double schoolPower = 1.0;

        var generalAttr = entity.getAttribute(AttributeRegistry.SPELL_POWER.get());
        if (generalAttr != null) {
            generalPower = generalAttr.getValue();
        }

        if (spell.getSchoolType() != null) {
            schoolPower = spell.getSchoolType().getPowerFor(entity);
        }

        double totalPowerBonus = Math.max(0, generalPower - 1.0) + Math.max(0, schoolPower - 1.0);
        double penalizedPowerBonus = Math.max(0, totalPowerBonus - penaltyThreshold);

        double effectiveness = 0.0;
        var effAttr = entity.getAttribute(com.complextalents.registry.ModAttributes.MAGIC_EFFECTIVENESS.get());
        if (effAttr != null) {
            effectiveness = effAttr.getValue();
        }

        ItemStack contextStack = com.complextalents.refinement.RefinementContext.getCurrentContextStack();
        if (contextStack == null || contextStack.isEmpty()) {
            if (entity instanceof Player player) {
                contextStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
            }
        }
        if (contextStack != null && !contextStack.isEmpty()) {
            effectiveness += com.complextalents.refinement.MagicRefinementManager.getSpellSubstatValue(
                    contextStack, spell, com.complextalents.refinement.MagicRefinementManager.MagicSubstatType.MAGIC_EFFECTIVENESS
            );
        }

        double adjustedPenalty = penalizedPowerBonus * Math.exp(-1.2 * Math.max(0.0, effectiveness));

        return 1.0 + (adjustedPenalty * weight);
    }

    /**
     * Intercepts spell casting event on server to set the penalized mana cost.
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        if (event.getEntity() == null || event.getCastSource() == null || !event.getCastSource().consumesMana()) {
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null)
            return;

        double weight = spell.getEffectiveCastTime(event.getSpellLevel(), event.getEntity()) <= 0
                ? instantSpellPenaltyWeight
                : spellPenaltyWeight;

        double multiplier = calculatePenaltyMultiplier(spell, event.getEntity(), weight);
        if (multiplier > 1.0) {
            int originalCost = event.getManaCost();
            int penalizedCost = (int) Math.ceil(originalCost * multiplier);
            event.setManaCost(penalizedCost);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onItemTooltipHighest(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack != null && !stack.isEmpty()) {
            com.complextalents.refinement.RefinementContext.setCurrentContextStack(stack);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onItemTooltipLowest(ItemTooltipEvent event) {
        com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
        com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
    }

    /**
     * Dynamically appends penalty information to spell tooltips for items containing spells.
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return;

        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) return;

        for (SpellSlot slot : container.getActiveSpells()) {
            if (slot == null || slot.getSpell() == null) continue;

            AbstractSpell spell = slot.getSpell();
            int spellLevel = slot.getLevel();

            double weight = spell.getEffectiveCastTime(spellLevel, player) <= 0
                    ? instantSpellPenaltyWeight
                    : spellPenaltyWeight;

            double multiplier = calculatePenaltyMultiplier(spell, player, weight);
            if (multiplier > 1.0) {
                double penaltyPct = (multiplier - 1.0) * 100.0;
                event.getToolTip().add(Component.literal(
                        String.format("⚠ Power Penalty: +%.0f%% Mana & Cast Time", penaltyPct)
                ).withStyle(ChatFormatting.RED));
                break; // Show one summary penalty notice per spell item
            }
        }
    }
}

