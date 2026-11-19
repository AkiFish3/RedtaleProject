package com.redtale.project.core.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 专业（D24）schema 的 L1 纯解析 + grants 结算纯函数：零 MC、零 IO、零日志。 */
public final class ProfessionSpec {
    public static final String DIR = "professions/";
    public record Grant(String token, boolean tag, long amount) {}
    public record Definition(String id, String name, int maxLevel, ExpCurve curve,
                             java.util.Map<String, Double> attributes,
                             java.util.Map<String, java.util.List<Grant>> grants,
                             String source) {}
    private ProfessionSpec() {}

    public static java.util.Map<String, Definition> parse(java.util.List<ConfigFile> configs,
                                                          java.util.List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        for (ConfigFile f : configs) {
            if (f == null || f.path() == null || !f.path().startsWith(DIR) || !f.path().endsWith(".json5")) continue;
            String path = f.path();
            JsonObject root = f.root();
            if (root == null) { warn(warnings, path + "：根节点不是对象，已跳过"); continue; }
            String id = stringOf(root.get("id"));
            if (id == null || id.indexOf(':') < 0) { warn(warnings, path + "：id 缺失或不是字符串（必填），已跳过这条"); continue; }
            if (out.containsKey(id)) {
                warn(warnings, path + "：id 重复（" + id + "，先出现在 " + out.get(id).source() + "），保留先出现的定义，本条已跳过");
                continue;
            }
            String pathPart = id.substring(id.indexOf(':') + 1);
            String name = pathPart;
            JsonElement ne = root.get("name");
            if (ne != null) {
                String raw = stringOf(ne);
                if (raw == null || raw.isBlank()) warn(warnings, path + "：name 不是字符串，已改用 " + pathPart);
                else name = raw;
            }
            int ml = maxLevel(path, root.get("max_level"), warnings);
            ExpCurve curve = curve(path, root.get("curve"), ml, warnings);
            Map<String, Double> attrs = attributes(path, root.get("attributes"), warnings);
            Map<String, List<Grant>> grants = grants(path, root.get("grants"), warnings);
            for (String k : root.keySet())
                if (!k.equals("id") && !k.equals("name") && !k.equals("max_level") && !k.equals("curve")
                    && !k.equals("attributes") && !k.equals("grants"))
                    warn(warnings, path + "：未知键 " + k + "（已忽略）");
            out.put(id, new Definition(id, name, ml, curve, attrs, grants, path));
        }
        return Collections.unmodifiableMap(out);
    }

    public static java.util.List<Grant> grantsOf(Definition def, String source) {
        if (def == null || source == null) return List.of();
        List<Grant> g = def.grants().get(source);
        return g == null ? List.of() : g;
    }

    public static long resolve(java.util.List<Grant> grants, String id, java.util.Set<String> tags) {
        if (grants == null || id == null || id.isBlank()) return 0L;
        Set<String> ts = tags == null ? Set.of() : tags;
        long tagMax = Long.MIN_VALUE, def = 0L;
        boolean hasDef = false;
        for (Grant g : grants) {
            if (g == null) continue;
            if (!g.tag() && g.token().equals(id)) return g.amount();
            if (g.tag() && g.token().length() > 1 && ts.contains(g.token().substring(1)) && g.amount() > tagMax) tagMax = g.amount();
            if (!g.tag() && g.token().equals("default")) { def = g.amount(); hasDef = true; }
        }
        if (tagMax != Long.MIN_VALUE) return tagMax;
        return hasDef ? def : 0L;
    }

    private static int maxLevel(String path, JsonElement el, List<String> w) {
        int ml = 100;
        if (el == null) return ml;
        boolean num = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        double d = num ? el.getAsDouble() : Double.NaN;
        if (!num || !Double.isFinite(d) || d != Math.rint(d) || d < 2) warn(w, path + "：max_level 不可用，已改用 100");
        else if (d > 1000) { ml = 1000; warn(w, path + "：max_level 不可用，已改用 1000"); }
        else ml = (int) d;
        return ml;
    }

    private static ExpCurve curve(String path, JsonElement el, int ml, List<String> w) {
        boolean geo = true;
        double base = 100.0, growth = 1.12, exp = 1.5;
        if (el == null || !el.isJsonObject()) warn(w, path + "：curve 缺失或不是对象，已用 geometric/100.0/1.12");
        else {
            JsonObject co = el.getAsJsonObject();
            String mode = stringOf(co.get("mode"));
            // §3 的示范件写的是显式 "geometric"（默认证），**不得当未知模式告警**（R65 修：原实现
            // 只判 polynomial，显式 geometric 会误报「未知模式」，每份默认件 reload 都刷一条假告警）
            if ("polynomial".equals(mode)) geo = false;
            else if (mode != null && !"geometric".equals(mode)) warn(w, path + "：curve.mode 未知（" + mode + "），已改用 geometric");
            base = num(co, "base", 100.0, 0.0, path, w);
            growth = num(co, "growth", 1.12, 1.0, path, w);
            exp = num(co, "exponent", 1.5, 0.0, path, w);
        }
        return geo ? ExpCurve.geometric(ml, base, growth) : ExpCurve.polynomial(ml, base, exp);
    }

    private static double num(JsonObject o, String key, double dflt, double min, String path, List<String> w) {
        JsonElement el = o.get(key);
        if (el == null) return dflt;
        boolean num = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        double v = num ? el.getAsDouble() : Double.NaN;
        if (!num || !Double.isFinite(v) || v < min) { warn(w, path + "：curve." + key + " 不可用，已改用 " + dflt); return dflt; }
        return v;
    }

    private static Map<String, Double> attributes(String path, JsonElement el, List<String> w) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (el == null) return Collections.unmodifiableMap(out);
        if (!el.isJsonObject()) { warn(w, path + "：attributes 不是对象（已忽略）"); return Collections.unmodifiableMap(out); }
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String k = e.getKey();
            if (k.isBlank()) { warn(w, "attributes 的键 " + k + " 不可用，已丢弃"); continue; }
            JsonElement v = e.getValue();
            boolean num = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
            double d = num ? v.getAsDouble() : Double.NaN;
            if (!num || !Double.isFinite(d)) { warn(w, "attributes." + k + " 的值不可用，已丢弃"); continue; }
            out.put(k, d);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, List<Grant>> grants(String path, JsonElement el, List<String> w) {
        Map<String, List<Grant>> out = new LinkedHashMap<>();
        if (el == null) return Collections.unmodifiableMap(out);
        if (!el.isJsonObject()) { warn(w, path + "：grants 不是对象（已忽略）"); return Collections.unmodifiableMap(out); }
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String src = e.getKey();
            if (!src.equals("mine") && !src.equals("craft") && !src.equals("smelt")) {
                warn(w, path + "：grants 的源 " + src + " 未知（只认 mine/craft/smelt），已忽略"); continue;
            }
            List<Grant> list = new ArrayList<>();
            JsonElement se = e.getValue();
            if (!se.isJsonObject()) warn(w, path + "：grants." + src + " 不是对象（已忽略）");
            else for (Map.Entry<String, JsonElement> le : se.getAsJsonObject().entrySet()) {
                String key = le.getKey();
                boolean tag = false;
                if (key.startsWith("#")) tag = true;
                else if (!key.equals("default") && key.indexOf(':') < 0) {
                    warn(w, path + "：grants." + src + "." + key + " 不是合法 id 也不带 #，已丢弃"); continue;
                }
                list.add(new Grant(key, tag, grantValue(path, src, key, le.getValue(), w)));
            }
            out.put(src, List.copyOf(list));
        }
        return Collections.unmodifiableMap(out);
    }

    private static long grantValue(String path, String src, String key, JsonElement v, List<String> w) {
        String pre = path + "：grants." + src + "." + key;
        boolean num = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
        double d = num ? v.getAsDouble() : Double.NaN;
        if (!num || !Double.isFinite(d)) { warn(w, pre + " 的值不可用，已改用 0"); return 0L; }
        if (d != Math.rint(d)) { warn(w, pre + " 不是整数，已四舍五入"); d = Math.rint(d); }
        if (d < 0) { warn(w, pre + " 的值为负，已归零"); return 0L; }
        if (d > 1000) { warn(w, pre + " 超过 1000，已夹到 1000"); return 1000L; }
        return (long) d;
    }

    private static String stringOf(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }
    private static void warn(List<String> w, String msg) { if (w != null) w.add(msg); }
}
