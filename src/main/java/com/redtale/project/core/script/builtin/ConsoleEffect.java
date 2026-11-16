package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 只写服务端日志，用来验证「配置改完 reload 就生效」而不刷玩家屏。 */
public final class ConsoleEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    @Override
    public String id() {
        return "console_log";
    }

    @Override
    public String description() {
        return "args.text：写一行服务端日志，支持 %player% 占位符";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        String text = args.has("text") ? args.get("text").getAsString() : "(空)";
        LOG.info("[script] {}", ctx.expand(text));
    }
}
