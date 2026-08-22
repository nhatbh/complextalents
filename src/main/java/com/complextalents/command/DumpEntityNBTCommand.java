package com.complextalents.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Command to dump all surrounding entity NBT data into a formatted JSON file.
 *
 * Usage:
 *   /dumpentitynbt [radius] [filename]
 *   /dumpentities [radius] [filename]
 *   /dumpnbt [radius] [filename]
 */
public class DumpEntityNBTCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DumpEntityNBTCommand.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final double DEFAULT_RADIUS = 32.0;
    private static final String DEFAULT_FILENAME = "entity_nbt_dump.json";
    public static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("dumpentitynbt")
                .requires(source -> source.hasPermission(OP_LEVEL))
                .executes(ctx -> executeDump(ctx, DEFAULT_RADIUS, DEFAULT_FILENAME))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 512.0))
                        .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DEFAULT_FILENAME))
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), StringArgumentType.getString(ctx, "filename")))));

        dispatcher.register(command);

        // Alias 1: /dumpentities
        dispatcher.register(Commands.literal("dumpentities")
                .requires(source -> source.hasPermission(OP_LEVEL))
                .executes(ctx -> executeDump(ctx, DEFAULT_RADIUS, DEFAULT_FILENAME))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 512.0))
                        .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DEFAULT_FILENAME))
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), StringArgumentType.getString(ctx, "filename"))))));

        // Alias 2: /dumpnbt
        dispatcher.register(Commands.literal("dumpnbt")
                .requires(source -> source.hasPermission(OP_LEVEL))
                .executes(ctx -> executeDump(ctx, DEFAULT_RADIUS, DEFAULT_FILENAME))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 512.0))
                        .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), DEFAULT_FILENAME))
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(ctx -> executeDump(ctx, DoubleArgumentType.getDouble(ctx, "radius"), StringArgumentType.getString(ctx, "filename"))))));
    }

    private static int executeDump(CommandContext<CommandSourceStack> ctx, double radius, String fileNameInput) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 centerPos = source.getPosition();
        Entity executor = source.getEntity();

        AABB area = new AABB(
                centerPos.x - radius, centerPos.y - radius, centerPos.z - radius,
                centerPos.x + radius, centerPos.y + radius, centerPos.z + radius
        );

        List<Entity> entities = level.getEntities((Entity) null, area, entity -> true);
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(centerPos)));

        JsonArray entitiesArray = new JsonArray();
        int index = 1;

        for (Entity entity : entities) {
            JsonObject entityJson = new JsonObject();
            entityJson.addProperty("index", index++);

            String entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()) != null
                    ? ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString()
                    : entity.getType().toString();

            entityJson.addProperty("entity_type", entityTypeId);
            entityJson.addProperty("uuid", entity.getUUID().toString());
            entityJson.addProperty("display_name", entity.getDisplayName().getString());
            if (entity.hasCustomName() && entity.getCustomName() != null) {
                entityJson.addProperty("custom_name", entity.getCustomName().getString());
            }
            entityJson.addProperty("is_player", entity instanceof Player);
            entityJson.addProperty("is_living", entity instanceof LivingEntity);
            if (executor != null) {
                entityJson.addProperty("is_executor", entity.getUUID().equals(executor.getUUID()));
            }

            JsonObject posJson = new JsonObject();
            posJson.addProperty("x", Math.round(entity.getX() * 100.0) / 100.0);
            posJson.addProperty("y", Math.round(entity.getY() * 100.0) / 100.0);
            posJson.addProperty("z", Math.round(entity.getZ() * 100.0) / 100.0);
            entityJson.add("position", posJson);

            double dist = Math.sqrt(entity.distanceToSqr(centerPos));
            entityJson.addProperty("distance_blocks", Math.round(dist * 100.0) / 100.0);

            // Serialize Entity NBT
            CompoundTag compound = entity.serializeNBT();
            if (!compound.contains("id")) {
                compound.putString("id", entityTypeId);
            }

            entityJson.add("nbt", nbtToJson(compound));
            entityJson.addProperty("snbt", compound.toString());

            entitiesArray.add(entityJson);
        }

        JsonObject root = new JsonObject();
        root.addProperty("dump_timestamp", System.currentTimeMillis());
        root.addProperty("dump_timestamp_formatted", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        JsonObject execPoint = new JsonObject();
        execPoint.addProperty("x", Math.round(centerPos.x * 100.0) / 100.0);
        execPoint.addProperty("y", Math.round(centerPos.y * 100.0) / 100.0);
        execPoint.addProperty("z", Math.round(centerPos.z * 100.0) / 100.0);
        execPoint.addProperty("dimension", level.dimension().location().toString());
        root.add("execution_point", execPoint);

        root.addProperty("search_radius", radius);
        root.addProperty("total_entities_found", entities.size());
        root.add("entities", entitiesArray);

        String fileName = fileNameInput.trim();
        if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }

        File configDir = new File("config/complextalents/dumps");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File dumpsDir = new File("dumps");
        if (!dumpsDir.exists()) {
            dumpsDir.mkdirs();
        }

        File configFile = new File(configDir, fileName);
        File rootFile = new File(dumpsDir, fileName);

        try {
            String jsonString = GSON.toJson(root);
            try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                writer.write(jsonString);
            }
            try (FileWriter writer = new FileWriter(rootFile, StandardCharsets.UTF_8)) {
                writer.write(jsonString);
            }

            String msg = String.format("\u00A7aDumped NBT for \u00A7e%d \u00A7aentities (Radius: \u00A7e%.1f\u00A7a) to \u00A7f%s\u00A7a & \u00A7f%s",
                    entities.size(), radius, configFile.getPath(), rootFile.getPath());
            LOGGER.info("[Entity NBT Dump] " + msg);
            source.sendSuccess(() -> Component.literal(msg), true);
            return entities.size();
        } catch (IOException e) {
            LOGGER.error("Failed to write entity NBT dump JSON", e);
            source.sendFailure(Component.literal("\u00A7cFailed to write entity NBT dump JSON file: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Recursively converts an NBT Tag to a Gson JsonElement.
     */
    public static JsonElement nbtToJson(Tag tag) {
        if (tag == null || tag.getId() == Tag.TAG_END) {
            return JsonNull.INSTANCE;
        }

        if (tag instanceof ByteTag byteTag) {
            return new JsonPrimitive(byteTag.getAsByte());
        } else if (tag instanceof ShortTag shortTag) {
            return new JsonPrimitive(shortTag.getAsShort());
        } else if (tag instanceof IntTag intTag) {
            return new JsonPrimitive(intTag.getAsInt());
        } else if (tag instanceof LongTag longTag) {
            return new JsonPrimitive(longTag.getAsLong());
        } else if (tag instanceof FloatTag floatTag) {
            return new JsonPrimitive(floatTag.getAsFloat());
        } else if (tag instanceof DoubleTag doubleTag) {
            return new JsonPrimitive(doubleTag.getAsDouble());
        } else if (tag instanceof StringTag stringTag) {
            return new JsonPrimitive(stringTag.getAsString());
        } else if (tag instanceof CompoundTag compoundTag) {
            JsonObject jsonObject = new JsonObject();
            for (String key : compoundTag.getAllKeys()) {
                Tag child = compoundTag.get(key);
                if (child != null) {
                    jsonObject.add(key, nbtToJson(child));
                }
            }
            return jsonObject;
        } else if (tag instanceof ListTag listTag) {
            JsonArray jsonArray = new JsonArray();
            for (Tag item : listTag) {
                jsonArray.add(nbtToJson(item));
            }
            return jsonArray;
        } else if (tag instanceof ByteArrayTag byteArrayTag) {
            JsonArray jsonArray = new JsonArray();
            for (byte b : byteArrayTag.getAsByteArray()) {
                jsonArray.add(b);
            }
            return jsonArray;
        } else if (tag instanceof IntArrayTag intArrayTag) {
            JsonArray jsonArray = new JsonArray();
            for (int i : intArrayTag.getAsIntArray()) {
                jsonArray.add(i);
            }
            return jsonArray;
        } else if (tag instanceof LongArrayTag longArrayTag) {
            JsonArray jsonArray = new JsonArray();
            for (long l : longArrayTag.getAsLongArray()) {
                jsonArray.add(l);
            }
            return jsonArray;
        }

        return new JsonPrimitive(tag.getAsString());
    }
}
