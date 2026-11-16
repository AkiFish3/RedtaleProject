package com.redtale.project.core.script;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * 一次脚本执行的上下文。effect / condition 只通过它拿世界与施法者，
 * 不直接摸静态单例，方便以后「怪物技能」「附魔触发」复用同一批原语。
 *
 * <p>player 可为 null（服务端级触发，如开服、定时器），调用方必须先用
 * {@link #hasPlayer()} 判断。
 *
 * <p><b>{@link #target()}（R24 新增）</b>：本次触发「作用在谁身上」。附魔
 * {@code post_attack} 组件里 {@code EnchantmentEntityEffect#apply} 的第 4 参就是**受害者**，
 * 施法者反而要从 {@code EnchantedItemInUse#owner()} 取（R22-c）。两个都塞不进来，
 * 所以施法者仍走 {@link #player()}（生物施法者见 {@link #vars()} 的 {@code attacker}），
 * 受害者单独放这里。target 可为 null（服务端级触发），effect 必须先判。
 */
public final class ScriptContext {

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final Map<String, Object> vars = new HashMap<>();
    private Entity target;

    private ScriptContext(MinecraftServer server, ServerPlayer player) {
        this.server = server;
        this.player = player;
    }

    public static ScriptContext ofServer(MinecraftServer server) {
        return new ScriptContext(server, null);
    }

    public static ScriptContext ofPlayer(ServerPlayer player) {
        return new ScriptContext(player.server, player);
    }

    /**
     * 一次「施法者 + 受害者」的触发（附魔 {@code post_attack} 等）。
     *
     * <p>caster 是 {@link ServerPlayer} 时 player() 有值，否则只有 {@link #target()} 与
     * {@link #vars()} 里的 {@code attacker}（生物施法者）。caster 为 null 时退化成服务端上下文。
     */
    public static ScriptContext ofEntity(Entity caster, Entity target) {
        ScriptContext ctx;
        if (caster instanceof ServerPlayer p) {
            ctx = ofPlayer(p);
        } else if (caster != null && caster.level() != null && caster.level().getServer() != null) {
            ctx = ofServer(caster.level().getServer());
        } else {
            ctx = new ScriptContext(target != null && target.level() != null
                    ? target.level().getServer() : null, null);
        }
        return ctx.target(target);
    }

    /** 本次触发的作用对象（受害者 / 被命中实体），可能为 null。 */
    public Entity target() {
        return target;
    }

    public ScriptContext target(Entity target) {
        this.target = target;
        return this;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public MinecraftServer server() {
        return server;
    }

    /** 可能为 null，用 {@link #hasPlayer()} 先判断。 */
    public ServerPlayer player() {
        return player;
    }

    public boolean hasPlayer() {
        return player != null;
    }

    /**
     * 极简占位符：{@code %player%} 与 {@code %var.&lt;key&gt;%}。
     *
     * <p>{@code %var.} 读 {@link #vars()}（附魔 {@code args} 会先灌进来，见
     * {@code ScriptedEnchantmentEffect#apply}）；**未定义的键原样保留**——写错的占位符
     * 应该原样出现在日志里，而不是被替换成空串后看不出问题。
     */
    public String expand(String template) {
        if (template == null) {
            return "";
        }
        String name = player != null ? player.getGameProfile().getName() : "server";
        String out = template.replace("%player%", name);
        if (out.indexOf("%var.") < 0 || vars.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String value = entry.getValue() == null ? "null" : String.valueOf(entry.getValue());
            out = out.replace("%var." + entry.getKey() + "%", value);
        }
        return out;
    }

    public Map<String, Object> vars() {
        return vars;
    }
}
