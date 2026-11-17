package com.redtale.project.mobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.math.StatValue;
import com.redtale.project.items.AffixPools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 怪物定义解析器（M6 第一层，纯解析）：身份 / 基础属性 / 装备 / 技能引用四组字段；不碰注册表与 MC 类型，失败只记 warnings。 */
public final class MobSpec {
    /** 8 个原型 id，不可变。 */
    public static final Set<String> BASES = Set.of("melee", "ranged", "caster", "summoner",
            "charger", "flyer", "elite", "ambient");
    /** 一条技能引用：{@code skillId} 原样保留（不补命名空间）。 */
    public record SkillRef(String skillId, int cooldownTicks, double chance) {}
    /** 六槽装备：值 = 物品 id，{@code ""} = 该槽空。 */
    public record Equipment(String mainhand, String offhand, String head,
                            String chest, String legs, String feet) {
        /** 六槽全空。 */
        public static final Equipment EMPTY = new Equipment("", "", "", "", "", "");
        /** 认 6 个槽名，未知槽 / 空值 → {@code ""}。 */
        public String slot(String name) {
            return switch (name == null ? "" : name) {
                case "mainhand" -> mainhand;
                case "offhand" -> offhand;
                case "head" -> head;
                case "chest" -> chest;
                case "legs" -> legs;
                case "feet" -> feet;
                default -> "";
            };
        }
    }
    /**
     * 一条怪物定义；{@code attributes} 由 {@link AffixPools#parseStats} 归一，与 {@code skills} 同为不可变。
     *
     * <p>{@code undead}（R39 新增）：这只怪算不算「亡灵」。它不参与任何数值结算，只决定
     * {@link MobEntityTags} 要不要把该 {@code base} 的 EntityType 追加进 {@code #minecraft:undead} ——
     * 而那张 tag 才是 {@code undead_damage} 条件乘区（{@code TargetClass}）的唯一判据。
     * 放在 <b>原型</b>粒度（同一 base 的任一怪声明 undead 就把整个原型打上）是因为 EntityType 按 base 注册，
     * tag 也只能按 EntityType 打；这是 R37-b「id 即 base」的必然结果，不是妥协。
     */
    public record Definition(String id, String name, String base, double scale, int level, boolean undead,
                             Map<String, StatValue> attributes,
                             Equipment equipment, List<SkillRef> skills, String source) {}
    private static final String DIR = "mobs/";
    private static final String SUFFIX = ".json5";
    private static final List<String> SLOTS =
            List.of("mainhand", "offhand", "head", "chest", "legs", "feet");
    private static final double MIN_SCALE = 0.25D, MAX_SCALE = 4.0D;
    private static final int MAX_LEVEL = 1000, DEFAULT_COOLDOWN = 100, MAX_COOLDOWN = 1_000_000;
    private MobSpec() {}
    /** 解析全部怪物定义；非 {@code mobs/*.json5} 静默跳过；返回 LinkedHashMap（保序）+ 不可变包装；null → 空表。 */
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
            if (root == null) { sink.add(path + ": 根节点不是对象，已跳过"); continue; }
            String id = id(root, path, sink);
            if (id == null) continue;
            String base = base(root, path, sink);
            if (base == null) continue;
            String prev = seen.putIfAbsent(id, path);
            if (prev != null) {
                sink.add(path + ": 怪物 id 重复（" + id + "，先出现在 " + prev + "），保留先出现的定义，本条已跳过");
                continue;
            }
            out.put(id, new Definition(id, name(root, id, path, sink), base, scale(root, path, sink),
                    level(root, path, sink), undead(root, path, sink), attributes(root, path, sink),
                    equipment(root, path, sink), skills(root, path, sink), path));
        }
        return Collections.unmodifiableMap(out);
    }
    /** {@code id} 必填：非字符串 / trim 后为空 → 整条跳过 + 1 条 WARN（不补命名空间）。 */
    private static String id(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("id");
        if (!isString(e) || e.getAsString().trim().isEmpty()) {
            w.add(path + ": id 缺失或不是字符串（必填），已跳过这条怪物");
            return null;
        }
        return e.getAsString().trim();
    }
    /** 缺省 = {@code id}；非字符串 → 回落 {@code id} + WARN；空串按缺省处理（不告警）。 */
    private static String name(JsonObject root, String id, String path, List<String> w) {
        JsonElement e = root.get("name");
        if (e == null) return id;
        if (!isString(e)) { w.add(path + ": name 不是字符串，已用 id 作名称"); return id; }
        String name = e.getAsString();
        return name.trim().isEmpty() ? id : name;
    }
    /** {@code base} 必填且必须在 {@link #BASES} 内，否则整条跳过 + 1 条 WARN。 */
    private static String base(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("base");
        if (!isString(e) || !BASES.contains(e.getAsString().trim())) {
            w.add(path + ": base 缺失或不在白名单（必填），已跳过这条怪物");
            return null;
        }
        return e.getAsString().trim();
    }
    /** 缺省 1.0；非数字 → 告警 + 1.0；{@code <=0} / 非有限 → 静默 1.0；合法值夹 {@code [0.25,4.0]} 不告警。 */
    private static double scale(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("scale");
        if (e == null) return 1.0D;
        if (!isNumber(e)) { w.add(path + ": scale 不是数字，已用 1.0"); return 1.0D; }
        double value = e.getAsDouble();
        if (!Double.isFinite(value) || value <= 0.0D) return 1.0D;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
    /** 缺省 1；小数向下取整；夹到 {@code [1,1000]}（不告警）；非数字 → 1 + WARN。 */
    private static int level(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("level");
        if (e == null) return 1;
        if (!isNumber(e)) { w.add(path + ": level 不是数字，已用 1"); return 1; }
        return Math.max(1, Math.min(MAX_LEVEL, (int) Math.floor(e.getAsDouble())));
    }
    /**
     * {@code undead}：缺省 {@code false}；非布尔 → {@code false} + 1 条 WARN（含 path）。
     *
     * <p>为什么容错到「静默降级成 false」而不是整条跳过：漏标亡灵只会让这只怪少吃 {@code undead_damage}，
     * 不会算错数值方向；而整条跳过等于「这只怪不生成」，代价大得多。告警留着，运营看得见。
     */
    private static boolean undead(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("undead");
        if (e == null) return false;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        w.add(path + ": undead 不是布尔（写了 " + e + "），已按 false 处理");
        return false;
    }
    /** 走 {@link AffixPools#parseStats} 的同一条归一与曲线规则（不另写一份、不降级成裸 double）；缺失 / 非对象 → 空表不告警。 */
    private static Map<String, StatValue> attributes(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("attributes");
        if (e == null || !e.isJsonObject()) return Map.of();
        JsonObject attrs = e.getAsJsonObject();
        JsonObject body = attrs.has("stats") ? attrs : new JsonObject();
        if (body != attrs) body.add("stats", attrs);
        return Collections.unmodifiableMap(AffixPools.parseStats(body, path + ".attributes", w));
    }
    /** 六槽：非空字符串才算数，否则该槽 "" + WARN；未知键 1 条 WARN（只报键名）。 */
    private static Equipment equipment(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("equipment");
        if (e == null) return Equipment.EMPTY;
        if (!e.isJsonObject()) { w.add(path + ": equipment 不是对象，已按六槽全空处理"); return Equipment.EMPTY; }
        JsonObject obj = e.getAsJsonObject();
        String[] values = new String[SLOTS.size()];
        for (int i = 0; i < values.length; i++) {
            String slot = SLOTS.get(i);
            JsonElement v = obj.get(slot);
            if (v != null && !(isString(v) && !v.getAsString().trim().isEmpty())) {
                w.add(path + ": equipment." + slot + " 不是非空字符串，已记为空");
            }
            values[i] = isString(v) ? v.getAsString().trim() : "";
        }
        for (String key : obj.keySet()) {
            if (!SLOTS.contains(key)) w.add(path + ": equipment 未知槽名 " + key + "，已忽略");
        }
        return new Equipment(values[0], values[1], values[2], values[3], values[4], values[5]);
    }
    /** 可选数组：缺失 → 空表；非数组 → 空表 + WARN；坏项只丢自己。 */
    private static List<SkillRef> skills(JsonObject root, String path, List<String> w) {
        JsonElement e = root.get("skills");
        if (e == null) return List.of();
        if (!e.isJsonArray()) { w.add(path + ": skills 不是数组，已按空表处理"); return List.of(); }
        List<SkillRef> out = new ArrayList<>();
        int i = 0;
        for (JsonElement item : e.getAsJsonArray()) {
            SkillRef ref = skill(item, path + ".skills[" + i++ + "]", w);
            if (ref != null) out.add(ref);
        }
        return List.copyOf(out);
    }
    /** 单项：必须对象且 {@code skill} 为非空字符串，否则跳过该项 + WARN。 */
    private static SkillRef skill(JsonElement item, String src, List<String> w) {
        if (item == null || !item.isJsonObject()) { w.add(src + ": 不是对象，已跳过该项"); return null; }
        JsonObject obj = item.getAsJsonObject();
        JsonElement s = obj.get("skill");
        if (!isString(s) || s.getAsString().trim().isEmpty()) {
            w.add(src + ": skill 缺失或不是非空字符串，已跳过该项");
            return null;
        }
        return new SkillRef(s.getAsString().trim(), cooldown(obj, src, w), chance(obj, src, w));
    }
    /** 缺省 100；非数字 → 告警 + 缺省；夹到 {@code [0,1000000]}。 */
    private static int cooldown(JsonObject obj, String src, List<String> w) {
        JsonElement e = obj.get("cooldown_ticks");
        if (e == null) return DEFAULT_COOLDOWN;
        if (!isNumber(e)) { w.add(src + ": cooldown_ticks 不是数字，已用 " + DEFAULT_COOLDOWN); return DEFAULT_COOLDOWN; }
        double value = e.getAsDouble();
        if (value <= 0.0D || !Double.isFinite(value)) return 0;
        return (int) Math.min(value, MAX_COOLDOWN);
    }
    /** 缺省 1.0；非数字 / 非有限 → 1.0 + WARN；夹到 {@code [0.0,1.0]} 不告警。 */
    private static double chance(JsonObject obj, String src, List<String> w) {
        JsonElement e = obj.get("chance");
        if (e == null) return 1.0D;
        if (!isNumber(e)) { w.add(src + ": chance 不是数字，已用 1.0"); return 1.0D; }
        double value = e.getAsDouble();
        if (!Double.isFinite(value)) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }
    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
}