package com.complextalents.dps.command;

import com.complextalents.dps.DPSManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command for DPS meter functionality.
 * Accessible to all players in Survival (permission level 0).
 *
 * Commands:
 * /dps - Toggle start/stop DPS meter
 * /dps start - Arm/start DPS meter
 * /dps stop - Stop DPS meter and view report
 * /dps status - Check current DPS meter status
 */
public class DPSCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dps")
                .requires(src -> true) // Eligible for all players (survival mode, non-op)
                .executes(ctx -> toggleDPS(ctx.getSource()))
                .then(Commands.literal("start")
                        .executes(ctx -> startDPS(ctx.getSource())))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopDPS(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> statusDPS(ctx.getSource()))));
    }

    private static int toggleDPS(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (DPSManager.getInstance().isTracking(player)) {
                DPSManager.getInstance().stopSession(player);
            } else {
                DPSManager.getInstance().startSession(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int startDPS(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DPSManager.getInstance().startSession(player);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int stopDPS(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DPSManager.getInstance().stopSession(player);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int statusDPS(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DPSManager.getInstance().sendStatus(player);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
