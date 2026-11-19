package com.redtale.project.mobs;

/**
 * per_tick_cap 数值规则的唯一纯函数实现（M6）。
 * 配置解析与运行期钳制共用此处口径，避免两处规则各自漂移。
 * 接线（把两处旧规则改成委托）由 S7 负责，本类零接线。
 */
public final class SpawnBudget {
    public static final int DEFAULT_CAP = 8;
    public static final int MIN_CAP = 1;
    public static final int MAX_CAP = 4096;

    /** 运行期钳制：clamp 到 [1,4096]。负→1，超→4096，区间内原样。 */
    public static int clamp(int value) {
        if (value < MIN_CAP) return MIN_CAP;
        if (value > MAX_CAP) return MAX_CAP;
        return value;
    }

    /** 配置数值合法化：非有限/非整数/越界 → DEFAULT_CAP，否则 (int) raw。 */
    public static int fromConfig(double raw) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return DEFAULT_CAP;
        if (raw != Math.rint(raw)) return DEFAULT_CAP;
        if (raw < MIN_CAP || raw > MAX_CAP) return DEFAULT_CAP;
        return (int) raw;
    }

    /** 一个 tick 内可处理的件数 = min(clamp(cap), max(0, queueSize))。 */
    public static int takePerTick(int cap, int queueSize) {
        if (queueSize <= 0) return 0;
        int bounded = clamp(cap);
        return bounded < queueSize ? bounded : queueSize;
    }

    /** 排空队列所需 tick 数 = ceil(queueSize / takePerTick(...))；queueSize<=0 → 0。 */
    public static int ticksToDrain(int cap, int queueSize) {
        if (queueSize <= 0) return 0;
        int take = takePerTick(cap, queueSize);
        return (queueSize + take - 1) / take;
    }

    /** 观察描述串：每 tick 上限 N · 队列 Q 件 · 预计 T tick 排空。 */
    public static String describe(int cap, int queueSize) {
        int n = clamp(cap);
        int q = queueSize <= 0 ? 0 : queueSize;
        int t = ticksToDrain(cap, queueSize);
        return "每 tick 上限 " + String.valueOf(n) + " · 队列 " + String.valueOf(q)
                + " 件 · 预计 " + String.valueOf(t) + " tick 排空";
    }
}
