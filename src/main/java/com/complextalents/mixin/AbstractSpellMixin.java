package com.complextalents.mixin;

import com.complextalents.registry.ModAttributes;
import com.complextalents.spellfx.events.SpellPowerPenaltyHandler;
import com.complextalents.spellmastery.SpellMasteryManager;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.spells.evocation.ShieldSpell;
import io.redspace.ironsspellbooks.spells.holy.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellMixin {

    @Shadow protected int baseSpellPower;
    @Shadow protected int spellPowerPerLevel;

    @Inject(method = "onCast", at = @At("HEAD"))
    private void complextalents$onCastHead(net.minecraft.world.level.Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (entity instanceof Player player) {
            AbstractSpell spell = (AbstractSpell) (Object) this;
            ItemStack castingStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
            com.complextalents.refinement.RefinementContext.setCurrentContextStack(castingStack);
            com.complextalents.refinement.RefinementContext.setCurrentContextSpell(spell);
        }
    }

    @Inject(method = "onCast", at = @At("RETURN"))
    private void complextalents$onCastReturn(net.minecraft.world.level.Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
        com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
    }

    @Inject(method = "getManaCost", at = @At("RETURN"), cancellable = true)
    private void complextalents$getPenalizedManaCost(int spellLevel, CallbackInfoReturnable<Integer> cir) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        int originalCost = cir.getReturnValue();
        if (originalCost <= 0) return;

        LivingEntity entity = SpellPowerPenaltyHandler.getCasterContext();
        if (entity == null && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            entity = net.minecraft.client.Minecraft.getInstance().player;
        }
        if (entity instanceof Player player && com.complextalents.impl.spellblade.origin.SpellbladeOrigin.isSpellblade(player)) {
            if (com.complextalents.impl.spellblade.SpellbladeData.hasFreeCast(player)) {
                cir.setReturnValue(0);
                return;
            }
        }
        if (entity == null) return;

        // Apply Refinement Mana Reduction
        double manaReduction = 0.0;
        if (com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell) == com.complextalents.classification.SpellClassificationManager.SpellType.DAMAGE) {
            ItemStack contextStack = com.complextalents.refinement.RefinementContext.getCurrentContextStack();
            if (contextStack.isEmpty() && entity instanceof Player player) {
                contextStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
            }

            if (!contextStack.isEmpty()) {
                if (com.complextalents.refinement.MagicRefinementManager.isScroll(contextStack)) {
                    int currentXp = com.complextalents.refinement.MagicRefinementManager.getRefineXp(contextStack);
                    int cumRank = com.complextalents.refinement.MagicRefinementManager.getRankFromXp(currentXp, 20);
                    manaReduction = com.complextalents.refinement.MagicRefinementManager.getScrollManaCostReduction(cumRank);
                } else {
                    net.minecraft.nbt.CompoundTag tag = contextStack.getTag();
                    if (tag != null && tag.contains("RefinedSpells")) {
                        net.minecraft.nbt.CompoundTag refinedSpells = tag.getCompound("RefinedSpells");
                        String spellId = spell.getSpellId();
                        if (refinedSpells.contains(spellId)) {
                            net.minecraft.nbt.CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                            int currentXp = spellRefineData.getInt("RefineXP");
                            int cumRank = com.complextalents.refinement.MagicRefinementManager.getRankFromXp(currentXp, 20);
                            manaReduction = com.complextalents.refinement.MagicRefinementManager.getScrollManaCostReduction(cumRank);
                        }
                    }
                }
            }
        }

        int costBeforePenalty = originalCost;
        if (manaReduction > 0.0) {
            costBeforePenalty = (int) Math.ceil(originalCost * (1.0 - manaReduction));
        }

        double weight = spell.getEffectiveCastTime(spellLevel, entity) <= 0
                ? SpellPowerPenaltyHandler.instantSpellPenaltyWeight
                : SpellPowerPenaltyHandler.spellPenaltyWeight;

        double penaltyMultiplier = SpellPowerPenaltyHandler.calculatePenaltyMultiplier(spell, entity, weight);

        int finalCost = costBeforePenalty;
        if (penaltyMultiplier > 1.0) {
            finalCost = (int) Math.ceil(costBeforePenalty * penaltyMultiplier);
        }

        cir.setReturnValue(finalCost);
    }

    @Inject(method = "getEffectiveCastTime", at = @At("RETURN"), cancellable = true)
    private void complextalents$dynamicallyIncreaseCastTime(int spellLevel, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        LivingEntity caster = entity != null ? entity : SpellPowerPenaltyHandler.getCasterContext();
        if (caster == null) return;

        AbstractSpell spell = (AbstractSpell) (Object) this;
        int originalCastTime = cir.getReturnValue();

        // Don't apply cast time penalties to instant spells (0 ticks)
        if (originalCastTime <= 0) return;

        // Apply refinement cast time reduction substat
        ItemStack contextStack = com.complextalents.refinement.RefinementContext.getCurrentContextStack();
        if (contextStack.isEmpty() && caster instanceof Player player) {
            contextStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
        }
        if (!contextStack.isEmpty()) {
            double substatCastReduction = com.complextalents.refinement.MagicRefinementManager.getSpellSubstatValue(
                    contextStack, spell, com.complextalents.refinement.MagicRefinementManager.MagicSubstatType.CAST_TIME_REDUCTION
            );
            if (substatCastReduction > 0.0) {
                originalCastTime = Math.round(originalCastTime * (1.0f - (float) substatCastReduction));
                cir.setReturnValue(originalCastTime);
            }
        }

        // Spellblade Overcharge Stance: Spells with cast duration <= 5.0 seconds (100 ticks) become instant cast
        if (caster instanceof Player player && com.complextalents.impl.spellblade.SpellbladeData.isOverchargeStance(player)) {
            if (originalCastTime <= 100) {
                cir.setReturnValue(0);
                return;
            }
        }

        double penaltyMultiplier = SpellPowerPenaltyHandler.calculatePenaltyMultiplier(
                spell, caster, SpellPowerPenaltyHandler.spellPenaltyWeight
        );

        if (penaltyMultiplier > 1.0) {
            int penalizedCastTime = (int) Math.ceil(originalCastTime * penaltyMultiplier);
            cir.setReturnValue(penalizedCastTime);
        }
    }

    @Inject(method = "canBeCastedBy", at = @At("HEAD"), cancellable = true)
    private void complextalents$canBeCastedBy(int spellLevel, CastSource castSource, MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        Optional<CastResult> masteryResult = SpellMasteryManager.verifyCast((AbstractSpell) (Object) this, spellLevel, castSource, player);
        if (masteryResult.isPresent()) {
            cir.setReturnValue(masteryResult.get());
            return;
        }

        if (player == null) return;

        AbstractSpell spell = (AbstractSpell) (Object) this;
        ItemStack castingStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
        com.complextalents.refinement.RefinementContext.setCurrentContextStack(castingStack);
        com.complextalents.refinement.RefinementContext.setCurrentContextSpell(spell);

        try {
            SpellPowerPenaltyHandler.setCasterContext(player);

            double weight = spell.getEffectiveCastTime(spellLevel, player) <= 0
                    ? SpellPowerPenaltyHandler.instantSpellPenaltyWeight
                    : SpellPowerPenaltyHandler.spellPenaltyWeight;

            double penaltyMultiplier = SpellPowerPenaltyHandler.calculatePenaltyMultiplier(spell, player, weight);

            if (penaltyMultiplier > 1.0 && castSource.consumesMana()) {
                int penalizedCost = spell.getManaCost(spellLevel);
                int unpenalizedCost = (int) Math.ceil(penalizedCost / penaltyMultiplier);

                boolean hasEnoughMana = playerMagicData.getMana() - penalizedCost >= 0;
                boolean hasRecastForSpell = playerMagicData.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());

                if (!hasRecastForSpell && !hasEnoughMana) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        int extraMana = penalizedCost - unpenalizedCost;
                        serverPlayer.sendSystemMessage(Component.literal(
                                String.format("§cYour immense power demands more mana! (Cost: %d + %d)", unpenalizedCost, extraMana)
                        ));
                    }
                    cir.setReturnValue(new CastResult(
                            CastResult.Type.FAILURE,
                            Component.translatable("ui.irons_spellbooks.cast_error_mana", spell.getDisplayName(player)).withStyle(ChatFormatting.RED)
                    ));
                }
            }
        } finally {
            com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
            com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
            SpellPowerPenaltyHandler.clearCasterContext();
        }
    }

    @Inject(method = "getSpellPower", at = @At("HEAD"), cancellable = true)
    private void complextalents$modifySpellPower(int spellLevel, Entity sourceEntity, CallbackInfoReturnable<Float> cir) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (com.complextalents.refinement.MagicRefinementManager.isHealingSpell(spell)) {
            boolean isShield = complextalents$isShieldSpell(spell);
            double healAndShieldModifier = 1.0;
            if (sourceEntity instanceof LivingEntity livingEntity) {
                var attr = livingEntity.getAttribute(ModAttributes.HEAL_AND_SHIELD_POWER.get());
                if (attr != null) {
                    healAndShieldModifier = attr.getValue();
                }
            }
            ItemStack contextStack = com.complextalents.refinement.RefinementContext.getCurrentContextStack();
            if (contextStack.isEmpty() && sourceEntity instanceof Player player) {
                contextStack = com.complextalents.refinement.MagicRefinementEventHandler.getCastingItemStack(player, spell);
            }
            if (!contextStack.isEmpty()) {
                healAndShieldModifier += com.complextalents.refinement.MagicRefinementManager.getSpellRefinementMainstatBonus(contextStack, spell);
                healAndShieldModifier += com.complextalents.refinement.MagicRefinementManager.getSpellSubstatValue(
                        contextStack, spell, com.complextalents.refinement.MagicRefinementManager.MagicSubstatType.HEAL_AND_SHIELD_POWER
                );
            }
            float configPowerModifier = SpellConfigManager.getSpellConfigValue(spell, SpellConfigParameter.POWER_MULTIPLIER).floatValue();
            if (isShield) {
                double entitySpellPowerModifier = 1.0;
                double entitySchoolPowerModifier = 1.0;
                if (sourceEntity instanceof LivingEntity livingEntity) {
                    entitySpellPowerModifier = livingEntity.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
                    entitySchoolPowerModifier = spell.getSchoolType().getPowerFor(livingEntity);
                }
                float power = (float) ((this.baseSpellPower + this.spellPowerPerLevel * (spellLevel - 1)) * entitySpellPowerModifier * entitySchoolPowerModifier * healAndShieldModifier * configPowerModifier);
                cir.setReturnValue(power);
            } else {
                float power = (float) ((this.baseSpellPower + this.spellPowerPerLevel * (spellLevel - 1)) * healAndShieldModifier * configPowerModifier);
                cir.setReturnValue(power);
            }
        }
    }

    private static boolean complextalents$isShieldSpell(AbstractSpell spell) {
        if (spell instanceof ShieldSpell) {
            return true;
        }
        return spell.getSpellId().endsWith("shield");
    }
}
