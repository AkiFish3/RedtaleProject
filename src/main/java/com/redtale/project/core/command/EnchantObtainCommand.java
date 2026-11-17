package com.redtale.project.core.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * {@code /redtale enchant obtain} —— 三条原版可获取性 tag 的只读对账（M4 / V4 观测面）。
 *
 * <p>读**生成结果**两份产物对比：已落盘附魔 vs {@code data/minecraft/tags/enchantment/*.json}，
 * 逐 tag 打印「已收录 X/Y + 差集」。只读：不写盘、不修 tag、不打日志。
 */
public final class EnchantObtainCommand {

    /** 数据包内附魔注册表目录（单数，原版固定）。 */
    private static final String ENCHANT_DIR = "enchantment";
    /** 三条原版可获取性 tag（与 {@code EnchantObtainTags} 写死口径一致，不从配置读）。 */
    private static final String[] TAGS = {"in_enchanting_table", "tradeable", "on_random_loot"};
    /** 对齐宽度 = 最长 tag 名（{@code in_enchanting_table} 19 字符）。 */
    private static final String PAD = "  %-19s  ";

    private EnchantObtainCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("enchant")
                .then(Commands.literal("obtain").executes(EnchantObtainCommand::obtain));
    }

    public static int obtain(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("redtale_project");
        Set<String> enchants = collectEnchantIds(root);
        int total = enchants.size();
        List<String> lines = new ArrayList<>();
        lines.add("[redtale] 附魔可获取性对账（已生成 " + total + " 条附魔）：");
        Set<String> missingAll = new TreeSet<>();
        for (String tag : TAGS) {
            List<String> values = readTagValues(root, tag);
            if (values == null) {
                lines.add(String.format(PAD, tag) + "未生成");
                missingAll.addAll(enchants);
                continue;
            }
            Set<String> have = new LinkedHashSet<>(values);
            Set<String> miss = new TreeSet<>();
            for (String id : enchants) {
                if (!have.contains(id)) {
                    miss.add(id);
                }
            }
            if (miss.isEmpty()) {
                lines.add(String.format(PAD, tag) + "已收录 " + total + "/" + total + "  ✓");
            } else {
                missingAll.addAll(miss);
                lines.add(String.format(PAD, tag) + "已收录 " + (total - miss.size()) + "/" + total
                        + "  缺：" + String.join(", ", miss));
            }
        }
        if (total == 0) {
            lines.add("[redtale] 还没有任何已生成的附魔（先 /redtale reload 生成数据包）");
        } else if (missingAll.isEmpty()) {
            lines.add("[redtale] 三条全齐 ✓");
        } else {
            lines.add("[redtale] 有 " + missingAll.size() + " 条附魔拿不到（缺 tag 见上）");
        }
        for (String line : lines) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return total;
    }

    /** 扫 {@code <root>/data/<ns>/enchantment/**}{@code .json}，id = {@code <ns>:<相对路径>}（{@code /} 连接、升序不可变）。 */
    static Set<String> collectEnchantIds(Path datapackRoot) {
        Set<String> ids = new TreeSet<>();
        if (datapackRoot == null) {
            return Collections.unmodifiableSet(ids);
        }
        Path dataRoot = datapackRoot.resolve("data");
        if (!Files.isDirectory(dataRoot)) {
            return Collections.unmodifiableSet(ids);
        }
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path nsDir : namespaces.toList()) {
                Path enchantDir = nsDir.resolve(ENCHANT_DIR);
                if (!Files.isDirectory(enchantDir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(enchantDir)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        String rel = enchantDir.relativize(file).toString().replace('\\', '/');
                        if (rel.endsWith(".json")) {
                            ids.add(nsDir.getFileName() + ":" + rel.substring(0, rel.length() - 5));
                        }
                    }
                }
            }
        } catch (IOException e) {
            return Collections.unmodifiableSet(ids);
        }
        return Collections.unmodifiableSet(ids);
    }

    /**
     * 读 {@code <root>/data/minecraft/tags/enchantment/<tag>.json} 的 {@code values}（原序、只收字符串项）。
     *
     * @return {@code null} = 未生成或坏文件（与「生成但 values 为空」的空列表区分开）
     */
    static List<String> readTagValues(Path datapackRoot, String tag) {
        if (datapackRoot == null || tag == null) {
            return null;
        }
        Path file = datapackRoot.resolve("data").resolve("minecraft").resolve("tags")
                .resolve("enchantment").resolve(tag + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonElement values = root.isJsonObject() ? root.getAsJsonObject().get("values") : null;
            if (values == null || !values.isJsonArray()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (JsonElement item : values.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    out.add(item.getAsString());
                }
            }
            return List.copyOf(out);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
