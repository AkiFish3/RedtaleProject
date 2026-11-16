package com.redtale.project.core.math;

/**
 * 攻速换算（D19）。**纯函数**，被三处共用，所以只允许一份实现：
 * <ul>
 *   <li>mixin {@code Player#getCurrentItemAttackStrengthDelay}（实际生效点）；</li>
 *   <li>{@code /redtale attr attack_speed}（来源拆解 + tick 换算）；</li>
 *   <li>tooltip（D20 的「攻速 3.4 tick · 5.9 次/秒」）。</li>
 * </ul>
 *
 * <p>两条约定：
 * <ol>
 *   <li>{@code softCap <= 0} → 饱和曲线不存在（D19 默认），raw 原样通过。</li>
 *   <li>地板 {@code minDelayTicks} 是**防除零**，不是平衡阀：{@code getAttackCooldownProgress}
 *       做的是 {@code ticker / delay}，delay 为 0 会得到 {@code NaN}/{@code Infinity}，
 *       表现就是「每 tick 都能出手」。所以它永远保留，且不低于 {@link #ABSOLUTE_MIN_DELAY}。</li>
 * </ol>
 */
public final class AttackSpeedCurve {

    /** 绝对地板：再往下 delay 已经没有物理意义（1 tick = 20 次/秒）。 */
    public static final double ABSOLUTE_MIN_DELAY = 0.05;

    private AttackSpeedCurve() {
    }

    /**
     * 原版语义：{@code delay = 20 / attack_speed}，反解出「次/秒」。
     * delay ≤ 0 或非有限值 → 返回 {@link Double#POSITIVE_INFINITY}，交给 {@link #delayTicks} 落地板。
     */
    public static double perSecondFromDelay(double delayTicks) {
        if (!(delayTicks > 0) || !Double.isFinite(delayTicks)) {
            return Double.POSITIVE_INFINITY;
        }
        return 20.0 / delayTicks;
    }

    /** 饱和曲线：{@code softCap * (1 - e^(-raw/softCap))}；{@code softCap <= 0} 直接返回 raw。 */
    public static double effectivePerSecond(double rawPerSecond, double softCap) {
        if (!Double.isFinite(rawPerSecond)) {
            return Double.POSITIVE_INFINITY;
        }
        if (rawPerSecond <= 0) {
            return 0;
        }
        if (softCap <= 0) {
            return rawPerSecond;
        }
        return softCap * (1.0 - Math.exp(-rawPerSecond / softCap));
    }

    /** 次/秒 → 冷却 tick，并施加地板。地板配置为 0/负数时用 1 tick（D19 默认）。 */
    public static double delayTicks(double perSecond, double minDelayTicks) {
        double floor = Math.max(minDelayTicks > 0 ? minDelayTicks : 1.0, ABSOLUTE_MIN_DELAY);
        if (!(perSecond > 0) || !Double.isFinite(perSecond)) {
            return floor;
        }
        return Math.max(20.0 / perSecond, floor);
    }

    /** 一步到位：原始 delay → 处理后的 delay（mixin 用的就是这个）。 */
    public static double apply(double originalDelayTicks, double softCap, double minDelayTicks) {
        double raw = perSecondFromDelay(originalDelayTicks);
        double eff = effectivePerSecond(raw, softCap);
        return delayTicks(eff, minDelayTicks);
    }
}
