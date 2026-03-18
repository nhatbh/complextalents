package com.complextalents.weaponmastery.network;

import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WeaponMasterySyncPacket {
    private final CompoundTag nbt;

    public WeaponMasterySyncPacket(CompoundTag nbt) {
        this.nbt = nbt;
    }

    public WeaponMasterySyncPacket(FriendlyByteBuf buf) {
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
                player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                    data.deserializeNBT(nbt);
                });
            }
        });
        context.setPacketHandled(true);
    }
}
