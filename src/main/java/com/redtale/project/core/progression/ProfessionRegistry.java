package com.redtale.project.core.progression;

import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 专业快照（D24 第二层）。{@link ProfessionSpec} 只负责解析，本类负责
 * 「交叉校验 + 属性键归一 + 把告警绑在快照上」，与 {@code ProgressRegistry} / {@code SkillTreeRegistry} 同构。
 *
 * <p>本层做的两件事（{@code PROFESSION.md} §7 的 L2 行）：
 * <ul>
 *   <li><b>{@code attributes} 过 80 条表 + 归一规范形态</b>：命中的条把键改写成 {@code AttributeSpec.id()}
 *       （自建属性 = 裸 path，原版 = 带命名空间），否则丢弃该条并告警 —— 不做这一步，L4 下发时又会踩
 *       R45-a 那种「自建裸 path 被补成 {@code minecraft:} → modifier 静默不下发」；</li>
 *   <li><b>{@code professions} 与 {@code jobs/} 的 id 重叠</b>：按 path 比（{@code mining} 与
 *       {@code redtale_project:mining} 算同一串）→ 告警但**不拦**（PROFESSION §2：重叠不拦，只让运营能解释）；
 *       重叠后同一串 id 在两个桶里各存一份经验，这是设计选择，不是错误。</li>
 * </ul>
 *
 * <p>**不做**：曲线能否产出等级（{@link ExpCurve} 构造函数已保证，重复判是死分支）、tag 是否存在
 * （datapack 数据，离线无答案，PROFESSION §4.3）、掉落、事件、存档、命令。
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class ProfessionRegistry {

    public static final ProfessionRegistry EMPTY = new ProfessionRegistry(Map.of(), List.of());

    private final Map<String, ProfessionSpec.Definition> professions;
    private final List<String> warnings;

    private ProfessionRegistry(Map<String, ProfessionSpec.Definition> professions, List<String> warnings) {
        this.professions = professions;
        this.warnings = warnings;
    }

    /**
     * 解析 {@code professions/*.json5} 并交叉校验。
     *
     * <p><b>调用顺序即依赖</b>：本方法必须排在 {@code progress} 之后调用，否则 jobs 重叠检查无从比对。
     *
     * @param progress 等级/职业快照（校验 id 重叠）；传 {@code null} 表示跳过该条校验
     * @param warnings 告警汇聚点；{@code null} 时内部自建
     */
    public static ProfessionRegistry load(List<ConfigFile> all, ProgressRegistry progress, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, ProfessionSpec.Definition> parsed = ProfessionSpec.parse(all, sink);
        Map<String, ProfessionSpec.Definition> out = new LinkedHashMap<>();
        Set<String> jobPaths = jobPaths(progress);
        for (ProfessionSpec.Definition def : parsed.values()) {
            out.put(def.id(), validate(def, jobPaths, sink));
        }
        return new ProfessionRegistry(Collections.unmodifiableMap(out), List.copyOf(sink));
    }

    /** 全部专业定义（不可变、保配置文件顺序）。 */
    public Map<String, ProfessionSpec.Definition> all() {
        return professions;
    }

    /** 按 **id 全名**精确取（{@code redtale_project:mining}）；不归一。 */
    public ProfessionSpec.Definition get(String id) {
        return id == null ? null : professions.get(id);
    }

    /**
     * 按 id 全名或裸 path 取（{@code mining} / {@code redtale_project:mining} 同一份）。
     * 精确命中优先；否则按 path 在全部定义里找第一个（重复 path 只可能来自不同命名空间，罕见，取先出现的）。
     */
    public ProfessionSpec.Definition find(String idOrPath) {
        ProfessionSpec.Definition direct = get(idOrPath);
        if (direct != null || idOrPath == null) {
            return direct;
        }
        String want = pathOf(idOrPath);
        for (ProfessionSpec.Definition d : professions.values()) {
            if (pathOf(d.id()).equals(want)) {
                return d;
            }
        }
        return null;
    }

    public boolean has(String idOrPath) {
        return find(idOrPath) != null;
    }

    public int size() {
        return professions.size();
    }

    /** 升序 id；输出与对账用。 */
    public List<String> sortedIds() {
        List<String> ids = new ArrayList<>(professions.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** 全部专业声明的属性条数合计（去重后**不去重**：按定义计数，仅用于摘要）。 */
    public int attributeTotal() {
        int n = 0;
        for (ProfessionSpec.Definition d : professions.values()) {
            n += d.attributes().size();
        }
        return n;
    }

    /** 本次解析的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 一行摘要，进 reload 日志与 {@code /redtale status}。 */
    public String describe() {
        if (professions.isEmpty()) {
            return "0 个专业";
        }
        return professions.size() + " 个专业（" + attributeTotal() + " 条属性）";
    }

    // ------------------------------------------------------------ 内部

    private static Set<String> jobPaths(ProgressRegistry progress) {
        Set<String> out = new LinkedHashSet<>();
        if (progress == null) {
            return out;
        }
        for (String id : progress.jobs().keySet()) {
            out.add(pathOf(id));
        }
        return out;
    }

    /**
     * 单个专业的交叉校验 + 属性键归一。返回**新**的定义对象（L1 的产物不就地改）。
     */
    private static ProfessionSpec.Definition validate(ProfessionSpec.Definition def, Set<String> jobPaths,
                                                      List<String> sink) {
        String path = pathOf(def.id());
        if (!jobPaths.isEmpty() && jobPaths.contains(path)) {
            sink.add(def.source() + ": 专业 " + def.id() + " 的 id 与 jobs/ 里的职业 " + path
                    + " 重叠（两个桶各存一份经验，不拦，但排查时要分清）");
        }
        Map<String, Double> attrs = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : def.attributes().entrySet()) {
            AttributeSpec spec = AttributeSpecs.resolve(e.getKey());
            if (spec == null) {
                sink.add(def.source() + ": 专业 " + def.id() + " 的属性 " + e.getKey()
                        + " 不在 80 条属性表里，已丢弃（该条不会下发）");
                continue;
            }
            attrs.put(spec.id(), e.getValue());
        }
        // 曲线正性**不在这里判**：ExpCurve 构造函数已把 base ≤ 0 / growth ≤ 1 / exponent ≤ 0 回落默认，
        // 且 expForLevel 有 1e9 硬顶 —— 本层再写一条「需求经验非正」就是永不可测的死分支（R65-a，
        // 同 R63-e「基线就红的检查等于没有检查」的反面：永远绿的检查等于没有检查）。
        return new ProfessionSpec.Definition(def.id(), def.name(), def.maxLevel(), def.curve(),
                Collections.unmodifiableMap(attrs), def.grants(), def.source());
    }

    /** 去掉命名空间：`redtale_project:mining` → `mining`；无冒号原样返回（trim）。 */
    private static String pathOf(String id) {
        if (id == null) {
            return "";
        }
        String t = id.trim();
        int i = t.indexOf(':');
        return i < 0 ? t : t.substring(i + 1);
    }
}
