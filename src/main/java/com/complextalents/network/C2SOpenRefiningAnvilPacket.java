package com.complextalents.network;

import com.complextalents.menu.RefiningAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class C2SOpenRefiningAnvilPacket {

    public C2SOpenRefiningAnvilPacket() {}

    public C2SOpenRefiningAnvilPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static C2SOpenRefiningAnvilPacket decode(FriendlyByteBuf buf) {
        return new C2SOpenRefiningAnvilPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new RefiningAnvilMenu(containerId, playerInventory, ContainerLevelAccess.NULL),
                    Component.translatable("block.complextalents.refining_anvil")
                )
            );
        });
        ctx.setPacketHandled(true);
    }
}
