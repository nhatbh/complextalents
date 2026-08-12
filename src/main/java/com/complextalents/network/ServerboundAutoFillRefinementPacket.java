package com.complextalents.network;

import com.complextalents.menu.RefiningAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundAutoFillRefinementPacket {
    private final int targetLevel;

    public ServerboundAutoFillRefinementPacket() {
        this(-1);
    }

    public ServerboundAutoFillRefinementPacket(int targetLevel) {
        this.targetLevel = targetLevel;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public static void encode(ServerboundAutoFillRefinementPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetLevel);
    }

    public static ServerboundAutoFillRefinementPacket decode(FriendlyByteBuf buf) {
        return new ServerboundAutoFillRefinementPacket(buf.readInt());
    }

    public static void handle(ServerboundAutoFillRefinementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof RefiningAnvilMenu menu) {
                menu.autoFillFromInventory(player, msg.targetLevel);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
