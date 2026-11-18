package com.redtale.project.core.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * {@code progression.json5} 的解析结果（M7 全局规则，非内容）。
 *
 * <p>v1 只有一个字段：{@code mob_exp_multiplier}（击杀经验倍率）。放在独立文件而不是
 * {@code damage.json5} 里，是因为它属于经验面 —— 而经验面的其它字段（来源、曲线）都在
 * {@code levels/} / {@code jobs/} 里，这里只留「跨内容的全局旋钮」。
 *
 * <p>纯解析，零 MC import、零日志；坏值回落缺省并往 {@code warnings} 塞一行。
 */
public record ProgressRules(double mobExpMultiplier) {

    public static final double DEFAULT_MOB_EXP_MULTIPLIER = 1.0;

    public static final ProgressRules DEFAULT = new ProgressRules(DEFAULT_MOB_EXP_MULTIPLIER);

    /** 免部署路径：{@code root == null}（文件缺失）直接给缺省，不告警。 */
    public static ProgressRules parse(JsonObject root, java.util.List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }
        double mult = DEFAULT_MOB_EXP_MULTIPLIER;
        JsonElement el = root.get("mob_exp_multiplier");
        if (el != null) {
            boolean isNum = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
            double v = isNum ? el.getAsDouble() : Double.NaN;
            if (!isNum || !Double.isFinite(v)) {
                warn(warnings, "progression.json5: mob_exp_multiplier 不是数字，已改用 "
                        + DEFAULT_MOB_EXP_MULTIPLIER);
            } else if (v < 0.0) {
                warn(warnings, "progression.json5: mob_exp_multiplier 为负数（" + v + "），已改用 0");
                mult = 0.0;
            } else if (v > 1.0e9) {
                // D16：只有物理硬顶，不做平衡钳制
                warn(warnings, "progression.json5: mob_exp_multiplier 超过 1e9，已截到 1e9");
                mult = 1.0e9;
            } else {
                mult = v;
            }
        }
        return new ProgressRules(mult);
    }

    public String describe() {
        return "击杀经验 ×" + mobExpMultiplier;
    }

    private static void warn(java.util.List<String> warnings, String msg) {
        if (warnings != null) {
            warnings.add(msg);
        }
    }
}
