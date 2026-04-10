package com.complextalents.network.darkmage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CRemoveBloodOrbPacket {
    private final UUID orbId;
    private final boolean detonate;

    public S2CRemoveBloodOrbPacket(UUID orbId, boolean detonate) {
        this.orbId = orbId;
        this.detonate = detonate;
    }

    public static void encode(S2CRemoveBloodOrbPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.orbId);
        buf.writeBoolean(msg.detonate);
    }

    public static S2CRemoveBloodOrbPacket decode(FriendlyByteBuf buf) {
        return new S2CRemoveBloodOrbPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(S2CRemoveBloodOrbPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.complextalents.impl.darkmage.client.BloodOrbRenderer.removeOrb(msg.orbId, msg.detonate);
        });
        ctx.get().setPacketHandled(true);
    }
}
