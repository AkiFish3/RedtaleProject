package com.redtale.project.mobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * {@code spawn.json5} 的解析结果（M6 收口，R53）：刷怪替换的**运行期开关**落盘。
 *
 * <p>为什么单独一份文件而不是塞进 {@code spawn_rules/*.json5}：规则的目录语义是「一条规则一张表」，
 * 开关是全局状态；也不塞进 {@code progression.json5}（那是经验面）。
 *
 * <p>纯解析，零 MC import、零日志；坏值回落缺省并往 {@code warnings} 塞一行。
 */
public record SpawnSettings(boolean replaceEnabled) {

    /** 缺省开：装了模组就该替换，关掉是运营动作，不是默认行为。 */
    public static final boolean DEFAULT_REPLACE_ENABLED = true;

    public static final SpawnSettings DEFAULT = new SpawnSettings(DEFAULT_REPLACE_ENABLED);

    /** 文件缺失（{@code root == null}）→ 缺省，不告警。 */
    public static SpawnSettings parse(JsonObject root, List<String> warnings) {
        if (root == null) {
            return DEFAULT;
        }
        boolean value = DEFAULT_REPLACE_ENABLED;
        JsonElement el = root.get("replace_enabled");
        if (el != null) {
            boolean isBool = el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean();
            if (!isBool) {
                warn(warnings, "spawn.json5: replace_enabled 不是布尔值，已改用 " + DEFAULT_REPLACE_ENABLED);
            } else {
                value = el.getAsBoolean();
            }
        }
        return new SpawnSettings(value);
    }

    public String describe() {
        return "刷怪替换开关 " + (replaceEnabled ? "开" : "关");
    }

    private static void warn(List<String> warnings, String msg) {
        if (warnings != null) {
            warnings.add(msg);
        }
    }
}
