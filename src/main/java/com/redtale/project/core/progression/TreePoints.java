package com.redtale.project.core.progression;

import java.util.*;

/** 技能树点数派生与可点判定：纯函数、零 MC 依赖（D25 L3，契约 SKILL-TREE §5.2）。 */
public final class TreePoints {
    public record State(int maxPoints, int used, int available, int taken, int unknown) {}
    public enum Status { OK, UNKNOWN_NODE, ALREADY_TAKEN, MISSING_PREREQ, NO_POINTS }

    private TreePoints() {}

    /** 不看等级的旧口径：上限恒等于 {@code max_points}（R53 行为，probe 与离线断言用）。 */
    public static State state(SkillTreeSpec.Definition def, Collection<String> selected) {
        return def == null ? new State(0, 0, 0, 0, 0)
                : stateWithCap(def, selected, capFor(def.maxPoints(), 0, 0));
    }

    /**
     * 带等级的点数状态（M7 出口：等级换点数）。{@code level} 是主等级，上限由 {@link #capFor} 派生。
     * 点数仍是**派生值**（SKILL-TREE §5 第 2 条），不落存档；等级回档 → 可用点数自动回落。
     */
    public static State state(SkillTreeSpec.Definition def, Collection<String> selected, int level) {
        return def == null ? new State(0, 0, 0, 0, 0)
                : stateWithCap(def, selected, capFor(def.maxPoints(), def.pointsPerLevel(), level));
    }

    /**
     * 等级 → 点数上限（R54-a）。{@code points_per_level <= 0} = 关掉等级门槛，退回 {@code max_points}；
     * 否则 {@code 赚到 = points_per_level × level}，再与 {@code max_points}（{@code >0} 时）取小。
     */
    public static int capFor(int maxPoints, int pointsPerLevel, int level) {
        if (pointsPerLevel <= 0) return Math.max(0, maxPoints);
        long earned = (long) pointsPerLevel * Math.max(0, level);
        int e = earned > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) earned;
        return maxPoints > 0 ? Math.min(maxPoints, e) : e;
    }

    private static State stateWithCap(SkillTreeSpec.Definition def, Collection<String> selected, int cap) {
        Map<String, SkillTreeSpec.Node> nodes = def.nodes();
        int used = 0, taken = 0, unknown = 0;
        for (String id : dedupe(selected)) {
            SkillTreeSpec.Node node = nodes == null ? null : nodes.get(id);
            if (node == null) { unknown++; continue; }
            used += node.cost(); taken++;
        }
        return new State(cap, used, cap - used, taken, unknown);
    }

    public static List<String> missingPrereqs(SkillTreeSpec.Definition def, Collection<String> selected, String nodeId) {
        List<String> out = new ArrayList<>();
        if (def == null || nodeId == null || def.nodes() == null) return List.copyOf(out);
        SkillTreeSpec.Node node = def.nodes().get(nodeId);
        if (node == null || node.requires() == null) return List.copyOf(out);
        Set<String> sel = dedupe(selected);
        Set<String> seen = new LinkedHashSet<>();
        for (String req : node.requires())
            if (!sel.contains(req) && seen.add(req)) out.add(req);
        return List.copyOf(out);
    }

    /** 不看等级的旧口径：上限恒等于 {@code max_points}（R53 行为）。 */
    public static Status canTake(SkillTreeSpec.Definition def, Collection<String> selected, String nodeId) {
        return canTakeWithCap(def, selected, nodeId, def == null ? 0 : Math.max(0, def.maxPoints()));
    }

    /** 带等级的判定：{@code NO_POINTS} 按 {@link #capFor} 派生的上限判，不看 {@code max_points} 裸值。 */
    public static Status canTake(SkillTreeSpec.Definition def, Collection<String> selected, String nodeId,
                                 int level) {
        return canTakeWithCap(def, selected, nodeId,
                def == null ? 0 : capFor(def.maxPoints(), def.pointsPerLevel(), level));
    }

    private static Status canTakeWithCap(SkillTreeSpec.Definition def, Collection<String> selected,
                                         String nodeId, int cap) {
        if (def == null || nodeId == null || def.nodes() == null) return Status.UNKNOWN_NODE;
        SkillTreeSpec.Node node = def.nodes().get(nodeId);
        if (node == null) return Status.UNKNOWN_NODE;
        if (dedupe(selected).contains(nodeId)) return Status.ALREADY_TAKEN;
        if (!missingPrereqs(def, selected, nodeId).isEmpty()) return Status.MISSING_PREREQ;
        if (node.cost() > cap - stateWithCap(def, selected, cap).used()) return Status.NO_POINTS;
        return Status.OK;
    }

    public static Map<String, Status> statusAll(SkillTreeSpec.Definition def, Collection<String> selected) {
        Map<String, Status> out = new LinkedHashMap<>();
        if (def != null && def.nodes() != null)
            for (String id : def.nodes().keySet()) out.put(id, canTake(def, selected, id));
        return Collections.unmodifiableMap(new LinkedHashMap<>(out));
    }

    private static Set<String> dedupe(Collection<String> selected) {
        Set<String> out = new LinkedHashSet<>();
        if (selected != null) out.addAll(selected);
        return out;
    }
}
