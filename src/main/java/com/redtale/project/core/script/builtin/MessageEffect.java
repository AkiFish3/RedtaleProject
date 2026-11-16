package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.network.chat.Component;

/** 给玩家发一条聊天栏消息。M0 的验收 effect。 */
public final class MessageEffect implements EffectType {

    @Override
    public String id() {
        return "message";
    }

    @Override
    public String description() {
        return "args.text：聊天栏文本，支持 %player% 占位符；无玩家上下文时静默跳过";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        if (!ctx.hasPlayer()) {
            return;
        }
        // FakePlayer / 离线上下文没有连接：发消息会 NPE，这里静默跳过（不影响同批其它 effect）
        if (ctx.player().connection == null) {
            return;
        }
        if (!args.has("text")) {
            return;
        }
        ctx.player().sendSystemMessage(Component.literal(ctx.expand(args.get("text").getAsString())));
    }
}
