package com.redtale.project.core.damage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 最近若干次伤害结算的环形记录，只服务 {@code /redtale damage} 与日志。
 *
 * <p>存在的理由与 {@code AttributeBreakdown} 一样：**让「到底乘了多少」可被看见**。
 * 伤害是最难复盘的一类数值 —— 玩家说「这一刀不对」，没有 trace 就只能靠猜。
 *
 * <p>三条约束：
 * <ol>
 *   <li>固定容量（{@value #CAPACITY} 条），只留摘要，不持有实体引用 —— 不会拖住实体卸载。</li>
 *   <li>只记数值，不改任何东西。{@code changed} 只统计「因子真的是 1 以外」的次数。</li>
 *   <li>全部 {@code synchronized}：事件在服务端线程跑，命令也可能从 RCON 线程进。</li>
 * </ol>
 */
public final class DamageTrace {

    private static final int CAPACITY = 8;

    private static final Deque<Entry> RECENT = new ArrayDeque<>(CAPACITY);
    /** 白名单外那些「原样通过」的进场值，同样固定容量。只留数字，用来肉眼对账。 */
    private static final Deque<Double> PASSTHROUGH = new ArrayDeque<>(CAPACITY);

    private static long seen;
    private static long inWhitelist;
    private static long changed;
    private static String lastPassthrough = "-";

    private DamageTrace() {
    }

    /**
     * 一次结算摘要。
     *
     * @param damageType 伤害类型 id（{@code namespace:path}）
     * @param base       进场值（别人算完、我们动手前）
     * @param percent    我们从属性池取到的百分比之和
     * @param factor     实际乘上去的因子（{@code 1.0} = 没动）
     * @param value      结算后的值
     * @param overflow   是否碰到 1e9 硬顶（D16 只观测）
     */
    public record Entry(String damageType, double base, double percent, double factor,
                        double value, boolean overflow) {
    }

    /**
     * 白名单外：只记一笔，不改值。
     *
     * <p>连 {@code base} 也留一份（R22）：没有客户端时，验「附魔/词条数值有没有真的进结算」
     * 唯一可靠的读数就是这里 —— 同怪同武器换一级附魔，看这串数字差多少。
     */
    public static synchronized void skip(String damageType, double base) {
        seen++;
        lastPassthrough = damageType;
        if (PASSTHROUGH.size() >= CAPACITY) {
            PASSTHROUGH.removeFirst();
        }
        PASSTHROUGH.addLast(base);
    }

    /** 最近几次「原样通过」的进场值，新的在后。 */
    public static synchronized List<Double> recentPassthrough() {
        return new ArrayList<>(PASSTHROUGH);
    }

    public static synchronized void record(Entry entry) {
        seen++;
        inWhitelist++;
        if (entry.factor() != 1.0) {
            changed++;
        }
        if (RECENT.size() >= CAPACITY) {
            RECENT.removeFirst();
        }
        RECENT.addLast(entry);
    }

    public static synchronized List<Entry> recent() {
        return new ArrayList<>(RECENT);
    }

    public static synchronized long seen() {
        return seen;
    }

    public static synchronized long inWhitelist() {
        return inWhitelist;
    }

    public static synchronized long changed() {
        return changed;
    }

    public static synchronized String lastPassthrough() {
        return lastPassthrough;
    }

    /** reload 后归零，避免把上一份配置的数字当成这一份的成绩。 */
    public static synchronized void reset() {
        RECENT.clear();
        seen = 0;
        inWhitelist = 0;
        changed = 0;
        lastPassthrough = "-";
        PASSTHROUGH.clear();
    }
}
