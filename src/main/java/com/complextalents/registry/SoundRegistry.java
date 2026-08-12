package com.complextalents.registry;

import com.complextalents.TalentsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistry {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister
            .create(ForgeRegistries.SOUND_EVENTS, TalentsMod.MODID);

    public static final RegistryObject<SoundEvent> SUPERCELL_AMBIENT = registerSoundEvent("supercell_ambient");
    public static final RegistryObject<SoundEvent> SUPERCELL_THUNDER = registerSoundEvent("supercell_thunder");

    public static final RegistryObject<SoundEvent> MARKSMAN_KILL1 = registerSoundEvent("marksman.kill1");
    public static final RegistryObject<SoundEvent> MARKSMAN_KILL2 = registerSoundEvent("marksman.kill2");
    public static final RegistryObject<SoundEvent> MARKSMAN_KILL3 = registerSoundEvent("marksman.kill3");
    public static final RegistryObject<SoundEvent> MARKSMAN_KILL4 = registerSoundEvent("marksman.kill4");
    public static final RegistryObject<SoundEvent> MARKSMAN_KILL5 = registerSoundEvent("marksman.kill5");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent
                .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
