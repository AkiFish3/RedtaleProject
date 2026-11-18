package com.redtale.project.core.progression;

/**
 * 等级曲线纯函数：M7 全层唯一的经验数学真相。
 * 无状态、可重入、零外部依赖；不解析配置、不写日志、不读文件。
 */
public final class ExpCurve {
    public static final long MAX_STEP_EXP = 1_000_000_000L;

    private final boolean geometric;
    private final int maxLevel;
    private final double base;
    private final double growth;
    private final double exponent;

    private ExpCurve(boolean geometric, int maxLevel, double base, double growth, double exponent) {
        this.geometric = geometric;
        this.maxLevel = Math.max(2, maxLevel);
        this.base = base > 0.0 ? base : 100.0;
        this.growth = growth > 1.0 ? growth : 1.12;
        this.exponent = exponent > 0.0 ? exponent : 1.5;
    }

    public static ExpCurve geometric(int maxLevel, double base, double growth) {
        return new ExpCurve(true, maxLevel, base, growth, 1.5);
    }

    public static ExpCurve polynomial(int maxLevel, double base, double exponent) {
        return new ExpCurve(false, maxLevel, base, 1.12, exponent);
    }

    public int maxLevel() {
        return maxLevel;
    }

    public long expForLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        if (level >= maxLevel) {
            return 0L;
        }
        double raw = geometric ? base * Math.pow(growth, level - 1) : base * Math.pow(level, exponent);
        long value = Math.round(raw);
        return value > MAX_STEP_EXP ? MAX_STEP_EXP : value;
    }

    public long totalExpForLevel(int level) {
        if (level <= 1) {
            return 0L;
        }
        if (level >= maxLevel) {
            return totalExpAtMax();
        }
        return sumThrough(level);
    }

    public long totalExpAtMax() {
        return sumThrough(maxLevel);
    }

    public int levelFor(long totalExp) {
        if (totalExp <= 0L) {
            return 1;
        }
        if (totalExp >= totalExpForLevel(maxLevel)) {
            return maxLevel;
        }
        int lo = 1;
        int hi = maxLevel;
        while (lo < hi) {
            int mid = lo + ((hi - lo + 1) >>> 1);
            if (totalExpForLevel(mid) <= totalExp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Σ_{i=1..level-1} expForLevel(i)；只被 totalExp* 调用，避免与 totalExpAtMax 互调递归。 */
    private long sumThrough(int level) {
        long sum = 0L;
        for (int i = 1; i < level; i++) {
            sum += expForLevel(i);
        }
        return sum;
    }
}
