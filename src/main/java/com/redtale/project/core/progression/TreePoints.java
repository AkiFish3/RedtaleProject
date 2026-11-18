package com.redtale.project.core.progression;

import java.util.*;

/** 技能树点数派生与可点判定：纯函数、零 MC 依赖（D25 L3，契约 SKILL-TREE §5.2）。 */
public final class TreePoints {
    public record State(int maxPoints, int used, int available, int taken, int unknown) {}
    public enum Status { OK, UNKNOWN_NODE, ALREADY_TAKEN, MISSING_PREREQ, NO_POINTS }

    private TreePoints() {}

    public static State state(SkillTreeSpec.Definition def, Collection<String> selected) {
        if (def == null) return new State(0, 0, 0, 0, 0);
        Map<String, SkillTreeSpec.Node> nodes = def.nodes();
        int used = 0, taken = 0, unknown = 0;
        for (String id : dedupe(selected)) {
            SkillTreeSpec.Node node = nodes == null ? null : nodes.get(id);
            if (node == null) { unknown++; continue; }
            used += node.cost(); taken++;
        }
        return new State(def.maxPoints(), used, def.maxPoints() - used, taken, unknown);
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

    public static Status canTake(SkillTreeSpec.Definition def, Collection<String> selected, String nodeId) {
        if (def == null || nodeId == null || def.nodes() == null) return Status.UNKNOWN_NODE;
        SkillTreeSpec.Node node = def.nodes().get(nodeId);
        if (node == null) return Status.UNKNOWN_NODE;
        if (dedupe(selected).contains(nodeId)) return Status.ALREADY_TAKEN;
        if (!missingPrereqs(def, selected, nodeId).isEmpty()) return Status.MISSING_PREREQ;
        if (node.cost() > state(def, selected).available()) return Status.NO_POINTS;
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
