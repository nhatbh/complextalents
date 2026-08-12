package com.complextalents.tacz;

import com.complextalents.gunmastery.command.GunMasteryCommand;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Command to dump all TacZ base guns and TacZ addon gunpack guns into a formatted JSON file.
 *
 * Registered commands:
 * - /taczdump
 * - /gunmastery dumpguns
 */
public class TacZGunDumpCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(TacZGunDumpCommand.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("taczdump")
                .requires(src -> src.hasPermission(GunMasteryCommand.OP_LEVEL))
                .executes(TacZGunDumpCommand::executeDump)
        );
    }

    public static int executeDump(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        Set<Map.Entry<ResourceLocation, CommonGunIndex>> gunEntries = TimelessAPI.getAllCommonGunIndex();
        if (gunEntries == null || gunEntries.isEmpty()) {
            source.sendFailure(Component.literal("\u00A7cNo TacZ guns found in registry. Ensure TimelessAPI is initialized."));
            return 0;
        }

        JsonArray gunsArray = new JsonArray();
        int totalGuns = 0;

        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : gunEntries) {
            ResourceLocation gunId = entry.getKey();
            CommonGunIndex index = entry.getValue();

            if (gunId == null || index == null) continue;

            JsonObject gunJson = new JsonObject();
            gunJson.addProperty("id", gunId.toString());
            gunJson.addProperty("namespace", gunId.getNamespace());
            gunJson.addProperty("path", gunId.getPath());

            // Raw Type & ComplexTalents Archetype mapping
            String rawType = index.getType();
            gunJson.addProperty("tacz_raw_type", rawType != null ? rawType : "unknown");
            GunType archetype = GunType.fromId(rawType);
            gunJson.addProperty("archetype", archetype.name());
            gunJson.addProperty("archetype_display", archetype.getDisplayName());

            // Dynamically dump all getter properties from CommonGunIndex
            JsonObject indexDetails = inspectObject(index);
            gunJson.add("index_details", indexDetails);

            // Gun Data
            GunData gunData = index.getGunData();
            if (gunData != null) {
                JsonObject gunDataJson = inspectObject(gunData);

                // Bullet Data
                BulletData bulletData = gunData.getBulletData();
                if (bulletData != null) {
                    JsonObject bulletJson = inspectObject(bulletData);
                    gunDataJson.add("bullet_data", bulletJson);
                }

                gunJson.add("gun_data", gunDataJson);
            }

            gunsArray.add(gunJson);
            totalGuns++;
        }

        JsonObject root = new JsonObject();
        root.addProperty("total_guns", totalGuns);
        root.addProperty("dump_timestamp", System.currentTimeMillis());
        root.add("guns", gunsArray);

        // Output destination files
        File outputDir = new File("config/complextalents");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File configFile = new File(outputDir, "tacz_guns_dump.json");
        File rootFile = new File("tacz_guns_dump.json");

        try {
            String jsonStr = GSON.toJson(root);
            try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                writer.write(jsonStr);
            }
            try (FileWriter writer = new FileWriter(rootFile, StandardCharsets.UTF_8)) {
                writer.write(jsonStr);
            }

            String msg = "\u00A7aDumped \u00A7e" + totalGuns + " \u00A7aTacZ guns (Base + Addons) to \u00A7f" + configFile.getPath() + "\u00A7a & \u00A7f" + rootFile.getName();
            LOGGER.info("[TacZ Dump] " + msg);
            source.sendSuccess(() -> Component.literal(msg), true);
            return totalGuns;
        } catch (IOException e) {
            LOGGER.error("Failed to write TacZ gun dump JSON", e);
            source.sendFailure(Component.literal("\u00A7cFailed to write JSON dump file: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Uses reflection to dynamically serialize any object's public getters into a JsonObject.
     * Handles primitives, Strings, Enums, ResourceLocations, Collections, and nested POJOs.
     */
    private static JsonObject inspectObject(Object obj) {
        JsonObject json = new JsonObject();
        if (obj == null) return json;

        Class<?> clazz = obj.getClass();
        Map<String, Method> getters = new TreeMap<>();

        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() != 0) continue;
            if (method.getDeclaringClass() == Object.class) continue;

            String name = method.getName();
            if ((name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2)) {
                getters.put(name, method);
            }
        }

        for (Map.Entry<String, Method> entry : getters.entrySet()) {
            String methodName = entry.getKey();
            Method method = entry.getValue();

            // Convert camelCase getter name to snake_case property key
            String propName;
            if (methodName.startsWith("get")) {
                propName = toSnakeCase(methodName.substring(3));
            } else {
                propName = toSnakeCase(methodName.substring(2));
            }

            try {
                Object val = method.invoke(obj);
                JsonElement elem = toJsonElement(val);
                if (elem != null) {
                    json.add(propName, elem);
                }
            } catch (Exception ignored) {
                // Ignore getter invocation failures
            }
        }

        return json;
    }

    private static JsonElement toJsonElement(Object val) {
        if (val == null) return null;

        if (val instanceof Number num) {
            return new JsonPrimitive(num);
        } else if (val instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        } else if (val instanceof String str) {
            return new JsonPrimitive(str);
        } else if (val instanceof Enum<?> en) {
            return new JsonPrimitive(en.name());
        } else if (val instanceof ResourceLocation rl) {
            return new JsonPrimitive(rl.toString());
        } else if (val instanceof Collection<?> col) {
            JsonArray arr = new JsonArray();
            for (Object item : col) {
                JsonElement sub = toJsonElement(item);
                if (sub != null) arr.add(sub);
            }
            return arr;
        } else if (val instanceof Map<?, ?> map) {
            JsonObject mapObj = new JsonObject();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    JsonElement sub = toJsonElement(e.getValue());
                    if (sub != null) mapObj.add(e.getKey().toString(), sub);
                }
            }
            return mapObj;
        } else {
            // Complex object POJO
            String className = val.getClass().getName();
            if (className.startsWith("java.") || className.startsWith("javax.")) {
                return new JsonPrimitive(val.toString());
            }
            return inspectObject(val);
        }
    }

    private static String toSnakeCase(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
