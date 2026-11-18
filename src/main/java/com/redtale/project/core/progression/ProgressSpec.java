package com.redtale.project.core.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** levels/ 与 jobs/ schema 解析：只读传入配置，坏输入只进 warnings，零日志零磁盘。 */
public final class ProgressSpec {
    public static final String LEVEL_DIR = "levels/";
    public static final String JOB_DIR = "jobs/";

    public record Definition(String id, String name, boolean job, ExpCurve curve,
                             Map<String, Double> attributes, List<String> expSources,
                             String source) {}

    private ProgressSpec() {}

    public static Map<String, Definition> parse(List<ConfigFile> configs, List<String> warnings, boolean job) {
        Map<String, Definition> out = new LinkedHashMap<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        String dir = job ? JOB_DIR : LEVEL_DIR;
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith(dir) || !path.endsWith(".json5")) continue;
            JsonObject root = file.root();
            if (root == null) { warn(warnings, path + ": 根节点不是对象，已跳过"); continue; }
            String id = stringOf(root.get("id"));
            if (id == null || id.trim().isEmpty() || id.indexOf(':') < 0) {
                warn(warnings, path + ": id 缺失或不是字符串（必填），已跳过这条"); continue;
            }
            if (out.containsKey(id)) {
                warn(warnings, path + ": id 重复（" + id + "，先出现在 " + out.get(id).source()
                        + "），保留先出现的定义，本条已跳过"); continue;
            }
            String defName = id.substring(id.indexOf(':') + 1);
            String name = defName;
            JsonElement nameEl = root.get("name");
            if (nameEl != null) {
                String raw = stringOf(nameEl);
                if (raw == null || raw.trim().isEmpty()) warn(warnings, path + ": name 不是字符串，已改用 " + defName);
                else name = raw;
            }
            int maxLevel = job ? 50 : 100;
            JsonElement ml = root.get("max_level");
            if (ml != null) {
                boolean isNum = ml.isJsonPrimitive() && ml.getAsJsonPrimitive().isNumber();
                double d = isNum ? ml.getAsDouble() : Double.NaN;
                if (!isNum || !Double.isFinite(d) || d != Math.rint(d) || d < 2) {
                    warn(warnings, path + ": max_level 不可用，已改用 " + maxLevel);
                } else if (d > 1000) {
                    maxLevel = 1000;
                    warn(warnings, path + ": max_level 不可用，已改用 1000");
                } else maxLevel = (int) d;
            }
            boolean geometric = true;
            double base = 100.0, growth = 1.12, exponent = 1.5;
            JsonElement cv = root.get("curve");
            if (cv == null || !cv.isJsonObject()) {
                warn(warnings, path + ": curve 缺失或不是对象，已用 geometric/100.0/1.12");
            } else {
                JsonObject co = cv.getAsJsonObject();
                String mode = stringOf(co.get("mode"));
                if ("polynomial".equals(mode)) geometric = false;
                else if (mode != null && !"geometric".equals(mode))
                    warn(warnings, path + ": curve.mode 未知（" + mode + "），已改用 geometric");
                base = num(co, "base", 100.0, 0.0, path, warnings, "curve.base");
                growth = num(co, "growth", 1.12, 1.0, path, warnings, "curve.growth");
                exponent = num(co, "exponent", 1.5, 0.0, path, warnings, "curve.exponent");
            }
            ExpCurve curve = geometric ? ExpCurve.geometric(maxLevel, base, growth)
                                       : ExpCurve.polynomial(maxLevel, base, exponent);
            Map<String, Double> attrs = new LinkedHashMap<>();
            JsonElement ae = root.get("attributes");
            if (ae != null) {
                if (!ae.isJsonObject()) warn(warnings, path + ": attributes 不是对象，已用空表");
                else for (Map.Entry<String, JsonElement> e : ae.getAsJsonObject().entrySet()) {
                    String k = e.getKey();
                    JsonElement v = e.getValue();
                    if (k.trim().isEmpty()) { warn(warnings, path + ": 属性 " + k + " 的键为空，已丢弃"); continue; }
                    boolean isNum = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
                    double d = isNum ? v.getAsDouble() : Double.NaN;
                    if (!isNum || !Double.isFinite(d)) {
                        warn(warnings, path + ": 属性 " + k + " 的值不是数字，已丢弃"); continue;
                    }
                    if (attrs.containsKey(k)) warn(warnings, path + ": 属性 " + k + " 重复，已用后一个值");
                    attrs.put(k, d);
                }
            }
            List<String> sources = new ArrayList<>();
            if (job) {
                JsonElement se = root.get("exp_sources");
                if (se == null) sources.add("kill");
                else if (!se.isJsonArray()) {
                    warn(warnings, path + ": exp_sources 不是数组，已用 [kill]"); sources.add("kill");
                } else {
                    Set<String> seen = new LinkedHashSet<>();
                    for (JsonElement el : se.getAsJsonArray()) {
                        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                            warn(warnings, path + ": exp_sources 元素不是字符串，已丢弃"); continue;
                        }
                        String s = el.getAsString();
                        if (!"kill".equals(s) && !"command".equals(s) && !"primitive".equals(s)) {
                            warn(warnings, path + ": 未知经验来源 " + s + "，已丢弃"); continue;
                        }
                        if (seen.add(s)) sources.add(s);
                    }
                }
            }
            out.put(id, new Definition(id, name, job, curve,
                    Collections.unmodifiableMap(new LinkedHashMap<>(attrs)),
                    Collections.unmodifiableList(new ArrayList<>(sources)), path));
        }
        return Collections.unmodifiableMap(out);
    }

    private static double num(JsonObject o, String key, double fallback, double min,
                              String path, List<String> warnings, String label) {
        JsonElement el = o.get(key);
        if (el == null) return fallback;
        boolean isNum = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        double v = isNum ? el.getAsDouble() : Double.NaN;
        if (!isNum || !Double.isFinite(v) || v <= min) {
            warn(warnings, path + ": " + label + " 不可用，已改用 " + fallback); return fallback;
        }
        return v;
    }

    private static String stringOf(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static void warn(List<String> warnings, String msg) {
        if (warnings != null) warnings.add(msg);
    }
}
