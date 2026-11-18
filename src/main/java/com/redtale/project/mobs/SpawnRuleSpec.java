package com.redtale.project.mobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 刷怪规则 L1 解析（D26 / M6）：只读 spawn_rules/*.json5，夹取 + 告警；不碰 MC、事件与运行时状态。 */
public final class SpawnRuleSpec {
    public record Conditions(List<String> worlds, List<String> biomes, Integer lightMin, Integer lightMax,
                             String time, Integer yMin, Integer yMax, Boolean outside, List<String> difficulties) {
        public static final Conditions ANY = new Conditions(List.of(), List.of(), null, null, "any", null, null, null, List.of());
    }
    public record Definition(String id, String name, List<String> match, String action, String mob,
                             double chance, int priority, int perChunkCap, Conditions conditions, String source) {}
    private static final String DIR = "spawn_rules/", SUFFIX = ".json5";
    private static final Set<String> TOP_KEYS = Set.of("id", "name", "match", "action", "mob", "chance",
            "priority", "per_chunk_cap", "conditions");
    private static final Set<String> COND_KEYS = Set.of("world", "biome", "light", "time", "y", "outside", "difficulty");
    private SpawnRuleSpec() {}

    /** 解析全部规则；非 spawn_rules/*.json5 静默跳过；保序 + 不可变；configs 为 null → 空表。 */
    public static Map<String, Definition> parse(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        List<String> w = warnings != null ? warnings : new ArrayList<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith(DIR) || !path.endsWith(SUFFIX)) continue;
            JsonObject root = file.root();
            if (root == null) { w.add(path + ": 根节点不是对象，已跳过这条刷怪规则"); continue; }
            String id = id(root, path, w);
            if (id == null) continue;
            List<String> match = match(root, path, w);
            if (match == null) continue;
            String mob = mob(root, path, w);
            if (mob == null) continue;
            unknown(root, TOP_KEYS, path, w);
            out.put(id, new Definition(id, name(root, id, path, w), match, action(root, path, w), mob,
                    chance(root, path, w), integer(root.get("priority"), 0, path + ".priority", w),
                    Math.max(0, integer(root.get("per_chunk_cap"), 0, path + ".per_chunk_cap", w)),
                    conditions(root, path, w), path));
        }
        return Collections.unmodifiableMap(out);
    }
    /** id 必填且含 ':'，否则整条跳过 + 1 条 WARN。 */
    private static String id(JsonObject root, String path, List<String> w) {
        String id = str(root.get("id")) ? root.get("id").getAsString().trim() : "";
        if (id.isEmpty() || !id.contains(":")) {
            w.add(path + ": id 缺失或不是字符串（含 ':' 必填），已跳过这条刷怪规则");
            return null;
        }
        return id;
    }
    /** match 必须是非空字符串数组（["*"] 原样保留），否则整条跳过 + 1 条 WARN。 */
    private static List<String> match(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("match");
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement el : e.getAsJsonArray()) {
                if (!str(el) || el.getAsString().trim().isEmpty()) { out.clear(); break; }
                out.add(el.getAsString().trim());
            }
        }
        if (out.isEmpty()) {
            w.add(path + ": match 缺失或不是非空字符串数组，已跳过这条刷怪规则");
            return null;
        }
        return List.copyOf(out);
    }
    /** mob 必填且非空串（v1 不区分 add/replace），否则整条跳过 + 1 条 WARN。 */
    private static String mob(JsonObject root, String path, List<String> w) {
        String mob = str(root.get("mob")) ? root.get("mob").getAsString().trim() : "";
        if (mob.isEmpty()) { w.add(path + ": mob 缺失或为空串，已跳过这条刷怪规则"); return null; }
        return mob;
    }
    /** name 缺省 = id；非字符串 → id + WARN；空白串按缺省处理。 */
    private static String name(JsonObject root, String id, String path, List<String> w) {
        JsonElement e = root.get("name");
        if (e == null) return id;
        if (!str(e)) { w.add(path + ": name 不是字符串，已用 id 作名称"); return id; }
        return e.getAsString().trim().isEmpty() ? id : e.getAsString();
    }
    /** action 只认 replace/add，其它 → replace + WARN。 */
    private static String action(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("action");
        if (e == null) return "replace";
        String v = str(e) ? e.getAsString().trim() : "";
        if (!v.equals("replace") && !v.equals("add")) {
            w.add(path + ": action 只认 replace/add（写了 " + e + "），已回落 replace");
            return "replace";
        }
        return v;
    }
    /** chance 缺省 1.0；非有限/非数字 → 1.0 + WARN；夹 [0,1] 不告警。 */
    private static double chance(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("chance");
        if (e == null) return 1.0D;
        if (!num(e) || !Double.isFinite(e.getAsDouble())) {
            w.add(path + ": chance 不是有效数字，已用 1.0");
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, e.getAsDouble()));
    }
    /** 整数读取：非整数 → fallback + 1 条 WARN；小数/NaN/Inf 都算非整数。 */
    private static int integer(JsonElement e, int fallback, String label, List<String> w) {
        if (e == null) return fallback;
        double v = num(e) ? e.getAsDouble() : Double.NaN;
        if (!Double.isFinite(v) || Math.rint(v) != v) { w.add(label + " 不是整数，已用 " + fallback); return fallback; }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }
    /** conditions 缺省 ANY；六维独立读取，min>max 交换并告警。 */
    private static Conditions conditions(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("conditions");
        if (e == null || e.isJsonNull()) return Conditions.ANY;
        if (!e.isJsonObject()) { w.add(path + ": conditions 不是对象，已按 ANY 处理"); return Conditions.ANY; }
        JsonObject c = e.getAsJsonObject();
        String src = path + ".conditions";
        unknown(c, COND_KEYS, src, w);
        Integer[] light = range(c.get("light"), src + ".light", w);
        Integer[] y = range(c.get("y"), src + ".y", w);
        JsonElement outside = c.get("outside");
        Boolean out = outside != null && outside.isJsonPrimitive() && outside.getAsJsonPrimitive().isBoolean()
                ? outside.getAsBoolean() : null;
        if (outside != null && !outside.isJsonNull() && out == null) w.add(src + ": outside 不是布尔，已按不限处理");
        return new Conditions(strings(c.get("world"), src + ".world", w), strings(c.get("biome"), src + ".biome", w),
                light[0], light[1], time(c.get("time"), src, w), y[0], y[1], out,
                strings(c.get("difficulty"), src + ".difficulty", w));
    }
    private static Integer[] range(JsonElement e, String src, List<String> w) {
        if (e == null || !e.isJsonObject()) return new Integer[]{null, null};
        JsonObject obj = e.getAsJsonObject();
        Integer min = intOrNull(obj.get("min"), src + ".min", w);
        Integer max = intOrNull(obj.get("max"), src + ".max", w);
        if (min != null && max != null && min > max) {
            w.add(src + " 的 min > max，已交换");
            return new Integer[]{max, min};
        }
        return new Integer[]{min, max};
    }
    private static Integer intOrNull(JsonElement e, String label, List<String> w) {
        if (e == null) return null;
        if (!num(e) || !Double.isFinite(e.getAsDouble()) || Math.rint(e.getAsDouble()) != e.getAsDouble()) {
            w.add(label + " 不是整数，已按不限处理");
            return null;
        }
        return (int) e.getAsDouble();
    }
    /** 可选字符串数组：坏项只丢自己 + WARN。 */
    private static List<String> strings(JsonElement e, String label, List<String> w) {
        if (e == null) return List.of();
        if (!e.isJsonArray()) { w.add(label + " 不是数组，已按不限处理"); return List.of(); }
        List<String> out = new ArrayList<>();
        for (JsonElement el : e.getAsJsonArray()) {
            if (str(el) && !el.getAsString().trim().isEmpty()) out.add(el.getAsString().trim());
            else w.add(label + " 含非字符串元素，已忽略该元素");
        }
        return List.copyOf(out);
    }
    /** time 缺省 any；非法值 → any + WARN。 */
    private static String time(JsonElement e, String src, List<String> w) {
        if (e == null) return "any";
        String v = str(e) ? e.getAsString().trim() : "";
        if (!v.equals("day") && !v.equals("night") && !v.equals("any")) {
            w.add(src + ".time 只认 day/night/any（写了 " + e + "），已用 any");
            return "any";
        }
        return v;
    }
    private static void unknown(JsonObject obj, Set<String> known, String label, List<String> w) {
        for (String key : obj.keySet()) if (!known.contains(key)) w.add(label + " 未知键 " + key + "，已忽略");
    }
    private static boolean str(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
    private static boolean num(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
}
