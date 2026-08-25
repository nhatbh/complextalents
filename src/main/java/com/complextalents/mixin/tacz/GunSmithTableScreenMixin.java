package com.complextalents.mixin.tacz;


import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin {

    @Shadow(remap = false)
    @Nullable
    private GunSmithTableRecipe selectedRecipe;

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
        ),
        require = 0
    )
    private MutableComponent redirectCountText(String key, Object[] args) {
        if ("gui.tacz.gun_smith_table.count".equals(key) && args != null && args.length > 0 && args[0] instanceof Integer baseCount) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && this.selectedRecipe != null) {
                ItemStack output = this.selectedRecipe.getResult().getResult();
                if (output.getItem() instanceof IAmmo || output.getItem() instanceof IAmmoBox) {
                    double multiplier = player.getAttributeValue(com.complextalents.registry.ModAttributes.AMMO_CRAFTING_YIELD.get());
                    if (multiplier > 0) {
                        int totalCount = (int) Math.round(baseCount * multiplier);
                        if (totalCount != baseCount) {
                            int bonus = totalCount - baseCount;
                            String bonusStr = bonus > 0 ? " (+" + bonus + ")" : " (" + bonus + ")";
                            return Component.translatable("gui.tacz.gun_smith_table.count", totalCount)
                                    .append(Component.literal(bonusStr).withStyle(bonus > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                        }
                    }
                }
            }
        }
        return Component.translatable(key, args);
    }
}
