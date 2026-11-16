package com.redtale.project.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/**
 * 物品更新（D33）里**唯一有分支判断的那一步**：给定物品上的 {@link ContentData} 与当前配置的
 * {@link ItemContent}，算出「要不要重建 / 重建后 {@code rolled_stats} 留哪些键 / 删了哪些键」。
 *
 * <p>纯函数：不读盘、不碰 {@code ItemStack}、不发事件、不写日志（告警全部走 {@link Diff#warnings()}）。
 * 事件钩子与写回由后续轮次接线，本类只回答「该不该动」。
 *
 * <p>判定口径（{@code docs/ITEM-UPDATE.md} §4.3）：
 * <ul>
 *   <li>任何分支都不抛异常——reload / 登录路径上崩一次就是全服卡死。</li>
 *   <li>快路径（{@code schema} 与 {@code content_rev} 都相等）**零分配零遍历**：U10 要求 200 件 &lt; 50ms。</li>
 *   <li>重建只删「新定义里没有的键」，**不加**新定义多出来的键（U3 不白送）、**不改**已有键的值（不重 roll）。</li>
 * </ul>
 */
public final class ItemUpgrader {

    /**
     * 当前组件结构版本，与 {@link ContentData#CURRENT_SCHEMA} 同源（D33）。
     *
     * <p>**刻意写成引用而不是字面量 2**：两处各写一个数字，早晚会漂一次，
     * 而漂一次的后果是「schema 判据恒假 → 全服物品永不迁移」，静默且难查。
     */
    public static final int SCHEMA = ContentData.CURRENT_SCHEMA;

    /** 一次判定的结果。{@code stats} 只可能在 {@code needsRebuild=false} 时等于旧值原样。 */
    public record Diff(boolean needsRebuild,
                       Map<String, Double> stats,
                       List<String> removedKeys,
                       List<String> warnings) {
    }

    /**
     * 物品更新的一次执行结果（D33 / {@code docs/ITEM-UPDATE.md} §4.1 的 4 个刷新点都返回它，便于计数）。
     */
    public record Result(Status status, String contentId, int fromRev, int toRev,
                         List<String> warnings) {
    }

    public enum Status {
        /** 不是我们的物品（没有 `redtale_project:content` 组件）。 */
        NOT_CONTENT,
        /** rev 与定义一致，什么都没做（绝大多数物品走这里）。 */
        UNCHANGED,
        /** 重建过定义型字段。 */
        UPGRADED,
        /** 内容定义已被删（孤儿）：**一个数值都不改**，只让调用方去标红。 */
        ORPHAN
    }

    private ItemUpgrader() {
    }

    public static Diff plan(ContentData current, ItemContent definition) {
        // 规则 1：拿不到任一侧就什么都不做，绝不抛异常。
        if (current == null || definition == null) {
            return new Diff(false, Collections.emptyMap(), List.of(),
                    List.of("plan: current/definition 为 null，跳过"));
        }
        // 规则 2：id 对不上说明调用方拿错了定义，保持原样并让它自己看告警。
        if (!current.contentId().equals(definition.id())) {
            return new Diff(false, current.rolledStats(), List.of(),
                    List.of("plan: id 不匹配（物品 " + current.contentId()
                            + " / 定义 " + definition.id() + "），跳过"));
        }
        boolean schemaStale = current.schema() != SCHEMA;
        // 规则 4：快路径。schema 相同且 rev 相同 → 绝大多数物品走这里，不做任何集合操作。
        if (!schemaStale && current.contentRev() == definition.rev()) {
            return new Diff(false, current.rolledStats(), List.of(), List.of());
        }
        // 规则 3 / 5：其余情况都要重建；stats 按「旧键集 ∩ 新定义」过滤，值一个都不改。
        List<String> warnings = new ArrayList<>(1);
        if (schemaStale) {
            warnings.add("plan: 组件 schema " + current.schema() + " ≠ " + SCHEMA + "，需结构迁移并重建");
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, Double> e : current.rolledStats().entrySet()) {
            if (definition.stats().containsKey(e.getKey())) {
                stats.put(e.getKey(), e.getValue());
            } else {
                // 规则 6：只记键名，不逐个告警（记账归调用方，本类只给数据）。
                removed.add(e.getKey());
            }
        }
        // 规则 7：LinkedHashMap 保序 + unmodifiableMap 保不可变（Map.copyOf 会丢序）。
        return new Diff(true, Collections.unmodifiableMap(stats), List.copyOf(removed), List.copyOf(warnings));
    }

    // ------------------------------------------------------------------ ensure

    /**
     * 按当前配置把一件**已有**物品刷新到最新定义（D33 的唯一写回点）。
     *
     * <p>四件事按顺序，缺一件就会出事故：
     * <ol>
     *   <li>没有组件 → 不是我们的物品，<b>立刻返回</b>（这是热路径，每件物品都要过一次）；</li>
     *   <li>定义不存在 → {@link Status#ORPHAN}，<b>一个数值都不改</b>（§4.4：不静默销毁玩家资产）；</li>
     *   <li>{@link #plan} 说不重建 → {@link Status#UNCHANGED}，成本是一次 int 比较；</li>
     *   <li>重建：`rolled_keys` 里的键保留旧值（玩家 roll 的），其余跟新定义走；
     *       定义里删掉的键丢弃并**逐个记账**（§4.3 第 3 条）。</li>
     * </ol>
     *
     * <p>绝不抛异常：登录 / 区块加载路径上崩一次就是全服卡死，出错只记 {@link Result#warnings()}。
     */
    public static Result ensure(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new Result(Status.NOT_CONTENT, "", 0, 0, List.of());
        }
        ContentData data = stack.get(ContentComponents.CONTENT.get());
        if (data == null) {
            return new Result(Status.NOT_CONTENT, "", 0, 0, List.of());
        }
        ItemContent def = com.redtale.project.core.RedtaleCore.get().contentItems().get(data.contentId());
        if (def == null) {
            return new Result(Status.ORPHAN, data.contentId(), data.contentRev(), data.contentRev(), List.of());
        }
        Diff diff = plan(data, def);
        if (!diff.needsRebuild()) {
            return new Result(Status.UNCHANGED, data.contentId(), data.contentRev(), def.rev(), List.of());
        }

        List<String> warnings = new ArrayList<>(diff.warnings());
        Set<String> rolled = new HashSet<>(data.rolledKeys());
        Map<String, Double> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : diff.stats().entrySet()) {
            // §4.3 第 1 条：roll 过的保留玩家值，没 roll 过的跟新定义走。
            Double v = rolled.contains(e.getKey()) ? e.getValue() : def.stats().get(e.getKey());
            effective.put(e.getKey(), v != null ? v : e.getValue());
        }
        for (String key : diff.removedKeys()) {
            // §4.3 第 3 条：删键必须留证据，否则玩家投诉「我属性少了」时无从对账。
            warnings.add("物品更新：" + data.contentId() + " 键 " + key + " 已从定义删除（旧值 "
                    + data.rolledStats().get(key) + "），rev " + data.contentRev() + "→" + def.rev());
        }
        List<String> keptRolled = new ArrayList<>(rolled.size());
        for (String key : data.rolledKeys()) {
            if (effective.containsKey(key)) {
                keptRolled.add(key);
            }
        }
        List<String> affixes = keepKnownAffixes(data.affixes(), warnings);
        try {
            ItemFactory.applyDefinition(stack, def, effective, warnings);
        } catch (RuntimeException ex) {
            warnings.add("物品更新：" + data.contentId() + " 重写面板失败（" + ex + "），保持原样");
            return new Result(Status.UNCHANGED, data.contentId(), data.contentRev(), def.rev(),
                    List.copyOf(warnings));
        }
        // M3：孔序按新定义对齐 —— **只补不截**（补空孔是安全的；截断等于销毁玩家资产）。
        // 定义把孔数改小、而物品上还插着宝石时，这里不静默吞掉，只记账让管理员显式取出。
        List<String> sockets = Socketer.aligned(data.sockets(), def.sockets().count());
        if (data.sockets().size() > def.sockets().count()) {
            List<String> extra = new ArrayList<>();
            for (int i = def.sockets().count(); i < data.sockets().size(); i++) {
                String gem = data.sockets().get(i);
                if (gem != null && !gem.isEmpty()) {
                    extra.add("#" + i + "=" + gem);
                }
            }
            if (!extra.isEmpty()) {
                warnings.add("物品更新：" + data.contentId() + " 定义孔数 " + def.sockets().count()
                        + " < 物品孔位 " + data.sockets().size() + "，超出部分未截断（宝石仍在）："
                        + String.join(", ", extra) + " —— 请先 /redtale socket extract 再刷新");
            }
        }
        // 宝石数值按对齐后的孔序重算：定义改了宝石的数值，这里要跟上（改配置全服生效）
        Map<String, Double> gemStats = Socketer.solve(sockets);
        stack.set(ContentComponents.CONTENT.get(), new ContentData(
                // set_id 是**定义型字段**（§5.4：改它要 rev+1）→ 重建时取新定义的值，
                // 不是照抄旧的（照抄 = 改了 assign 也不跟，与 rev 语义矛盾）
                data.contentId(), def.rev(), SCHEMA, def.setId(), data.upgradeLevel(),
                effective, keptRolled, affixes, data.abilities(), def.weaponAttributes(),
                sockets, gemStats));
        return new Result(Status.UPGRADED, data.contentId(), data.contentRev(), def.rev(),
                List.copyOf(warnings));
    }

    /**
     * 词条只留「定义仍存在」的 id（不补新的）。词条表为空时原样返回——
     * 没装词条配置不等于「所有词条都失效」，那样会静默清空全服词条。
     */
    private static List<String> keepKnownAffixes(List<String> affixes, List<String> warnings) {
        if (affixes.isEmpty()) {
            return affixes;
        }
        Map<String, AffixPools.Def> defs = com.redtale.project.core.RedtaleCore.get().affixDefs();
        if (defs.isEmpty()) {
            return affixes;
        }
        List<String> kept = new ArrayList<>(affixes.size());
        for (String id : affixes) {
            if (defs.containsKey(id)) {
                kept.add(id);
            } else {
                warnings.add("物品更新：词条 " + id + " 已不存在，已移除");
            }
        }
        return kept;
    }
}
