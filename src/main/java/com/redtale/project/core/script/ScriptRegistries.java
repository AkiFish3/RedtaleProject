package com.redtale.project.core.script;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * effect / trigger / condition 三张注册表。
 *
 * <p>这三个是**启动期冻结**的注册表项（PLAN §9），加新原语必须换 jar，
 * 所以 M1 要一次预留够。重复注册直接抛异常——宁可开服就炸，也不要静默覆盖。
 */
public final class ScriptRegistries {

    private final Map<String, EffectType> effects = new LinkedHashMap<>();
    private final Map<String, TriggerType> triggers = new LinkedHashMap<>();
    private final Map<String, ConditionType> conditions = new LinkedHashMap<>();

    public void register(EffectType type) {
        put(effects, type.id(), type, "effect");
    }

    public void register(TriggerType type) {
        put(triggers, type.id(), type, "trigger");
    }

    public void register(ConditionType type) {
        put(conditions, type.id(), type, "condition");
    }

    private static <T> void put(Map<String, T> map, String id, T value, String kind) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("注册 " + kind + " 时 id 为空");
        }
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalStateException("注册 " + kind + " 的 id 必须是小写字母数字下划线：" + id);
        }
        T previous = map.putIfAbsent(id, value);
        if (previous != null) {
            throw new IllegalStateException("重复注册 " + kind + "：" + id);
        }
    }

    public EffectType effect(String id) {
        return effects.get(id);
    }

    public TriggerType trigger(String id) {
        return triggers.get(id);
    }

    public ConditionType condition(String id) {
        return conditions.get(id);
    }

    public Collection<EffectType> effects() {
        return Collections.unmodifiableCollection(effects.values());
    }

    public Collection<TriggerType> triggers() {
        return Collections.unmodifiableCollection(triggers.values());
    }

    public Collection<ConditionType> conditions() {
        return Collections.unmodifiableCollection(conditions.values());
    }
}
