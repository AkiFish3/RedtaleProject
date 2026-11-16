package com.redtale.project.core.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.config.Json5;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code /redtale content check} —— D33 发布纪律的只读检查：内容改了但 {@code rev} 没 +1。
 *
 * <p>漏加 rev 不报错、不崩、不刷日志，只是存量物品静默不更新。这里拿「文件字节的 SHA-256 前 16 位」
 * 与上次基线（{@code config/redtale_project/.rev-baseline.json}，跨重启保留）逐份比对，只读，不改配置。
 * 只有 {@code items/} 前缀参与判定，其余文件没有 rev 概念、只更新基线。扫描行用 {@link JsonObject} 当载体
 * （不用 record）：内部类型会多生成 {@code $Entry.class}，让「jar 内类数 = 1」的验收计数失真。
 */
public final class ContentCheckCommand {
    public static final String BASELINE_FILE = ".rev-baseline.json";
    private static final String ITEMS = "items/";
    private ContentCheckCommand() {
    }

    public static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            Path root = RedtaleCore.get().config().root();
            Path baselinePath = root.resolve(BASELINE_FILE);
            List<JsonObject> rows = scan(root);
            Map<String, JsonObject> baseline = readBaseline(baselinePath);
            JsonObject verdict = baseline == null ? null : judge(rows, baseline);
            JsonArray problems = verdict == null ? null : verdict.getAsJsonArray("lines");
            final int items = (int) rows.stream().filter(r -> text(r, "rel").startsWith(ITEMS)).count();
            final int built = (int) rows.stream().filter(r -> !r.has("error")).count();
            final int missed = verdict == null ? 0 : verdict.get("missed").getAsInt();
            src.sendSuccess(() -> Component.literal("内容检查：" + rows.size() + " 份 json5（items "
                    + items + " 份）· rev 漏加 " + missed + " 份"), false);
            for (JsonObject row : rows) {
                if (!row.has("error")) {
                    continue;
                }
                String line = "  " + text(row, "rel") + ": 解析失败（" + text(row, "error") + "），跳过";
                src.sendSuccess(() -> Component.literal(line), false);
            }
            if (verdict == null) {
                src.sendSuccess(() -> Component.literal("  已建立基线 " + built
                        + " 份（下次起参与比对）"), false);
            } else if (problems.isEmpty()) {
                src.sendSuccess(() -> Component.literal("  （没有 rev 漏加）"), false);
            } else {
                for (JsonElement line : problems) {
                    String shown = line.getAsString();
                    src.sendSuccess(() -> Component.literal(shown), false);
                }
            }
            // 先算完、判定完、打印完，最后才写盘：中途抛异常不能把基线改脏
            write(baselinePath, rows);
            String basePath = baselinePath.toAbsolutePath().toString();
            src.sendSuccess(() -> Component.literal("  基线已更新：" + basePath), false);
            return missed;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 内容检查失败：" + e));
            return 0;
        }
    }
    /** 扫全部 {@code *.json5}（跳过 {@code .} 开头），逐份算哈希与 rev；行键 rel / hash / rev，解析失败另带 error。 */
    private static List<JsonObject> scan(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json5"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        List<JsonObject> rows = new ArrayList<>(paths.size());
        for (Path path : paths) {
            JsonObject row = new JsonObject();
            row.addProperty("rel", root.relativize(path).toString().replace('\\', '/'));
            byte[] bytes = Files.readAllBytes(path);
            row.addProperty("hash", sha16(bytes));
            int rev = 0;
            try {
                JsonElement parsed = Json5.parse(new String(bytes, StandardCharsets.UTF_8));
                JsonElement raw = parsed.isJsonObject() ? parsed.getAsJsonObject().get("rev") : null;
                // rev 缺省 0、非数字 0、负数按 0 —— 与 ItemContent.parse 同口径
                if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                    rev = Math.max(0, (int) raw.getAsDouble());
                }
            } catch (RuntimeException e) {
                row.addProperty("error", e.getClass().getSimpleName());
            }
            row.addProperty("rev", rev);
            rows.add(row);
        }
        return rows;
    }
    private static String sha16(byte[] bytes) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(bytes);
            return String.format("%064x", new java.math.BigInteger(1, sum)).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
    private static String text(JsonObject row, String key) {
        JsonElement element = row.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
    /** 纯判定：本次扫描 × 基线 → 问题行 + 漏加份数；只判 items/ 且本次解析成功的行。 */
    private static JsonObject judge(List<JsonObject> rows, Map<String, JsonObject> baseline) {
        JsonArray lines = new JsonArray();
        int missed = 0;
        for (JsonObject row : rows) {
            String rel = text(row, "rel");
            if (!rel.startsWith(ITEMS) || row.has("error")) {
                continue;
            }
            JsonObject old = baseline.get(rel);
            // 基线没有 = 新文件（首次出现没有「上次」）；哈希相同 = 没改内容，rev 变没变都不管
            if (old == null || text(old, "hash").equals(text(row, "hash"))) {
                continue;
            }
            int before = old.get("rev").getAsInt();
            int now = row.get("rev").getAsInt();
            if (now < before) {
                lines.add("  " + rel + ": rev 倒退（基线 " + before + " → 当前 " + now + "）");
            } else if (now == before) {
                lines.add("  " + rel + ": 内容已改但 rev 没加（基线 " + before + " → 当前 " + now + "）");
                missed++;
            }
        }
        JsonObject verdict = new JsonObject();
        verdict.add("lines", lines);
        verdict.addProperty("missed", missed);
        return verdict;
    }
    /** 读基线；文件不存在 → {@code null}（= 首次）。每项键 hash / rev。 */
    private static Map<String, JsonObject> readBaseline(Path path) throws IOException {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        JsonElement parsed = Json5.parse(Files.readString(path, StandardCharsets.UTF_8));
        JsonElement files = parsed.isJsonObject() ? parsed.getAsJsonObject().get("files") : null;
        if (files == null || !files.isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : files.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            JsonElement hash = value.isJsonObject() ? value.getAsJsonObject().get("hash") : null;
            if (hash == null || !hash.isJsonPrimitive() || !hash.getAsJsonPrimitive().isString()) {
                continue;
            }
            JsonElement rev = value.getAsJsonObject().get("rev");
            boolean hasRev = rev != null && rev.isJsonPrimitive() && rev.getAsJsonPrimitive().isNumber();
            JsonObject row = new JsonObject();
            row.addProperty("hash", hash.getAsString());
            row.addProperty("rev", hasRev ? Math.max(0, rev.getAsInt()) : 0);
            out.put(entry.getKey(), row);
        }
        return out;
    }
    /** 整份覆写基线（解析失败的文件不进基线），gson pretty + 末尾换行。 */
    private static void write(Path path, List<JsonObject> rows) throws IOException {
        JsonObject files = new JsonObject();
        for (JsonObject row : rows) {
            if (row.has("error")) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("hash", text(row, "hash"));
            entry.addProperty("rev", row.get("rev").getAsInt());
            files.add(text(row, "rel"), entry);
        }
        JsonObject root = new JsonObject();
        root.add("files", files);
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
