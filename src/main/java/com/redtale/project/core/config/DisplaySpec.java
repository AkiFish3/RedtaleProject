package com.redtale.project.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** 显示开关（D20 / Q-023 默认 A）schema 的 L1 纯解析：零 MC、零 IO、零日志。 */
public final class DisplaySpec {
    /** 定位用的相对路径，精确等于这个串才算本文件。 */
    public static final String PATH = "display.json5";
    /** 无文件 / 全默认时的值。 */
    public static final Settings DEFAULT = new Settings(false, false, "minecraft:zombie", "");

    public record Settings(boolean showDps,
                           boolean showAttributeBreakdown,
                           String assumeTarget,
                           String source) {}

    private static final String DEF_TARGET = "minecraft:zombie";

    private DisplaySpec() {}

    /** 从整批配置里挑出 PATH 那一个解析；configs 为 null / 无该文件 → DEFAULT 且零告警。 */
    public static Settings parse(java.util.List<ConfigFile> configs,
                                 java.util.List<String> warnings) {
        if (configs == null) return DEFAULT;
        for (ConfigFile f : configs) {
            if (f == null || !PATH.equals(f.path())) continue;
            JsonObject root = f.root();
            if (root == null || !root.isJsonObject()) {
                warn(warnings, PATH + "：根节点不是对象，已用默认值");
                return DEFAULT;
            }
            boolean dps = bool(root.get("show_dps"), "show_dps", warnings);
            boolean ab = bool(root.get("show_attribute_breakdown"), "show_attribute_breakdown", warnings);
            String target = target(root.get("assume_target"), warnings);
            for (String k : root.keySet())
                if (!k.equals("show_dps") && !k.equals("show_attribute_breakdown") && !k.equals("assume_target"))
                    warn(warnings, PATH + "：未知键 " + k + "（已忽略）");
            return new Settings(dps, ab, target, f.path());
        }
        return DEFAULT;
    }

    private static boolean bool(JsonElement el, String key, List<String> w) {
        if (el == null || el.isJsonNull()) return false;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        warn(w, PATH + "：" + key + " 不是布尔值（已用 false）");
        return false;
    }

    private static String target(JsonElement el, List<String> w) {
        if (el == null || el.isJsonNull()) return DEF_TARGET;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            if (s.isBlank()) return DEF_TARGET;
            if (s.indexOf(':') >= 0 && clean(s)) return s;
        }
        warn(w, PATH + "：assume_target 不是合法 id（已用 minecraft:zombie）");
        return DEF_TARGET;
    }

    /** 合法 id 字符：不含空白、不含大写。 */
    private static boolean clean(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isUpperCase(c)) return false;
        }
        return true;
    }

    private static void warn(List<String> w, String msg) { if (w != null) w.add(msg); }
}
