package com.complextalents.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

/**
 * Command for spawning moving dummy players for testing player-targeted skills.
 *
 * Usage:
 * /dummy [name] - Spawn a moving dummy player
 * /dummy spawn [name] - Spawn a moving dummy player
 * /dummy clear - Remove nearby dummy players
 */
public class DummyPlayerCommand {

    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dummy")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .executes(ctx -> spawnDummy(ctx.getSource(), "DummyPlayer"))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawnDummy(ctx.getSource(), "DummyPlayer"))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> spawnDummy(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")
                                ))
                        )
                )
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> spawnDummy(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")
                        ))
                )
                .then(Commands.literal("clear")
                        .executes(ctx -> clearDummies(ctx.getSource()))
                )
        );
    }

    private static int spawnDummy(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ServerLevel serverLevel = player.serverLevel();
        Vec3 spawnPos = player.position().add(player.getLookAngle().scale(2.0));
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);

        MovingDummyPlayer fakePlayer = new MovingDummyPlayer(serverLevel, profile);
        fakePlayer.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        fakePlayer.setYRot(player.getYRot() + 180.0f);
        fakePlayer.setXRot(0.0f);
        fakePlayer.setHealth(20.0f);

        serverLevel.addFreshEntity(fakePlayer);
        src.sendSuccess(() -> Component.literal("\u00A7aSpawned moving dummy player '" + name + "' at " +
                String.format("%.1f, %.1f, %.1f", spawnPos.x, spawnPos.y, spawnPos.z)), true);

        return 1;
    }

    private static int clearDummies(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ServerLevel serverLevel = player.serverLevel();
        var dummies = serverLevel.getEntitiesOfClass(
                FakePlayer.class,
                player.getBoundingBox().inflate(32.0));

        int count = dummies.size();
        for (var dummy : dummies) {
            dummy.discard();
        }

        src.sendSuccess(() -> Component.literal("\u00A7eRemoved " + count + " dummy player(s) nearby."), true);
        return 1;
    }

    public static class MovingDummyPlayer extends FakePlayer {
        private int moveTimer = 0;
        private Vec3 currentDirection = Vec3.ZERO;

        public MovingDummyPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void tick() {
            super.tick();

            if (!level().isClientSide) {
                if (moveTimer <= 0 || getRandom().nextInt(40) == 0) {
                    moveTimer = 30 + getRandom().nextInt(50);
                    double angle = getRandom().nextDouble() * Math.PI * 2;
                    currentDirection = new Vec3(Math.cos(angle) * 0.12, 0, Math.sin(angle) * 0.12);
                    setYRot((float) Math.toDegrees(Math.atan2(-currentDirection.x, currentDirection.z)));
                }

                moveTimer--;
                setDeltaMovement(currentDirection.x, getDeltaMovement().y, currentDirection.z);
                move(MoverType.SELF, getDeltaMovement());
            }
        }
    }
}
