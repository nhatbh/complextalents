package com.complextalents.command;

import com.complextalents.util.WeaponFinder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.util.List;

/**
 * Client-side command to dump icons of all weapons.
 * Uses a tick-based queue to render items one by one to prevent game freezes.
 */
@OnlyIn(Dist.CLIENT)
public class WeaponIconDumpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weapon_icons")
                .requires(source -> source.hasPermission(0))
                .executes(WeaponIconDumpCommand::dumpIcons)
        );
    }

    private static int dumpIcons(CommandContext<CommandSourceStack> context) {
        List<Item> weapons = WeaponFinder.getAllWeapons();
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        
        File outputDirectory = new File(Minecraft.getInstance().gameDirectory, "weapon_icons");
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        for (Item item : weapons) {
            if (item != net.minecraft.world.item.Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }

        com.complextalents.util.ItemIconExporter.startExport(stacks, outputDirectory, 64);

        context.getSource().sendSuccess(() -> Component.literal("\u00A7aStarted weapon icon export (64x64)."), true);
        context.getSource().sendSuccess(() -> Component.literal("\u00A7eIcons are being rendered in the background."), true);

        return 1;
    }
}
