package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.script.ScriptCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 消耗品 {@code use} 段解析器（D23 第一层，纯解析）。schema 见 {@code docs/ITEM-MODEL.md} §3.1。
 * 只把内容 JSON5 的 {@code use} 段变成不可变对象：不接 {@code Item#use} / {@code ItemFactory}、不发效果、
 * 不读磁盘。{@code use} 缺失 = 不是消耗品，返回 {@code null} 且不告警。失败策略：类型不符 / {@code null} /
 * 缺键只记 {@code warnings} 一行，用默认值或跳过该条，<b>绝不抛异常</b>（与 T-0001/T-0004/T-0006 同口径）。
 */
public final class ConsumableSpec {

    /** 一条状态效果。{@code chance} 已夹到 {@code [0.0, 1.0]}。 */
    public record Effect(String id, int duration, int amplifier, double chance) {}

    /** 消耗品定义。{@code effects} / {@code script} 不可变；{@code consume} 只会是 always / only_when_hungry。 */
    public record Spec(List<Effect> effects, int restoreFood, double saturation, int cooldownTicks,
                       List<ScriptCall> script, String consume) {}

    private static final int DEFAULT_DURATION = 200;
    private static final int MAX_RESTORE_FOOD = 20;
    private static final String CONSUME_ALWAYS = "always";
    private static final String CONSUME_HUNGRY = "only_when_hungry";
    private static final List<String> CONSUMES = List.of(CONSUME_ALWAYS, CONSUME_HUNGRY);

    private ConsumableSpec() {}

    /** {@code root} 或 {@code use} 缺失 → {@code null}（不告警）；{@code use} 不是对象 → 告警 + {@code null}。 */
    public static Spec parse(JsonObject root, String source, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        String src = source == null ? "<unknown>" : source;
        if (root == null) return null;
        JsonElement useElement = root.get("use");
        if (useElement == null) return null;
        if (!useElement.isJsonObject()) {
            sink.add(src + ": use 不是对象，已跳过");
            return null;
        }
        JsonObject use = useElement.getAsJsonObject();
        return new Spec(effects(use, src, sink), restoreFood(use, src, sink),
                saturation(use, src, sink), cooldownTicks(use, src, sink),
                script(use, src, sink), consume(root, src, sink));
    }

    private static List<Effect> effects(JsonObject use, String src, List<String> w) {
        List<Effect> out = new ArrayList<>();
        JsonElement e = use.get("effects");
        if (e == null) return List.copyOf(out);
        if (!e.isJsonArray()) {
            w.add(src + ": effects 不是数组，已忽略");
            return List.copyOf(out);
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                w.add(src + ": effects 里有非对象项，已跳过");
                continue;
            }
            Effect effect = effect(item.getAsJsonObject(), src, w);
            if (effect != null) out.add(effect);
        }
        return List.copyOf(out);
    }

    /** {@code effect} 键（不是 {@code type}）必须是非空字符串，否则告警 + 返回 {@code null} 由调用方跳过。 */
    private static Effect effect(JsonObject item, String src, List<String> w) {
        String id = string(item, "effect");
        if (id == null) {
            w.add(src + ": effects 里有一项缺 effect 键或不是非空字符串，已跳过该项");
            return null;
        }
        int duration = integer(item, "duration", DEFAULT_DURATION, src, w);
        if (duration < 1) {
            w.add(src + ": effects." + id + ".duration 小于 1，已夹到 1");
            duration = 1;
        }
        int amplifier = integer(item, "amplifier", 0, src, w);
        if (amplifier < 0) {
            w.add(src + ": effects." + id + ".amplifier 小于 0，已归 0");
            amplifier = 0;
        }
        double chance = decimal(item, "chance", 1.0, src, w);
        if (chance < 0.0 || chance > 1.0) {
            double clamped = Math.max(0.0, Math.min(1.0, chance));
            w.add(src + ": effects." + id + ".chance 超出 [0.0, 1.0]，已夹到 " + clamped);
            chance = clamped;
        }
        return new Effect(id, duration, amplifier, chance);
    }

    private static int restoreFood(JsonObject use, String src, List<String> w) {
        int value = integer(use, "restore_food", 0, src, w);
        if (value < 0 || value > MAX_RESTORE_FOOD) {
            int clamped = Math.max(0, Math.min(MAX_RESTORE_FOOD, value));
            w.add(src + ": restore_food 超出 [0, 20]，已夹到 " + clamped);
            return clamped;
        }
        return value;
    }

    private static double saturation(JsonObject use, String src, List<String> w) {
        double value = decimal(use, "saturation", 0.0, src, w);
        if (value < 0.0) {
            w.add(src + ": saturation 小于 0，已归 0");
            return 0.0;
        }
        return value;
    }

    private static int cooldownTicks(JsonObject use, String src, List<String> w) {
        int value = integer(use, "cooldown_ticks", 0, src, w);
        if (value < 0) {
            w.add(src + ": cooldown_ticks 小于 0，已归 0");
            return 0;
        }
        return value;
    }

    private static List<ScriptCall> script(JsonObject use, String src, List<String> w) {
        List<ScriptCall> out = new ArrayList<>();
        JsonElement e = use.get("script");
        if (e == null) return List.copyOf(out);
        if (!e.isJsonArray()) {
            w.add(src + ": script 不是数组，已忽略");
            return List.copyOf(out);
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                w.add(src + ": script 里有非对象项，已跳过");
                continue;
            }
            JsonObject call = item.getAsJsonObject();
            // 先认 effect 键、再认 type 键
            String type = string(call, "effect");
            if (type == null) type = string(call, "type");
            if (type == null) {
                w.add(src + ": script 项缺 effect/type 键或不是非空字符串，已跳过该项");
                continue;
            }
            JsonObject args = new JsonObject();
            JsonElement argsElement = call.get("args");
            if (argsElement != null) {
                if (argsElement.isJsonObject()) {
                    args = argsElement.getAsJsonObject();
                } else {
                    w.add(src + ": script." + type + ".args 不是对象，已用空对象");
                }
            }
            out.add(new ScriptCall(type, args));
        }
        return List.copyOf(out);
    }

    /** {@code consume} 在根节点（不在 {@code use} 里），只认两个值。 */
    private static String consume(JsonObject root, String src, List<String> w) {
        if (root.get("consume") == null) return CONSUME_ALWAYS;
        String value = string(root, "consume");
        if (value == null) {
            w.add(src + ": consume 不是字符串，已回落 always");
            return CONSUME_ALWAYS;
        }
        if (!CONSUMES.contains(value)) {
            w.add(src + ": consume 只能是 " + CONSUME_ALWAYS + "/" + CONSUME_HUNGRY + "，已回落 always");
            return CONSUME_ALWAYS;
        }
        return value;
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
        String value = e.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static int integer(JsonObject parent, String key, int fallback, String src, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return fallback;
        if (isNumber(e)) return e.getAsInt();
        w.add(src + ": " + key + " 不是数字，已用默认值 " + fallback);
        return fallback;
    }

    private static double decimal(JsonObject parent, String key, double fallback, String src, List<String> w) {
        JsonElement e = parent.get(key);
        if (e == null) return fallback;
        if (isNumber(e)) return e.getAsDouble();
        w.add(src + ": " + key + " 不是数字，已用默认值 " + fallback);
        return fallback;
    }
}
