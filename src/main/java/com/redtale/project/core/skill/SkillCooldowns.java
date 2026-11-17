package com.redtale.project.core.skill;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 技能冷却表（M5-A 门槛层，纯数据）：只记账，不读注册表、不调脚本引擎、不碰 Minecraft 类型；
 * 时间由调用方传入 tick。前提：只在服务端主线程调用，故不加 synchronized、不用 ConcurrentHashMap。
 * 键是 {@code (UUID, 技能 id)}：按玩家名记账会让改名变成清冷却的手段。
 */
public final class SkillCooldowns {

    private static final double MAX_REDUCTION = 0.9;

    private final Map<UUID, Map<String, Long>> table = new HashMap<>();

    public SkillCooldowns() {}

    /** 冷却减免后实际 tick 数：{@code cd<=0} → 0；{@code reduction} 夹 [0,0.9]，结果地板 1 tick。 */
    public static int effectiveTicks(int cooldownTicks, double reduction) {
        if (cooldownTicks <= 0) {
            return 0;
        }
        double r = (Double.isNaN(reduction) || Double.isInfinite(reduction) || reduction < 0.0) ? 0.0 : reduction;
        if (r > MAX_REDUCTION) {
            r = MAX_REDUCTION;
        }
        int ticks = (int) Math.round(cooldownTicks * (1.0 - r));
        return ticks < 1 ? 1 : ticks;
    }

    public boolean ready(UUID player, String skillId, long nowTick) {
        return remaining(player, skillId, nowTick) <= 0L;
    }

    /** 剩余 tick；已到期或无记录返回 0，并顺手删掉过期的那条。 */
    public long remaining(UUID player, String skillId, long nowTick) {
        if (invalid(player, skillId)) {
            return 0L;
        }
        Map<String, Long> mine = table.get(player);
        Long end = mine == null ? null : mine.get(skillId);
        if (end == null) {
            return 0L;
        }
        if (nowTick >= end) {
            mine.remove(skillId);
            if (mine.isEmpty()) {
                table.remove(player);
            }
            return 0L;
        }
        return end - nowTick;
    }

    /** 记一次冷却；减免后不足 1 tick 视为无冷却（不留 0 tick 记录）。 */
    public void start(UUID player, String skillId, int cooldownTicks, double reduction, long nowTick) {
        if (invalid(player, skillId)) {
            return;
        }
        int ticks = effectiveTicks(cooldownTicks, reduction);
        if (ticks <= 0) {
            forget(player, skillId);
            return;
        }
        Map<String, Long> mine = table.get(player);
        if (mine == null) {
            mine = new HashMap<>();
            table.put(player, mine);
        }
        mine.put(skillId, nowTick + ticks);
    }

    /** 不在冷却则记账并返回 true；冷却中返回 false 且不动表。 */
    public boolean tryStart(UUID player, String skillId, int cooldownTicks, double reduction, long nowTick) {
        if (invalid(player, skillId)) {
            return true;
        }
        if (remaining(player, skillId, nowTick) > 0L) {
            return false;
        }
        start(player, skillId, cooldownTicks, reduction, nowTick);
        return true;
    }

    /** 删掉所有已到期记录，返回删除条数（玩家下线 / 定期清理用）。 */
    public int prune(long nowTick) {
        int removed = 0;
        Iterator<Map<String, Long>> players = table.values().iterator();
        while (players.hasNext()) {
            Map<String, Long> mine = players.next();
            Iterator<Long> ends = mine.values().iterator();
            while (ends.hasNext()) {
                if (ends.next() <= nowTick) {
                    ends.remove();
                    removed++;
                }
            }
            if (mine.isEmpty()) {
                players.remove();
            }
        }
        return removed;
    }

    public void clear(UUID player) {
        if (player != null) {
            table.remove(player);
        }
    }

    public void clearAll() {
        table.clear();
    }

    /** 当前记录总数；不主动 prune。 */
    public int size() {
        int total = 0;
        for (Map<String, Long> mine : table.values()) {
            total += mine.size();
        }
        return total;
    }

    private void forget(UUID player, String skillId) {
        Map<String, Long> mine = table.get(player);
        if (mine == null) {
            return;
        }
        mine.remove(skillId);
        if (mine.isEmpty()) {
            table.remove(player);
        }
    }

    private static boolean invalid(UUID player, String skillId) {
        return player == null || skillId == null || skillId.isBlank();
    }
}
