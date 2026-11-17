package com.redtale.project.mobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 掉落表解析器（M6 第四层，纯解析）：只读 drops/*.json5 的 xp 与条目，不生成战利品表、不碰任何运行时状态。 */
public final class DropSpec {
    /** 一条掉落项：{@code min}/{@code max} 是件数区间，{@code chance} 是单次命中概率，{@code rolls} 是掷几次。 */
    public record Entry(String item, int min, int max, double chance, int rolls) {}
    /** 一张掉落表：{@code xp} 是经验白值（不乘任何属性），{@code drops} 不可变。 */
    public record Definition(String id, int xp, List<Entry> drops, String source) {}
    public static final String DIR = "drops/";
    public static final String SUFFIX = ".json5";
    private static final int MAX_XP = 1_000_000, MAX_COUNT = 64, MAX_ROLLS = 16;
    private DropSpec() {}

    /** 解析全部掉落表；非 drops/*.json5 静默跳过；保序 + 不可变；null → 空表。 */
    public static Map<String, Definition> parse(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        List<String> w = warnings != null ? warnings : new ArrayList<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        Map<String, String> seen = new LinkedHashMap<>();
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith(DIR) || !path.endsWith(SUFFIX)) continue;
            JsonObject root = file.root();
            if (root == null) { w.add(path + ": 根节点不是对象，已跳过"); continue; }
            String id = id(root, path, w);
            if (id == null) continue;
            String prev = seen.putIfAbsent(id, path);
            if (prev != null) {
                w.add(path + ": 掉落表 id 重复（" + id + "，先出现在 " + prev + "），保留先出现的定义，本条已跳过");
                continue;
            }
            out.put(id, new Definition(id, xp(root, path, w), drops(root, path, w), path));
        }
        return Collections.unmodifiableMap(out);
    }
    /** id 必填：非字符串 / trim 后为空 → 整条跳过 + 1 条 WARN；不补命名空间。 */
    private static String id(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("id");
        if (!str(e) || e.getAsString().trim().isEmpty()) {
            w.add(path + ": id 缺失或不是字符串（必填），已跳过这张掉落表");
            return null;
        }
        return e.getAsString().trim();
    }
    /** xp 缺省 0；非整数 / 负数 → 0 + WARN；超上限静默夹取（D16）。 */
    private static int xp(JsonObject root, String path, List<String> w) {
        int value = integer(root.get("xp"), 0, path + ": xp", w);
        if (value < 0) { w.add(path + ": xp 为负数，已用 0"); value = 0; }
        return Math.min(MAX_XP, value);
    }
    /** drops 缺键 / 不是数组 → 空表 + 1 条 WARN；数组内非对象项逐项 WARN 后跳过。 */
    private static List<Entry> drops(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("drops");
        if (e == null) { w.add(path + ": drops 缺失，按空表处理"); return List.of(); }
        if (!e.isJsonArray()) { w.add(path + ": drops 不是数组，按空表处理"); return List.of(); }
        List<Entry> out = new ArrayList<>();
        int i = 0;
        for (JsonElement el : e.getAsJsonArray()) {
            String at = path + ": drops[" + i++ + "]";
            if (el == null || !el.isJsonObject()) { w.add(at + " 不是对象，已跳过该项"); continue; }
            JsonObject item = el.getAsJsonObject();
            Entry entry = entry(item, at, w);
            if (entry != null) out.add(entry);
        }
        return List.copyOf(out);
    }
    /** item 必填且只校验 id 形态（存在性归上层命令）；坏值 → 跳过该项 + WARN（含原始文本）。 */
    private static Entry entry(JsonObject item, String at, List<String> w) {
        JsonElement e = item.get("item");
        if (!str(e) || ResourceLocation.tryParse(e.getAsString().trim()) == null) {
            w.add(at + ".item 缺失或不是合法 id（" + (e == null ? "null" : e.toString()) + "），已跳过该项");
            return null;
        }
        int min = Math.max(0, Math.min(MAX_COUNT, integer(item.get("min"), 1, at + ".min", w)));
        int max = item.has("max")
                ? Math.max(0, Math.min(MAX_COUNT, integer(item.get("max"), min, at + ".max", w))) : min;
        if (max < min) { w.add(at + " 的 max < min，已把 max 提到 " + min); max = min; }
        return new Entry(e.getAsString().trim(), min, max, chance(item, at, w),
                Math.max(1, Math.min(MAX_ROLLS, integer(item.get("rolls"), 1, at + ".rolls", w))));
    }
    /** chance 缺省 1.0；非数字 / 非有限 → 1.0 + WARN；夹取不告警（D16）。 */
    private static double chance(JsonObject item, String at, List<String> w) {
        JsonElement e = item.get("chance");
        if (e == null) return 1.0D;
        if (!num(e) || !Double.isFinite(e.getAsDouble())) {
            w.add(at + ".chance 不是有效数字，已用 1.0");
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, e.getAsDouble()));
    }
    /** 整数读取：缺键取缺省；非整数（含 NaN/Inf/小数）→ 缺省 + 1 条 WARN，夹取由调用方做。 */
    private static int integer(JsonElement e, int fallback, String label, List<String> w) {
        if (e == null) return fallback;
        if (!num(e) || !Double.isFinite(e.getAsDouble()) || Math.rint(e.getAsDouble()) != e.getAsDouble()) {
            w.add(label + " 不是整数，已用 " + fallback);
            return fallback;
        }
        return (int) e.getAsDouble();
    }
    private static boolean str(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
    private static boolean num(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
}
