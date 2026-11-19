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
 *
 * <p>R55 追加 {@code per_tick_cap}：原本硬编在 {@link SpawnReplacer} 里的 8 挪到配置面。
 * 它**不是玩法旋钮而是防洪阀**（一批自然刷怪可能同 tick 命中好几只），所以 `0` 不表示「关闭」，
 * 而是回落缺省 8 —— 与 D16 的 `0 = 关闭` 刻意区分，避免有人写 0 以为关掉了、实际变成每 tick 无限生成。
 */
public record SpawnSettings(boolean replaceEnabled, int perTickCap) {

    /** 缺省开：装了模组就该替换，关掉是运营动作，不是默认行为。 */
    public static final boolean DEFAULT_REPLACE_ENABLED = true;

    /** 每 tick 最多补生成几只（R48 硬编值，R55 挪进配置；R71 常量源收口到 SpawnBudget）。 */
    public static final int DEFAULT_PER_TICK_CAP = SpawnBudget.DEFAULT_CAP;

    public static final SpawnSettings DEFAULT = new SpawnSettings(DEFAULT_REPLACE_ENABLED, DEFAULT_PER_TICK_CAP);

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
        return new SpawnSettings(value, perTickCap(root.get("per_tick_cap"), warnings));
    }

    /**
     * {@code per_tick_cap}：必须是 `>= 1` 的整数；缺失 / 非整数 / `< 1` / `> 4096` → 缺省 8 并告警（缺失不告警）。
     *
     * <p>R71：数值口径委托 {@link SpawnBudget#fromConfig(double)}（单一实现），告警文案与判罚条件不变。
     * 「告警」与「取值」分开：合法值恰好等于缺省 8 时不告警，只有非法输入才告警。
     */
    private static int perTickCap(JsonElement el, List<String> warnings) {
        if (el == null) {
            return DEFAULT_PER_TICK_CAP;
        }
        boolean isNumber = el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        if (!isNumber) {
            warn(warnings, "spawn.json5: per_tick_cap 不是数字，已改用 " + DEFAULT_PER_TICK_CAP);
            return DEFAULT_PER_TICK_CAP;
        }
        double raw = el.getAsDouble();
        int value = SpawnBudget.fromConfig(raw);
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw != Math.rint(raw)
                || raw < SpawnBudget.MIN_CAP || raw > SpawnBudget.MAX_CAP) {
            warn(warnings, "spawn.json5: per_tick_cap 必须是 1~4096 的整数，已改用 " + DEFAULT_PER_TICK_CAP);
        }
        return value;
    }

    public String describe() {
        return "刷怪替换开关 " + (replaceEnabled ? "开" : "关") + " · 每 tick 上限 " + perTickCap;
    }

    private static void warn(List<String> warnings, String msg) {
        if (warnings != null) {
            warnings.add(msg);
        }
    }
}
