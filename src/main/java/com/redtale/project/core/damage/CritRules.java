package com.redtale.project.core.damage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * {@code config/redtale_project/crit.json5} 的解析结果：**暴击入口的开关与属性映射**。
 *
 * <p>为什么单独一份文件而不是塞进 {@code damage.json5}：暴击是「出手时」的事，
 * 乘区是「落地时」的事，两者的事件点、生效对象（攻击者 vs 目标）都不同；
 * 放同一份配置里，改暴击的人会以为自己动了伤害结算。
 *
 * <p><b>默认全开</b>：暴击率的属性基值是 0（没装备就不暴击），暴击倍率基值是 2.0
 * （见 {@code AttributeSpecs.cb("critical_strike_power", 2.0, ...)}，Q-008 照搬上游）。
 * 所以「默认全开」在没有内容投放时等价于「只有原版跳跃暴击，且倍率从 1.5 变 2.0」——
 * 这一条差异是本文件 {@code apply_to_vanilla_crit} 的作用域。
 *
 * @param enabled            总开关。关掉 = 完全不碰 {@code CriticalHitEvent}
 * @param chanceAttribute    暴击率属性（逻辑 id，0~1）
 * @param powerAttribute     暴击倍率属性（1.0 = 不额外加成）
 * @param applyToVanillaCrit 原版跳跃暴击是否也换成我们的倍率（false = 保留原版 1.5）
 * @param forceOnRoll        掷中概率时把「原版没判暴击」强制判成暴击
 * @param maxChance          暴击率上限，0 = 不限（D16：默认不设平衡上限）
 */
public record CritRules(boolean enabled, String chanceAttribute, String powerAttribute,
                        boolean applyToVanillaCrit, boolean forceOnRoll, double maxChance) {

    public static final CritRules DEFAULT = new CritRules(
            true, "critical_strike_chance", "critical_strike_power", true, true, 0.0);

    public static CritRules parse(JsonObject root, List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }
        boolean enabled = bool(root, "enabled", DEFAULT.enabled(), warnings);
        String chance = str(root, "chance_attribute", DEFAULT.chanceAttribute(), warnings);
        String power = str(root, "power_attribute", DEFAULT.powerAttribute(), warnings);
        boolean applyVanilla = bool(root, "apply_to_vanilla_crit", DEFAULT.applyToVanillaCrit(), warnings);
        boolean force = bool(root, "force_on_roll", DEFAULT.forceOnRoll(), warnings);
        double maxChance = num(root, "max_chance", DEFAULT.maxChance(), warnings);
        // 负上限视同关闭：写错配置不该把暴击率锁成 0（D16「结算层永不偷偷砍」）
        return new CritRules(enabled, chance, power, applyVanilla, force, Math.max(0, maxChance));
    }

    /** 暴击率归一：负数归零；{@code maxChance > 0} 时才截顶（D16 默认不截）。 */
    public double clampChance(double raw) {
        if (!Double.isFinite(raw)) {
            return 0;
        }
        double v = Math.max(0, raw);
        return maxChance > 0 ? Math.min(v, maxChance) : v;
    }

    public String describe() {
        return (enabled ? "开启" : "关闭")
                + " · 暴击率 ← " + chanceAttribute
                + " · 倍率 ← " + powerAttribute
                + " · 原版跳跃暴击" + (applyToVanillaCrit ? "套我们的倍率" : "保留 1.5")
                + " · 掷中强制暴击 " + (forceOnRoll ? "是" : "否")
                + " · 暴击率上限 " + (maxChance > 0
                ? String.format(java.util.Locale.ROOT, "%.0f%%", maxChance * 100) : "不限（D16 默认）");
    }

    private static boolean bool(JsonObject obj, String key, boolean fallback, List<String> warnings) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            warnings.add("crit: " + key + " 不是布尔值，按默认 " + fallback + " 处理");
            return fallback;
        }
        return el.getAsBoolean();
    }

    private static String str(JsonObject obj, String key, String fallback, List<String> warnings) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            warnings.add("crit: " + key + " 不是字符串，按默认 " + fallback + " 处理");
            return fallback;
        }
        String v = el.getAsString();
        return v.isBlank() ? fallback : v;
    }

    private static double num(JsonObject obj, String key, double fallback, List<String> warnings) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            warnings.add("crit: " + key + " 不是数字，按默认 " + fallback + " 处理");
            return fallback;
        }
        double v = el.getAsDouble();
        if (!Double.isFinite(v)) {
            warnings.add("crit: " + key + " 不是有限数，按默认 " + fallback + " 处理");
            return fallback;
        }
        return v;
    }
}
