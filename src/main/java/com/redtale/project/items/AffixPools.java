package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.math.StatValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 词条定义 / 词条池解析器（D22 第一层，纯解析）。schema 见 {@code docs/ITEM-MODEL.md} §2。
 *
 * <p>只把 {@code config/redtale_project/affixes/*.json5} 与 {@code affix_pools/*.json5} 变成内存对象：
 * 不 roll、不接物品、不读磁盘（入参是已解析好的 {@link ConfigFile}）。
 *
 * <p>失败策略：类型不符 / {@code null} / 缺键只记 {@code warnings} 一行，用默认值或跳过该条，
 * <b>绝不抛异常</b>（reload 不能因为一个词条文件写歪就停摆，{@code PLAN.md} R1-a）。
 * 返回的 Map 一律 {@link LinkedHashMap}，保持文件顺序；同 id 重复保留先出现的。
 */
public final class AffixPools {

    /**
     * 词条定义。{@code stats} 的值是 {@link StatValue}（R11 升级，兑现 T-0003 记下的欠账）：
     * {@code { base: 3.0, per_item_level: 0.08 }} 这种曲线形态现在装得下了。
     * 裸数字（{@code attack_damage: 3}）按 {@code StatValue.of(3, 0)} 处理，两种写法等价可混用。
     */
    public record Def(String id, String kind, int weight, int minItemLevel, int maxItemLevel,
                      String exclusiveGroup, Map<String, StatValue> stats) {
    }

    /** 池内一条绑定：词条 id + 池内权重。 */
    public record Entry(String affixId, int weight) {
    }

    /** 词条池。{@code entries} 已按 prefix → suffix → core 拍平。 */
    public record Pool(String id, List<String> appliesTo, int prefixSlots, int suffixSlots,
                       int coreSlots, List<Entry> entries) {
    }

    private static final String DEF_DIR = "affixes/";
    private static final String POOL_DIR = "affix_pools/";
    private static final String SUFFIX = ".json5";
    private static final List<String> KINDS = List.of("prefix", "suffix", "core");

    private AffixPools() {
    }

    /** 解析全部词条定义；{@code affixes/} 前缀以外的文件静默跳过。 */
    public static Map<String, Def> parseDefs(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Def> out = new LinkedHashMap<>();
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        for (ConfigFile file : accept(configs, DEF_DIR)) {
            String path = file.path();
            JsonObject root = file.root();
            if (root == null) {
                sink.add(path + ": 根节点不是对象，已跳过");
                continue;
            }
            String id = resolveId(root, path, DEF_DIR, sink);
            if (id == null) continue;
            String kind = string(root, "kind", "", path, sink);
            if (!KINDS.contains(kind)) {
                sink.add(path + ": kind 只能是 prefix/suffix/core，已跳过 " + id);
                continue;
            }
            JsonObject level = object(root, "item_level", path, sink);
            int minLevel = level == null ? 1 : integer(level, "min", 1, path, sink);
            int maxLevel = level == null ? 60 : integer(level, "max", 60, path, sink);
            Def def = new Def(id, kind, integer(root, "weight", 100, path, sink), minLevel, maxLevel,
                    string(root, "exclusive_group", "", path, sink), stats(root, path, sink));
            if (out.containsKey(id)) {
                sink.add(path + ": 词条 id 重复（" + id + "），保留先出现的定义，本条已跳过");
                continue;
            }
            out.put(id, def);
        }
        return out;
    }

    /** 解析全部词条池；{@code affix_pools/} 前缀以外的文件静默跳过。 */
    public static Map<String, Pool> parsePools(List<ConfigFile> configs, List<String> warnings) {
        Map<String, Pool> out = new LinkedHashMap<>();
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        for (ConfigFile file : accept(configs, POOL_DIR)) {
            String path = file.path();
            JsonObject root = file.root();
            if (root == null) {
                sink.add(path + ": 根节点不是对象，已跳过");
                continue;
            }
            String id = resolveId(root, path, POOL_DIR, sink);
            if (id == null) continue;
            JsonObject slots = object(root, "slots", path, sink);
            List<Entry> entries = new ArrayList<>();
            // 顺序必须 prefix → suffix → core；组内保持 JSON 出现顺序（map 遍历序 = 写入序）
            for (String kind : KINDS) {
                JsonObject group = object(root, kind, path, sink);
                if (group == null) continue;
                for (Map.Entry<String, JsonElement> e : group.entrySet()) {
                    String affixId = trim(e.getKey());
                    if (affixId.isEmpty()) {
                        sink.add(path + ": " + kind + " 里有空的词条 id，已跳过");
                    } else if (!isNumber(e.getValue())) {
                        sink.add(path + ": " + kind + "." + affixId + " 的权重不是数字，已跳过");
                    } else {
                        entries.add(new Entry(affixId, e.getValue().getAsInt()));
                    }
                }
            }
            Pool pool = new Pool(id, strings(root, "applies_to", path, sink),
                    slots == null ? 0 : integer(slots, "prefix", 0, path, sink),
                    slots == null ? 0 : integer(slots, "suffix", 0, path, sink),
                    slots == null ? 0 : integer(slots, "core", 0, path, sink), entries);
            if (out.containsKey(id)) {
                sink.add(path + ": 词条池 id 重复（" + id + "），保留先出现的定义，本条已跳过");
                continue;
            }
            out.put(id, pool);
        }
        return out;
    }

    // ---- 取值助手：缺键用默认值，类型不符告警后同样用默认值，绝不抛 ----
    /** 只留 {@code dir}/{@code .json5} 的文件，保持原顺序。 */
    private static List<ConfigFile> accept(List<ConfigFile> configs, String dir) {
        List<ConfigFile> out = new ArrayList<>();
        if (configs == null) return out;
        for (ConfigFile file : configs) {
            String path = file == null ? null : file.path();
            if (path != null && path.startsWith(dir) && path.endsWith(SUFFIX)) out.add(file);
        }
        return out;
    }

    /** {@code root.id} 优先，其次路径去掉前缀与后缀；都不行返回 {@code null}（已告警）。 */
    private static String resolveId(JsonObject root, String path, String dir, List<String> w) {
        String fallback = trim(path.substring(dir.length(), path.length() - SUFFIX.length()));
        String id = string(root, "id", fallback, path, w);
        if (!id.isEmpty()) return id;
        if (!fallback.isEmpty()) return fallback;
        w.add(path + ": 无法确定 id，已跳过");
        return null;
    }

    private static String trim(String raw) { return raw == null ? "" : raw.trim(); }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static String string(JsonObject parent, String key, String fallback, String path, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return fallback;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return trim(e.getAsString());
        w.add(path + ": " + key + " 不是字符串，已用默认值 " + fallback);
        return fallback;
    }

    private static int integer(JsonObject parent, String key, int fallback, String path, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return fallback;
        if (isNumber(e)) return e.getAsInt();
        w.add(path + ": " + key + " 不是数字，已用默认值 " + fallback);
        return fallback;
    }

    /** 键不存在 → {@code null}（正常，不告警）；存在但不是对象 → 告警并按缺失处理。 */
    private static JsonObject object(JsonObject parent, String key, String path, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return null;
        if (e.isJsonObject()) return e.getAsJsonObject();
        w.add(path + ": " + key + " 不是对象，已忽略");
        return null;
    }

    /** 字符串数组；非字符串元素跳过并告警。 */
    private static List<String> strings(JsonObject parent, String key, String path, List<String> w) {
        List<String> out = new ArrayList<>();
        JsonElement e = parent.get(key);
        if (e == null) return out;
        if (!e.isJsonArray()) {
            w.add(path + ": " + key + " 不是数组，已忽略");
            return out;
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                out.add(trim(item.getAsString()));
            } else {
                w.add(path + ": " + key + " 里有非字符串项，已跳过");
            }
        }
        return out;
    }

    /**
     * {@code stats} 收「数字」与「曲线对象」两种形态，其它跳过 + 告警。
     *
     * <p>数字 = {@code StatValue.of(n, 0)}；对象 = {@code {base, per_item_level, spread, max_spread}}，
     * 缺键取 0。这是 {@code ITEM-MODEL.md} §2 的 {@code sharp: {base: 3.0, per_item_level: 0.08}}。
     */
    private static Map<String, StatValue> stats(JsonObject root, String path, List<String> w) {
        Map<String, StatValue> out = new LinkedHashMap<>();
        JsonObject obj = object(root, "stats", path, w);
        if (obj == null) return out;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String key = normalize(e.getKey());
            if (key == null) {
                w.add(path + ": stats." + e.getKey() + " 不是已知属性，已跳过");
                continue;
            }
            JsonElement v = e.getValue();
            StatValue value;
            if (isNumber(v)) {
                value = StatValue.of(v.getAsDouble(), 0);
            } else if (v != null && v.isJsonObject()) {
                JsonObject c = v.getAsJsonObject();
                value = new StatValue(
                        number(c, "base", path, e.getKey(), w),
                        number(c, "per_item_level", path, e.getKey(), w),
                        number(c, "spread", path, e.getKey(), w),
                        number(c, "max_spread", path, e.getKey(), w));
            } else {
                w.add(path + ": stats." + e.getKey() + " 既不是数字也不是曲线对象，已跳过");
                continue;
            }
            if (out.putIfAbsent(key, value) != null) {
                w.add(path + ": stats." + e.getKey() + " 与前面的键归一后重复（" + key + "），保留先出现的");
            }
        }
        return out;
    }

    /**
     * 属性键归一：与 {@code ItemContent.parse} 同一条规则（{@code AttributeSpecs.resolve} → 全名）。
     *
     * <p><b>为什么必须归一</b>：词条数值会并进 {@code ContentData.rolledStats}，
     * 而 {@code ItemFactory.modifiers} 用 {@code modifierId(属性路径)} 当 id —— 同一个属性
     * 同时出现 {@code attack_damage} 与 {@code minecraft:generic.attack_damage} 两种写法时，
     * 会往同一个 id 上加两条 modifier，属性面板直接翻倍。
     */
    private static String normalize(String raw) {
        if (raw == null) return null;
        var spec = com.redtale.project.core.attr.AttributeSpecs.resolve(raw.trim());
        return spec == null ? null : spec.id();
    }

    /**
     * R17 升可见性（零行为变更）：宝石（{@code ITEM-MODEL.md} §5.3）与套装的 {@code stats}
     * 必须走**同一条**解析与归一规则，否则「词条与宝石写同一个属性」会往同一个 modifier id
     * 加两条（面板翻倍，R11-b 的教训）。{@code SocketSpec} 直接吃这两个入口。
     */
    public static Map<String, StatValue> parseStats(JsonObject root, String path, List<String> warnings) {
        return stats(root, path, warnings);
    }

    /** R17 升可见性（零行为变更）：见 {@link #parseStats}。 */
    public static String normalizeKey(String raw) {
        return normalize(raw);
    }

    /** 曲线对象的子键：不是数字就告警并用 0（不炸 reload）。 */
    private static double number(JsonObject parent, String key, String path, String owner, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return 0;
        if (isNumber(e)) return e.getAsDouble();
        w.add(path + ": stats." + owner + "." + key + " 不是数字，已按 0 处理");
        return 0;
    }
}
