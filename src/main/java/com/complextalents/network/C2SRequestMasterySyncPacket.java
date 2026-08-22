package com.complextalents.network;

import com.complextalents.gunmastery.capability.GunMasteryDataProvider;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRequestMasterySyncPacket {

    public C2SRequestMasterySyncPacket() {
    }

    public C2SRequestMasterySyncPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Sync Gun Mastery
            player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(cap -> {
                cap.sync();
            });

            // Sync Weapon Mastery
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(cap -> {
                if (cap instanceof com.complextalents.weaponmastery.capability.WeaponMasteryData wmd) {
                    wmd.applyStatRewards();
                }
                cap.sync();
            });

            // Sync Spell Mastery
            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(cap -> {
                cap.sync();
            });
        });
        ctx.setPacketHandled(true);
    }
}
