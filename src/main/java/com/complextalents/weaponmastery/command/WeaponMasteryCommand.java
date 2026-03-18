package com.complextalents.weaponmastery.command;

import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class WeaponMasteryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weaponmastery").requires(s -> s.hasPermission(2))
                .then(Commands.literal("addDamage")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("path", net.minecraft.commands.arguments.MessageArgument.message())
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1.0))
                                                .executes(c -> addDamage(c.getSource(), EntityArgument.getPlayer(c, "player"), net.minecraft.commands.arguments.MessageArgument.getMessage(c, "path").getString(), DoubleArgumentType.getDouble(c, "amount")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("setLevel")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("path", net.minecraft.commands.arguments.MessageArgument.message())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 25))
                                                .executes(c -> setLevel(c.getSource(), EntityArgument.getPlayer(c, "player"), net.minecraft.commands.arguments.MessageArgument.getMessage(c, "path").getString(), IntegerArgumentType.getInteger(c, "level")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> showInfo(c.getSource(), EntityArgument.getPlayer(c, "player")))
                        )
                )
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.complextalents.dev.SimpleUIFactory.INSTANCE.open(player, com.complextalents.weaponmastery.client.WeaponMasteryUI.UI_ID);
                            return 1;
                        })
                )
                .then(Commands.literal("reload")
                        .executes(c -> {
                            WeaponMasteryManager.getInstance().initialize();
                            c.getSource().sendSuccess(() -> Component.literal("Reloaded weapon_data.json mappings!"), true);
                            return 1;
                        })
                )
        );
    }

    private static int addDamage(CommandSourceStack source, ServerPlayer player, String pathStr, double amount) {
        IWeaponMasteryData.WeaponPath path = IWeaponMasteryData.WeaponPath.fromString(pathStr);
        if (path == null) {
            source.sendFailure(Component.literal("Invalid Weapon Path: " + pathStr));
            return 0;
        }

        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            data.addAccumulatedDamage(path, amount);
            source.sendSuccess(() -> Component.literal("Added " + amount + " damage to " + path.name() + " for " + player.getName().getString()), true);
        });

        return 1;
    }

    private static int setLevel(CommandSourceStack source, ServerPlayer player, String pathStr, int level) {
        IWeaponMasteryData.WeaponPath path = IWeaponMasteryData.WeaponPath.fromString(pathStr);
        if (path == null) {
            source.sendFailure(Component.literal("Invalid Weapon Path: " + pathStr));
            return 0;
        }

        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            data.setMasteryLevel(path, level);
            source.sendSuccess(() -> Component.literal("Set " + path.name() + " mastery level to " + level + " for " + player.getName().getString()), true);
        });

        return 1;
    }

    private static int showInfo(CommandSourceStack source, ServerPlayer player) {
        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            source.sendSuccess(() -> Component.literal("Weapon Mastery for " + player.getName().getString() + ":"), false);
            for (IWeaponMasteryData.WeaponPath path : IWeaponMasteryData.WeaponPath.values()) {
                double damage = data.getAccumulatedDamage(path);
                int level = data.getMasteryLevel(path);
                source.sendSuccess(() -> Component.literal(path.name() + " - Level: " + level + " | Damage: " + damage), false);
            }
        });
        return 1;
    }
}
