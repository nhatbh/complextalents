package com.complextalents.spellmastery.command;

import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Commands for managing player spell mastery and knowledge.
 * 
 * /mastery mastery <schoolId> <level> [targets]
 * /mastery learn <spellId> [targets]
 * /mastery forget <spellId> [targets]
 * /mastery purchase <spellId> <level>
 * /mastery info [target]
 */
public class SpellMasteryCommand {

    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mastery")
                .then(PurchaseSubCommand.register())
                .then(Commands.literal("mastery")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(MasterySubCommand.registerBody()))
                .then(Commands.literal("learn")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(LearnSubCommand.registerBody()))
                .then(Commands.literal("forget")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(ForgetSubCommand.registerBody()))
                .then(Commands.literal("info")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(InfoSubCommand.registerBody()))
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ctx.getSource().sendSuccess(() -> Component.literal("Use the keybind to open the progression UI"), true);
                            return 1;
                        }))
        );
    }

    private static class PurchaseSubCommand {
        static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> register() {
            return Commands.literal("purchase")
                    .requires(src -> true) // Open to all players for chat prompt purchase
                    .then(Commands.argument("spellId", ResourceLocationArgument.id())
                            .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                    .executes(ctx -> purchaseSpell(ctx, ResourceLocationArgument.getId(ctx, "spellId"), IntegerArgumentType.getInteger(ctx, "level")))
                            )
                    );
        }

        private static int purchaseSpell(CommandContext<CommandSourceStack> ctx, ResourceLocation spellId, int level) {
            try {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                AbstractSpell spell = SpellRegistry.getSpell(spellId);
                if (spell == null) return 0;

                int entryLevel = SpellMasteryManager.getMinLevelForRarity(spell, spell.getRarity(level));

                player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                    if (mastery.isSpellLearned(spellId, entryLevel)) {
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eYou have already learned " + spell.getDisplayName(player).getString() + " (" + spell.getRarity(entryLevel).getDisplayName().getString() + " Tier - Level " + entryLevel + ")!"), false);
                        return;
                    }

                    ResourceLocation activeOrigin = player.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA)
                            .map(data -> data.getActiveOrigin()).orElse(null);

                    com.complextalents.leveling.data.PlayerLevelingData levelingData = com.complextalents.leveling.data.PlayerLevelingData.get(player.getServer());
                    long availableSP = levelingData.getAvailableSkillPoints(player.getUUID());

                    int cost = SpellMasteryManager.getSpellUpgradeCost(spell, entryLevel, mastery, true, activeOrigin);

                    if (cost < 0) {
                        ctx.getSource().sendFailure(Component.literal("Your class cannot learn spells from the " + spell.getSchoolType().getDisplayName().getString() + " school!"));
                        return;
                    }

                    if (availableSP >= cost) {
                        levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + cost);
                        com.complextalents.leveling.handlers.LevelingSyncHandler.syncPlayerLevelData(player);

                        mastery.learnSpell(spellId, entryLevel);
                        SpellMasteryManager.onSpellLearned(player, spell);
                        mastery.sync();

                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a✦ Successfully learned " + spell.getDisplayName(player).getString() + " L" + entryLevel + "! (Spent " + cost + " SP)"), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Not enough SP to learn " + spell.getDisplayName(player).getString() + "! Required: " + cost + " SP (Available: " + availableSP + " SP)"));
                    }
                });
                return 1;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static class MasterySubCommand {
        static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> registerBody() {
            return Commands.argument("school", ResourceLocationArgument.id())
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                            .executes(ctx -> setMastery(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), ResourceLocationArgument.getId(ctx, "school"), IntegerArgumentType.getInteger(ctx, "level")))
                            .then(Commands.argument("target", EntityArgument.players())
                                    .executes(ctx -> setMastery(ctx, EntityArgument.getPlayers(ctx, "target"), ResourceLocationArgument.getId(ctx, "school"), IntegerArgumentType.getInteger(ctx, "level")))
                            )
                    );
        }

        private static int setMastery(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation schoolId, int level) {
            for (ServerPlayer player : targets) {
                player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                    data.setMasteryLevel(schoolId, level);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aSet " + schoolId + " mastery to " + level + " for " + player.getName().getString()), true);
                });
            }
            return targets.size();
        }
    }

    private static class LearnSubCommand {
        static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> registerBody() {
            return Commands.argument("spellId", ResourceLocationArgument.id())
                    .executes(ctx -> learnSpell(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), ResourceLocationArgument.getId(ctx, "spellId"), 1))
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> learnSpell(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), ResourceLocationArgument.getId(ctx, "spellId"), IntegerArgumentType.getInteger(ctx, "level")))
                            .then(Commands.argument("target", EntityArgument.players())
                                    .executes(ctx -> learnSpell(ctx, EntityArgument.getPlayers(ctx, "target"), ResourceLocationArgument.getId(ctx, "spellId"), IntegerArgumentType.getInteger(ctx, "level")))
                            )
                    )
                    .then(Commands.argument("target", EntityArgument.players())
                            .executes(ctx -> learnSpell(ctx, EntityArgument.getPlayers(ctx, "target"), ResourceLocationArgument.getId(ctx, "spellId"), 1))
                    );
        }

        private static int learnSpell(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation spellId, int level) {
            for (ServerPlayer player : targets) {
                player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                    data.learnSpell(spellId, level);
                    AbstractSpell spell = SpellRegistry.getSpell(spellId);
                    SpellMasteryManager.onSpellLearned(player, spell);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aLearned spell " + spellId + " at lvl " + level + " for " + player.getName().getString()), true);
                });
            }
            return targets.size();
        }
    }

    private static class ForgetSubCommand {
        static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> registerBody() {
            return Commands.argument("spellId", ResourceLocationArgument.id())
                    .executes(ctx -> forgetSpell(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), ResourceLocationArgument.getId(ctx, "spellId")))
                    .then(Commands.argument("target", EntityArgument.players())
                            .executes(ctx -> forgetSpell(ctx, EntityArgument.getPlayers(ctx, "target"), ResourceLocationArgument.getId(ctx, "spellId")))
                    );
        }

        private static int forgetSpell(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, ResourceLocation spellId) {
            for (ServerPlayer player : targets) {
                player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                    data.forgetSpell(spellId);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A76Forgot spell " + spellId + " for " + player.getName().getString()), true);
                });
            }
            return targets.size();
        }
    }

    private static class InfoSubCommand {
        static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> registerBody() {
            return Commands.literal("info")
                    .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target")))
                    );
        }

        private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eSpell Mastery Info for " + player.getName().getString() + ":"), false);
                
                var masteryLevels = data.getAllMasteryLevels();
                if (!masteryLevels.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A76Mastery Levels:"), false);
                    masteryLevels.forEach((schoolId, level) -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("  - " + schoolId + ": \u00A7b" + level), false);
                    });
                }

                Set<ResourceLocation> learned = data.getLearnedSpells();
                if (!learned.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A76Learned Spells:"), false);
                    learned.forEach(spellId -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("  - " + spellId), false);
                    });
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A77No spells learned."), false);
                }
            });
            return 1;
        }
    }
}
