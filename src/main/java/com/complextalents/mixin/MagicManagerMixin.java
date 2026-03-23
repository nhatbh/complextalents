package com.complextalents.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA;

@Mixin(value = MagicManager.class, remap = false)
public abstract class MagicManagerMixin {

    @Inject(method = "regenPlayerMana", at = @At("HEAD"), cancellable = true)
    private void complextalents$stopManaRegen(ServerPlayer serverPlayer, MagicData playerMagicData,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void complextalents$moonTick(Level level, CallbackInfo ci) {
        if (level.isClientSide)
            return;
        var server = level.getServer();
        if (server == null)
            return;

        long timeOfDay = level.getDayTime() % 24000;
        int phase = level.getMoonPhase();

        // Restrict moonrise logic to Overworld to prevent multiple messages/triggers
        // (once per world tick across dimensions)
        if (timeOfDay == 13000 && level.dimension() == Level.OVERWORLD) {
            Component message;
            boolean shouldRestore = true;

            if (phase == 4) { // New Moon
                message = Component.literal(
                        "A moonless night claims the sky. Your connection to the ancient power remains dormant.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
                shouldRestore = false;
            } else if (phase == 0) { // Full Moon
                message = Component
                        .literal("Bathed in the brilliant light of the full moon, your ancient power overflows.")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
            } else {
                message = Component.literal("You feel a surge of ancient power as the moon takes the sky.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC);
            }

            server.getPlayerList().broadcastSystemMessage(message, false);

            if (shouldRestore) {
                server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                    MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                    float maxMana = (float) serverPlayer.getAttributeValue(MAX_MANA.get());
                    magicData.setMana(maxMana);
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                });
            }
        }

        // Full Moon continuous regen (Phase 0, Night time)
        // Restrict to Overworld tick but apply to all players globally
        if (phase == 0 && timeOfDay >= 13000 && timeOfDay < 23000 && level.dimension() == Level.OVERWORLD) {
            if (server.getTickCount() % 10 == 0) {
                server.getPlayerList().getPlayers().forEach(serverPlayer -> {
                    MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                    float maxMana = (float) serverPlayer.getAttributeValue(MAX_MANA.get());
                    float currentMana = magicData.getMana();
                    if (currentMana < maxMana) {
                        float increment = maxMana * 0.025f;
                        magicData.setMana(Mth.clamp(currentMana + increment, 0, maxMana));
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));
                    }
                });
            }
        }
    }
}
