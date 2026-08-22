package com.complextalents.network.spellblade;

import com.complextalents.impl.spellblade.SpellbladeDataProvider;
import com.complextalents.spellmastery.SpellSchool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpellbladeDataSyncPacket {

    private final SpellSchool activeElement;
    private final int enhancedAttackTicks;
    private final boolean imbueCharge;
    private final int overchargeTicks;
    private final boolean overchargeStance;
    private final boolean freeCast;

    public SpellbladeDataSyncPacket(SpellSchool activeElement, int enhancedAttackTicks, boolean imbueCharge, int overchargeTicks, boolean overchargeStance, boolean freeCast) {
        this.activeElement = activeElement;
        this.enhancedAttackTicks = enhancedAttackTicks;
        this.imbueCharge = imbueCharge;
        this.overchargeTicks = overchargeTicks;
        this.overchargeStance = overchargeStance;
        this.freeCast = freeCast;
    }

    public static void encode(SpellbladeDataSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.activeElement != null);
        if (msg.activeElement != null) {
            buf.writeEnum(msg.activeElement);
        }
        buf.writeInt(msg.enhancedAttackTicks);
        buf.writeBoolean(msg.imbueCharge);
        buf.writeInt(msg.overchargeTicks);
        buf.writeBoolean(msg.overchargeStance);
        buf.writeBoolean(msg.freeCast);
    }

    public static SpellbladeDataSyncPacket decode(FriendlyByteBuf buf) {
        boolean hasElement = buf.readBoolean();
        SpellSchool element = hasElement ? buf.readEnum(SpellSchool.class) : null;
        int enhancedAttackTicks = buf.readInt();
        boolean imbueCharge = buf.readBoolean();
        int overchargeTicks = buf.readInt();
        boolean overchargeStance = buf.readBoolean();
        boolean freeCast = buf.readBoolean();

        return new SpellbladeDataSyncPacket(element, enhancedAttackTicks, imbueCharge, overchargeTicks, overchargeStance, freeCast);
    }

    public static void handle(SpellbladeDataSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            @SuppressWarnings("resource")
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;

            player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).ifPresent(cap -> {
                cap.setActiveElement(msg.activeElement);
                cap.setEnhancedAttackTicks(msg.enhancedAttackTicks);
                cap.setHasImbueCharge(msg.imbueCharge);
                cap.setOverchargeTicks(msg.overchargeTicks);
                cap.setOverchargeStance(msg.overchargeStance);
                cap.setFreeCast(msg.freeCast);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
