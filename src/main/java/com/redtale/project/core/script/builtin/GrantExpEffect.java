package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.progression.ProgressService;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code grant_exp} 原语：给一个玩家发经验（M7 第三条经验来源 {@code primitive}，R46）。
 *
 * <p>三条经验来源（{@code PROGRESSION.md} §5）：{@code kill}（{@code MobDrops} 同一处）、
 * {@code command}（{@code /redtale exp add}）、{@code primitive}（本原语，给技能/附魔脚本用）。
 * 三者最终都走同一个 {@link ProgressService#award}，**不新建第二份经验表**（R45-b 的孪生口径）。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code amount}：经验点数，必填；非正数 / 非数字 → 一条 WARN + 跳过（不写存档）。</li>
 *   <li>{@code target}：{@code caster}（缺省，施法者）或 {@code target}（本次触发的受害者）。
 *       只有解析出来是**玩家**才发经验 —— 怪物没有累加器，静默跳过会掩盖配置错误，故打一条 WARN。</li>
 * </ul>
 *
 * <p>异常保护（D16 口径）：负数归零、{@code 1e9} 硬顶并记日志、不拦不改。分配倍数**不在这里乘**：
 * {@code kill} 才吃 {@code mob_exp_multiplier}，{@code primitive} 原样入账（{@code ProgressService#scale}）。
 */
public final class GrantExpEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 单次发放上限，D16 的「异常保护」硬顶（不是平衡钳制；存档另有 1e15 总量顶）。
     *  public 是为了让观测面（{@code /redtale exp primitive}）用同一个数算期望，不抄第二份。 */
    public static final long HARD_CAP = 1_000_000_000L;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "grant_exp";
    }

    @Override
    public String description() {
        return "args.amount：经验点数（必填）；args.target：caster（缺省）| target —— 只对玩家生效，来源记为 primitive";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        ServerPlayer player = pick(ctx, args);
        if (player == null) {
            warnOnce("无玩家", "grant_exp 没有解析出玩家（target=caster 时需要玩家上下文；怪物没有经验累加器）");
            return;
        }
        long amount = amount(args);
        if (amount <= 0L) {
            warnOnce("零值", "grant_exp 的 args.amount 缺失 / 非数字 / <= 0，已跳过");
            return;
        }
        MinecraftServer server = ctx.server() != null ? ctx.server() : player.server;
        ProgressService.Award award = ProgressService.award(server, player, amount, ProgressService.SRC_PRIMITIVE);
        LOG.info("[script] grant_exp · {} +{}（请求 {}）· 主等级 {}→{} · modifier {} 条",
                player.getGameProfile().getName(), award.granted(), award.requested(),
                award.mainLevelBefore(), award.mainLevelAfter(), award.modifiers());
    }

    private static ServerPlayer pick(ScriptContext ctx, JsonObject args) {
        String which = args.has("target") ? args.get("target").getAsString() : "caster";
        if ("target".equals(which)) {
            return ctx.target() instanceof ServerPlayer p ? p : null;
        }
        if (ctx.hasPlayer()) {
            return ctx.player();
        }
        // 生物施法者（怪物武器上的附魔触发）：vars.attacker 可能是玩家
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof ServerPlayer p ? p : null;
    }

    private static long amount(JsonObject args) {
        if (!args.has("amount")) {
            return 0L;
        }
        long raw;
        try {
            raw = args.get("amount").getAsLong();
        } catch (RuntimeException e) {
            LOG.warn("grant_exp 的 args.amount 不是整数，按 0 处理");
            return 0L;
        }
        if (raw <= 0L) {
            return 0L;
        }
        if (raw > HARD_CAP) {
            LOG.warn("grant_exp 的 args.amount {} 超过硬顶 {}，按硬顶发放（D16 异常保护）", raw, HARD_CAP);
            return HARD_CAP;
        }
        return raw;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
