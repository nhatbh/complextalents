package com.complextalents.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class C2SOpenGunTablePacket {

    private final ResourceLocation blockId;

    public C2SOpenGunTablePacket(ResourceLocation blockId) {
        this.blockId = blockId;
    }

    public C2SOpenGunTablePacket(FriendlyByteBuf buf) {
        this.blockId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.blockId);
    }

    public static C2SOpenGunTablePacket decode(FriendlyByteBuf buf) {
        return new C2SOpenGunTablePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Block block = ForgeRegistries.BLOCKS.getValue(this.blockId);
            Component title = block != null ? block.getName() : Component.literal("Gun Smith Table");

            NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new com.tacz.guns.inventory.GunSmithTableMenu(containerId, playerInventory, this.blockId),
                    title
                ),
                buf -> buf.writeResourceLocation(this.blockId)
            );
        });
        ctx.setPacketHandled(true);
    }
}
