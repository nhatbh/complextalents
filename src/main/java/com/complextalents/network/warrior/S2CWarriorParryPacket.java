package com.complextalents.network.warrior;

import com.complextalents.impl.warrior.client.ClientParryData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to Client packet to sync active Warrior weapon path parry effects for HUD rendering.
 */
public class S2CWarriorParryPacket {

    private final String pathName;
    private final String translationKey;
    private final int valueArg;
    private final int durationTicks;
    private final String icon;
    private final int color;

    public S2CWarriorParryPacket(String pathName, String translationKey, int valueArg, int durationTicks, String icon, int color) {
        this.pathName = pathName;
        this.translationKey = translationKey;
        this.valueArg = valueArg;
        this.durationTicks = durationTicks;
        this.icon = icon;
        this.color = color;
    }

    public static void encode(S2CWarriorParryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.pathName);
        buffer.writeUtf(packet.translationKey);
        buffer.writeVarInt(packet.valueArg);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeUtf(packet.icon);
        buffer.writeInt(packet.color);
    }

    public static S2CWarriorParryPacket decode(FriendlyByteBuf buffer) {
        String pathName = buffer.readUtf();
        String translationKey = buffer.readUtf();
        int valueArg = buffer.readVarInt();
        int durationTicks = buffer.readVarInt();
        String icon = buffer.readUtf();
        int color = buffer.readInt();
        return new S2CWarriorParryPacket(pathName, translationKey, valueArg, durationTicks, icon, color);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientParryData.setParryEffect(pathName, translationKey, valueArg, durationTicks, icon, color);
            });
        });
        context.get().setPacketHandled(true);
    }
}
