package com.complextalents.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SLearnSpellPromptPacket {
    private final ResourceLocation spellId;
    private final int level;

    public C2SLearnSpellPromptPacket(ResourceLocation spellId, int level) {
        this.spellId = spellId;
        this.level = level;
    }

    public C2SLearnSpellPromptPacket(FriendlyByteBuf buf) {
        this.spellId = buf.readResourceLocation();
        this.level = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(spellId);
        buf.writeInt(level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null) return;

            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                if (mastery.isSpellLearned(spellId, level)) {
                    player.sendSystemMessage(Component.literal("You have already learned " + spell.getDisplayName(player).getString() + " L" + level + "!").withStyle(ChatFormatting.YELLOW));
                    return;
                }

                ResourceLocation activeOrigin = player.getCapability(OriginDataProvider.ORIGIN_DATA)
                        .map(data -> data.getActiveOrigin()).orElse(null);

                PlayerLevelingData levelingData = PlayerLevelingData.get(player.getServer());
                long availableSP = levelingData.getAvailableSkillPoints(player.getUUID());

                int cost = SpellMasteryManager.getSpellUpgradeCost(spell, level, mastery, activeOrigin);

                if (cost < 0) {
                    player.sendSystemMessage(Component.literal("Your class cannot learn spells from the " + spell.getSchoolType().getDisplayName().getString() + " school!").withStyle(ChatFormatting.RED));
                    return;
                }

                if (availableSP >= cost) {
                    // Deduct SP
                    levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + cost);
                    LevelingSyncHandler.syncPlayerLevelData(player);

                    // Learn spell
                    mastery.learnSpell(spellId, level);
                    mastery.sync();

                    // Play level up sound & announce
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                    player.sendSystemMessage(Component.literal("§a✦ Successfully learned " + spell.getDisplayName(player).getString() + " L" + level + "! (Spent " + cost + " SP)"));
                } else {
                    player.sendSystemMessage(Component.literal("Not enough SP to learn " + spell.getDisplayName(player).getString() + "! Required: " + cost + " SP").withStyle(ChatFormatting.RED));
                }
            });
        });
        ctx.setPacketHandled(true);
    }
}
