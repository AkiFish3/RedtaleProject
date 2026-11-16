package com.redtale.project.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redtale.project.core.math.StatValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 孔位布局 {@code sockets} 与宝石定义 {@code gem} 解析器（M3 第一层，纯解析）。
 * schema 见 {@code docs/ITEM-MODEL.md} §5.2 / §5.3，接口见 {@code docs/CONTRACTS.md} §4.5。
 *
 * <p>只把内容 JSON5 的两段变成不可变对象：不实现镶嵌 / 取出 / 互斥判定、不碰 {@code ItemStack}、
 * 不给 {@code ContentData} / {@code ItemContent} 加字段、不读盘、不加命令。
 * {@code sockets} 缺失 → {@code Layout.EMPTY}、{@code gem} 缺失 → {@code null}，两者都不告警。
 * 失败策略：类型不符 / {@code null} / 缺键只记 {@code warnings} 一行，用默认值或跳过，
 * <b>绝不抛异常</b>（与 T-0001/T-0004/T-0006/T-0008/T-0010 同口径）。
 */
public final class SocketSpec {

    /**
     * 孔位布局。{@code types} 不可变且长度<b>恒等于</b> {@code count}；
     * {@code count} 恒在 {@code [0, 6]}，孔类型词汇开放（不做枚举校验）。
     */
    public record Layout(int count, List<String> types) {
        public static final Layout EMPTY = new Layout(0, List.of());
    }

    /** 宝石定义。{@code stats} 由 {@link AffixPools#parseStats} 产出：不可变、保序、键已归一。 */
    public record Gem(String type, int tier, String exclusiveGroup, Map<String, StatValue> stats) {}

    /** 显示上限，不是平衡闸门（§5.2）。 */
    public static final int MAX_COUNT = 6;

    private static final String ANY = "any";

    private SocketSpec() {}

    /** {@code root} / {@code sockets} 缺失 → {@code EMPTY}（不告警）；不是对象 → 告警 + {@code EMPTY}。 */
    public static Layout parseLayout(JsonObject root, String source, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        String src = source == null ? "<unknown>" : source;
        if (root == null) return Layout.EMPTY;
        JsonElement socketsElement = root.get("sockets");
        if (socketsElement == null) return Layout.EMPTY;
        if (!socketsElement.isJsonObject()) {
            sink.add(src + ": sockets 不是对象，已跳过");
            return Layout.EMPTY;
        }
        JsonObject sockets = socketsElement.getAsJsonObject();
        int count = count(sockets, src, sink);
        if (count == 0) return Layout.EMPTY;
        return new Layout(count, types(sockets, count, src, sink));
    }

    /** {@code root} / {@code gem} 缺失 → {@code null}（不告警）；不是对象 → 告警 + {@code null}。 */
    public static Gem parseGem(JsonObject root, String source, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        String src = source == null ? "<unknown>" : source;
        if (root == null) return null;
        JsonElement gemElement = root.get("gem");
        if (gemElement == null) return null;
        if (!gemElement.isJsonObject()) {
            sink.add(src + ": gem 不是对象，已跳过");
            return null;
        }
        JsonObject gem = gemElement.getAsJsonObject();
        // stats 必须走 AffixPools 的同一条解析与归一（R11-b：另写一份会让同一属性加两条 modifier）
        Map<String, StatValue> stats = Collections.unmodifiableMap(
                AffixPools.parseStats(gem, src + ".gem", sink));
        return new Gem(type(gem, src, sink), tier(gem, src, sink), exclusiveGroup(gem, src, sink), stats);
    }

    private static int count(JsonObject sockets, String src, List<String> w) {
        JsonElement e = sockets.get("count");
        if (e == null) return 0;
        if (!isNumber(e)) {
            w.add(src + ": count 不是数字，已用默认值 0");
            return 0;
        }
        int value = e.getAsInt();
        if (value < 0) {
            w.add(src + ": count 小于 0，已归 0");
            return 0;
        }
        if (value > MAX_COUNT) {
            w.add(src + ": count 超过 " + MAX_COUNT + "，已夹到 " + MAX_COUNT);
            return MAX_COUNT;
        }
        return value;
    }

    /** 逐孔取值：非法项换成 {@code any}（不跳过，否则孔序错位）；短了补 {@code any}、长了截断。 */
    private static List<String> types(JsonObject sockets, int count, String src, List<String> w) {
        List<String> out = new ArrayList<>();
        JsonElement e = sockets.get("types");
        if (e == null) {
            // 只给了 count：全部补 any（正常写法，不告警）
        } else if (!e.isJsonArray()) {
            w.add(src + ": types 不是数组，已忽略");
        } else {
            for (JsonElement item : e.getAsJsonArray()) {
                String value = string(item);
                if (value == null) {
                    w.add(src + ": types 里有非字符串或空项，已换成 " + ANY);
                    out.add(ANY);
                } else {
                    out.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (out.size() > count) {
            w.add(src + ": types 有 " + out.size() + " 项但 count=" + count + "，已截断");
            out = new ArrayList<>(out.subList(0, count));
        }
        while (out.size() < count) out.add(ANY);
        return List.copyOf(out);
    }

    /** 缺省 {@code any}；非字符串 → 告警 + {@code any}；空白串 → {@code any}（不告警）；结果 trim + 小写。 */
    private static String type(JsonObject gem, String src, List<String> w) {
        JsonElement e = gem.get("type");
        if (e == null) return ANY;
        if (!isString(e)) {
            w.add(src + ": type 不是字符串，已用 " + ANY);
            return ANY;
        }
        String value = e.getAsString().trim();
        return value.isEmpty() ? ANY : value.toLowerCase(Locale.ROOT);
    }

    private static int tier(JsonObject gem, String src, List<String> w) {
        JsonElement e = gem.get("tier");
        if (e == null) return 1;
        if (!isNumber(e)) {
            w.add(src + ": tier 不是数字，已用默认值 1");
            return 1;
        }
        int value = e.getAsInt();
        if (value < 1) {
            w.add(src + ": tier 小于 1，已归 1");
            return 1;
        }
        return value;
    }

    /** 缺省 {@code ""}（= 不互斥）；非字符串 → 告警 + {@code ""}；结果只 trim，<b>保留大小写</b>（相等比较的键）。 */
    private static String exclusiveGroup(JsonObject gem, String src, List<String> w) {
        JsonElement e = gem.get("exclusive_group");
        if (e == null) return "";
        if (!isString(e)) {
            w.add(src + ": exclusive_group 不是字符串，已用空串");
            return "";
        }
        return e.getAsString().trim();
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    /** {@code trim} 后为空 → {@code null}。 */
    private static String string(JsonElement e) {
        if (!isString(e)) return null;
        String value = e.getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
