package com.redtale.project.items;

import com.redtale.project.core.attr.AttributeSpec;

import java.util.Locale;
import java.util.Set;

/**
 * 属性的**显示**单位（不是 modifier 运算单位）。
 *
 * <p>这里踩过的坑值得记下来：一开始想按「百分比属性 → {@code ADD_MULTIPLIED_TOTAL}」写 modifier，
 * 但自建属性基值全是 0，乘 0 永远是 0 —— 一件写着「物理伤害 +20%」的剑会一点都不加。
 * 所以**所有内容数值一律 {@code ADD_VALUE}**，百分比只是给人看的显示层（与 Q-014 的
 * 「0.2 = +20%」同口径）。真正把它当百分比用，是 L2 乘区读属性值之后的事。
 *
 * <p>集合来源：{@code docs/ATTRIBUTE-REGISTRY.md} §2 里说明列带 {@code %} 的行，逐条抄下来。
 * 没列到的一律按平加显示（护甲、生命、攻击伤害、暴击率 0.12 这类本来就不是百分比）。
 */
public final class StatFormat {

    private StatFormat() {
    }

    private static final Set<String> PERCENT = Set.of(
            "physical_damage", "weapon_damage", "projectile_damage",
            "pvp_damage", "pve_damage", "undead_damage",
            "damage_reduction", "block_cooldown_reduction", "dodge_cooldown_reduction",
            "parry_cooldown_reduction", "pvp_defense", "pve_defense", "magic_resistance",
            "reflect_damage", "magical_damage", "spell_vampirism", "cooldown_reduction",
            "healing_power", "fire_damage", "frost_damage", "lightning_damage", "arcane_damage",
            "nature_damage", "shadow_damage", "holy_damage", "water_damage", "earth_damage",
            "sneaking_speed", "water_movement_efficiency", "mining_efficiency",
            "additional_experience", "skill_damage", "spell_resistance",
            "air_damage", "soul_damage", "blood_damage", "unholy_damage", "true_damage",
            "hunger_efficiency", "mana_cost_reduction", "charge_speed", "projectile_velocity",
            "healing_received", "armor_durability", "potion_duration",
            "critical_damage_resistance", "lifesteal", "armor_penetration");

    public static boolean isPercent(AttributeSpec spec) {
        return spec != null && PERCENT.contains(spec.id());
    }

    /** {@code +20%} / {@code +7} / {@code +0.12}，按属性语义选单位。 */
    public static String signed(AttributeSpec spec, double value) {
        String sign = value >= 0 ? "+" : "";
        if (isPercent(spec)) {
            return sign + num(value * 100) + "%";
        }
        return sign + num(value);
    }

    /** {@code 12.4} 这类去掉浮点噪声的短数字。 */
    public static String num(double v) {
        if (!Double.isFinite(v)) {
            return "0";
        }
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "0" : s;
    }
}
