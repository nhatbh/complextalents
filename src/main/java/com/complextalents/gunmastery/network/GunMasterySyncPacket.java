package com.complextalents.gunmastery.network;

import com.complextalents.gunmastery.capability.GunMasteryDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GunMasterySyncPacket {
    private final CompoundTag nbt;

    public GunMasterySyncPacket(CompoundTag nbt) {
        this.nbt = nbt;
    }

    public GunMasterySyncPacket(FriendlyByteBuf buf) {
        this.nbt = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(nbt);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                    data.deserializeNBT(nbt);
                });
            }
        });
        context.setPacketHandled(true);
    }
}
