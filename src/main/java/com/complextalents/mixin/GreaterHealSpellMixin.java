package com.complextalents.mixin;

import com.complextalents.registry.ModAttributes;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.network.particles.HealParticlesPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import io.redspace.ironsspellbooks.spells.holy.GreaterHealSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GreaterHealSpell.class, remap = false)
public abstract class GreaterHealSpellMixin extends AbstractSpell {

    @Inject(method = "onCast", at = @At("HEAD"), cancellable = true)
    private void complextalents$onCastGreaterHeal(Level world, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData, CallbackInfo ci) {
        float healAmount = entity.getMaxHealth();
        var attr = entity.getAttribute(ModAttributes.HEAL_AND_SHIELD_POWER.get());
        if (attr != null) {
            healAmount *= (float) attr.getValue();
        }
        MinecraftForge.EVENT_BUS.post(new SpellHealEvent(entity, entity, healAmount, getSchoolType()));
        entity.heal(healAmount);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new HealParticlesPacket(entity.position()));
        super.onCast(world, spellLevel, entity, castSource, playerMagicData);
        ci.cancel();
    }
}
