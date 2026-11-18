package com.redtale.project.core.progression;

import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能树快照（M7 · D25 第二层）。{@link SkillTreeSpec} 只负责解析，本类负责
 * 「交叉校验 + 属性键归一 + 把告警绑在快照上」，与 {@code ProgressRegistry} / {@code SkillRegistry} 同构。
 *
 * <p>本层做的三件事（{@code SKILL-TREE.md} §3 / §5）：
 * <ul>
 *   <li><b>classes 对齐 {@code jobs/}</b>：职业名不在 {@code ProgressRegistry.jobs()} 里 → 告警但保留树
 *       （先写树、后补职业是正常运营动作，与 R47-b 同一条纪律）；</li>
 *   <li><b>{@code unlock_skill} 对齐 {@code skills/}</b>：技能 id 不存在 → 告警但保留节点（解析层不认识技能表）；</li>
 *   <li><b>{@code attribute} 效果过 80 条表 + 归一全名</b>：命中的条把 {@code attribute} 改写成
 *       {@code AttributeSpec.id()}（带命名空间的原版 id / 自建裸 path），否则丢弃该条并告警 ——
 *       不做这一步，L3 下发时又会踩 R45-a 那种「{@code ResourceLocation.parse} 把自建裸 path 补成
 *       {@code minecraft:} → modifier 静默不下发」。</li>
 * </ul>
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class SkillTreeRegistry {

    public static final SkillTreeRegistry EMPTY = new SkillTreeRegistry(Map.of(), List.of());

    private final Map<String, SkillTreeSpec.Definition> trees;
    private final List<String> warnings;

    private SkillTreeRegistry(Map<String, SkillTreeSpec.Definition> trees, List<String> warnings) {
        this.trees = trees;
        this.warnings = warnings;
    }

    /**
     * 解析 {@code skill_trees/*.json5} 并交叉校验。
     *
     * <p><b>调用顺序即依赖</b>（R47-a）：本方法必须排在 {@code progress} 与 {@code skills} 之后调用，
     * 否则两条交叉校验会把每棵树都判成悬空。
     *
     * @param progress 等级/职业快照（校验 {@code classes}）；可传 {@code null} 表示跳过该条校验
     * @param skills   技能快照（校验 {@code unlock_skill.skill}）；可传 {@code null} 表示跳过该条校验
     * @param warnings 告警汇聚点；{@code null} 时内部自建
     */
    public static SkillTreeRegistry load(List<ConfigFile> all, ProgressRegistry progress,
                                         SkillRegistry skills, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, SkillTreeSpec.Definition> parsed = SkillTreeSpec.parse(all, sink);
        Map<String, SkillTreeSpec.Definition> out = new LinkedHashMap<>();
        for (SkillTreeSpec.Definition def : parsed.values()) {
            out.put(def.id(), validate(def, progress, skills, sink));
        }
        return new SkillTreeRegistry(Collections.unmodifiableMap(out), List.copyOf(sink));
    }

    /** 全部树定义（不可变、保配置文件顺序）。 */
    public Map<String, SkillTreeSpec.Definition> all() {
        return trees;
    }

    public SkillTreeSpec.Definition get(String id) {
        return id == null ? null : trees.get(id);
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public int size() {
        return trees.size();
    }

    /** 全部节点数合计（`/redtale tree list` 的汇总行用）。 */
    public int nodeCount() {
        int n = 0;
        for (SkillTreeSpec.Definition d : trees.values()) {
            n += d.nodes().size();
        }
        return n;
    }

    /** 全部点数上限合计（`max_points` 之和，仅用于摘要，不参与任何判定）。 */
    public int pointTotal() {
        int n = 0;
        for (SkillTreeSpec.Definition d : trees.values()) {
            n += d.maxPoints();
        }
        return n;
    }

    /** 升序 id；输出与对账用。 */
    public List<String> sortedIds() {
        List<String> ids = new ArrayList<>(trees.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /**
     * 某个职业可用的树（`classes` 为空 = 全职业可用，{@code SKILL-TREE.md} §3 原话）。
     * 保配置顺序；`job` 为 {@code null} 时返回空表。
     *
     * <p>比较一律按 **path**（`redtale_project:warrior` 与 `warrior` 视为同一职业）——
     * `classes` 里写裸名（§3 的写法），`jobs/*.json5` 的 id 是带命名空间的全名，两边形态不同（R50-a）。
     */
    public List<String> treesForJob(String job) {
        List<String> ids = new ArrayList<>();
        if (job == null) {
            return ids;
        }
        String want = pathOf(job);
        for (SkillTreeSpec.Definition d : trees.values()) {
            if (d.classes().isEmpty()) {
                ids.add(d.id());
                continue;
            }
            for (String c : d.classes()) {
                if (pathOf(c).equals(want)) {
                    ids.add(d.id());
                    break;
                }
            }
        }
        return List.copyOf(ids);
    }

    /** 本次解析的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 一行摘要，进 reload 日志与 `/redtale status`。 */
    public String describe() {
        if (trees.isEmpty()) {
            return "0 棵技能树";
        }
        return trees.size() + " 棵技能树（" + nodeCount() + " 个节点，" + pointTotal() + " 点上限）";
    }

    // ------------------------------------------------------------ 内部

    /**
     * 单棵树的交叉校验 + 属性键归一。返回**新**的定义对象：节点重建、效果表重建，
     * 原 {@link SkillTreeSpec} 的输出保持只读不变（L1 的产物不就地改）。
     */
    private static SkillTreeSpec.Definition validate(SkillTreeSpec.Definition def, ProgressRegistry progress,
                                                    SkillRegistry skills, List<String> sink) {
        if (progress != null) {
            // 形态差异：classes 写裸名（§3），jobs/*.json5 的 id 是带命名空间的全名 → 按 path 比（R50-a）
            java.util.Set<String> jobPaths = new java.util.LinkedHashSet<>();
            for (String id : progress.jobs().keySet()) {
                jobPaths.add(pathOf(id));
            }
            for (String job : def.classes()) {
                if (!jobPaths.contains(pathOf(job))) {
                    sink.add(def.source() + ": 树 " + def.id() + " 的职业 " + job
                            + " 不在 jobs/ 里（树仍保留，职业过滤时不会命中）");
                }
            }
        }
        Map<String, SkillTreeSpec.Node> nodes = new LinkedHashMap<>();
        for (SkillTreeSpec.Node node : def.nodes().values()) {
            List<SkillTreeSpec.Effect> effects = new ArrayList<>();
            for (SkillTreeSpec.Effect e : node.effects()) {
                if ("unlock_skill".equals(e.type())) {
                    if (skills != null && !skills.has(e.skill())) {
                        sink.add(def.source() + ": 树 " + def.id() + " 的节点 " + node.id()
                                + " 引用的技能 " + e.skill() + " 不存在（该节点点了也不给技能）");
                    }
                    effects.add(e);
                } else if ("attribute".equals(e.type())) {
                    AttributeSpec spec = AttributeSpecs.resolve(e.attribute());
                    if (spec == null) {
                        sink.add(def.source() + ": 树 " + def.id() + " 的节点 " + node.id()
                                + " 的属性 " + e.attribute() + " 不在 80 条属性表里，已丢弃（该条不会下发）");
                        continue;
                    }
                    effects.add(new SkillTreeSpec.Effect(e.type(), spec.id(), e.operation(), e.value(), null, null));
                } else {
                    effects.add(e);
                }
            }
            nodes.put(node.id(), new SkillTreeSpec.Node(node.id(), node.name(), node.cost(), node.pos(),
                    node.requires(), Collections.unmodifiableList(effects)));
        }
        return new SkillTreeSpec.Definition(def.id(), def.name(), def.icon(), def.classes(),
                def.maxPoints(), def.pointsPerLevel(), Collections.unmodifiableMap(nodes), def.source());
    }

    /** 去掉命名空间：`redtale_project:warrior` → `warrior`；无冒号原样返回（trim）。 */
    private static String pathOf(String id) {
        if (id == null) {
            return "";
        }
        String t = id.trim();
        int i = t.indexOf(':');
        return i < 0 ? t : t.substring(i + 1);
    }
}
