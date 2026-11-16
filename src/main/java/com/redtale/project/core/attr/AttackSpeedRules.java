package com.redtale.project.core.attr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code config/redtale_project/attack_speed.json5} 的解析结果（D18 声明式攻速来源表）。
 *
 * <p>**只声明「谁能加攻速」，不猜别人做了什么**：所有条目最终都以
 * {@code ADD_MULTIPLIED_TOTAL} 写进 {@code minecraft:generic.attack_speed}，
 * 所以咖啡因 / 茶 / Ferocity / 双持 / 我们自己的规则，全部在同一个属性池里相加，
 * {@code /attribute} 与 {@code /redtale attr} 看得见同一份数值。
 *
 * <p>三条实现约束（DAMAGE-PIPELINE §4.2.3）：
 * <ol>
 *   <li>modifier id 固定：{@code redtale_project:haste_attack_speed} 等。等级变化时**先移除再加**，
 *       绝不叠加泄漏。</li>
 *   <li>{@code minecraft:haste} 只能由 {@link #hasteAttackSpeedPerLevel} 控制。写在
 *       {@code effect_attack_speed} 里的同名条目会被忽略并告警（否则一份配置两条路径 = 双倍）。</li>
 *   <li>别人已经给的（咖啡因 +20%、茶 +5%、Ferocity 0.10/层、双持 ×1.2）默认写 0，
 *       避免「喝咖啡 +40%」。</li>
 * </ol>
 *
 * @param hasteAttackSpeedPerLevel 急迫每级提供的攻速倍率（D19 默认 0.10）
 * @param effectRules              其它「效果 → 攻速」映射
 * @param softCap                  饱和点，0 = 关闭（D19 默认 0）
 * @param minDelayTicks            物理地板，默认 1.0 tick（防除零，不是平衡阀）
 */
public record AttackSpeedRules(
        double hasteAttackSpeedPerLevel,
        Map<String, EffectRule> effectRules,
        double softCap,
        double minDelayTicks) {

    /** 一条效果规则：固定值 + 每级增量（{@code flat + per_level × 等级}）。 */
    public record EffectRule(double flat, double perLevel) {

        public double amount(int amplifierZeroBased) {
            return flat + perLevel * (amplifierZeroBased + 1);
        }

        public boolean isZero() {
            return flat == 0 && perLevel == 0;
        }
    }

    /** 配置缺失时的默认值 —— 与 {@code docs/DAMAGE-PIPELINE.md} §4.2.3 的示例一致。 */
    public static final AttackSpeedRules DEFAULT = new AttackSpeedRules(
            0.10,
            Map.of(
                    "minecraft:haste", new EffectRule(0.0, 0.0),
                    "farmersrespite:caffeinated", new EffectRule(0.0, 0.0)
            ),
            0.0,
            1.0);

    public AttackSpeedRules {
        effectRules = Map.copyOf(effectRules);
    }

    /** 急迫在 {@code amplifier}（0 基）时的攻速加成；无急迫或规则为 0 → 0。 */
    public double hasteAmount(int amplifierZeroBased) {
        if (amplifierZeroBased < 0 || hasteAttackSpeedPerLevel == 0) {
            return 0;
        }
        return hasteAttackSpeedPerLevel * (amplifierZeroBased + 1);
    }

    public boolean hasteEnabled() {
        return hasteAttackSpeedPerLevel != 0;
    }

    /**
     * 解析一份配置。写错字段不抛异常 —— 用默认值兜住并把问题塞进 {@code warnings}，
     * 这样一份写坏的攻速表不会让整个 {@code /redtale reload} 失败（R1-a 的分级失败原则）。
     */
    public static AttackSpeedRules parse(JsonObject root, List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }
        double haste = readDouble(root, "haste_attack_speed_per_level",
                DEFAULT.hasteAttackSpeedPerLevel, warnings);
        double softCap = readDouble(root, "soft_cap", DEFAULT.softCap, warnings);
        double floor = readDouble(root, "min_attack_delay_ticks", DEFAULT.minDelayTicks, warnings);

        if (haste < 0) {
            warnings.add("attack_speed: haste_attack_speed_per_level 为负（" + haste + "），按 0 处理");
            haste = 0;
        }
        if (softCap < 0) {
            warnings.add("attack_speed: soft_cap 为负（" + softCap + "），按 0（关闭）处理");
            softCap = 0;
        }
        if (floor <= 0) {
            warnings.add("attack_speed: min_attack_delay_ticks <= 0 会让 delay 除零（每 tick 出手），"
                    + "按默认 1.0 处理");
            floor = 1.0;
        }

        Map<String, EffectRule> rules = new LinkedHashMap<>();
        rules.put("minecraft:haste", new EffectRule(0.0, 0.0));
        JsonElement effects = root.get("effect_attack_speed");
        if (effects != null && effects.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : effects.getAsJsonObject().entrySet()) {
                String id = normalizeEffectId(e.getKey());
                if (!e.getValue().isJsonObject()) {
                    warnings.add("attack_speed: " + id + " 的值必须是对象（{flat: 0} 或 {per_level: 0}）");
                    continue;
                }
                JsonObject obj = e.getValue().getAsJsonObject();
                double flat = readDouble(obj, "flat", 0.0, warnings);
                double perLevel = readDouble(obj, "per_level", 0.0, warnings);
                if (id.equals("minecraft:haste")) {
                    // 默认配置里就带这一行（值 0），是给人看的「这里能配」；只有真写了非零才告警，
                    // 否则每次 reload 都报一条无意义告警，久了就没人看告警了。
                    if ((flat != 0 || perLevel != 0) && haste != 0) {
                        warnings.add("attack_speed: effect_attack_speed 里的 minecraft:haste 已忽略 —— "
                                + "急迫只走 haste_attack_speed_per_level 一条路径，否则会双倍计");
                    }
                    continue;
                }
                rules.put(id, new EffectRule(flat, perLevel));
            }
        }

        return new AttackSpeedRules(haste, rules, softCap, floor);
    }

    /** 一行摘要，给 {@code /redtale speed} 与日志用。 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("急迫 ").append(num(hasteAttackSpeedPerLevel)).append("/级（I = +")
                .append(num(hasteAttackSpeedPerLevel * 100)).append("%）");
        sb.append(" · soft_cap ").append(softCap <= 0 ? "0（不饱和）" : num(softCap));
        sb.append(" · 地板 ").append(num(minDelayTicks)).append(" tick");
        long active = effectRules.values().stream().filter(r -> !r.isZero()).count();
        sb.append(" · 其它效果规则 ").append(active).append("/").append(effectRules.size()).append(" 条生效");
        return sb.toString();
    }

    private static String normalizeEffectId(String raw) {
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "redtale_project:" + id;
    }

    private static double readDouble(JsonObject obj, String key, double fallback, List<String> warnings) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            warnings.add("attack_speed: " + key + " 不是数字，按默认 " + fallback + " 处理");
            return fallback;
        }
        double v = el.getAsDouble();
        if (!Double.isFinite(v)) {
            warnings.add("attack_speed: " + key + " 不是有限数，按默认 " + fallback + " 处理");
            return fallback;
        }
        return v;
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
