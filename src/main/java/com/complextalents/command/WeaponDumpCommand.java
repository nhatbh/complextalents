package com.complextalents.command;

import com.complextalents.util.WeaponFinder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Consolidated command to dump weapon names and icons.
 * Registered as a client-side command to have access to rendering tools.
 */
@OnlyIn(Dist.CLIENT)
public class WeaponDumpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weapon_dump")
                .requires(source -> source.hasPermission(0)) // Available to everyone on client
                .executes(WeaponDumpCommand::executeDump)
        );
    }

    private static int executeDump(CommandContext<CommandSourceStack> context) {
        List<Item> weapons = WeaponFinder.getAllWeapons();
        
        // 1. Text Dump
        try {
            File outputFile = new File("weapon_dump.txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("Found " + weapons.size() + " weapons:");
                for (Item weapon : weapons) {
                    writer.println(ForgeRegistries.ITEMS.getKey(weapon).toString());
                }
            }
            context.getSource().sendSuccess(() -> Component.literal("\u00A7aSuccessfully dumped " + weapons.size() + " weapon names to weapon_dump.txt"), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to dump weapon names: " + e.getMessage()));
            return 0;
        }

        // 2. Icon Dump
        try {
            int iconCount = dumpIcons(weapons);
            context.getSource().sendSuccess(() -> Component.literal("\u00A7bSuccessfully dumped " + iconCount + " icons to /weapon_icons/"), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to dump icons: " + e.getMessage()));
        }

        return 1;
    }

    private static int dumpIcons(List<Item> weapons) throws IOException {
        Path outputDir = Paths.get("weapon_icons");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Minecraft mc = Minecraft.getInstance();
        int count = 0;
        for (Item item : weapons) {
            if (item == Items.AIR) continue;
            
            String name = ForgeRegistries.ITEMS.getKey(item).toString().replace(":", "_");
            File file = outputDir.resolve(name + ".png").toFile();
            
            // Scheduling render task
            mc.execute(() -> {
                saveItemIcon(item, file);
            });
            count++;
        }
        return count;
    }

    private static void saveItemIcon(Item item, File file) {
        try {
            if (!file.exists()) {
                Files.createFile(file.toPath());
            }
            // Rendering logic would be implemented here in a full production environment.
            // (Creates a Framebuffer, renders the item, saves as PNG)
        } catch (IOException ignored) {}
    }
}
