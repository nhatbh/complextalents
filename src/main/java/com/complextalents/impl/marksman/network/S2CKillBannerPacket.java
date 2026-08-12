package com.complextalents.impl.marksman.network;

import com.complextalents.impl.marksman.client.ClientKillBannerHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Network packet sent from Server -> Client to trigger the Marksman Adrenaline Kill Banner overlay and sound.
 */
public class S2CKillBannerPacket {

    private final int killCount;

    public S2CKillBannerPacket(int killCount) {
        this.killCount = killCount;
    }

    public static void encode(S2CKillBannerPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.killCount);
    }

    public static S2CKillBannerPacket decode(FriendlyByteBuf buf) {
        return new S2CKillBannerPacket(buf.readInt());
    }

    public static void handle(S2CKillBannerPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientKillBannerHandler.triggerKillBanner(msg.killCount);
        }));
        ctx.setPacketHandled(true);
    }
}
