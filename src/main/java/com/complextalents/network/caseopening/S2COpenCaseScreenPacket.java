package com.complextalents.network.caseopening;

import com.complextalents.caseopening.CaseReward;
import com.complextalents.client.screen.CSGOCaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2COpenCaseScreenPacket {
    private final List<CaseReward> sequence;
    private final int winningIndex;
    private final CaseReward winningReward;
    private final List<CaseReward> fullPool;

    public S2COpenCaseScreenPacket(List<CaseReward> sequence, int winningIndex, CaseReward winningReward, List<CaseReward> fullPool) {
        this.sequence = sequence;
        this.winningIndex = winningIndex;
        this.winningReward = winningReward;
        this.fullPool = fullPool != null ? fullPool : List.of();
    }

    public S2COpenCaseScreenPacket(List<CaseReward> sequence, int winningIndex, CaseReward winningReward) {
        this(sequence, winningIndex, winningReward, sequence);
    }

    public S2COpenCaseScreenPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.sequence = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.sequence.add(CaseReward.decode(buf));
        }
        this.winningIndex = buf.readVarInt();
        this.winningReward = CaseReward.decode(buf);

        if (buf.isReadable()) {
            int poolCount = buf.readVarInt();
            this.fullPool = new ArrayList<>(poolCount);
            for (int i = 0; i < poolCount; i++) {
                this.fullPool.add(CaseReward.decode(buf));
            }
        } else {
            this.fullPool = new ArrayList<>(this.sequence);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sequence.size());
        for (CaseReward reward : sequence) {
            reward.encode(buf);
        }
        buf.writeVarInt(winningIndex);
        winningReward.encode(buf);

        buf.writeVarInt(fullPool.size());
        for (CaseReward reward : fullPool) {
            reward.encode(buf);
        }
    }

    public static S2COpenCaseScreenPacket decode(FriendlyByteBuf buf) {
        return new S2COpenCaseScreenPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleClient());
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        Minecraft.getInstance().setScreen(new CSGOCaseScreen(sequence, winningIndex, winningReward, fullPool));
    }
}
