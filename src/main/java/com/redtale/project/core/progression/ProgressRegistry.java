package com.redtale.project.core.progression;

import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级 / 职业定义快照（M7 第二层）。{@link ProgressSpec} 只负责解析，本类负责
 * 「校验属性键 + 归一成一份可执行的定义 + 把告警绑在快照上」。
 *
 * <p>与 {@code MobRegistry} / {@code SkillRegistry} 同构：命令、校验、服务层共用同一份
 * **不可变**视图，告警随快照换代（换配置时旧告警不许残留）。
 *
 * <p>两条本层独有的口径（{@code PROGRESSION.md} §8 红线）：
 * <ul>
 *   <li><b>属性键必须命中 80 条表</b>，否则丢弃该条 + 一条 WARN（解析层故意不校验，因为它不认识属性表）；</li>
 *   <li><b>属性键归一成全名</b>（{@code attack_damage} / {@code generic.attack_damage} /
 *       {@code minecraft:attack_damage} → {@code minecraft:generic.attack_damage}）——
 *       否则同一条属性两种写法会在 {@code AttributeMap} 上变成两个 modifier，面板翻倍（R11 的坑）。</li>
 * </ul>
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class ProgressRegistry {

    public static final ProgressRegistry EMPTY = new ProgressRegistry(Map.of(), Map.of(), List.of());

    private final Map<String, ProgressSpec.Definition> levels;
    private final Map<String, ProgressSpec.Definition> jobs;
    private final List<String> warnings;

    private ProgressRegistry(Map<String, ProgressSpec.Definition> levels,
                             Map<String, ProgressSpec.Definition> jobs,
                             List<String> warnings) {
        this.levels = levels;
        this.jobs = jobs;
        this.warnings = warnings;
    }

    /**
     * 解析 {@code levels/*.json5} 与 {@code jobs/*.json5} 并打包成快照。
     *
     * @param all      全部已加载配置（本类只挑两个前缀）
     * @param warnings 告警汇聚点；{@code null} 时内部自建
     */
    public static ProgressRegistry load(List<ConfigFile> all, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, ProgressSpec.Definition> levels = normalise(ProgressSpec.parse(all, sink, false), sink);
        Map<String, ProgressSpec.Definition> jobs = normalise(ProgressSpec.parse(all, sink, true), sink);
        // 同 id 同时写进 levels/ 与 jobs/：留主等级、丢职业那份（否则 L4 会下发两套 modifier）
        if (!levels.isEmpty() && !jobs.isEmpty()) {
            Map<String, ProgressSpec.Definition> kept = new LinkedHashMap<>(jobs);
            for (String id : levels.keySet()) {
                ProgressSpec.Definition dropped = kept.remove(id);
                if (dropped != null) {
                    sink.add(dropped.source() + ": " + id + " 与主等级定义 " + levels.get(id).source()
                            + " 同 id，已按主等级处理（职业那份不下发）");
                }
            }
            jobs = Collections.unmodifiableMap(kept);
        }
        return new ProgressRegistry(levels, jobs, List.copyOf(sink));
    }

    /** 主等级定义（不可变、保配置文件顺序；v1 通常只有一条）。 */
    public Map<String, ProgressSpec.Definition> levels() {
        return levels;
    }

    /** 职业定义（不可变、保配置文件顺序）。 */
    public Map<String, ProgressSpec.Definition> jobs() {
        return jobs;
    }

    /**
     * 按 id 取定义：先查主等级再查职业。
     *
     * <p>两级分开存是因为它们的语义不同（主等级只有一个、职业可以有很多），
     * 但 id 全局唯一 —— 同 id 同时写进 {@code levels/} 与 {@code jobs/} 时主等级优先（并告警）。
     */
    public ProgressSpec.Definition get(String id) {
        if (id == null) {
            return null;
        }
        ProgressSpec.Definition lv = levels.get(id);
        return lv != null ? lv : jobs.get(id);
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    /** 主等级 + 职业的条数合计。 */
    public int size() {
        return levels.size() + jobs.size();
    }

    public int levelCount() {
        return levels.size();
    }

    public int jobCount() {
        return jobs.size();
    }

    /** 按级下发的属性条目总数（只看条数，不看值）。 */
    public int attributeCount() {
        int n = 0;
        for (ProgressSpec.Definition d : levels.values()) {
            n += d.attributes().size();
        }
        for (ProgressSpec.Definition d : jobs.values()) {
            n += d.attributes().size();
        }
        return n;
    }

    /** 升序 id（只看主等级或只看职业）；输出与对账用。 */
    public List<String> sortedIds(boolean job) {
        List<String> ids = new ArrayList<>((job ? jobs : levels).keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** 本次解析的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 一行摘要，进 reload 日志与 {@code /redtale status}。 */
    public String describe() {
        if (size() == 0) {
            return "0 条等级定义";
        }
        return "主等级 " + levels.size() + " 条 / 职业 " + jobs.size()
                + " 条（" + attributeCount() + " 条按级属性）";
    }

    // ------------------------------------------------------------ 内部

    /**
     * 属性键校验 + 归一。坏键丢弃并告警；两种写法归一到同一属性时后者覆盖并告警
     * （与 {@code AffixPools} 同一口径，R11-b）。
     */
    private static Map<String, ProgressSpec.Definition> normalise(
            Map<String, ProgressSpec.Definition> src, List<String> sink) {
        Map<String, ProgressSpec.Definition> out = new LinkedHashMap<>();
        for (ProgressSpec.Definition d : src.values()) {
            Map<String, Double> attrs = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : d.attributes().entrySet()) {
                AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
                if (spec == null) {
                    sink.add(d.source() + ": " + d.id() + " 的属性 " + e.getKey()
                            + " 不在 80 条属性表里，已丢弃（该条不会下发）");
                    continue;
                }
                if (attrs.containsKey(spec.id())) {
                    sink.add(d.source() + ": " + d.id() + " 的属性 " + e.getKey()
                            + " 与另一种写法指向同一属性（" + spec.id() + "），已用后一个值");
                }
                attrs.put(spec.id(), e.getValue());
            }
            out.put(d.id(), new ProgressSpec.Definition(d.id(), d.name(), d.job(), d.curve(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(attrs)), d.expSources(), d.source()));
        }
        return Collections.unmodifiableMap(out);
    }
}
