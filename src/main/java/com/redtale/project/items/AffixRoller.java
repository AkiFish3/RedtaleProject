package com.redtale.project.items;

import com.redtale.project.core.math.StatValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 词条 roll（D22 第二层）：给定「词条池 + 词条定义表 + 物品等级」，摇出这件装备的词条与数值。
 *
 * <p><b>纯函数，零 Minecraft 依赖</b> —— 由 {@code gradle selftest} 离线验证。
 * 随机源是 {@link Random}（不是 {@code RandomSource}），所以同一 seed 必然同结果，
 * 验收不用靠肉眼。
 *
 * <p>五条口径：
 * <ol>
 *   <li><b>槽位决定条数</b>：prefix / suffix / core 各自 {@code slots} 条，全 0 就是没词条。
 *       不做全局钳制（D16），投放层写多少就是多少。</li>
 *   <li><b>等级区间过滤</b>：{@code item_level.min ≤ 等级 ≤ max} 才进候选。</li>
 *   <li><b>{@code exclusive_group} 同组最多一条</b>：先摇到的占位，后来同组的整个移出候选。</li>
 *   <li><b>不放回抽取</b>：已抽中的词条 id 从候选里移走，同一件装备不会出现两个 {@code sharp}。</li>
 *   <li><b>数值 = {@code StatValue#valueAt(等级)} 求和</b>：多个词条给同一属性时累加，
 *       写进 {@code rolledStats}；键记进 {@code rolledKeys}（D33：这些是玩家资产，迁移时不重 roll）。</li>
 * </ol>
 *
 * <p>权重为 0 / 负数的条目视为「不出现」，不算候选（配置里写 0 是用来临时停用的）。
 */
public final class AffixRoller {

    /** 一次 roll 的结果。{@code stats} 只含**词条贡献**的数值，不含内容定义本身的数值。 */
    public record Result(Map<String, Double> stats, List<String> affixIds, List<String> warnings) {

        public Result {
            stats = Map.copyOf(stats);
            affixIds = List.copyOf(affixIds);
            warnings = List.copyOf(warnings);
        }

        public static final Result EMPTY = new Result(Map.of(), List.of(), List.of());
    }

    private static final String DEFAULT_NS = "redtale_project";

    private AffixRoller() {
    }

    /**
     * 按基底挑池：{@code applies_to} 里出现 {@code redtale_project:<基底>}、裸 {@code <基底>}
     * 或 {@code "*"} 即命中。多个池命中时取**配置顺序第一个**（文件顺序 = 优先级，不按 id 排序）。
     */
    public static AffixPools.Pool poolFor(Collection<AffixPools.Pool> pools, String baseId) {
        if (pools == null || baseId == null || baseId.isEmpty()) {
            return null;
        }
        String full = DEFAULT_NS + ":" + baseId;
        for (AffixPools.Pool pool : pools) {
            if (pool == null) {
                continue;
            }
            for (String ref : pool.appliesTo()) {
                if (ref == null) {
                    continue;
                }
                if (ref.equals("*") || ref.equals(baseId) || ref.equals(full)) {
                    return pool;
                }
            }
        }
        return null;
    }

    /** 便捷入口：不 roll（{@code random == null}）时返回 {@link Result#EMPTY}。 */
    public static Result roll(AffixPools.Pool pool, Map<String, AffixPools.Def> defs, int itemLevel,
                              Random random) {
        return roll(pool, defs, itemLevel, random, null);
    }

    /**
     * 摇一件装备的词条。
     *
     * @param pool      命中基底的那个池；{@code null} = 这件装备没有词条池（返回空，不告警）
     * @param defs      词条定义表（{@code AffixPools.parseDefs} 的结果）
     * @param itemLevel 物品等级（物品等级系统未接前调用方传 1）
     * @param random    随机源；{@code null} = 不 roll
     * @param warnings  告警出口，可为 {@code null}
     */
    public static Result roll(AffixPools.Pool pool, Map<String, AffixPools.Def> defs, int itemLevel,
                              Random random, List<String> warnings) {
        List<String> log = warnings == null ? new ArrayList<>() : warnings;
        if (pool == null || defs == null || random == null) {
            return Result.EMPTY;
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        List<String> picked = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        List<String> deadEntries = new ArrayList<>();

        int[] slots = {pool.prefixSlots(), pool.suffixSlots(), pool.coreSlots()};
        String[] kinds = {"prefix", "suffix", "core"};
        for (int i = 0; i < kinds.length; i++) {
            for (int n = 0; n < slots[i]; n++) {
                AffixPools.Def chosen = pick(pool, defs, kinds[i], itemLevel, picked, groups, deadEntries, random);
                if (chosen == null) {
                    break; // 这一类摇不出候选了，剩下的槽位空着（不降级到别类）
                }
                picked.add(chosen.id());
                if (!chosen.exclusiveGroup().isEmpty()) {
                    groups.add(chosen.exclusiveGroup());
                }
                for (Map.Entry<String, StatValue> e : chosen.stats().entrySet()) {
                    stats.merge(e.getKey(), e.getValue().valueAt(itemLevel), Double::sum);
                }
            }
        }
        for (String dead : deadEntries) {
            log.add("词条池 " + pool.id() + " 引用了未定义的词条 " + dead + "，已跳过");
        }
        return new Result(stats, picked, log);
    }

    /** 加权抽一条；候选为空返回 {@code null}。 */
    private static AffixPools.Def pick(AffixPools.Pool pool, Map<String, AffixPools.Def> defs, String kind,
                                       int itemLevel, List<String> picked, List<String> groups,
                                       List<String> deadEntries, Random random) {
        List<AffixPools.Entry> candidates = new ArrayList<>();
        List<AffixPools.Def> resolved = new ArrayList<>();
        int total = 0;
        for (AffixPools.Entry entry : pool.entries()) {
            AffixPools.Def def = defs.get(entry.affixId());
            if (def == null) {
                if (!deadEntries.contains(entry.affixId())) {
                    deadEntries.add(entry.affixId());
                }
                continue;
            }
            if (!def.kind().equals(kind) || entry.weight() <= 0) {
                continue;
            }
            if (itemLevel < def.minItemLevel() || itemLevel > def.maxItemLevel()) {
                continue;
            }
            if (picked.contains(def.id())) {
                continue;
            }
            if (!def.exclusiveGroup().isEmpty() && groups.contains(def.exclusiveGroup())) {
                continue;
            }
            candidates.add(entry);
            resolved.add(def);
            total += entry.weight();
        }
        if (candidates.isEmpty() || total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            roll -= candidates.get(i).weight();
            if (roll < 0) {
                return resolved.get(i);
            }
        }
        return resolved.get(resolved.size() - 1); // 理论上到不了，兜底给最后一条
    }
}
