package com.redtale.project.core.math;

import java.util.function.DoubleSupplier;

/**
 * 四段式数值：{@code {base, scale, spread, maxSpread}}（上游 MMOItems 的物品数值模型）。
 *
 * <pre>
 * 值 = base + scale × 等级 + spread
 * spread 在生成时一次性 roll 出来并**存进物品数据**，之后不再变 —— 所以同一件装备
 * 谁拿到都是同一个数，tooltip 与结算天然一致。
 * </pre>
 *
 * <p>为什么 spread 要 clamp 到 {@code maxSpread}：上游允许「roll 范围」和「允许的最大偏差」
 * 分开配（先 roll 再钳），这样调平衡时可以只压极端值而不缩小方差。
 *
 * <p>纯 Java，不 import 任何 Minecraft 类 —— 由 {@code gradle selftest} 离线验证。
 */
public record StatValue(double base, double scale, double spread, double maxSpread) {

    public static final StatValue ZERO = new StatValue(0, 0, 0, 0);

    /** 未 roll 的裸值（{@code spread} 视为 0）。 */
    public static StatValue of(double base, double scale) {
        return new StatValue(base, scale, 0, 0);
    }

    /** 该等级下的值（spread 已 clamp）。 */
    public double valueAt(double level) {
        return base + scale * level + clampedSpread();
    }

    public double clampedSpread() {
        if (maxSpread <= 0) {
            return spread;
        }
        return Math.max(-maxSpread, Math.min(maxSpread, spread));
    }

    /**
     * 生成时摇一次：把 {@code spread} 换成 {@code [-spread, +spread]} 上的均匀随机数，再 clamp。
     * 返回新实例，原实例不变（配置是共享的，绝不能就地改）。
     *
     * @param random 返回 {@code [0,1)}；传 {@code null} 表示不摇（固定取 +spread 的中间 0）
     */
    public StatValue roll(DoubleSupplier random) {
        if (spread == 0 || random == null) {
            return this;
        }
        double unit = random.getAsDouble();
        if (!Double.isFinite(unit)) {
            unit = 0.5;
        }
        double rolled = (unit * 2.0 - 1.0) * spread;
        // 摇完立刻 clamp 并存下来：物品数据里的 spread 就是最终生效值，
        // 这样 tooltip / 结算 / 日志三处读同一个数（clampedSpread 只是二次保险）。
        if (maxSpread > 0) {
            rolled = Math.max(-maxSpread, Math.min(maxSpread, rolled));
        }
        return new StatValue(base, scale, rolled, maxSpread);
    }

    /** 展示用：{@code 7（+0.5/级，±2）}。 */
    public String describe() {
        StringBuilder sb = new StringBuilder(AttributeNumbers.num(base));
        if (scale != 0) {
            sb.append("（").append(scale > 0 ? "+" : "").append(AttributeNumbers.num(scale)).append("/级");
            if (spread != 0 || maxSpread != 0) {
                sb.append("，±").append(AttributeNumbers.num(Math.max(spread, maxSpread)));
            }
            sb.append("）");
        } else if (spread != 0 || maxSpread != 0) {
            sb.append("（±").append(AttributeNumbers.num(Math.max(spread, maxSpread))).append("）");
        }
        return sb.toString();
    }
}
