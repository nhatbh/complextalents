package com.complextalents.combatpower;

import com.complextalents.TalentsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class CombatPowerEventHandler {

    @SubscribeEvent
    public static void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player == null) return;

        int highestCP = CombatPowerCalculator.getHighestCombatPower(player);
        KnightRank rank = KnightRank.fromCP(highestCP);
        String fullTitle = rank.getTitleForCP(highestCP);

        Component originalName = event.getDisplayname();
        String nameStr = originalName.getString();

        // Format: ⚜ PlayerName [Exalted I] in rank color
        MutableComponent formattedName = Component.literal(rank.getFormattedSymbol() + " ")
                .append(Component.literal(nameStr).withStyle(rank.getColor()))
                .append(Component.literal(" [" + fullTitle + "]").withStyle(rank.getColor()));

        event.setDisplayname(formattedName);
    }
}
