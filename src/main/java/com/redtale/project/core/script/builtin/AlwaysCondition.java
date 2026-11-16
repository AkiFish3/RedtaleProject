package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.ConditionType;
import com.redtale.project.core.script.ScriptContext;

/** 恒真。用来把脚本写成显式结构（conditions 数组永远是数组，不用特判）。 */
public final class AlwaysCondition implements ConditionType {

    @Override
    public String id() {
        return "always";
    }

    @Override
    public String description() {
        return "无参数，恒为 true";
    }

    @Override
    public boolean test(ScriptContext ctx, JsonObject args) {
        return true;
    }
}
