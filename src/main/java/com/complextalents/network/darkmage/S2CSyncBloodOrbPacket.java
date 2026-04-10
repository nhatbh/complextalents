package com.complextalents.network.darkmage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncBloodOrbPacket {
    private final UUID orbId;
    private final Vec3 position;
    private final int tier;
    private final UUID ownerUUID;
    private final int lifetime;

    public S2CSyncBloodOrbPacket(UUID orbId, Vec3 position, int tier, UUID ownerUUID, int lifetime) {
        this.orbId = orbId;
        this.position = position;
        this.tier = tier;
        this.ownerUUID = ownerUUID;
        this.lifetime = lifetime;
    }

    public static void encode(S2CSyncBloodOrbPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.orbId);
        buf.writeDouble(msg.position.x);
        buf.writeDouble(msg.position.y);
        buf.writeDouble(msg.position.z);
        buf.writeInt(msg.tier);
        buf.writeUUID(msg.ownerUUID);
        buf.writeInt(msg.lifetime);
    }

    public static S2CSyncBloodOrbPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncBloodOrbPacket(
                buf.readUUID(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                buf.readUUID(),
                buf.readInt()
        );
    }

    public static void handle(S2CSyncBloodOrbPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.complextalents.impl.darkmage.client.BloodOrbRenderer.addOrb(msg.orbId, msg.position, msg.tier, msg.ownerUUID, msg.lifetime);
        });
        ctx.get().setPacketHandled(true);
    }
}
