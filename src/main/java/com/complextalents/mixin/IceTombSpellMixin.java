package com.complextalents.mixin;

import com.complextalents.registry.ModAttributes;
import io.redspace.ironsspellbooks.spells.ice.IceTombSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IceTombSpell.class, remap = false)
public abstract class IceTombSpellMixin {

    @Inject(method = "getHealing", at = @At("HEAD"), cancellable = true)
    private void complextalents$getHealing(int spellLevel, LivingEntity caster, CallbackInfoReturnable<Float> cir) {
        double healAndShieldModifier = 1.0;
        if (caster != null) {
            var attr = caster.getAttribute(ModAttributes.HEAL_AND_SHIELD_POWER.get());
            if (attr != null) {
                healAndShieldModifier = attr.getValue();
            }
        }
        cir.setReturnValue((float) (1.0 * healAndShieldModifier));
    }
}
