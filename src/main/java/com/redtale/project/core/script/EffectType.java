package com.redtale.project.core.script;

/**
 * effect 原语。注册表项在启动期冻结，**加新原语必须换 jar**，
 * 所以 M0/M1 要一次预留够（PLAN §9）。
 */
public interface EffectType {

    /** 配置里写的 id，全小写下划线。 */
    String id();

    /** 一句话说明，会写进 config/redtale_project/README.md。 */
    String description();

    void run(ScriptContext ctx, com.google.gson.JsonObject args);
}
