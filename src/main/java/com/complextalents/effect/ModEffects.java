package com.complextalents.effect;

import com.complextalents.TalentsMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TalentsMod.MODID);

    public static final RegistryObject<MobEffect> ENGAGED = MOB_EFFECTS.register("engaged", EngagedEffect::new);
    public static final RegistryObject<MobEffect> LAMBS_MARK = MOB_EFFECTS.register("lambs_mark", LambsMarkEffect::new);
    public static final RegistryObject<MobEffect> PIXIE_MARK = MOB_EFFECTS.register("pixie_mark", com.complextalents.impl.pixie.effects.PixieMarkEffect::new);
    public static final RegistryObject<MobEffect> VOID_STRIKE = MOB_EFFECTS.register("void_strike", com.complextalents.impl.darkmage.effect.VoidStrikeEffect::new);
    public static final RegistryObject<MobEffect> SILENCED = MOB_EFFECTS.register("silenced", com.complextalents.impl.darkmage.effect.SilencedEffect::new);
    public static final RegistryObject<MobEffect> POSSESSED = MOB_EFFECTS.register("possessed", com.complextalents.impl.darkmage.effect.PossessedEffect::new);
    public static final RegistryObject<MobEffect> BLOOD_EXHAUSTION = MOB_EFFECTS.register("blood_exhaustion", com.complextalents.impl.darkmage.effect.BloodExhaustionEffect::new);
    public static final RegistryObject<MobEffect> ELDRITCH_RIFT = MOB_EFFECTS.register("eldritch_rift", com.complextalents.impl.spellblade.effect.EldritchRiftEffect::new);
    public static final RegistryObject<MobEffect> BLOOD_BLEED = MOB_EFFECTS.register("blood_bleed", com.complextalents.impl.spellblade.effect.BloodBleedEffect::new);
    public static final RegistryObject<MobEffect> LIGHTNING_HASTE = MOB_EFFECTS.register("lightning_haste", com.complextalents.impl.spellblade.effect.LightningHasteEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
