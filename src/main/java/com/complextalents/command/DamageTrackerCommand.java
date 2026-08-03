package com.complextalents.command;

import com.complextalents.handlers.DamageTrackerHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Toggles live incoming damage and mitigation chat logging for the executing player.
 * 
 * Usage:
 *   /damagelog
 *   /damagetracker
 */
public class DamageTrackerCommand {

    public static final int OP_LEVEL = 0; // Available to all players for testing combat stats

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("damagelog")
                .executes(DamageTrackerCommand::toggleLog);

        dispatcher.register(command);

        dispatcher.register(Commands.literal("damagetracker")
                .executes(DamageTrackerCommand::toggleLog));
    }

    private static int toggleLog(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean enabled = DamageTrackerHandler.toggleTracking(player.getUUID());
            if (enabled) {
                ctx.getSource().sendSuccess(() -> Component.literal("§a[Damage Tracker] Live incoming damage logging ENABLED."), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("§c[Damage Tracker] Live incoming damage logging DISABLED."), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }
}
