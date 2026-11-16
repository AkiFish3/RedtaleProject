package com.redtale.project.core.damage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L2 乘区的作用范围（{@code damage.json5} 的 {@code multiplier} 段，D12 / D31）。
 *
 * <p>核心是一条**白名单**：只有我们自己命名空间的 {@code DamageType} 才会被乘，
 * 其它 mod 的伤害原样放行。理由写在 {@code docs/DAMAGE-PIPELINE.md} §12.3 ——
 * 12~19 个 mod 在同一个 {@code LivingIncomingDamageEvent} 里加减乘，
 * 我们多乘一次别人算好的数，等于替别人改平衡，且排查时永远说不清是谁干的。
 *
 * <p>判据用 {@code ResourceLocation} 的字符串（{@code "namespace:path"}），
 * 不用 {@code instanceof} 对方的类：对方换内部实现我们不受影响，
 * 而且这层逻辑不碰 Minecraft 类型，{@code gradle selftest} 能直接测。
 *
 * @param damageTypes          生效的伤害类型，形如 {@code redtale_project:*}（命名空间通配）、
 *                             {@code minecraft:player_attack}（精确）、{@code *}（全部）
 * @param passthroughExternal  白名单外的伤害是否记进观测（只计数与记最后一次，不改值）
 * @param percentAttributes    贡献百分比池的**无条件**属性（逻辑 id，经 {@code AttributeSpecs.resolve} 解析）
 * @param conditionalAttributes 条件乘区：条件键（{@code pvp} / {@code pve} / {@code undead}，见
 *                             {@link TargetClass}）→ 该条件下追加的属性清单。
 *                             与 {@code percentAttributes} 一起进**同一个**百分比池相加（先加后乘，不连乘）。
 */
public record MultiplierRules(List<String> damageTypes, boolean passthroughExternal,
                              List<String> percentAttributes,
                              Map<String, List<String>> conditionalAttributes,
                              List<Category> categories) {

    /** 全通配。只用于「纯观测」场景，正常配置永远不要写它。 */
    public static final String ALL = "*";

    /** 技能·物理伤害类型（启动期 datapack registry，见 {@code data/redtale_project/damage_type/}）。 */
    public static final String SKILL_PHYSICAL = "redtale_project:skill_physical";
    /** 技能·法术伤害类型（同上；与物理分开是为了让两条属性池各算各的，R26-b）。 */
    public static final String SKILL_MAGICAL = "redtale_project:skill_magical";

    /**
     * 伤害类别 → 百分比属性池。
     *
     * <p>存在的理由（R26-b / Q-018 默认 A）：技能伤害按类别分轴——物理技能吃 {@code physical_damage}，
     * 法术技能吃 {@code magical_damage}。分轴**不等于**按技能各注册一个 {@code damage_type}：
     * {@code damage_type} 是启动期 registry，按内容注册就得重启，违反 D17。
     * 所以只冻结两个通用类型，再用这张表把「类型 → 属性池」的映射做成配置。
     *
     * <p>匹配语义与 {@link #appliesTo} 完全一致（{@code *} / {@code ns:*} / 精确 id），
     * **按声明顺序 first-match**，一条都没命中就落回 {@link #percentAttributes}。
     *
     * @param match      伤害类型模式
     * @param attributes 命中后进百分比池的属性逻辑 id（空列表 = 命中但该类别不吃任何百分比）
     */
    public record Category(String match, List<String> attributes) {
        public Category {
            match = match == null ? "" : match.trim();
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }
    }

    /**
     * 默认：只乘 {@code redtale_project:*}，别人的伤害放行但记一笔。
     *
     * <p>条件项默认就开着（D1「全做」），因为三条条件属性的基值都是 0 ——
     * 没有内容写它们时行为与关着完全一致，不需要额外开关。
     */
    public static final MultiplierRules DEFAULT = new MultiplierRules(
            List.of("redtale_project:*"), true, List.of("physical_damage"),
            Map.of("pve", List.of("pve_damage"),
                    "pvp", List.of("pvp_damage"),
                    "undead", List.of("undead_damage")),
            List.of(new Category(SKILL_MAGICAL, List.of("magical_damage")),
                    new Category(SKILL_PHYSICAL, List.of("physical_damage"))));

    /** 兼容三参构造（M1 时代的测试与旧调用方），条件乘区取空 = 只有无条件项。 */
    public MultiplierRules(List<String> damageTypes, boolean passthroughExternal,
                           List<String> percentAttributes) {
        this(damageTypes, passthroughExternal, percentAttributes, Map.of(), List.of());
    }

    /** 兼容四参构造（R26 之前的调用方），类别表取空 = 全部落回 {@code percentAttributes}。 */
    public MultiplierRules(List<String> damageTypes, boolean passthroughExternal,
                           List<String> percentAttributes,
                           Map<String, List<String>> conditionalAttributes) {
        this(damageTypes, passthroughExternal, percentAttributes, conditionalAttributes, List.of());
    }

    public MultiplierRules {
        damageTypes = List.copyOf(damageTypes);
        percentAttributes = List.copyOf(percentAttributes);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (conditionalAttributes != null) {
            conditionalAttributes.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null) {
                    copy.put(k, List.copyOf(v));
                }
            });
        }
        conditionalAttributes = Collections.unmodifiableMap(copy);
        List<Category> cats = new ArrayList<>();
        if (categories != null) {
            for (Category c : categories) {
                if (c != null && !c.match().isEmpty()) {
                    cats.add(c);
                }
            }
        }
        categories = List.copyOf(cats);
    }

    /**
     * 这条伤害要不要我们出手。
     *
     * <p>匹配规则（从宽到严）：{@code *} → {@code ns:*} → 精确 id。大小写不敏感，
     * 因为 ResourceLocation 的 namespace/path 本来就只允许小写，写大写八成是手误。
     */
    public boolean appliesTo(String damageTypeId) {
        String id = normalise(damageTypeId);
        if (id == null) {
            return false;
        }
        for (String entry : damageTypes) {
            if (matches(entry, id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这条伤害该吃哪个百分比池：类别表 first-match，未命回落 {@link #percentAttributes}。
     *
     * <p>与 {@link #appliesTo} 分开是刻意的：前者决定「要不要我们出手」，后者决定「出手时吃哪条属性」。
     * 一个类型可以「要出手但一条属性都不吃」（类别表里给它一个空属性列表），这不矛盾。
     */
    public List<String> attributesFor(String damageTypeId) {
        String id = normalise(damageTypeId);
        if (id == null) {
            return percentAttributes;
        }
        for (Category c : categories) {
            if (matches(c.match(), id)) {
                return c.attributes();
            }
        }
        return percentAttributes;
    }

    /** 归一：trim + 小写 + 裸路径补 {@code minecraft:}；空/null → {@code null}。 */
    private static String normalise(String damageTypeId) {
        if (damageTypeId == null || damageTypeId.isEmpty()) {
            return null;
        }
        String id = damageTypeId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;      // 裸路径当原版，与 /damage 的省略写法一致
        }
        return id;
    }

    /** {@code *} / {@code ns:*} / 精确 id；{@code id} 必须已归一。 */
    private static boolean matches(String pattern, String id) {
        String e = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty()) {
            return false;
        }
        if (e.equals(ALL)) {
            return true;
        }
        if (e.endsWith(":*")) {
            return id.startsWith(e.substring(0, e.length() - 1));
        }
        return id.equals(e);
    }

    /** 一行摘要，给 {@code /redtale damage} 与日志用。 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("伤害类型 ").append(String.join(" / ", damageTypes))
                .append(" · 白名单外").append(passthroughExternal ? "只观测不改值" : "完全忽略")
                .append(" · 百分比属性 ")
                .append(percentAttributes.isEmpty() ? "无" : String.join(" + ", percentAttributes));
        if (!categories.isEmpty()) {
            sb.append(" · 类别池 ");
            boolean first = true;
            for (Category c : categories) {
                if (!first) {
                    sb.append(" / ");
                }
                first = false;
                sb.append(c.match()).append("=").append(
                        c.attributes().isEmpty() ? "无" : String.join("+", c.attributes()));
            }
        }
        if (conditionalAttributes.isEmpty()) {
            sb.append(" · 条件乘区 关");
        } else {
            sb.append(" · 条件乘区 ");
            boolean first = true;
            for (Map.Entry<String, List<String>> e : conditionalAttributes.entrySet()) {
                if (!first) {
                    sb.append(" / ");
                }
                first = false;
                sb.append(e.getKey()).append('=').append(
                        e.getValue().isEmpty() ? "无" : String.join("+", e.getValue()));
            }
        }
        return sb.toString();
    }

    /**
     * 解析 {@code damage.json5} 的 {@code multiplier} 段。
     *
     * <p>与 {@code AttackSpeedRules} 同一套失败策略：字段写歪就用默认值兜住 + 收一条告警，
     * 不让一份写坏的配置把整个 reload 打停（R1-a 的分级失败原则）。
     */
    public static MultiplierRules parse(JsonObject root, List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }

        List<String> types = new ArrayList<>();
        JsonElement typesEl = root.get("damage_types");
        if (typesEl != null && typesEl.isJsonArray()) {
            for (JsonElement el : typesEl.getAsJsonArray()) {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String v = el.getAsString().trim();
                    if (!v.isEmpty()) {
                        types.add(v);
                    }
                } else {
                    warnings.add("damage: multiplier.damage_types 里有非字符串项，已跳过");
                }
            }
        } else if (typesEl != null) {
            warnings.add("damage: multiplier.damage_types 必须是数组，按默认 "
                    + DEFAULT.damageTypes + " 处理");
        }
        if (types.isEmpty()) {
            types = new ArrayList<>(DEFAULT.damageTypes);
        }
        if (types.contains(ALL)) {
            warnings.add("damage: multiplier.damage_types 含 \"*\" —— 会把别人的伤害也乘一遍，"
                    + "除非你确定，否则改成 redtale_project:*");
        }

        boolean passthrough = DEFAULT.passthroughExternal;
        JsonElement passEl = root.get("passthrough_external");
        if (passEl != null && passEl.isJsonPrimitive() && passEl.getAsJsonPrimitive().isBoolean()) {
            passthrough = passEl.getAsBoolean();
        }

        List<String> stats = new ArrayList<>();
        JsonElement statsEl = root.get("percent_attributes");
        if (statsEl != null && statsEl.isJsonArray()) {
            for (JsonElement el : statsEl.getAsJsonArray()) {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String v = el.getAsString().trim();
                    if (!v.isEmpty()) {
                        stats.add(v);
                    }
                }
            }
        } else if (statsEl != null) {
            warnings.add("damage: multiplier.percent_attributes 必须是数组，按默认 "
                    + DEFAULT.percentAttributes + " 处理");
        }

        // 条件乘区：{ pve: ["pve_damage"], pvp: [...], undead: [...] }
        // 缺这一整段 = 取默认（三条全开）；显式写 {} = 关掉条件乘区。
        Map<String, List<String>> conditional = new LinkedHashMap<>(DEFAULT.conditionalAttributes);
        JsonElement condEl = root.get("conditional_attributes");
        if (condEl != null && condEl.isJsonObject()) {
            Map<String, List<String>> parsed = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : condEl.getAsJsonObject().entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (!TargetClass.KEYS.contains(key)) {
                    warnings.add("damage: multiplier.conditional_attributes 的键 \"" + entry.getKey()
                            + "\" 不认识（只能是 pvp / pve / undead），已跳过");
                    continue;
                }
                List<String> ids = new ArrayList<>();
                JsonElement val = entry.getValue();
                if (val != null && val.isJsonArray()) {
                    for (JsonElement el : val.getAsJsonArray()) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String v = el.getAsString().trim();
                            if (!v.isEmpty()) {
                                ids.add(v);
                            }
                        } else {
                            warnings.add("damage: conditional_attributes." + key + " 里有非字符串项，已跳过");
                        }
                    }
                } else if (val != null) {
                    warnings.add("damage: conditional_attributes." + key + " 必须是数组，已跳过");
                    continue;
                }
                parsed.put(key, ids);
            }
            conditional = parsed;
        } else if (condEl != null) {
            warnings.add("damage: multiplier.conditional_attributes 必须是对象，按默认 "
                    + DEFAULT.conditionalAttributes.keySet() + " 处理");
        }

        // 类别池：按声明顺序 first-match。缺这一整段 = 取默认（物理/法术两轴）；
        // 显式写 [] = 关闭分轴（全部落回 percent_attributes）。
        List<Category> cats = new ArrayList<>(DEFAULT.categories);
        JsonElement catEl = root.get("category_attributes");
        if (catEl != null && catEl.isJsonArray()) {
            List<Category> parsed = new ArrayList<>();
            for (JsonElement el : catEl.getAsJsonArray()) {
                if (el == null || !el.isJsonObject()) {
                    warnings.add("damage: multiplier.category_attributes 里有非对象项，已跳过");
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                JsonElement typeEl = obj.get("type");
                boolean typeOk = typeEl != null && typeEl.isJsonPrimitive()
                        && typeEl.getAsJsonPrimitive().isString()
                        && !typeEl.getAsString().isBlank();
                if (!typeOk) {
                    warnings.add("damage: multiplier.category_attributes 有项缺 type（或不是非空字符串），已跳过");
                    continue;
                }
                String type = typeEl.getAsString().trim();
                List<String> ids = new ArrayList<>();
                JsonElement attrEl = obj.get("attributes");
                if (attrEl != null && attrEl.isJsonArray()) {
                    for (JsonElement attr : attrEl.getAsJsonArray()) {
                        if (attr != null && attr.isJsonPrimitive() && attr.getAsJsonPrimitive().isString()) {
                            String v = attr.getAsString().trim();
                            if (!v.isEmpty()) {
                                ids.add(v);
                            }
                        } else {
                            warnings.add("damage: category_attributes[" + type + "].attributes 里有非字符串项，已跳过");
                        }
                    }
                } else if (attrEl != null) {
                    warnings.add("damage: category_attributes[" + type + "].attributes 必须是数组，该项已跳过");
                    continue;
                }
                parsed.add(new Category(type, ids));
            }
            cats = parsed;
        } else if (catEl != null) {
            warnings.add("damage: multiplier.category_attributes 必须是数组，按默认 "
                    + DEFAULT.categories.size() + " 条处理");
        }

        return new MultiplierRules(types, passthrough, stats, conditional, cats);
    }

    /** 供解析器判断「写的是不是数组」的便利方法（保持 record 简洁，外部少 import gson）。 */
    static boolean isArray(JsonElement el) {
        return el instanceof JsonArray;
    }
}
