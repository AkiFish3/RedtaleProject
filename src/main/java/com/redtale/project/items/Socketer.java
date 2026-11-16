package com.redtale.project.items;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.math.StatValue;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 镶嵌 / 取出结算（M3 第 4 步，{@code ITEM-MODEL.md} §5.5）：孔位与宝石的**唯一结算点**。
 *
 * <p>与 {@link SocketSpec} 的分工：那份是**解析**（把 json5 变成 {@code Layout} / {@code Gem}），
 * 本类是**结算**（改孔序、重算 {@code gemStats}、重写面板）。
 *
 * <p>四条口径：
 * <ol>
 *   <li><b>宝石数值单列</b>：结果写进 {@code ContentData.gemStats}，**不并进 {@code rolled_stats}**。
 *       并进去以后拆宝石会把词条那部分一起扣掉（§5.1 的理由）。</li>
 *   <li><b>取宝石 = 重算整张表</b>：不是「减去那颗宝石的数」。重算永远和「当前孔序」一致，
 *       也就不会出现「拆了三颗还剩一颗的数」这种累积误差。</li>
 *   <li><b>失败 = 物品逐字未变</b>：类型不符 / 同组冲突 / 孔位越界都在写之前返回，
 *       调用方拿到 {@link Status} 就能区分「没镶上」与「镶上了但没效果」。</li>
 *   <li><b>孔数只补不截</b>：定义把孔数改大 → 补空孔；改小 → **不自动截断**（截断等于销毁玩家资产，
 *       见 {@link #aligned}）。要收回请显式 {@link #extract}。</li>
 * </ol>
 */
public final class Socketer {

    /** 结算结果。{@code affectedGemId}：镶嵌 = 被替换下来的旧宝石（没有则空串），取出 = 取下的宝石。 */
    public enum Status {
        /** 成了。 */
        APPLIED,
        /** 物品没有 content 组件 / 定义已被删（孤儿）。 */
        NOT_CONTENT,
        /** 定义里没有孔。 */
        NO_SOCKETS,
        /** 孔位下标越界。 */
        INDEX_OUT_OF_RANGE,
        /** 目标不是宝石（内容不存在，或那件内容没有 {@code gem} 段）。 */
        NOT_A_GEM,
        /** 宝石类型与该孔类型不匹配（两边都不是 {@code any}）。 */
        TYPE_MISMATCH,
        /** 同组互斥：别的孔里已经有一颗 {@code exclusive_group} 相同的宝石。 */
        EXCLUSIVE_CONFLICT,
        /** 该孔是空的，没东西可取。 */
        EMPTY_SLOT
    }

    public record Result(Status status, String affectedGemId, Map<String, Double> gemStats, String message) {
        public boolean applied() {
            return status == Status.APPLIED;
        }

        static Result fail(Status status, String message) {
            return new Result(status, "", Map.of(), message);
        }
    }

    private Socketer() {
    }

    /** 把宝石内容 {@code gemContentId} 镶进第 {@code index} 个孔（该孔若有旧宝石，被替换并回报）。 */
    public static Result insert(ItemStack stack, int index, String gemContentId) {
        ContentData data = dataOf(stack);
        if (data == null) {
            return Result.fail(Status.NOT_CONTENT, "不是内容物品");
        }
        ItemContent def = RedtaleCore.get().contentItems().get(data.contentId());
        if (def == null) {
            return Result.fail(Status.NOT_CONTENT, "内容定义已不存在（孤儿物品）：" + data.contentId());
        }
        int count = def.sockets().count();
        if (count <= 0) {
            return Result.fail(Status.NO_SOCKETS, def.id() + " 没有孔位");
        }
        if (index < 0 || index >= count) {
            return Result.fail(Status.INDEX_OUT_OF_RANGE, "孔位下标 " + index + " 越界（0~" + (count - 1) + "）");
        }
        ItemContent gemContent = gemContentId == null ? null
                : RedtaleCore.get().contentItems().get(gemContentId);
        if (gemContent == null || gemContent.gem() == null) {
            return Result.fail(Status.NOT_A_GEM, "不是宝石内容：" + gemContentId);
        }
        SocketSpec.Gem gem = gemContent.gem();

        List<String> sockets = aligned(data.sockets(), count);
        String socketType = def.sockets().types().get(index);
        if (!accepts(socketType, gem.type())) {
            return Result.fail(Status.TYPE_MISMATCH,
                    "孔 " + index + " 是 " + socketType + " 型，宝石是 " + gem.type() + " 型");
        }
        String conflict = exclusiveConflict(sockets, index, gem.exclusiveGroup());
        if (conflict != null) {
            return Result.fail(Status.EXCLUSIVE_CONFLICT,
                    "互斥组 " + gem.exclusiveGroup() + " 已在孔 " + conflict + " 里有一颗");
        }

        String old = sockets.get(index);
        List<String> next = new ArrayList<>(sockets);
        next.set(index, gemContent.id());
        Map<String, Double> gemStats = solve(next);
        ItemFactory.applySockets(stack, def, data.withSockets(List.copyOf(next), gemStats));
        return new Result(Status.APPLIED, old == null ? "" : old, gemStats,
                "孔 " + index + " ← " + gemContent.id());
    }

    /** 把第 {@code index} 个孔的宝石取下来（只清孔 + 重算，宝石本身由调用方退回）。 */
    public static Result extract(ItemStack stack, int index) {
        ContentData data = dataOf(stack);
        if (data == null) {
            return Result.fail(Status.NOT_CONTENT, "不是内容物品");
        }
        ItemContent def = RedtaleCore.get().contentItems().get(data.contentId());
        if (def == null) {
            return Result.fail(Status.NOT_CONTENT, "内容定义已不存在（孤儿物品）：" + data.contentId());
        }
        int count = def.sockets().count();
        if (count <= 0) {
            return Result.fail(Status.NO_SOCKETS, def.id() + " 没有孔位");
        }
        if (index < 0 || index >= count) {
            return Result.fail(Status.INDEX_OUT_OF_RANGE, "孔位下标 " + index + " 越界（0~" + (count - 1) + "）");
        }
        List<String> sockets = aligned(data.sockets(), count);
        String old = sockets.get(index);
        if (old == null || old.isEmpty()) {
            return Result.fail(Status.EMPTY_SLOT, "孔 " + index + " 是空的");
        }
        List<String> next = new ArrayList<>(sockets);
        next.set(index, "");
        Map<String, Double> gemStats = solve(next);
        ItemFactory.applySockets(stack, def, data.withSockets(List.copyOf(next), gemStats));
        return new Result(Status.APPLIED, old, gemStats, "孔 " + index + " 取下 " + old);
    }

    /** 孔位匹配的唯一规则（§5.2）：任一侧是 {@code any} 即可镶，否则必须相等。 */
    public static boolean accepts(String socketType, String gemType) {
        String s = socketType == null || socketType.isEmpty() ? "any" : socketType;
        String g = gemType == null || gemType.isEmpty() ? "any" : gemType;
        return "any".equals(s) || "any".equals(g) || s.equals(g);
    }

    /**
     * 孔序对齐：**只补不清**。
     *
     * <p>补：定义孔数比物品记录的多 → 尾巴补 {@code ""}（改大是安全的）。
     * 不截：定义孔数比物品少 → 原样保留多出来的孔与里面的宝石。理由：截断 = 无声销毁玩家资产，
     * 而「定义把孔数改小」是管理员动作，应该看得见代价（先 {@link #extract} 再刷新）。
     * 面板与 {@code gemStats} 仍按整张孔序算，所以多出来的宝石继续生效，不会「插着却没用」。
     */
    public static List<String> aligned(List<String> sockets, int count) {
        List<String> base = sockets == null ? List.of() : sockets;
        if (base.size() >= count) {
            return base;
        }
        List<String> out = new ArrayList<>(count);
        out.addAll(base);
        while (out.size() < count) {
            out.add("");
        }
        return List.copyOf(out);
    }

    /**
     * 按孔序重算 {@code gemStats}（**唯一算法**，tooltip / 结算 / 面板三处同源）。
     *
     * <p>同一属性多颗宝石 = 相加；{@code StatValue} 按 {@code ItemFactory.ITEM_LEVEL} 取当前等级值
     * （等级系统未接，现在恒为 1，接上只改那一个常量）。
     * 互斥组在这里**再挡一次**（先出现的生效）：镶嵌路径已经拦过，这里防的是手写组件的数据。
     */
    public static Map<String, Double> solve(List<String> sockets) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (sockets == null || sockets.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemContent> all = RedtaleCore.get().contentItems().allById();
        Set<String> usedGroups = new LinkedHashSet<>();
        for (String gemId : sockets) {
            if (gemId == null || gemId.isEmpty()) {
                continue;
            }
            ItemContent content = all.get(gemId);
            if (content == null || content.gem() == null) {
                continue;
            }
            SocketSpec.Gem gem = content.gem();
            if (!gem.exclusiveGroup().isEmpty() && !usedGroups.add(gem.exclusiveGroup())) {
                continue;
            }
            for (Map.Entry<String, StatValue> e : gem.stats().entrySet()) {
                double v = e.getValue().valueAt(ItemFactory.ITEM_LEVEL);
                if (v == 0 || !Double.isFinite(v)) {
                    continue;
                }
                out.merge(e.getKey(), v, Double::sum);
            }
        }
        return Map.copyOf(out);
    }

    /** 别的孔里有没有同组宝石；有则返回那个孔的下标（字符串），没有返回 {@code null}。 */
    private static String exclusiveConflict(List<String> sockets, int index, String group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        Map<String, ItemContent> all = RedtaleCore.get().contentItems().allById();
        for (int i = 0; i < sockets.size(); i++) {
            if (i == index) {
                continue;
            }
            String id = sockets.get(i);
            if (id == null || id.isEmpty()) {
                continue;
            }
            ItemContent other = all.get(id);
            if (other != null && other.gem() != null && group.equals(other.gem().exclusiveGroup())) {
                return String.valueOf(i);
            }
        }
        return null;
    }

    private static ContentData dataOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(ContentComponents.CONTENT.get());
    }
}
