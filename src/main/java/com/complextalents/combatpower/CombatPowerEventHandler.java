package com.complextalents.combatpower;

import com.complextalents.TalentsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class CombatPowerEventHandler {

    @SubscribeEvent
    public static void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player == null) return;

        // Get player level
        int level = 1;
        if (!player.level().isClientSide) {
            if (player instanceof ServerPlayer serverPlayer) {
                level = com.complextalents.leveling.service.LevelingService.getInstance().getLevel(serverPlayer);
            }
        } else {
            // Client side: only format the local player's name.
            // For other players, keep the synced display name sent from the server.
            final boolean[] isLocal = {false};
            final int[] clientLevel = {1};
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                isLocal[0] = com.complextalents.combatpower.client.ClientNameFormatHelper.isLocalPlayer(player);
                if (isLocal[0]) {
                    clientLevel[0] = com.complextalents.combatpower.client.ClientNameFormatHelper.getClientLevel();
                }
            });
            if (!isLocal[0]) {
                return;
            }
            level = clientLevel[0];
        }

        int highestCP = CombatPowerCalculator.getHighestCombatPower(player);
        KnightRank rank = KnightRank.fromCP(highestCP);
        String fullTitle = rank.getTitleForCP(highestCP);

        Component originalName = event.getDisplayname();
        String nameStr = originalName.getString();

        // Format: ⚜ PlayerName [Exalted I] (Lvl 12)
        MutableComponent formattedName = Component.literal(rank.getFormattedSymbol() + " ")
                .append(Component.literal(nameStr).withStyle(rank.getColor()))
                .append(Component.literal(" [" + fullTitle + "]").withStyle(rank.getColor()))
                .append(Component.literal(" (Lvl " + level + ")").withStyle(ChatFormatting.GRAY));

        event.setDisplayname(formattedName);
    }
}
