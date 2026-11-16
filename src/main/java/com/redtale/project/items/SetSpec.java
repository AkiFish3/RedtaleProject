package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.math.StatValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 套装定义解析器（M3 第五步，纯解析 + 一个纯函数）。schema 见 {@code docs/ITEM-MODEL.md} §5.4。
 *
 * <p>只把 {@code config/redtale_project/sets/*.json5} 变成不可变对象：不算件数、不下发 modifier、
 * 不读盘、不碰 {@code ItemStack}、不加命令。{@code activeBonuses} 只回答「穿了几件时哪几档生效」。
 * 失败策略：类型不符 / {@code JsonNull} / 缺键只记 {@code warnings} 一行，用默认值或跳过，
 * <b>绝不抛异常</b>（与 T-0001/T-0004/T-0006/T-0008/T-0010 同口径）。
 */
public final class SetSpec {

    /** 一档加成。{@code stats} 不可变、保序、键已归一（由 {@link AffixPools#parseStats} 产出）。 */
    public record Bonus(int count, String name, Map<String, StatValue> stats) {}

    /** 一个套装定义。{@code pieces} / {@code bonuses} 不可变，{@code bonuses} 按 {@code count} 升序。 */
    public record Definition(String id, String name, List<String> pieces, List<Bonus> bonuses) {}

    private static final String DIR = "sets/";
    private static final String SUFFIX = ".json5";

    private SetSpec() {}

    /** 解析全部套装定义；非 {@code sets/*.json5} 的文件静默跳过。返回 {@link LinkedHashMap} 保文件顺序。 */
    public static Map<String, Definition> parseAll(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        if (configs == null) return out;
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith(DIR) || !path.endsWith(SUFFIX)) continue;
            JsonObject root = file.root();
            if (root == null) {
                sink.add(path + ": 根节点不是对象，已跳过");
                continue;
            }
            String fallback = path.substring(DIR.length(), path.length() - SUFFIX.length()).trim();
            String id = resolveId(root, path, fallback, sink);
            if (id.isEmpty()) {
                sink.add(path + ": 套装 id 为空串，已跳过该文件（空 id 会匹配到所有没写 set_id 的物品）");
                continue;
            }
            Definition def = new Definition(id, name(root, id, path, sink),
                    pieces(root, path, sink), bonuses(root, path, sink));
            if (out.putIfAbsent(id, def) != null) {
                sink.add(path + ": 套装 id 重复（" + id + "），保留先出现的定义，本条已跳过");
            }
        }
        return out;
    }

    /**
     * 纯函数：返回 {@code bonuses} 里<b>所有</b> {@code count <= wornPieces} 的档（不是只取最高档，§5.4），
     * 保持 {@code Definition} 里的升序。不修改入参、不读全局状态。
     */
    public static List<Bonus> activeBonuses(Definition definition, int wornPieces) {
        if (definition == null || wornPieces <= 0) return List.of();
        List<Bonus> out = new ArrayList<>();
        for (Bonus bonus : definition.bonuses()) {
            if (bonus != null && bonus.count() <= wornPieces) out.add(bonus);
        }
        return List.copyOf(out);
    }

    // ---- 取值助手：缺键用默认值，类型不符告警后同样用默认值，绝不抛 ----
    /** {@code root.id} 优先，缺键用路径推导值；非字符串告警后回落路径推导值；非字符串不覆盖空串跳过。 */
    private static String resolveId(JsonObject root, String path, String fallback, List<String> w) {
        JsonElement e = root.get("id");
        if (e == null) return fallback;
        if (!isString(e)) {
            w.add(path + ": id 不是字符串，已用路径推导的 id " + fallback);
            return fallback;
        }
        return e.getAsString().trim();
    }

    /** {@code name} 缺省 = {@code id}；非字符串 → 告警 + 回落 {@code id}。 */
    private static String name(JsonObject root, String id, String path, List<String> w) {
        JsonElement e = root.get("name");
        if (e == null) return id;
        if (!isString(e)) {
            w.add(path + ": name 不是字符串，已用 id 代替");
            return id;
        }
        return e.getAsString().trim();
    }

    /** 字符串数组；非字符串 / 空项跳过并告警，重复元素去重（保留首次）+ 合并成 1 条告警。 */
    private static List<String> pieces(JsonObject root, String path, List<String> w) {
        List<String> out = new ArrayList<>();
        JsonElement e = root.get("pieces");
        if (e == null) return List.of();
        if (!e.isJsonArray()) {
            w.add(path + ": pieces 不是数组，已按空表处理");
            return List.of();
        }
        List<String> dup = new ArrayList<>();
        for (JsonElement item : e.getAsJsonArray()) {
            if (!isString(item)) {
                w.add(path + ": pieces 里有非字符串项，已跳过");
                continue;
            }
            String value = item.getAsString().trim();
            if (value.isEmpty()) {
                w.add(path + ": pieces 里有空项，已跳过");
            } else if (out.contains(value)) {
                if (!dup.contains(value)) dup.add(value);
            } else {
                out.add(value);
            }
        }
        if (!dup.isEmpty()) {
            w.add(path + ": pieces 有重复项 " + dup + "，已去重只算一件");
        }
        return List.copyOf(out);
    }

    /** 数组，缺省空表；元素非对象跳过并告警；返回前按 {@code count} 稳定升序。 */
    private static List<Bonus> bonuses(JsonObject root, String path, List<String> w) {
        List<Bonus> out = new ArrayList<>();
        JsonElement e = root.get("bonuses");
        if (e == null) return List.of();
        if (!e.isJsonArray()) {
            w.add(path + ": bonuses 不是数组，已按空表处理");
            return List.of();
        }
        int i = 0;
        for (JsonElement item : e.getAsJsonArray()) {
            String src = path + ".bonus[" + i++ + "]";
            if (item == null || !item.isJsonObject()) {
                w.add(src + ": 不是对象，已跳过");
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            int count = count(obj, src, w);
            if (count < 1) continue;
            // stats 必须走 AffixPools 的同一条解析与归一（R11-b：另写一份会让同一属性加两条 modifier）
            Map<String, StatValue> stats = Collections.unmodifiableMap(AffixPools.parseStats(obj, src, w));
            out.add(new Bonus(count, name(obj, "", src, w), stats));
        }
        out.sort(Comparator.comparingInt(Bonus::count));
        return List.copyOf(out);
    }

    /** 缺省 0 / 非数字 / {@code < 1} → 告警 + {@code -1}（该档跳过：「0 件生效」不存在）。 */
    private static int count(JsonObject obj, String src, List<String> w) {
        JsonElement e = obj.get("count");
        if (e == null) {
            w.add(src + ": 缺 count，已跳过该档");
            return -1;
        }
        if (!isNumber(e)) {
            w.add(src + ": count 不是数字，已跳过该档");
            return -1;
        }
        int value = e.getAsInt();
        if (value < 1) {
            w.add(src + ": count 小于 1，已跳过该档");
            return -1;
        }
        return value;
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
}
