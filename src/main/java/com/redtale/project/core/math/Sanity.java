package com.redtale.project.core.math;

/**
 * 异常保护（D16 / DAMAGE-PIPELINE §7.2）。**不参与平衡**，只在工程事故时兜底：
 * 负数归零、{@code 1e9} 硬顶、超阈值记日志（只观测，不拦）。
 *
 * @param negativeGuard 结果为负 → 归零
 * @param hardCeiling   真正的兜底天花板（默认 1e9，只有溢出才会碰到）
 * @param logThreshold  单次伤害超过它 → 记日志，不修改数值
 * @param logSampleRate 采样率，避免刷屏；1.0 = 全记
 */
public record Sanity(boolean negativeGuard, double hardCeiling, double logThreshold, double logSampleRate) {

    public static final Sanity DEFAULT = new Sanity(true, 1.0e9, 10000.0, 0.1);

    public boolean ceilingEnabled() {
        return hardCeiling > 0;
    }

    public boolean loggingEnabled() {
        return logThreshold > 0;
    }
}
