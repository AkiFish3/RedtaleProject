package com.redtale.project.core.script;

import com.google.gson.JsonObject;

/**
 * condition 原语：脚本执行前的守门员。返回 false 就整条脚本不执行。
 */
public interface ConditionType {

    String id();

    String description();

    boolean test(ScriptContext ctx, JsonObject args);
}
