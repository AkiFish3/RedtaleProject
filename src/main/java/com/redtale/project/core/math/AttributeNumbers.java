package com.redtale.project.core.math;

import java.util.Locale;

/**
 * 数字格式化：最多 3 位小数、去尾零。纯 Java。
 *
 * <p>存在的理由：{@code 12.400000000000002} 这种浮点噪声直接丢给玩家会被当成 bug。
 * 结算与显示共用同一套格式化，避免「tooltip 写 12.4、日志写 12.400000001」。
 */
public final class AttributeNumbers {

    private AttributeNumbers() {
    }

    public static String num(double v) {
        if (!Double.isFinite(v)) {
            return String.valueOf(v);
        }
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String signed(double v) {
        return (v >= 0 ? "+" : "") + num(v);
    }
}
