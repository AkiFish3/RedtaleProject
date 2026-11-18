package com.redtale.project.core.progression;

/** unlock_skill / passive 已点节点的纯派生：只回答「解锁了哪些 id」，零 IO、零 MC 依赖。 */
public final class TreeUnlocks {
    private TreeUnlocks() {}

    public static java.util.List<String> unlocked(SkillTreeSpec.Definition def, java.util.Collection<String> selected) {
        return values(def, selected, "unlock_skill", true);
    }

    public static java.util.List<String> passives(SkillTreeSpec.Definition def, java.util.Collection<String> selected) {
        return values(def, selected, "passive", false);
    }

    public static java.util.List<String> gained(SkillTreeSpec.Definition def,
                                                java.util.Collection<String> before,
                                                java.util.Collection<String> after) {
        return diff(unlocked(def, after), unlocked(def, before));
    }

    public static java.util.List<String> lost(SkillTreeSpec.Definition def,
                                              java.util.Collection<String> before,
                                              java.util.Collection<String> after) {
        return diff(unlocked(def, before), unlocked(def, after));
    }

    public static String describe(SkillTreeSpec.Definition def, java.util.Collection<String> selected) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (def != null && def.nodes() != null) {
            for (java.util.Map.Entry<String, SkillTreeSpec.Node> e : def.nodes().entrySet()) {
                if (!picked(selected, e.getKey())) continue;
                SkillTreeSpec.Node n = e.getValue();
                if (n == null || n.effects() == null) continue;
                for (SkillTreeSpec.Effect fx : n.effects()) {
                    if (fx == null || !"unlock_skill".equals(fx.type())) continue;
                    String sk = fx.skill();
                    if (sk == null || sk.trim().isEmpty()) continue;
                    lines.add("  " + e.getKey() + " · " + sk);
                }
            }
        }
        return lines.isEmpty() ? "  （无）" : String.join("\n", lines);
    }

    private static java.util.List<String> values(SkillTreeSpec.Definition def, java.util.Collection<String> selected,
                                                 String type, boolean skillField) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (def != null && def.nodes() != null) {
            for (java.util.Map.Entry<String, SkillTreeSpec.Node> e : def.nodes().entrySet()) {
                if (!picked(selected, e.getKey())) continue;
                SkillTreeSpec.Node n = e.getValue();
                if (n == null || n.effects() == null) continue;
                for (SkillTreeSpec.Effect fx : n.effects()) {
                    if (fx == null || !type.equals(fx.type())) continue;
                    String v = skillField ? fx.skill() : fx.id();
                    if (v == null || v.trim().isEmpty() || out.contains(v)) continue;
                    out.add(v);
                }
            }
        }
        return java.util.List.copyOf(out);
    }

    private static java.util.List<String> diff(java.util.List<String> main, java.util.List<String> other) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : main) if (!other.contains(s)) out.add(s);
        return java.util.List.copyOf(out);
    }

    private static boolean picked(java.util.Collection<String> selected, String nodeId) {
        return selected != null && selected.contains(nodeId);
    }
}
