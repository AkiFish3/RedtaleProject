package com.redtale.project.core.math;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.damage.MultiplierRules;

import java.util.List;

/**
 * {@code config/redtale_project/damage.json5} 的解析结果：闸门 + 异常保护（D16）
 * + L2 乘区的作用范围（D12 / D31）。
 *
 * <p>默认值全部「放开」：{@code caps} 全 0（关闭），只留 {@link Sanity} 的兜底。
 * 想收紧时改配置 + {@code /redtale reload}，不换 jar。
 *
 * @param caps       平衡闸门（默认全关）
 * @param sanity     异常保护（负数归零 / 1e9 硬顶 / 超阈值记日志）
 * @param multiplier 乘区白名单（只乘 {@code redtale_project:*}，别人的伤害放行）
 */
public record DamageRules(Caps caps, Sanity sanity, MultiplierRules multiplier) {

    public static final DamageRules DEFAULT =
            new DamageRules(Caps.OFF, Sanity.DEFAULT, MultiplierRules.DEFAULT);

    public static DamageRules parse(JsonObject root, List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }
        Caps caps = Caps.OFF;
        Sanity sanity = Sanity.DEFAULT;

        JsonElement capsEl = root.get("caps");
        if (capsEl != null && capsEl.isJsonObject()) {
            JsonObject o = capsEl.getAsJsonObject();
            double percent = num(o, "percentCap", Caps.OFF.percentCap(), warnings);
            double cond = num(o, "condCap", Caps.OFF.condCap(), warnings);
            int count = (int) num(o, "condCount", Caps.OFF.condCount(), warnings);
            double damage = num(o, "damageCap", Caps.OFF.damageCap(), warnings);
            // 负值视同关闭：写错配置不该把伤害打成 0（D16「结算层永不偷偷砍」）
            caps = new Caps(Math.max(0, percent), Math.max(0, cond), Math.max(0, count),
                    Math.max(0, damage));
            if (o.has("attackSpeedSoftCap")) {
                warnings.add("damage.json5 的 caps.attackSpeedSoftCap 已忽略："
                        + "攻速饱和点归 attack_speed.json5 的 soft_cap（D18，一处配置一处生效）");
            }
        }

        JsonElement sanityEl = root.get("sanity");
        if (sanityEl != null && sanityEl.isJsonObject()) {
            JsonObject o = sanityEl.getAsJsonObject();
            boolean guard = o.has("negativeGuard") && o.get("negativeGuard").isJsonPrimitive()
                    ? o.get("negativeGuard").getAsBoolean()
                    : Sanity.DEFAULT.negativeGuard();
            double ceiling = num(o, "hardCeiling", Sanity.DEFAULT.hardCeiling(), warnings);
            double threshold = num(o, "logThreshold", Sanity.DEFAULT.logThreshold(), warnings);
            double rate = num(o, "logSampleRate", Sanity.DEFAULT.logSampleRate(), warnings);
            sanity = new Sanity(guard, ceiling, threshold, Math.max(0, Math.min(1, rate)));
        }

        JsonElement multiplierEl = root.get("multiplier");
        MultiplierRules multiplier = MultiplierRules.DEFAULT;
        if (multiplierEl != null && multiplierEl.isJsonObject()) {
            multiplier = MultiplierRules.parse(multiplierEl.getAsJsonObject(), warnings);
        } else if (multiplierEl != null) {
            warnings.add("damage: multiplier 必须是对象，按默认处理");
        }

        return new DamageRules(caps, sanity, multiplier);
    }

    public String describe() {
        return "闸门 " + (caps.percentLimited() || caps.condLimited() || caps.countLimited()
                || caps.damageLimited() ? "已开启 " + caps : "全关（D16 默认）")
                + " · 兜底 ceiling " + AttributeNumbers.num(sanity.hardCeiling())
                + " · 日志阈值 " + AttributeNumbers.num(sanity.logThreshold())
                + "（采样 " + AttributeNumbers.num(sanity.logSampleRate() * 100) + "%）";
    }

    private static double num(JsonObject obj, String key, double fallback, List<String> warnings) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            warnings.add("damage: " + key + " 不是数字，按默认 " + fallback + " 处理");
            return fallback;
        }
        double v = el.getAsDouble();
        if (!Double.isFinite(v)) {
            warnings.add("damage: " + key + " 不是有限数，按默认 " + fallback + " 处理");
            return fallback;
        }
        return v;
    }
}
