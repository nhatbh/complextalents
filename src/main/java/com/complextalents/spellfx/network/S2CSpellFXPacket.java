package com.complextalents.spellfx.network;

import com.complextalents.spellfx.client.ClientSpellFXHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client packet to trigger spell casting visual feedback (screen shake, camera recoil kick,
 * FOV impulse, and school-colored muzzle flash overlay) for the casting player.
 */
public class S2CSpellFXPacket {
    private final int manaCost;
    private final int colorHex;
    private final float maxFlashAlpha;

    public S2CSpellFXPacket(int manaCost, int colorHex) {
        this(manaCost, colorHex, -1.0f);
    }

    public S2CSpellFXPacket(int manaCost, int colorHex, float maxFlashAlpha) {
        this.manaCost = manaCost;
        this.colorHex = colorHex;
        this.maxFlashAlpha = maxFlashAlpha;
    }

    public S2CSpellFXPacket(FriendlyByteBuf buffer) {
        this.manaCost = buffer.readInt();
        this.colorHex = buffer.readInt();
        if (buffer.readableBytes() >= 4) {
            this.maxFlashAlpha = buffer.readFloat();
        } else {
            this.maxFlashAlpha = -1.0f;
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(manaCost);
        buffer.writeInt(colorHex);
        buffer.writeFloat(maxFlashAlpha);
    }

    public static S2CSpellFXPacket decode(FriendlyByteBuf buffer) {
        return new S2CSpellFXPacket(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(this::handleClient);
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (maxFlashAlpha > 0.0f) {
            ClientSpellFXHandler.triggerFeedback(manaCost, colorHex, maxFlashAlpha);
        } else {
            ClientSpellFXHandler.triggerFeedback(manaCost, colorHex);
        }
    }
}
