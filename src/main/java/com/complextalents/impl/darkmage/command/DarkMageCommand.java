package com.complextalents.impl.darkmage.command;

import com.complextalents.impl.darkmage.data.SoulData;
import com.complextalents.impl.darkmage.origin.DarkMageOrigin;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DarkMageCommand {

    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("darkmage")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.literal("souls")
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> setSouls(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("get")
                                .executes(ctx -> getSouls(ctx.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> addSouls(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "amount")))))
                )
        );
    }

    private static int setSouls(CommandSourceStack src, double amount) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        if (!DarkMageOrigin.isDarkMage(player)) {
            src.sendFailure(Component.literal("You must be a Dark Mage to have souls"));
            return 0;
        }

        SoulData.setSouls(player, amount);
        src.sendSuccess(() -> Component.literal("\u00A75Soul count set to \u00A7d" + String.format("%.1f", amount)), true);
        return 1;
    }

    private static int getSouls(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        if (!DarkMageOrigin.isDarkMage(player)) {
            src.sendFailure(Component.literal("You must be a Dark Mage to have souls"));
            return 0;
        }

        double souls = SoulData.getSouls(player);
        src.sendSuccess(() -> Component.literal("\u00A75Current soul count: \u00A7d" + String.format("%.1f", souls)), false);
        return 1;
    }

    private static int addSouls(CommandSourceStack src, double amount) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        if (!DarkMageOrigin.isDarkMage(player)) {
            src.sendFailure(Component.literal("You must be a Dark Mage to have souls"));
            return 0;
        }

        SoulData.addSouls(player, amount);
        double total = SoulData.getSouls(player);
        src.sendSuccess(() -> Component.literal("\u00A75Added \u00A7d" + String.format("%.1f", amount) + " \u00A75souls. Total: \u00A7d" + String.format("%.1f", total)), true);
        return 1;
    }
}
