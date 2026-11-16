package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 修理段 {@code repair} 解析器（D23 第二半，纯解析）。schema 见 {@code docs/ITEM-MODEL.md} §3.2。
 * 只把内容 JSON5 的 {@code repair} 段变成不可变对象：不实现修理结算 / 耐久计算 / 扣费、不接 {@code Item#use}、
 * 不读磁盘、不发效果。{@code repair} 缺失 = 不是修理物品，返回 {@code null} 且不告警。
 * 失败策略：类型不符 / {@code null} / 缺键只记 {@code warnings} 一行，用默认值或跳过该条，
 * <b>绝不抛异常</b>（与 T-0001/T-0004/T-0006/T-0008 同口径）。
 */
public final class RepairSpec {

    /** 修理代价。{@code currency} 为空串且 {@code amount <= 0} 的 {@code Cost} 不会出现在结果里（归一成不收费）。 */
    public record Cost(String currency, int amount) {}

    /** 修理定义。{@code targets} 不可变；{@code mode} 只会是 amount / percent。 */
    public record Spec(List<String> targets, String mode, int amount, double percent,
                       Cost cost, boolean consumeItem) {}

    private static final String MODE_AMOUNT = "amount";
    private static final String MODE_PERCENT = "percent";
    private static final List<String> MODES = List.of(MODE_AMOUNT, MODE_PERCENT);

    private RepairSpec() {}

    /** {@code root} 或 {@code repair} 缺失 → {@code null}（不告警）；{@code repair} 不是对象 → 告警 + {@code null}。 */
    public static Spec parse(JsonObject root, String source, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        String src = source == null ? "<unknown>" : source;
        if (root == null) return null;
        JsonElement repairElement = root.get("repair");
        if (repairElement == null) return null;
        if (!repairElement.isJsonObject()) {
            sink.add(src + ": repair 不是对象，已跳过");
            return null;
        }
        JsonObject repair = repairElement.getAsJsonObject();
        return new Spec(targets(repair, src, sink), mode(repair, src, sink),
                amount(repair, src, sink), percent(repair, src, sink),
                cost(repair, src, sink), consumeItem(repair, src, sink));
    }

    /** 字符串数组，缺省空 List；非字符串 / 空串 → 告警 + 跳过该元素（不影响其它元素）。 */
    private static List<String> targets(JsonObject repair, String src, List<String> w) {
        List<String> out = new ArrayList<>();
        JsonElement e = repair.get("targets");
        if (e == null) return List.copyOf(out);
        if (!e.isJsonArray()) {
            w.add(src + ": targets 不是数组，已忽略");
            return List.copyOf(out);
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                w.add(src + ": targets 里有非字符串项，已跳过");
                continue;
            }
            String value = item.getAsString().trim();
            if (value.isEmpty()) {
                w.add(src + ": targets 里有空串，已跳过");
                continue;
            }
            out.add(value);
        }
        return List.copyOf(out);
    }

    /** 只接受 {@code amount} / {@code percent}，缺省 {@code amount}。 */
    private static String mode(JsonObject repair, String src, List<String> w) {
        if (repair.get("mode") == null) return MODE_AMOUNT;
        String value = string(repair, "mode");
        if (value == null) {
            w.add(src + ": mode 不是字符串，已回落 " + MODE_AMOUNT);
            return MODE_AMOUNT;
        }
        if (!MODES.contains(value)) {
            w.add(src + ": mode 只能是 " + MODE_AMOUNT + "/" + MODE_PERCENT + "，已回落 " + MODE_AMOUNT);
            return MODE_AMOUNT;
        }
        return value;
    }

    private static int amount(JsonObject repair, String src, List<String> w) {
        int value = integer(repair, "amount", 0, src, w);
        if (value < 0) {
            w.add(src + ": amount 小于 0，已归 0");
            return 0;
        }
        return value;
    }

    /** 夹到 {@code [0.0, 1.0]}，只在真的夹了才告警。 */
    private static double percent(JsonObject repair, String src, List<String> w) {
        double value = decimal(repair, "percent", 0.0, src, w);
        if (value < 0.0 || value > 1.0) {
            double clamped = Math.max(0.0, Math.min(1.0, value));
            w.add(src + ": percent 超出 [0.0, 1.0]，已夹到 " + clamped);
            return clamped;
        }
        return value;
    }

    /** 缺省 {@code null} = 不收费；{@code currency} 空且 {@code amount <= 0} → 也归一成 {@code null}（不告警）。 */
    private static Cost cost(JsonObject repair, String src, List<String> w) {
        JsonElement e = repair.get("cost");
        if (e == null) return null;
        if (!e.isJsonObject()) {
            w.add(src + ": cost 不是对象，已按不收费处理");
            return null;
        }
        JsonObject cost = e.getAsJsonObject();
        String currency = "";
        JsonElement currencyElement = cost.get("currency");
        if (currencyElement != null) {
            if (currencyElement.isJsonPrimitive() && currencyElement.getAsJsonPrimitive().isString()) {
                currency = currencyElement.getAsString().trim();
            } else {
                w.add(src + ": cost.currency 不是字符串，已用空串");
            }
        }
        int amount = 0;
        JsonElement amountElement = cost.get("amount");
        if (amountElement != null) {
            if (isNumber(amountElement)) {
                amount = amountElement.getAsInt();
            } else {
                w.add(src + ": cost.amount 不是数字，已用默认值 0");
            }
        }
        if (amount < 0) {
            w.add(src + ": cost.amount 小于 0，已归 0");
            amount = 0;
        }
        if (currency.isEmpty() && amount <= 0) return null;
        return new Cost(currency, amount);
    }

    private static boolean consumeItem(JsonObject repair, String src, List<String> w) {
        JsonElement e = repair.get("consume_item");
        if (e == null) return true;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        w.add(src + ": consume_item 不是布尔，已用默认值 true");
        return true;
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
