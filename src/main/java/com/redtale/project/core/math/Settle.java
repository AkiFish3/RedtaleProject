package com.redtale.project.core.math;

import java.util.List;

/**
 * 唯一的伤害结算函数（D20 硬约束：**tooltip 与实打共用这一份实现**）。
 *
 * <pre>
 * L1 平加：由 AttributeModifier 完成，这里不重复
 * L2 乘区：out = base × (1 + Σpercent) × Π cond[i]      ← 先加后乘，不连乘百分比
 * L3 兜底：负数归零 → 1e9 硬顶 → 超阈值记日志（只观测）
 * </pre>
 *
 * <p>三条不变量：
 * <ol>
 *   <li>输入里的 {@code NaN} 一律当 0 处理，{@code ±Infinity} 当 0 处理 —— 绝不让一格坏配置把整条
 *       管线变成 {@code NaN}（{@code NaN} 会污染后续所有比较，伤害显示成 "NaN"）。</li>
 *   <li>闸门默认全 0 = 关闭，所以默认路径上 {@code out == base × (1+Σp) × Πc} 原样通过。</li>
 *   <li>{@code settle()} 没有 {@code setAmount} 的语义：它算的是「我们这一层的值」，
 *       调用方只能拿它去**乘**别人的结果，不能反过来覆盖（DAMAGE-PIPELINE §2）。</li>
 * </ol>
 */
public final class Settle {

    private Settle() {
    }

    /**
     * 一次结算的完整结果，供日志 / tooltip 展开用。
     *
     * @param value          最终值（已过 L3 兜底）
     * @param percentApplied 实际生效的百分比池（可能被 percentCap 截过）
     * @param condsApplied   实际生效的条件乘区个数
     * @param overflow       触碰到硬顶
     * @param negative       曾被归零（原始结果为负）
     * @param raw            未过 L3 的原始值，排查用
     */
    public record Result(double value, double percentApplied, int condsApplied,
                         boolean overflow, boolean negative, double raw) {
    }

    public static double value(double base, double sumPercent, List<Double> conds, Caps caps, Sanity sanity) {
        return settle(base, sumPercent, conds, caps, sanity).value();
    }

    public static Result settle(double base, double sumPercent, List<Double> conds, Caps caps, Sanity sanity) {
        Caps c = caps != null ? caps : Caps.OFF;
        Sanity s = sanity != null ? sanity : Sanity.DEFAULT;

        double b = finite(base);
        double p = finite(sumPercent);
        if (c.percentLimited()) {
            p = Math.min(p, c.percentCap());
        }

        double condProduct = 1.0;
        int applied = 0;
        if (conds != null) {
            for (double rawCond : conds) {
                if (c.countLimited() && applied >= c.condCount()) {
                    break;
                }
                double cond = finite(rawCond);
                if (cond == 0.0) {
                    continue;
                }
                if (c.condLimited()) {
                    cond = Math.min(cond, c.condCap());
                }
                condProduct *= (1.0 + cond);
                applied++;
            }
        }

        double raw = b * (1.0 + p) * condProduct;
        // 平衡闸门（默认关闭）：单次伤害绝对值上限。它是**平衡**手段，不是兜底，
        // 所以先于 L3 的 1e9 硬顶执行；两者都关时 raw 原样通过。
        if (c.damageLimited()) {
            raw = Math.min(raw, c.damageCap());
        }
        boolean negative = false;
        if (s.negativeGuard() && raw < 0) {
            raw = 0;
            negative = true;
        }
        boolean overflow = false;
        if (!Double.isFinite(raw)) {
            raw = 0;
            overflow = true;
        } else if (s.ceilingEnabled() && raw > s.hardCeiling()) {
            raw = s.hardCeiling();
            overflow = true;
        }
        return new Result(raw, p, applied, overflow, negative, raw);
    }

    /** 该结果是否该写日志（超阈值，按采样率）。**不改变数值**。 */
    public static boolean shouldLog(Result result, Sanity sanity, double roll) {
        Sanity s = sanity != null ? sanity : Sanity.DEFAULT;
        if (!s.loggingEnabled() || result == null) {
            return false;
        }
        if (result.value() < s.logThreshold()) {
            return false;
        }
        return roll < Math.max(0.0, Math.min(1.0, s.logSampleRate()));
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
