package com.complextalents.command;

import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MysteriousLootItem;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class CrateCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WEAPON_PATHS = (ctx, builder) -> {
        List<String> list = new ArrayList<>();
        for (WeaponPath path : WeaponPath.values()) {
            list.add(path.name().toLowerCase(Locale.ROOT));
        }
        list.add("random");
        return SharedSuggestionProvider.suggest(list, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MAGIC_SCHOOLS = (ctx, builder) -> {
        List<String> list = new ArrayList<>();
        try {
            if (SchoolRegistry.REGISTRY != null && SchoolRegistry.REGISTRY.get() != null) {
                for (SchoolType school : SchoolRegistry.REGISTRY.get().getValues()) {
                    if (school != null) {
                        list.add(school.getId().toString());
                    }
                }
            }
        } catch (Exception ignored) {}
        if (list.isEmpty()) {
            list.add("irons_spellbooks:fire");
            list.add("irons_spellbooks:ice");
            list.add("irons_spellbooks:lightning");
            list.add("irons_spellbooks:holy");
            list.add("irons_spellbooks:ender");
            list.add("irons_spellbooks:blood");
            list.add("irons_spellbooks:evocation");
            list.add("irons_spellbooks:nature");
        }
        list.add("random");
        return SharedSuggestionProvider.suggest(list, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CATEGORIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("all", "weapon", "magic"), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RARITIES = (ctx, builder) -> {
        List<String> list = new ArrayList<>();
        for (CrateRarity rarity : CrateRarity.values()) {
            list.add(rarity.name().toLowerCase(Locale.ROOT));
        }
        list.add("random");
        return SharedSuggestionProvider.suggest(list, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_AMOUNTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("1", "5", "10", "64", "random", "1..5", "1..10", "5..15"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("givecrate")
                .requires(s -> s.hasPermission(2))

                // /givecrate <targets> weapon <path|random> [rarity] [amount]
                .then(Commands.literal("weapon")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("path", StringArgumentType.word())
                                        .suggests(SUGGEST_WEAPON_PATHS)
                                        .executes(ctx -> executeWeapon(ctx, "random", "1"))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests(SUGGEST_RARITIES)
                                                .executes(ctx -> executeWeapon(ctx, StringArgumentType.getString(ctx, "rarity"), "1"))
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .suggests(SUGGEST_AMOUNTS)
                                                        .executes(ctx -> executeWeapon(ctx, StringArgumentType.getString(ctx, "rarity"), StringArgumentType.getString(ctx, "amount")))
                                                )
                                        )
                                )
                        )
                )

                // /givecrate <targets> magic <school|random> [rarity] [amount]
                .then(Commands.literal("magic")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("school", StringArgumentType.string())
                                        .suggests(SUGGEST_MAGIC_SCHOOLS)
                                        .executes(ctx -> executeMagic(ctx, "random", "1"))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests(SUGGEST_RARITIES)
                                                .executes(ctx -> executeMagic(ctx, StringArgumentType.getString(ctx, "rarity"), "1"))
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .suggests(SUGGEST_AMOUNTS)
                                                        .executes(ctx -> executeMagic(ctx, StringArgumentType.getString(ctx, "rarity"), StringArgumentType.getString(ctx, "amount")))
                                                )
                                        )
                                )
                        )
                )

                // /givecrate <targets> random [category] [rarity] [amount]
                .then(Commands.literal("random")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeRandom(ctx, "all", "random", "1"))
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(SUGGEST_CATEGORIES)
                                        .executes(ctx -> executeRandom(ctx, StringArgumentType.getString(ctx, "category"), "random", "1"))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests(SUGGEST_RARITIES)
                                                .executes(ctx -> executeRandom(ctx, StringArgumentType.getString(ctx, "category"), StringArgumentType.getString(ctx, "rarity"), "1"))
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .suggests(SUGGEST_AMOUNTS)
                                                        .executes(ctx -> executeRandom(ctx, StringArgumentType.getString(ctx, "category"), StringArgumentType.getString(ctx, "rarity"), StringArgumentType.getString(ctx, "amount")))
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int executeWeapon(CommandContext<CommandSourceStack> ctx, String rarityStr, String amountStr) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String pathArg = StringArgumentType.getString(ctx, "path");
        CommandSourceStack source = ctx.getSource();

        int totalGiven = 0;
        for (ServerPlayer player : targets) {
            int amount = parseAmount(amountStr, player.getRandom());
            for (int i = 0; i < amount; i++) {
                WeaponPath path = resolveWeaponPath(pathArg, player.getRandom());
                CrateRarity rarity = resolveRarity(rarityStr, path, null, player.getRandom());
                ItemStack stack = MysteriousLootItem.createWeaponCase(path, rarity);
                giveItemToPlayer(player, stack);
                totalGiven++;
            }
            source.sendSuccess(() -> Component.literal("§aGave §e" + amount + " §aweapon crate(s) to §e" + player.getScoreboardName()), true);
        }
        return totalGiven;
    }

    private static int executeMagic(CommandContext<CommandSourceStack> ctx, String rarityStr, String amountStr) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String schoolArg = StringArgumentType.getString(ctx, "school");
        CommandSourceStack source = ctx.getSource();

        int totalGiven = 0;
        for (ServerPlayer player : targets) {
            int amount = parseAmount(amountStr, player.getRandom());
            for (int i = 0; i < amount; i++) {
                ResourceLocation schoolId = resolveMagicSchool(schoolArg, player.getRandom());
                CrateRarity rarity = resolveRarity(rarityStr, null, schoolId, player.getRandom());
                ItemStack stack = MysteriousLootItem.createMagicCase(schoolId, rarity);
                giveItemToPlayer(player, stack);
                totalGiven++;
            }
            source.sendSuccess(() -> Component.literal("§aGave §e" + amount + " §amagic crate(s) to §e" + player.getScoreboardName()), true);
        }
        return totalGiven;
    }

    private static int executeRandom(CommandContext<CommandSourceStack> ctx, String categoryStr, String rarityStr, String amountStr) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        CommandSourceStack source = ctx.getSource();

        int totalGiven = 0;
        for (ServerPlayer player : targets) {
            int amount = parseAmount(amountStr, player.getRandom());
            for (int i = 0; i < amount; i++) {
                boolean isWeapon;
                if ("weapon".equalsIgnoreCase(categoryStr)) {
                    isWeapon = true;
                } else if ("magic".equalsIgnoreCase(categoryStr)) {
                    isWeapon = false;
                } else {
                    isWeapon = player.getRandom().nextBoolean();
                }

                ItemStack stack;
                if (isWeapon) {
                    WeaponPath path = resolveWeaponPath("random", player.getRandom());
                    CrateRarity rarity = resolveRarity(rarityStr, path, null, player.getRandom());
                    stack = MysteriousLootItem.createWeaponCase(path, rarity);
                } else {
                    ResourceLocation schoolId = resolveMagicSchool("random", player.getRandom());
                    CrateRarity rarity = resolveRarity(rarityStr, null, schoolId, player.getRandom());
                    stack = MysteriousLootItem.createMagicCase(schoolId, rarity);
                }

                giveItemToPlayer(player, stack);
                totalGiven++;
            }
            source.sendSuccess(() -> Component.literal("§aGave §e" + amount + " §arandom crate(s) to §e" + player.getScoreboardName()), true);
        }
        return totalGiven;
    }

    private static void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int parseAmount(String input, RandomSource random) {
        if (input == null || input.equalsIgnoreCase("random")) {
            return 1 + random.nextInt(5); // Default random 1..5
        }
        try {
            if (input.contains("..")) {
                String[] parts = input.split("\\.\\.");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) {
                    int tmp = min; min = max; max = tmp;
                }
                return min + random.nextInt((max - min) + 1);
            } else if (input.contains("-")) {
                String[] parts = input.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) {
                    int tmp = min; min = max; max = tmp;
                }
                return min + random.nextInt((max - min) + 1);
            }
            return Math.max(1, Integer.parseInt(input.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static WeaponPath resolveWeaponPath(String input, RandomSource random) {
        if (!"random".equalsIgnoreCase(input)) {
            WeaponPath parsed = WeaponPath.fromString(input);
            if (parsed != null) return parsed;
        }
        WeaponPath[] paths = WeaponPath.values();
        return paths[random.nextInt(paths.length)];
    }

    private static ResourceLocation resolveMagicSchool(String input, RandomSource random) {
        if (!"random".equalsIgnoreCase(input)) {
            ResourceLocation loc = ResourceLocation.tryParse(input);
            if (loc != null) return loc;
        }
        List<ResourceLocation> availableSchools = new ArrayList<>();
        try {
            if (SchoolRegistry.REGISTRY != null && SchoolRegistry.REGISTRY.get() != null) {
                for (SchoolType school : SchoolRegistry.REGISTRY.get().getValues()) {
                    if (school != null) {
                        availableSchools.add(school.getId());
                    }
                }
            }
        } catch (Exception ignored) {}
        if (availableSchools.isEmpty()) {
            availableSchools.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire"));
            availableSchools.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice"));
            availableSchools.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning"));
            availableSchools.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy"));
            availableSchools.add(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ender"));
        }
        return availableSchools.get(random.nextInt(availableSchools.size()));
    }

    private static CrateRarity resolveRarity(String input, WeaponPath weaponPath, ResourceLocation schoolId, RandomSource random) {
        if (input != null && !"random".equalsIgnoreCase(input)) {
            try {
                return CrateRarity.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {}
        }

        List<CrateRarity> validRarities;
        if (weaponPath != null) {
            validRarities = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(weaponPath);
        } else if (schoolId != null) {
            validRarities = DynamicCasePoolBuilder.getValidRaritiesForSchool(schoolId);
        } else {
            validRarities = List.of(CrateRarity.values());
        }

        if (validRarities.isEmpty()) return CrateRarity.COMMON;
        return validRarities.get(random.nextInt(validRarities.size()));
    }
}
