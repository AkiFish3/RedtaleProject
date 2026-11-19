package com.redtale.project.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 热重载快照差分：只比相对路径→mtime，不读文件、不读时钟。 */
public final class ConfigDiff {
    public record Report(List<String> added, List<String> removed, List<String> changed) {}

    public static Report between(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> b = before == null ? Map.of() : before;
        Map<String, Long> a = after == null ? Map.of() : after;
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (String k : a.keySet()) {
            if (!b.containsKey(k)) added.add(k);
            else if (nz(b.get(k)) != nz(a.get(k))) changed.add(k);
        }
        for (String k : b.keySet()) if (!a.containsKey(k)) removed.add(k);
        Collections.sort(added);
        Collections.sort(removed);
        Collections.sort(changed);
        return new Report(List.copyOf(added), List.copyOf(removed), List.copyOf(changed));
    }

    private static long nz(Long v) { return v == null ? 0L : v; }

    public static String describe(Report report) {
        if (report == null) return "新增 0 · 删除 0 · 变更 0";
        return "新增 " + report.added().size() + " · 删除 " + report.removed().size() + " · 变更 " + report.changed().size();
    }
}
