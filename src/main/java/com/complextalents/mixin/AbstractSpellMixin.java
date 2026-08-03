package com.complextalents.mixin;

import com.complextalents.registry.ModAttributes;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

    @Inject(method = "canBeCastedBy", at = @At("HEAD"), cancellable = true)
    private void complextalents$canBeCastedBy(int spellLevel, CastSource castSource, MagicData playerMagicData, Player player, CallbackInfoReturnable<CastResult> cir) {
        Optional<CastResult> masteryResult = SpellMasteryManager.verifyCast((AbstractSpell) (Object) this, spellLevel, castSource, player);
        if (masteryResult.isPresent()) {
            cir.setReturnValue(masteryResult.get());
        }
    }

    @Inject(method = "getSpellPower", at = @At("HEAD"), cancellable = true)
    private void complextalents$modifySpellPower(int spellLevel, Entity sourceEntity, CallbackInfoReturnable<Float> cir) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (complextalents$isFortifyOrHealingSpell(spell)) {
            float configPowerModifier = SpellConfigManager.getSpellConfigValue(spell, SpellConfigParameter.POWER_MULTIPLIER).floatValue();
            double healAndShieldModifier = 1.0;
            if (sourceEntity instanceof LivingEntity livingEntity) {
                var attr = livingEntity.getAttribute(ModAttributes.HEAL_AND_SHIELD_POWER.get());
                if (attr != null) {
                    healAndShieldModifier = attr.getValue();
                }
            }
            float power = (float) ((this.baseSpellPower + this.spellPowerPerLevel * (spellLevel - 1)) * healAndShieldModifier * configPowerModifier);
            cir.setReturnValue(power);
        } else if (complextalents$isShieldSpell(spell)) {
            double entitySpellPowerModifier = 1.0;
            double entitySchoolPowerModifier = 1.0;
            double healAndShieldModifier = 1.0;
            float configPowerModifier = SpellConfigManager.getSpellConfigValue(spell, SpellConfigParameter.POWER_MULTIPLIER).floatValue();

            if (sourceEntity instanceof LivingEntity livingEntity) {
                entitySpellPowerModifier = livingEntity.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
                entitySchoolPowerModifier = spell.getSchoolType().getPowerFor(livingEntity);
                var attr = livingEntity.getAttribute(ModAttributes.HEAL_AND_SHIELD_POWER.get());
                if (attr != null) {
                    healAndShieldModifier = attr.getValue();
                }
            }
            float power = (float) ((this.baseSpellPower + this.spellPowerPerLevel * (spellLevel - 1)) * entitySpellPowerModifier * entitySchoolPowerModifier * healAndShieldModifier * configPowerModifier);
            cir.setReturnValue(power);
        }
    }

    private static boolean complextalents$isFortifyOrHealingSpell(AbstractSpell spell) {
        if (spell instanceof FortifySpell
                || spell instanceof HealSpell
                || spell instanceof GreaterHealSpell
                || spell instanceof BlessingOfLifeSpell
                || spell instanceof CloudOfRegenerationSpell
                || spell instanceof HealingCircleSpell) {
            return true;
        }
        String id = spell.getSpellId();
        return id.endsWith("fortify")
                || id.endsWith("heal")
                || id.endsWith("greater_heal")
                || id.endsWith("blessing_of_life")
                || id.endsWith("cloud_of_regeneration")
                || id.endsWith("healing_circle");
    }

    private static boolean complextalents$isShieldSpell(AbstractSpell spell) {
        if (spell instanceof ShieldSpell) {
            return true;
        }
        return spell.getSpellId().endsWith("shield");
    }
}
