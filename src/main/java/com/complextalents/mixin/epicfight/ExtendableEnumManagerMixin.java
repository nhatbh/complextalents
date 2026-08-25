package com.complextalents.mixin.epicfight;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.utils.ExtendableEnum;
import yesman.epicfight.api.utils.ExtendableEnumManager;

import java.util.Locale;
import java.util.Map;

@Mixin(value = ExtendableEnumManager.class, remap = false)
public abstract class ExtendableEnumManagerMixin<T extends ExtendableEnum> {

    private static final Logger LOGGER = LogManager.getLogger(ExtendableEnumManagerMixin.class);

    @Shadow
    private Map<String, T> enumMapByName;

    @Inject(
        method = "getOrThrow(Ljava/lang/String;)Lyesman/epicfight/api/utils/ExtendableEnum;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void injectGetOrThrow(String name, CallbackInfoReturnable<T> cir) {
        if (name == null) {
            cir.setReturnValue(null);
            return;
        }

        String key = name.toLowerCase(Locale.ROOT);
        if (this.enumMapByName != null && !this.enumMapByName.containsKey(key)) {
            LOGGER.warn("[ComplexTalents] Suppressed missing ExtendableEnum lookup for '{}'. Returning null to prevent world loading crash.", name);
            cir.setReturnValue(null);
        }
    }
}
