package com.complextalents.impl.marksman.network;

import com.complextalents.impl.marksman.client.ClientAdrenalineFXHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Network packet from Server -> Client syncing Marksman Adrenaline State & Dismiss Resource.
 */
public class S2CAdrenalineStatePacket {

    private final boolean active;
    private final int skillLevel;
    private final float remainingSecs;
    private final float maxSecs;
    private final float dismissResource;
    private final int dismissCount;
    private final int overclockStacks;
    private final int bossEntityId;
    private final double markOffsetX;
    private final double markOffsetY;
    private final double markOffsetZ;
    private final boolean bossMarkRespawning;

    public S2CAdrenalineStatePacket(boolean active, int skillLevel, float remainingSecs, float maxSecs, float dismissResource,
                                    int dismissCount, int overclockStacks, int bossEntityId, Vec3 markOffset, boolean bossMarkRespawning) {
        this.active = active;
        this.skillLevel = skillLevel;
        this.remainingSecs = remainingSecs;
        this.maxSecs = maxSecs;
        this.dismissResource = dismissResource;
        this.dismissCount = dismissCount;
        this.overclockStacks = overclockStacks;
        this.bossEntityId = bossEntityId;
        this.markOffsetX = markOffset != null ? markOffset.x : 0.0;
        this.markOffsetY = markOffset != null ? markOffset.y : 0.0;
        this.markOffsetZ = markOffset != null ? markOffset.z : 0.0;
        this.bossMarkRespawning = bossMarkRespawning;
    }

    public static void encode(S2CAdrenalineStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeInt(msg.skillLevel);
        buf.writeFloat(msg.remainingSecs);
        buf.writeFloat(msg.maxSecs);
        buf.writeFloat(msg.dismissResource);
        buf.writeInt(msg.dismissCount);
        buf.writeInt(msg.overclockStacks);
        buf.writeInt(msg.bossEntityId);
        buf.writeDouble(msg.markOffsetX);
        buf.writeDouble(msg.markOffsetY);
        buf.writeDouble(msg.markOffsetZ);
        buf.writeBoolean(msg.bossMarkRespawning);
    }

    public static S2CAdrenalineStatePacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        int skillLevel = buf.readInt();
        float remainingSecs = buf.readFloat();
        float maxSecs = buf.readFloat();
        float dismissResource = buf.readFloat();
        int dismissCount = buf.readInt();
        int overclockStacks = buf.readInt();
        int bossEntityId = buf.readInt();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        boolean respawning = buf.readBoolean();
        return new S2CAdrenalineStatePacket(active, skillLevel, remainingSecs, maxSecs, dismissResource, dismissCount, overclockStacks,
                bossEntityId, new Vec3(x, y, z), respawning);
    }

    public static void handle(S2CAdrenalineStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientAdrenalineFXHandler.updateState(
                    msg.active, msg.skillLevel, msg.remainingSecs, msg.maxSecs, msg.dismissResource, msg.dismissCount,
                    msg.overclockStacks, msg.bossEntityId,
                    new Vec3(msg.markOffsetX, msg.markOffsetY, msg.markOffsetZ),
                    msg.bossMarkRespawning
            );
        }));
        ctx.setPacketHandled(true);
    }
}
