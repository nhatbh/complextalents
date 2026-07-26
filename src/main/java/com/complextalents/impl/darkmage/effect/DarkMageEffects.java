package com.complextalents.impl.darkmage.effect;

import com.complextalents.TalentsMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DarkMageEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TalentsMod.MODID);

    public static final RegistryObject<MobEffect> BLEED = MOB_EFFECTS.register("bleed", BleedEffect::new);
    public static final RegistryObject<MobEffect> SOUL_STASIS = MOB_EFFECTS.register("soul_stasis", SoulStasisEffect::new);
    public static final RegistryObject<MobEffect> HARVEST_FRENZY = MOB_EFFECTS.register("harvest_frenzy", HarvestFrenzyEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
