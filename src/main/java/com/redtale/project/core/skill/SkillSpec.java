package com.redtale.project.core.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.script.ScriptCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能定义解析器（M5 第一层，纯解析）。schema 唯一权威：{@code docs/SKILLS.md} §3。
 * 不建 {@code SkillEngine}、不读注册表、不碰 Minecraft 类型；失败只记 {@code warnings} 一行，绝不抛异常。
 */
public final class SkillSpec {

    /**
     * 一条技能定义。{@code conditions} / {@code effects} 不可变；{@code args} 原样透传（不改键名、不换算）。
     *
     * @param source 来源配置的相对路径（{@code skills/xxx.json5}），排障用
     */
    public record Definition(String id, String name, String icon, int cooldownTicks,
                             List<ScriptCall> conditions, List<ScriptCall> effects, String source) {}

    private static final String DIR = "skills/";
    private static final String SUFFIX = ".json5";
    private static final String NAME_PREFIX = "skill.redtale.";
    private static final String DEFAULT_ICON = "minecraft:paper";
    private static final int MAX_COOLDOWN = 1_000_000_000;

    private SkillSpec() {}

    /**
     * 解析全部技能定义；非 {@code skills/*.json5} 的文件静默跳过。
     * 返回 {@link LinkedHashMap}（保文件顺序）+ 不可变包装，调用方 {@code put} 抛 {@link UnsupportedOperationException}。
     */
    public static Map<String, Definition> parse(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        Map<String, String> seen = new LinkedHashMap<>();
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith(DIR) || !path.endsWith(SUFFIX)) continue;
            JsonObject root = file.root();
            if (root == null) {
                sink.add(path + ": 根节点不是对象，已跳过");
                continue;
            }
            String id = id(root, path, sink);
            if (id == null) continue;
            String prev = seen.putIfAbsent(id, path);
            if (prev != null) {
                sink.add(path + ": 技能 id 重复（" + id + "，先出现在 " + prev + "），保留先出现的定义，本条已跳过");
                continue;
            }
            List<ScriptCall> effects = effects(root, path, sink);
            if (effects == null) continue;
            out.put(id, new Definition(id, name(root, id, path, sink), icon(root, path, sink),
                    cooldown(root, path, sink), conditions(root, path, sink), effects, path));
        }
        return Collections.unmodifiableMap(out);
    }

    // ---- 取值助手：缺键用缺省，类型不符告警后同样用缺省，绝不抛 ----
    /** {@code id} 必填：缺失 / 非字符串 / 空白 / 不含 {@code :} 一律整条跳过 + 1 条 WARN（不补命名空间）。 */
    private static String id(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("id");
        if (e == null || !isString(e)) {
            w.add(path + ": id 缺失或不是字符串（必填），已跳过这条技能");
            return null;
        }
        String id = e.getAsString().trim();
        if (id.isEmpty()) {
            w.add(path + ": id 是空白串，已跳过这条技能");
            return null;
        }
        if (id.indexOf(':') < 0) {
            w.add(path + ": id（" + id + "）不含 ':'，已跳过这条技能（不补命名空间）");
            return null;
        }
        return id;
    }

    /** 缺省 {@code skill.redtale.<id 的 path 部分>}；非字符串 → 告警 + 缺省。 */
    private static String name(JsonObject root, String id, String path, List<String> w) {
        String fallback = NAME_PREFIX + id.substring(id.indexOf(':') + 1);
        JsonElement e = root.get("name");
        if (e == null) return fallback;
        if (!isString(e)) {
            w.add(path + ": name 不是字符串，已用缺省 " + fallback);
            return fallback;
        }
        return e.getAsString();
    }

    /** 缺省 {@code minecraft:paper}；非字符串 → 告警 + 缺省。 */
    private static String icon(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("icon");
        if (e == null) return DEFAULT_ICON;
        if (!isString(e)) {
            w.add(path + ": icon 不是字符串，已用缺省 " + DEFAULT_ICON);
            return DEFAULT_ICON;
        }
        return e.getAsString();
    }

    /** 缺省 0；负数 → 0 + WARN；{@code > 1e9} → 1e9 + WARN；非数字 → 0 + WARN。 */
    private static int cooldown(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("cooldown_ticks");
        if (e == null) return 0;
        if (!isNumber(e)) {
            w.add(path + ": cooldown_ticks 不是数字，已用 0");
            return 0;
        }
        double value = e.getAsDouble();
        if (value < 0) {
            w.add(path + ": cooldown_ticks 为负数（" + e.getAsString() + "），已用 0");
            return 0;
        }
        if (value > MAX_COOLDOWN) {
            w.add(path + ": cooldown_ticks 超出上限（" + e.getAsString() + "），已夹到 " + MAX_COOLDOWN);
            return MAX_COOLDOWN;
        }
        return (int) value;
    }

    /** 可选：缺失 = 空表；非数组 → 空表 + 1 条 WARN；单项坏掉只丢该项。 */
    private static List<ScriptCall> conditions(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("conditions");
        if (e == null) return List.of();
        if (!e.isJsonArray()) {
            w.add(path + ": conditions 不是数组，已按空表处理");
            return List.of();
        }
        return List.copyOf(calls(e, path, "conditions", w));
    }

    /** 必填：缺失 / 非数组 / 有效项 0 条 → 返回 null（调用方整条跳过）+ 1 条 WARN。 */
    private static List<ScriptCall> effects(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("effects");
        if (e == null) {
            w.add(path + ": 缺 effects（必填，至少 1 条有效项），已跳过这条技能");
            return null;
        }
        if (!e.isJsonArray()) {
            w.add(path + ": effects 不是数组，已跳过这条技能");
            return null;
        }
        List<ScriptCall> out = List.copyOf(calls(e, path, "effects", w));
        if (out.isEmpty()) {
            w.add(path + ": effects 没有有效项，已跳过这条技能");
            return null;
        }
        return out;
    }

    /** 逐项解析数组；坏项只丢自己，不因单项坏而丢整条技能。 */
    private static List<ScriptCall> calls(JsonElement array, String path, String key, List<String> w) {
        List<ScriptCall> out = new ArrayList<>();
        int i = 0;
        for (JsonElement item : array.getAsJsonArray()) {
            ScriptCall call = call(item, path + "." + key + "[" + i++ + "]", w);
            if (call != null) out.add(call);
        }
        return out;
    }

    /** 单项：必须是对象且 {@code type} 为非空字符串；{@code args} 缺失 = {@link ScriptCall#EMPTY}，非对象 = 空 args + WARN。 */
    private static ScriptCall call(JsonElement item, String src, List<String> w) {
        if (item == null || !item.isJsonObject()) {
            w.add(src + ": 不是对象，已跳过该项");
            return null;
        }
        JsonObject obj = item.getAsJsonObject();
        JsonElement t = obj.get("type");
        if (!isString(t) || t.getAsString().trim().isEmpty()) {
            w.add(src + ": type 缺失或不是非空字符串，已跳过该项");
            return null;
        }
        String type = t.getAsString().trim();
        JsonElement a = obj.get("args");
        if (a != null && a.isJsonObject()) return new ScriptCall(type, a.getAsJsonObject());
        if (a != null) w.add(src + ": args 不是对象，已按空 args 处理");
        return new ScriptCall(type, ScriptCall.EMPTY);
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
}
