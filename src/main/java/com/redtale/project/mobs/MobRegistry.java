package com.redtale.project.mobs;

import com.redtale.project.core.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 怪物定义快照（M6 第二层）。{@link MobSpec} 只负责解析，本类负责「存一份给人读的」。
 *
 * <p>与 {@code SkillRegistry} 同一个思路，原因也一样：怪物定义要被命令、校验、
 * 刷怪替换三处共用同一份**不可变**视图，并且解析告警要跟定义绑在一起换代
 * （换配置时一起换掉，旧告警不许残留）。
 *
 * <p>本类**不注册 EntityType、不建实体、不碰刷怪** —— 那些是 M6 后面几步的事。
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class MobRegistry {

    public static final MobRegistry EMPTY = new MobRegistry(Map.of(), List.of());

    private final Map<String, MobSpec.Definition> byId;
    private final List<String> warnings;

    private MobRegistry(Map<String, MobSpec.Definition> byId, List<String> warnings) {
        this.byId = byId;
        this.warnings = warnings;
    }

    /**
     * 解析全部 {@code mobs/*.json5} 并打包成快照。
     *
     * @param all      全部已加载配置（本类只挑 {@code mobs/} 前缀）
     * @param warnings 告警汇聚点；{@code null} 时内部自建（不往回写，避免调用方 NPE）
     */
    public static MobRegistry load(List<ConfigFile> all, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, MobSpec.Definition> parsed = MobSpec.parse(all, sink);
        return new MobRegistry(parsed, List.copyOf(sink));
    }

    /** 全部怪物定义（不可变、保配置文件顺序）。 */
    public Map<String, MobSpec.Definition> all() {
        return byId;
    }

    /** 按 id 取；不存在返回 {@code null}（调用方自己决定报错文案）。 */
    public MobSpec.Definition get(String id) {
        return id == null ? null : byId.get(id);
    }

    public boolean has(String id) {
        return id != null && byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    /** 升序 id 列表（输出/对账用；{@link #all()} 保留的是配置顺序）。 */
    public List<String> sortedIds() {
        List<String> ids = new ArrayList<>(byId.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** 本次解析的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 按原型分组的件数（升序键），`describe` 与 `/redtale mob list` 用。 */
    public Map<String, Integer> countByBase() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (MobSpec.Definition def : byId.values()) {
            out.merge(def.base(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(out);
    }

    /** 全部怪物引用的技能条数合计（只读统计，排障时看「怪物到底会不会放技能」）。 */
    public int skillCount() {
        int n = 0;
        for (MobSpec.Definition def : byId.values()) {
            n += def.skills().size();
        }
        return n;
    }

    /** 一行摘要，进 reload 日志与 {@code /redtale status}。 */
    public String describe() {
        if (byId.isEmpty()) {
            return "0 只怪物";
        }
        return byId.size() + " 只怪物（" + skillCount() + " 条技能引用，原型 "
                + countByBase().size() + " 种）";
    }
}
