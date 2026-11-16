package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.ConditionType;
import com.redtale.project.core.script.ScriptContext;

/** 玩家当前生命比例低于阈值时为 true。无玩家上下文一律 false。 */
public final class HealthBelowCondition implements ConditionType {

    @Override
    public String id() {
        return "health_below";
    }

    @Override
    public String description() {
        return "args.ratio：0~1 的生命比例阈值，缺省 0.5";
    }

    @Override
    public boolean test(ScriptContext ctx, JsonObject args) {
        if (!ctx.hasPlayer()) {
            return false;
        }
        double ratio = args.has("ratio") ? args.get("ratio").getAsDouble() : 0.5D;
        double max = ctx.player().getMaxHealth();
        if (max <= 0.0D) {
            return false;
        }
        return (ctx.player().getHealth() / max) < ratio;
    }
}
