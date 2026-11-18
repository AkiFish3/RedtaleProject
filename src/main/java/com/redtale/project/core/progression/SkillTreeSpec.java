package com.redtale.project.core.progression;

import com.google.gson.JsonArray;
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

/** skill_trees/ schema 解析：只读、坏输入只进 warnings、零 MC 依赖（D25 L1）。 */
public final class SkillTreeSpec {
    public record Effect(String type, String attribute, String operation, double value, String skill, String id) {}
    public record Node(String id, String name, int cost, int[] pos, List<String> requires, List<Effect> effects) {}
    public record Definition(String id, String name, String icon, List<String> classes,
                             int maxPoints, int pointsPerLevel, Map<String, Node> nodes, String source) {}

    private static final Set<String> ROOT_KEYS = new LinkedHashSet<>(List.of(
        "id", "name", "icon", "classes", "max_points", "points_per_level", "respec", "nodes", "exclusive_with"));
    private static final Set<String> NODE_KEYS = new LinkedHashSet<>(List.of(
        "name", "cost", "pos", "requires", "effects", "exclusive_with"));

    private SkillTreeSpec() {}

    public static Map<String, Definition> parse(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Definition> out = new LinkedHashMap<>();
        if (configs == null) return Collections.unmodifiableMap(out);
        for (ConfigFile file : configs) {
            if (file == null) continue;
            String path = file.path();
            if (path == null || !path.startsWith("skill_trees/") || !path.endsWith(".json5")) continue;
            JsonObject root = file.root();
            if (root == null) { warn(warnings, path + "：id 缺失，已跳过"); continue; }
            String id = stringOf(root.get("id"));
            if (id == null || id.trim().isEmpty()) { warn(warnings, path + "：id 缺失，已跳过"); continue; }
            JsonElement ne = root.get("nodes");
            if (ne == null || !ne.isJsonObject() || ne.getAsJsonObject().size() == 0) {
                warn(warnings, path + "：nodes 为空，已跳过"); continue;
            }
            JsonObject nodesObj = ne.getAsJsonObject();
            for (String k : root.keySet()) if (!ROOT_KEYS.contains(k)) warn(warnings, "未知键 " + k + "（已忽略）");
            Set<String> nodeIds = new LinkedHashSet<>(nodesObj.keySet());
            Map<String, Node> nodes = new LinkedHashMap<>();
            int sum = 0;
            for (Map.Entry<String, JsonElement> e : nodesObj.entrySet()) {
                String nid = e.getKey();
                JsonElement nv = e.getValue();
                JsonObject no = nv.isJsonObject() ? nv.getAsJsonObject() : new JsonObject();
                for (String k : no.keySet()) if (!NODE_KEYS.contains(k)) warn(warnings, "节点 " + nid + " 的未知键 " + k + "（已忽略）");
                String name = stringOf(no.get("name"));
                if (name == null || name.trim().isEmpty()) name = "tree.node." + nid;
                int cost = intOf(no.get("cost"), 0);
                if (cost < 0) { warn(warnings, "节点 " + nid + " 的 cost 为负（" + cost + "），已归零"); cost = 0; }
                sum += cost;
                int[] pos = posOf(no.get("pos"), nid, warnings);
                List<String> requires = requiresOf(no.get("requires"), nid, nodeIds, warnings);
                List<Effect> effects = effectsOf(no.get("effects"), nid, warnings);
                nodes.put(nid, new Node(nid, name, cost, pos,
                    Collections.unmodifiableList(new ArrayList<>(requires)),
                    Collections.unmodifiableList(new ArrayList<>(effects))));
            }
            int maxPoints = intOf(root.get("max_points"), sum);
            JsonElement mp = root.get("max_points");
            if (mp == null || !isInt(mp)) warn(warnings, path + "：max_points 缺失，已取节点 cost 合计 " + sum);
            else if (maxPoints < 0) { warn(warnings, path + "：max_points 为负，已归零"); maxPoints = 0; }
            int ppl = intOf(root.get("points_per_level"), 1);
            JsonElement pp = root.get("points_per_level");
            if (pp != null && isInt(pp) && ppl < 0) { warn(warnings, path + "：points_per_level 为负，已归零"); ppl = 0; }
            String icon = stringOf(root.get("icon"));
            if (icon == null || icon.trim().isEmpty()) icon = "minecraft:stone";
            List<String> classes = stringsOf(root.get("classes"));
            Definition def = new Definition(id, stringOf(root.get("name")), icon,
                Collections.unmodifiableList(new ArrayList<>(classes)), maxPoints, ppl,
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)), path);
            if (out.containsKey(id)) warn(warnings, path + "：id " + id + " 与 " + out.get(id).source() + " 重复（后者覆盖）");
            out.put(id, def);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(out));
    }

    private static int[] posOf(JsonElement el, String nid, List<String> w) {
        if (el != null && el.isJsonArray() && el.getAsJsonArray().size() == 2
                && isInt(el.getAsJsonArray().get(0)) && isInt(el.getAsJsonArray().get(1)))
            return new int[]{el.getAsJsonArray().get(0).getAsInt(), el.getAsJsonArray().get(1).getAsInt()};
        warn(w, "节点 " + nid + " 的 pos 无效，已用 [0,0]");
        return new int[]{0, 0};
    }

    private static List<String> requiresOf(JsonElement el, String nid, Set<String> ids, List<String> w) {
        List<String> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) return out;
        for (JsonElement e : el.getAsJsonArray()) {
            String dep = stringOf(e);
            if (dep == null) continue;
            if (nid.equals(dep)) { warn(w, "节点 " + nid + " 的前置包含自身，已忽略"); continue; }
            if (!ids.contains(dep)) warn(w, "节点 " + nid + " 的前置 " + dep + " 不存在");
            out.add(dep);
        }
        return out;
    }

    private static List<Effect> effectsOf(JsonElement el, String nid, List<String> w) {
        List<Effect> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) return out;
        for (JsonElement e : el.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String type = stringOf(o.get("type"));
            if (!"attribute".equals(type) && !"unlock_skill".equals(type) && !"passive".equals(type)) {
                warn(w, "节点 " + nid + " 的效果类型 " + type + " 未知，已丢弃"); continue;
            }
            if ("attribute".equals(type)) {
                String attr = stringOf(o.get("attribute")), op = stringOf(o.get("operation"));
                JsonElement v = o.get("value");
                if (attr == null || attr.trim().isEmpty() || op == null || op.trim().isEmpty() || !isNum(v)) {
                    warn(w, "节点 " + nid + " 的 attribute 效果缺字段，已丢弃"); continue;
                }
                out.add(new Effect(type, attr, op, v.getAsDouble(), null, null));
            } else if ("unlock_skill".equals(type)) {
                String sk = stringOf(o.get("skill"));
                if (sk == null || sk.trim().isEmpty()) { warn(w, "节点 " + nid + " 的 unlock_skill 效果缺 skill，已丢弃"); continue; }
                out.add(new Effect(type, null, null, 0, sk, null));
            } else {
                String eid = stringOf(o.get("id"));
                if (eid == null || eid.trim().isEmpty()) { warn(w, "节点 " + nid + " 的 passive 效果缺 id，已丢弃"); continue; }
                out.add(new Effect(type, null, null, 0, null, eid));
            }
        }
        return out;
    }

    private static List<String> stringsOf(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el != null && el.isJsonArray()) for (JsonElement e : el.getAsJsonArray()) {
            String s = stringOf(e);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static String stringOf(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static boolean isNum(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
            && Double.isFinite(el.getAsDouble());
    }

    private static boolean isInt(JsonElement el) {
        if (!isNum(el)) return false;
        double d = el.getAsDouble();
        return d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
    }

    private static int intOf(JsonElement el, int fallback) {
        return isInt(el) ? el.getAsInt() : fallback;
    }

    private static void warn(List<String> warnings, String msg) {
        if (warnings != null) warnings.add(msg);
    }
}
