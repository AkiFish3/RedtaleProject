package com.redtale.project.mobs;

import com.redtale.project.core.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷怪规则快照（D26 / M6 第二层）。{@link SpawnRuleSpec} 只解析，本类负责「排好序、存一份给人读的」。
 *
 * <p>与 {@code MobRegistry} / {@code SkillRegistry} 同构：命令、校验、执行层（L3）三处共用同一个不可变视图，
 * 解析告警随快照换代（换配置时一起换掉，旧告警不许残留）。
 *
 * <p>本类**不挂事件、不生成实体、不读 {@code Level}** —— 那是 L3 {@code SpawnReplacer} 的事（SPAWN-RULES §7）。
 *
 * <p>排序口径（SPAWN-RULES §4）：{@code priority} 降序，同 priority 按 {@code id} 升序。
 * 两键都不相等才可能并列，而 {@code id} 是唯一键 ⇒ **排序结果唯一**，同配置两跑逐条一致。
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class SpawnRegistry {

    public static final SpawnRegistry EMPTY = new SpawnRegistry(List.of(), List.of(), List.of());

    /** priority 降序 → id 升序（{@link Comparator#comparingInt} 反序 + 自然序，List.sort 本身稳定）。 */
    private static final Comparator<SpawnRuleSpec.Definition> ORDER =
            Comparator.comparingInt(SpawnRuleSpec.Definition::priority).reversed()
                    .thenComparing(SpawnRuleSpec.Definition::id);

    private final List<SpawnRuleSpec.Definition> ordered;
    private final List<SpawnRuleSpec.Definition> declared;
    private final List<String> warnings;

    private SpawnRegistry(List<SpawnRuleSpec.Definition> ordered, List<SpawnRuleSpec.Definition> declared,
                          List<String> warnings) {
        this.ordered = ordered;
        this.declared = declared;
        this.warnings = warnings;
    }

    /**
     * 解析全部 {@code spawn_rules/*.json5}，做交叉校验并打包成快照。
     *
     * @param all      全部已加载配置（本类只挑 {@code spawn_rules/} 前缀）
     * @param mobs     怪物快照；{@code null} 视为「没有怪物」→ 每条规则都会报悬空（调用方应传真实快照）
     * @param warnings 告警汇聚点；{@code null} 时内部自建（不往回写，避免调用方 NPE）
     */
    public static SpawnRegistry load(List<ConfigFile> all, MobRegistry mobs, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, SpawnRuleSpec.Definition> parsed = SpawnRuleSpec.parse(all, sink);
        List<SpawnRuleSpec.Definition> declared = new ArrayList<>(parsed.values());
        // §9 快照层：mob 在 MobRegistry 里查不到 → 1 条 WARN + 规则保留（内容后补即可，不要求重启）
        for (SpawnRuleSpec.Definition def : declared) {
            if (mobs == null || !mobs.has(def.mob())) {
                sink.add(def.source() + ": mob " + def.mob() + " 在 mobs/ 里找不到（规则保留，补齐定义后 reload 即生效）");
            }
        }
        List<SpawnRuleSpec.Definition> ordered = new ArrayList<>(declared);
        ordered.sort(ORDER);
        return new SpawnRegistry(List.copyOf(ordered), List.copyOf(declared), List.copyOf(sink));
    }

    /** 执行顺序视图：priority 降序、同 priority 按 id 升序（L3 只认这个顺序）。 */
    public List<SpawnRuleSpec.Definition> ordered() {
        return ordered;
    }

    /** 配置文件顺序视图（`/redtale spawn list` 想按「写文件的顺序」显示时用）。 */
    public List<SpawnRuleSpec.Definition> declared() {
        return declared;
    }

    /** 按 id 取；不存在返回 {@code null}（调用方自己决定报错文案）。 */
    public SpawnRuleSpec.Definition get(String id) {
        if (id == null) {
            return null;
        }
        for (SpawnRuleSpec.Definition def : ordered) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public int size() {
        return ordered.size();
    }

    /** 升序 id 列表（对账用；{@link #declared()} 保留的是配置顺序）。 */
    public List<String> sortedIds() {
        List<String> ids = new ArrayList<>();
        for (SpawnRuleSpec.Definition def : ordered) {
            ids.add(def.id());
        }
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** 本次解析 + 交叉校验的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 按 action 分组的件数（大小写不敏感地原样输出解析结果，升序键）。 */
    public Map<String, Integer> countByAction() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SpawnRuleSpec.Definition def : ordered) {
            out.merge(def.action(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(out);
    }

    /** mob 引用悬空的规则条数（`/redtale spawn check` 与 reload 回显用；快照已告警，这里只做计数）。 */
    public int danglingCount(MobRegistry mobs) {
        int n = 0;
        for (SpawnRuleSpec.Definition def : ordered) {
            if (mobs == null || !mobs.has(def.mob())) {
                n++;
            }
        }
        return n;
    }

    /** 一行摘要，进 reload 日志与 {@code /redtale status}。 */
    public String describe() {
        if (ordered.isEmpty()) {
            return "0 条刷怪规则";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ordered.size()).append(" 条刷怪规则（");
        boolean first = true;
        for (Map.Entry<String, Integer> e : countByAction().entrySet()) {
            if (!first) {
                sb.append(" / ");
            }
            sb.append(e.getKey()).append(' ').append(e.getValue());
            first = false;
        }
        return sb.append('）').toString();
    }
}
