package com.complextalents.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SpellDumpCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dump_spell_tooltips")
                .requires(source -> source.hasPermission(2))
                .executes(SpellDumpCommand::executeDump)
        );

        dispatcher.register(Commands.literal("spell_dump")
                .requires(source -> source.hasPermission(2))
                .executes(SpellDumpCommand::executeDump)
        );
    }

    public static class SpellDumpEntry {
        public String spell_id;
        public String display_name;
        public String description;
        public String school;
        public int max_level;

        public SpellDumpEntry(String spell_id, String display_name, String description, String school, int max_level) {
            this.spell_id = spell_id;
            this.display_name = display_name;
            this.description = description;
            this.school = school;
            this.max_level = max_level;
        }
    }

    private static int executeDump(CommandContext<CommandSourceStack> context) {
        try {
            List<SpellDumpEntry> list = new ArrayList<>();
            var registry = SpellRegistry.REGISTRY.get();
            if (registry != null) {
                for (AbstractSpell spell : registry.getValues()) {
                    if (spell == null || spell == SpellRegistry.none()) continue;
                    String spellId = spell.getSpellId();
                    String displayName = Language.getInstance().getOrDefault(spell.getComponentId());
                    String description = Language.getInstance().getOrDefault(spell.getComponentId() + ".guide");
                    String school = spell.getSchoolType().getId().toString();
                    int maxLevel = spell.getMaxLevel();
                    list.add(new SpellDumpEntry(spellId, displayName, description, school, maxLevel));
                }
            }

            File file = FMLPaths.GAMEDIR.get().resolve("spell_tooltips_dump.json").toFile();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(list, writer);
            }

            context.getSource().sendSuccess(() -> Component.literal("§aSuccessfully dumped spell tooltips and descriptions to " + file.getName() + "!"), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cFailed to dump spell tooltips: " + e.getMessage()));
            return 0;
        }
    }
}
