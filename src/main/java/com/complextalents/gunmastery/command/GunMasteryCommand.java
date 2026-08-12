package com.complextalents.gunmastery.command;

import com.complextalents.gunmastery.GunMasteryManager;
import com.complextalents.gunmastery.capability.GunMasteryDataProvider;
import com.complextalents.gunmastery.capability.IGunMasteryData;
import com.complextalents.tacz.GunType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;

/**
 * Commands for managing player gun mastery.
 *
 * /gunmastery set <gunType> <level> [targets]
 * /gunmastery adddamage <gunType> <amount> [targets]
 * /gunmastery reset [targets]
 * /gunmastery info [target]
 */
public class GunMasteryCommand {

    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gunmastery")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.literal("set")
                        .then(Commands.argument("gunType", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 20))
                                        .executes(ctx -> setLevel(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "gunType"), IntegerArgumentType.getInteger(ctx, "level")))
                                        .then(Commands.argument("target", EntityArgument.players())
                                                .executes(ctx -> setLevel(ctx, EntityArgument.getPlayers(ctx, "target"), StringArgumentType.getString(ctx, "gunType"), IntegerArgumentType.getInteger(ctx, "level")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("adddamage")
                        .then(Commands.argument("gunType", StringArgumentType.word())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> addDamage(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "gunType"), DoubleArgumentType.getDouble(ctx, "amount")))
                                        .then(Commands.argument("target", EntityArgument.players())
                                                .executes(ctx -> addDamage(ctx, EntityArgument.getPlayers(ctx, "target"), StringArgumentType.getString(ctx, "gunType"), DoubleArgumentType.getDouble(ctx, "amount")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx, Collections.singleton(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(ctx -> reset(ctx, EntityArgument.getPlayers(ctx, "target")))
                        )
                )
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target")))
                        )
                )
                .then(Commands.literal("dumpguns")
                        .executes(com.complextalents.tacz.TacZGunDumpCommand::executeDump)
                )
        );
    }


    private static int setLevel(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String typeName, int level) {
        GunType type;
        try {
            type = GunType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid GunType: " + typeName));
            return 0;
        }

        for (ServerPlayer player : targets) {
            player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                data.setMasteryLevel(type, level);
                data.sync();
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aSet " + type.getDisplayName() + " mastery level to " + level + " for " + player.getName().getString()), true);
            });
        }
        return targets.size();
    }

    private static int addDamage(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String typeName, double amount) {
        GunType type;
        try {
            type = GunType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid GunType: " + typeName));
            return 0;
        }

        for (ServerPlayer player : targets) {
            player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                data.addAccumulatedDamage(type, (float) amount);
                data.sync();
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aAdded " + (int) amount + " damage to " + type.getDisplayName() + " for " + player.getName().getString()), true);
            });
        }
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
                data.reset();
                data.sync();
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A76Reset gun mastery data for " + player.getName().getString()), true);
            });
        }
        return targets.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        player.getCapability(GunMasteryDataProvider.GUN_MASTERY_DATA).ifPresent(data -> {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eGun Mastery Info for " + player.getName().getString() + ":"), false);
            for (GunType type : GunType.values()) {
                if (type == GunType.RPG || type == GunType.GLOBAL) continue;
                int lvl = data.getMasteryLevel(type);
                double dmg = data.getAccumulatedDamage(type);
                int maxLvl = GunMasteryManager.getInstance().getMaxLevel(type);
                ctx.getSource().sendSuccess(() -> Component.literal("  - \u00A76" + type.getDisplayName() + "\u00A7f: Level \u00A7b" + lvl + "/" + maxLvl + " \u00A77(Dmg: " + (int) dmg + ")"), false);
            }
        });
        return 1;
    }
}
