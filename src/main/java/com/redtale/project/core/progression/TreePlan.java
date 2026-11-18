package com.redtale.project.core.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 技能树属性下发的纯函数：固定 modifier id + plan/declared（D25 L5 第一步，零外部依赖）。 */
public final class TreePlan {
    private TreePlan() {}

    public record Modifier(String id, String attribute, String operation, double amount) {}

    public static String pathOf(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public static String modifierPrefix(String treeId) { return "tree/" + pathOf(treeId) + "/"; }

    public static String operationOf(String raw) {
        if (raw == null) return "add_value";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "add_value";
        if (s.equals("add") || s.equals("add_value")) return "add_value";
        if (s.equals("add_multiplied_base") || s.equals("add_multiplied_total")) return s;
        return "add_value";
    }

    public static List<Modifier> plan(SkillTreeSpec.Definition def, Collection<String> selected) {
        Set<String> set = selected == null ? null : new HashSet<>(selected);
        return collect(def, set, false);
    }

    public static List<Modifier> declared(SkillTreeSpec.Definition def) { return collect(def, null, true); }

    private static List<Modifier> collect(SkillTreeSpec.Definition def, Set<String> selected, boolean all) {
        List<Modifier> out = new ArrayList<>();
        if (def == null || def.nodes() == null) return List.copyOf(out);
        String prefix = modifierPrefix(def.id());
        for (SkillTreeSpec.Node node : def.nodes().values()) {
            if (node == null) continue;
            if (!all && (selected == null || !selected.contains(node.id()))) continue;
            List<SkillTreeSpec.Effect> attrs = new ArrayList<>();
            if (node.effects() != null) for (SkillTreeSpec.Effect e : node.effects()) {
                if (e == null || e.type() == null || !e.type().equalsIgnoreCase("attribute")) continue;
                if (e.attribute() == null || e.attribute().trim().isEmpty()) continue;
                attrs.add(e);
            }
            for (int i = 0; i < attrs.size(); i++) {
                SkillTreeSpec.Effect e = attrs.get(i);
                String mid = attrs.size() == 1 ? prefix + node.id() : prefix + node.id() + "/" + i;
                out.add(new Modifier(mid, e.attribute(), operationOf(e.operation()),
                    Math.round(e.value() * 1_000_000.0) / 1_000_000.0));
            }
        }
        return List.copyOf(out);
    }
}
