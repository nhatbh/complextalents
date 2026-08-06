package com.complextalents.command;

import com.complextalents.classification.UnifiedWeaponClassificationMenu;
import com.complextalents.classification.WeaponClassificationStorage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;

public class WeaponClassifyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("classify_weapons")
                .requires(source -> source.hasPermission(2))
                .executes(WeaponClassifyCommand::executeOpenMenu)
        );

        dispatcher.register(Commands.literal("weapon_classify")
                .requires(source -> source.hasPermission(2))
                .executes(WeaponClassifyCommand::executeOpenMenu)
        );
    }

    private static int executeOpenMenu(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            WeaponClassificationStorage.ensureInitialized();

            player.openMenu(new SimpleMenuProvider(
                    (id, playerInv, p) -> new UnifiedWeaponClassificationMenu(id, playerInv, new SimpleContainer(54)),
                    Component.literal("§8Weapon Classification Manager")
            ));
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cThis command must be executed by a player in-game."));
            return 0;
        }
    }
}
