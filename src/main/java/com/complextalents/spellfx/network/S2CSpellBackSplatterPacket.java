package com.complextalents.spellfx.network;

import com.complextalents.spellfx.client.ClientSpellFXHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from Server to nearby clients when a spell hits an entity.
 * Synchronizes back splatter vector particles scaled by damage dealt.
 */
public class S2CSpellBackSplatterPacket {

    private final double posX, posY, posZ;
    private final double dirX, dirY, dirZ;
    private final float damage;
    private final String schoolPath;

    public S2CSpellBackSplatterPacket(double posX, double posY, double posZ, double dirX, double dirY, double dirZ, float damage, String schoolPath) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.damage = damage;
        this.schoolPath = schoolPath != null ? schoolPath : "default";
    }

    public static void encode(S2CSpellBackSplatterPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.posX);
        buf.writeDouble(packet.posY);
        buf.writeDouble(packet.posZ);
        buf.writeDouble(packet.dirX);
        buf.writeDouble(packet.dirY);
        buf.writeDouble(packet.dirZ);
        buf.writeFloat(packet.damage);
        buf.writeUtf(packet.schoolPath);
    }

    public static S2CSpellBackSplatterPacket decode(FriendlyByteBuf buf) {
        return new S2CSpellBackSplatterPacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readUtf()
        );
    }

    public static void handle(S2CSpellBackSplatterPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientSpellFXHandler.spawnBackSplatter(
                            packet.posX, packet.posY, packet.posZ,
                            packet.dirX, packet.dirY, packet.dirZ,
                            packet.damage, packet.schoolPath
                    )
            );
        });
        ctx.setPacketHandled(true);
    }
}
