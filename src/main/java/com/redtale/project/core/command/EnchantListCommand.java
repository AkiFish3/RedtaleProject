package com.redtale.project.core.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code /redtale enchant list} —— 已落盘附魔的只读清单（M4 运营可见性）。读**生成结果**而非配置，
 * 用来发现「配置改了但数据包没重新生成」；不写盘、不打日志、不碰附魔注册表。行尾「脚本不存在」= L3 断链提示。
 */
public final class EnchantListCommand {

    /** 数据包内附魔注册表目录（单数，原版固定）。 */
    private static final String ENCHANT_DIR = "enchantment";
    /** L2 脚本化 effect 的类型 id。 */
    private static final String SCRIPTED = "redtale_project:scripted";

    private EnchantListCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("enchant")
                .then(Commands.literal("list").executes(EnchantListCommand::list));
    }

    static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            src.sendFailure(Component.literal("当前没有服务端实例"));
            return 0;
        }
        Path dataRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("redtale_project").resolve("data");
        Map<String, JsonObject> found = new TreeMap<>();
        try {
            collect(dataRoot, found);
        } catch (IOException e) {
            src.sendFailure(Component.literal("读取数据包失败：" + e.getMessage()));
            return 0;
        }
        if (found.isEmpty()) {
            src.sendSuccess(() -> Component.literal("已生成附魔 0 条（数据包 redtale_project 未生成）"), false);
            return 0;
        }

        List<String> lines = new ArrayList<>();
        String scriptText = null;
        for (Map.Entry<String, JsonObject> entry : found.entrySet()) {
            Set<String> scripts = new TreeSet<>();
            collectScripts(entry.getValue().get("effects"), scripts);
            String suffix = "";
            if (!scripts.isEmpty()) {
                if (scriptText == null) {
                    scriptText = loadScripts();
                }
                for (String script : scripts) {
                    if (!hasScript(scriptText, script)) {
                        suffix = " · 脚本不存在";
                        break;
                    }
                }
            }
            lines.add("  " + entry.getKey() + " · " + level(entry.getValue())
                    + " · " + items(entry.getValue()) + " · " + triggers(entry.getValue()) + suffix);
        }
        int count = found.size();
        src.sendSuccess(() -> Component.literal("已生成附魔 " + count + " 条"), false);
        for (String line : lines) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return count;
    }

    /** 扫 {@code data/<ns>/enchantment/**}{@code .json}，键为 {@code ns:相对路径}（升序靠 TreeMap）。 */
    private static void collect(Path dataRoot, Map<String, JsonObject> out) throws IOException {
        if (!Files.isDirectory(dataRoot)) {
            return;
        }
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(dataRoot)) {
            for (Path nsDir : namespaces) {
                Path enchantDir = nsDir.resolve(ENCHANT_DIR);
                if (!Files.isDirectory(enchantDir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(enchantDir)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        String rel = enchantDir.relativize(file).toString().replace('\\', '/');
                        if (!rel.endsWith(".json")) {
                            continue;
                        }
                        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                        if (root.isJsonObject()) {
                            out.put(nsDir.getFileName() + ":" + rel.substring(0, rel.length() - 5), root.getAsJsonObject());
                        }
                    }
                }
            }
        }
    }

    /** {@code max_level} 缺失或非数字 → {@code L?}。 */
    private static String level(JsonObject json) {
        JsonElement el = json.get("max_level");
        boolean isNumber = el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        return isNumber ? "L" + el.getAsInt() : "L?";
    }

    /** {@code supported_items} 原样（字符串去引号，其余取 JSON 文本），缺失 → {@code -}。 */
    private static String items(JsonObject json) {
        JsonElement el = json.get("supported_items");
        if (el == null || el.isJsonNull()) {
            return "-";
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        return el.toString();
    }

    /** {@code effects} 的键（触发组件名）排序后逗号连接；缺失或空对象 → {@code 无}。 */
    private static String triggers(JsonObject json) {
        JsonElement el = json.get("effects");
        if (el == null || !el.isJsonObject() || el.getAsJsonObject().isEmpty()) {
            return "无";
        }
        return String.join(",", new TreeSet<>(el.getAsJsonObject().keySet()));
    }

    /** 递归收集所有 {@code {"type": "redtale_project:scripted", "script": "..."}} 的 script 值。 */
    private static void collectScripts(JsonElement el, Set<String> out) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectScripts(child, out);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject obj = el.getAsJsonObject();
        JsonElement type = obj.get("type");
        JsonElement script = obj.get("script");
        if (type != null && type.isJsonPrimitive() && SCRIPTED.equals(type.getAsString())
                && script != null && script.isJsonPrimitive() && script.getAsJsonPrimitive().isString()) {
            out.add(script.getAsString());
        }
        for (Map.Entry<String, JsonElement> child : obj.entrySet()) {
            collectScripts(child.getValue(), out);
        }
    }

    /** 把 {@code config/redtale_project/scripts/*.json5} 全文拼成一段，供子串查找；失败当空。 */
    private static String loadScripts() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("redtale_project").resolve("scripts");
        if (!Files.isDirectory(dir)) {
            return "";
        }
        StringBuilder all = new StringBuilder();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json5")) {
            for (Path file : files) {
                all.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            return "";
        }
        return all.toString();
    }

    /** {@code id: "x"} / {@code "id": 'x'} 都算命中（默认配置用的是单引号；键也可能被引号包住）。 */
    private static boolean hasScript(String text, String script) {
        String pattern = "(?<![A-Za-z0-9_])[\"']?id[\"']?\\s*:\\s*[\"']" + Pattern.quote(script) + "[\"']";
        return Pattern.compile(pattern).matcher(text).find();
    }
}
