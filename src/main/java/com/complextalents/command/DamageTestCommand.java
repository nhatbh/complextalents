package com.complextalents.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Collection;
import java.util.Collections;

/**
 * Command to deal a specific amount of raw damage to a player and display
 * the exact damage received (after armor, absorption, and mitigation calculations).
 * 
 * Usage:
 *   /dealdamage <amount> [target]
 *   /testdamage <amount> [target]
 */
public class DamageTestCommand {

    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("dealdamage")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.1f, 100000.0f))
                        .executes(ctx -> dealDamage(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), FloatArgumentType.getFloat(ctx, "amount")))
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(ctx -> dealDamage(ctx, EntityArgument.getPlayers(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount")))));

        dispatcher.register(command);
        
        // Alias: /testdamage
        dispatcher.register(Commands.literal("testdamage")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.1f, 100000.0f))
                        .executes(ctx -> dealDamage(ctx, Collections.singleton(ctx.getSource().getPlayerOrException()), FloatArgumentType.getFloat(ctx, "amount")))
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(ctx -> dealDamage(ctx, EntityArgument.getPlayers(ctx, "target"), FloatArgumentType.getFloat(ctx, "amount"))))));
    }

    private static int dealDamage(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, float rawAmount) {
        for (ServerPlayer target : targets) {
            float oldHealth = target.getHealth();
            float oldAbsorption = target.getAbsorptionAmount();
            double armorValue = target.getArmorValue();
            double toughnessValue = target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);

            // Deal generic damage
            DamageSource source = target.damageSources().generic();
            boolean hitSuccess = target.hurt(source, rawAmount);

            float newHealth = target.getHealth();
            float newAbsorption = target.getAbsorptionAmount();

            float totalDamageReceived = (oldHealth + oldAbsorption) - (newHealth + newAbsorption);
            if (totalDamageReceived < 0) totalDamageReceived = 0;

            float mitigatedAmount = rawAmount - totalDamageReceived;
            double mitigationPercent = rawAmount > 0 ? (mitigatedAmount / rawAmount) * 100.0 : 0.0;

            String message = String.format(
                    "§6[Damage Test] §fTarget: §e%s§f | §cRaw Damage: §f%.1f\n" +
                    "  §7- §aArmor: §f%.1f §7| §aToughness: §f%.1f\n" +
                    "  §7- §cActual Damage Received: §e%.2f §7(HP: %.1f → %.1f, Abs: %.1f → %.1f)\n" +
                    "  §7- §bDamage Mitigated: §f%.2f §7(§e%.1f%% Reduction§7) §7[Hit: %s]",
                    target.getName().getString(),
                    rawAmount,
                    armorValue,
                    toughnessValue,
                    totalDamageReceived,
                    oldHealth, newHealth,
                    oldAbsorption, newAbsorption,
                    mitigatedAmount,
                    mitigationPercent,
                    hitSuccess ? "§aSUCCESS§7" : "§cBLOCKED/IMMUNE§7"
            );

            ctx.getSource().sendSuccess(() -> Component.literal(message), true);
            if (!ctx.getSource().getTextName().equals(target.getScoreboardName())) {
                target.sendSystemMessage(Component.literal(message));
            }
        }
        return targets.size();
    }
}
