package com.redtale.project.core.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 法力池（M5 收口第一层，纯数据）：只记每个玩家的当前/上限法力和回复量，不读属性、不读配置、不碰 SkillEngine、不持久化。
 * 前提：只在服务端主线程调用，故用普通 HashMap，不加任何锁（单线程假设写在调用方）。键是 UUID，改名不影响账。
 */
public final class ManaPool {

    public static final double DEFAULT_MAX = 100.0D;

    private static final double MAX_REDUCTION = 0.9;

    public enum Reason { PAID, NOT_ENOUGH, NO_COST }

    public record Payment(boolean paid, double cost, double before, double after, Reason reason) {}

    private record Entry(double current, double max) {}

    private final Map<UUID, Entry> table = new HashMap<>();

    public ManaPool() {}

    /** 减免后实际消耗：{@code rawCost<=0} → 0；{@code reduction} 夹 [0,0.9]，结果地板 1.0（满减免不等于免费）。 */
    public static double effectiveCost(double rawCost, double reduction) {
        if (!(rawCost > 0.0)) {
            return 0.0;
        }
        double r = (Double.isNaN(reduction) || Double.isInfinite(reduction) || reduction < 0.0) ? 0.0 : reduction;
        if (r > MAX_REDUCTION) {
            r = MAX_REDUCTION;
        }
        double cost = Math.round(rawCost * (1.0 - r));
        return cost < 1.0 ? 1.0 : cost;
    }

    /** 当前法力；没记录过按满蓝算，与 {@link #max(UUID)} 自洽。 */
    public double current(UUID player) {
        Entry e = entry(player);
        return e == null ? DEFAULT_MAX : e.current;
    }

    /** 法力上限；没 {@code setMax}/{@code set} 过用 {@link #DEFAULT_MAX}。 */
    public double max(UUID player) {
        Entry e = entry(player);
        return e == null ? DEFAULT_MAX : e.max;
    }

    public boolean canAfford(UUID player, double rawCost, double reduction) {
        return effectiveCost(rawCost, reduction) <= current(player);
    }

    /** 扣蓝：免费不写账、不够不扣、够则扣；全程不抛。 */
    public Payment spend(UUID player, double rawCost, double reduction) {
        double cost = effectiveCost(rawCost, reduction);
        double cur = current(player);
        if (cost == 0.0D) {
            return new Payment(true, 0.0D, cur, cur, Reason.NO_COST);
        }
        if (cost > cur) {
            return new Payment(false, cost, cur, cur, Reason.NOT_ENOUGH);
        }
        double after = cur - cost;
        write(player, after, max(player));
        return new Payment(true, cost, cur, after, Reason.PAID);
    }

    /** 按秒回复，返回实际回复量（已封顶的差值，可能是 0）；小数余额留在 current 里，不取整、不做累积器。 */
    public double regen(UUID player, double perSecond, long ticks) {
        if (!(perSecond > 0.0) || Double.isInfinite(perSecond) || ticks <= 0L) {
            return 0.0D;
        }
        double before = current(player);
        double cap = max(player);
        double after = Math.min(cap, before + perSecond * ticks / 20.0D);
        if (!(after > before)) {
            return 0.0D;
        }
        write(player, after, cap);
        return after - before;
    }

    public void setMax(UUID player, double max) {
        double cap = capOf(max);
        write(player, clamp(entry(player) == null ? DEFAULT_MAX : entry(player).current, cap), cap);
    }

    public void set(UUID player, double current, double max) {
        double cap = capOf(max);
        write(player, clamp(sanitize(current), cap), cap);
    }

    /** 登录还原：只写当前值，上限沿用已记录的（未知玩家即 DEFAULT_MAX）。 */
    public void restore(UUID player, double current) {
        double cap = max(player);
        write(player, clamp(sanitize(current), cap), cap);
    }

    public void clear(UUID player) {
        if (player != null) {
            table.remove(player);
        }
    }

    public int tracked() {
        return table.size();
    }

    private Entry entry(UUID player) {
        return player == null ? null : table.get(player);
    }

    private void write(UUID player, double current, double max) {
        if (player != null) {
            table.put(player, new Entry(current, max));
        }
    }

    private static double sanitize(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? 0.0D : value;
    }

    /** 上限口径：NaN/Inf → 0，负数 → 0（`max<=0` 记 0，不回落 DEFAULT_MAX）。 */
    private static double capOf(double max) {
        double v = sanitize(max);
        return v < 0.0D ? 0.0D : v;
    }

    private static double clamp(double value, double cap) {
        if (value < 0.0D) {
            return 0.0D;
        }
        return value > cap ? cap : value;
    }
}
