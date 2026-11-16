package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.ConditionType;
import com.redtale.project.core.script.ScriptContext;

/**
 * 按概率通过，用 {@link ScriptContext#server()} 的随机源之外的 ThreadLocalRandom，
 * 避免影响世界随机序列。
 */
public final class ChanceCondition implements ConditionType {

    @Override
    public String id() {
        return "chance";
    }

    @Override
    public String description() {
        return "args.chance：0~1 的通过概率，缺省 1.0";
    }

    @Override
    public boolean test(ScriptContext ctx, JsonObject args) {
        double chance = args.has("chance") ? args.get("chance").getAsDouble() : 1.0D;
        if (chance >= 1.0D) {
            return true;
        }
        if (chance <= 0.0D) {
            return false;
        }
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance;
    }
}
