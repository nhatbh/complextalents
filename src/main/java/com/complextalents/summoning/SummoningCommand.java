package com.complextalents.summoning;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Command for managing and inspecting active summoned entities.
 *
 * Usage:
 *   /summoning list
 *   /summoning inspect
 *   /summoning dismiss
 */
public class SummoningCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("summoning")
                .requires(source -> source.hasPermission(0)) // Available to all players
                .then(Commands.literal("list")
                        .executes(SummoningCommand::listSummons))
                .then(Commands.literal("inspect")
                        .executes(SummoningCommand::inspectTarget))
                .then(Commands.literal("dismiss")
                        .executes(SummoningCommand::dismissSummons));

        dispatcher.register(command);
    }

    private static int listSummons(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            List<LivingEntity> summons = SummoningManager.getSummons(player);

            if (summons.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eYou currently have no active summoned entities."), false);
                return 0;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("\u00A76=== Active Summons (%d) ===\u00A7r\n", summons.size()));
            int idx = 1;
            for (LivingEntity summon : summons) {
                String typeId = ForgeRegistries.ENTITY_TYPES.getKey(summon.getType()) != null
                        ? ForgeRegistries.ENTITY_TYPES.getKey(summon.getType()).toString()
                        : summon.getType().toString();
                double dist = Math.sqrt(summon.distanceToSqr(player));
                sb.append(String.format(" \u00A7e%d. \u00A7a%s \u00A77[\u00A7f%s\u00A77] - \u00A7cHP: %.1f/%.1f \u00A77(\u00A7b%.1fm away\u00A77)\n",
                        idx++, summon.getDisplayName().getString(), typeId, summon.getHealth(), summon.getMaxHealth(), dist));
            }

            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString().trim()), false);
            return summons.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to list summons: " + e.getMessage()));
            return 0;
        }
    }

    private static int inspectTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            CommandSourceStack source = ctx.getSource();
            Vec3 pos = source.getPosition();
            double radius = 10.0;
            AABB searchArea = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);

            List<LivingEntity> nearby = source.getLevel().getEntitiesOfClass(LivingEntity.class, searchArea, e -> e.isAlive());
            Entity executor = source.getEntity();
            if (executor != null) nearby.remove(executor);

            if (nearby.isEmpty()) {
                source.sendFailure(Component.literal("\u00A7cNo nearby entities found within 10 blocks to inspect."));
                return 0;
            }

            nearby.sort((e1, e2) -> Double.compare(e1.distanceToSqr(pos), e2.distanceToSqr(pos)));
            LivingEntity target = nearby.get(0);

            Entity owner = SummoningManager.getOwner(target);
            String ownerName = (owner != null) ? owner.getDisplayName().getString() : "\u00A7cNone (Unowned / Hostile)";
            String typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()) != null
                    ? ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString()
                    : target.getType().toString();

            String info = String.format(
                    "\u00A76[Summon Inspector]\u00A7r\n" +
                    "  \u00A7fTarget: \u00A7e%s \u00A77(%s)\n" +
                    "  \u00A7fOwner: \u00A7a%s\n" +
                    "  \u00A7fIs Summon: \u00A7b%s\n" +
                    "  \u00A7fHealth: \u00A7c%.1f / %.1f",
                    target.getDisplayName().getString(), typeId,
                    ownerName,
                    (owner != null) ? "YES" : "NO",
                    target.getHealth(), target.getMaxHealth()
            );

            source.sendSuccess(() -> Component.literal(info), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to inspect entity: " + e.getMessage()));
            return 0;
        }
    }

    private static int dismissSummons(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int count = SummoningManager.dismissAllSummons(player);

            if (count > 0) {
                ctx.getSource().sendSuccess(() -> Component.literal(String.format("\u00A7aDismissed \u00A7e%d \u00A7aactive summoned entities.", count)), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eYou have no active summons to dismiss."), false);
            }
            return count;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to dismiss summons: " + e.getMessage()));
            return 0;
        }
    }
}
