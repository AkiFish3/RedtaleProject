package com.redtale.project.core.skill;

import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.script.ScriptCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能定义快照（M5-A）。{@link SkillSpec} 只负责解析，本类负责「存一份给人读的」。
 *
 * <p>为什么单开一类而不是直接拿 {@code Map<String, Definition>}：
 * 技能不只是 id → 定义，还要能被命令、校验、怪物 AI 三处共用同一份**不可变**视图，
 * 并且把「解析告警」与「定义」绑在一起（换配置时一起换掉，不让旧告警残留）。
 *
 * <p>线程：只在服务端主线程读写（reload 与命令同线程），不加锁。
 */
public final class SkillRegistry {

    public static final SkillRegistry EMPTY = new SkillRegistry(Map.of(), List.of());

    private final Map<String, SkillSpec.Definition> byId;
    private final List<String> warnings;

    private SkillRegistry(Map<String, SkillSpec.Definition> byId, List<String> warnings) {
        this.byId = byId;
        this.warnings = warnings;
    }

    /**
     * 解析全部 {@code skills/*.json5} 并打包成快照。
     *
     * @param all      全部已加载配置（本类只挑 {@code skills/} 前缀）
     * @param warnings 告警汇聚点；{@code null} 时内部自建（不往回写，避免调用方 NPE）
     */
    public static SkillRegistry load(List<ConfigFile> all, List<String> warnings) {
        List<String> sink = warnings != null ? warnings : new ArrayList<>();
        Map<String, SkillSpec.Definition> parsed = SkillSpec.parse(all, sink);
        return new SkillRegistry(parsed, List.copyOf(sink));
    }

    /** 全部技能定义（不可变、保配置文件顺序）。 */
    public Map<String, SkillSpec.Definition> all() {
        return byId;
    }

    /** 按全名 id 取；不存在返回 {@code null}（调用方自己决定报错文案）。 */
    public SkillSpec.Definition get(String id) {
        return id == null ? null : byId.get(id);
    }

    public boolean has(String id) {
        return id != null && byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    /** 升序 id 列表（输出/对账用；{@link #all()} 保留的是配置顺序，两者用途不同）。 */
    public List<String> sortedIds() {
        List<String> ids = new ArrayList<>(byId.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** 本次解析的告警（与 {@code RedtaleCore.configWarnings()} 同源，这里留一份便于本包自查）。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 全部技能加起来有多少条 effect（只读统计，排障时看「技能是不是空的」）。 */
    public int effectCount() {
        int n = 0;
        for (SkillSpec.Definition def : byId.values()) {
            n += def.effects().size();
        }
        return n;
    }

    /** 全部技能引用的原语 type（去重、升序）。运行期校验「原语是否存在」的输入，不在解析期做。 */
    public List<String> primitiveTypes() {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (SkillSpec.Definition def : byId.values()) {
            for (ScriptCall call : def.conditions()) {
                seen.putIfAbsent(call.type(), Boolean.TRUE);
            }
            for (ScriptCall call : def.effects()) {
                seen.putIfAbsent(call.type(), Boolean.TRUE);
            }
        }
        List<String> out = new ArrayList<>(seen.keySet());
        Collections.sort(out);
        return List.copyOf(out);
    }

    /** 一行摘要，进 reload 日志与 {@code /redtale status}。 */
    public String describe() {
        if (byId.isEmpty()) {
            return "0 条技能";
        }
        return byId.size() + " 条技能（" + effectCount() + " 条 effect，原语 "
                + primitiveTypes().size() + " 种）";
    }
}
