package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.progression.ProgressService;
import com.redtale.project.core.progression.ProgressSpec;
import com.redtale.project.core.progression.ProgressStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * {@code /redtale exp} —— M7 L4 的观测面与运营入口（等级是派生值，这里一律现算）。
 *
 * <p>与 {@code /redtale progress}（L3 存档层，只报 {@code total_exp}）分工：本命令回答
 * 「现在几级 / 离下一级还差多少 / 属性下发了几条」，写操作只有 {@code add}（{@code command} 来源）与 {@code sync}（重算）。
 *
 * <p>{@code probe} 走 {@link net.neoforged.neoforge.common.util.FakePlayerFactory}，控制台可跑，
 * 断言写在命令里（A 首加 / B 升级后属性值 / C 到顶 / D 归零清 modifier）。
 */
public final class ExpCommand {

    private ExpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("exp")
                .executes(ExpCommand::status)
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ExpCommand::add)))
                .then(Commands.literal("sync").executes(ExpCommand::sync))
                .then(Commands.literal("primitive")
                        .then(Commands.argument("amount", LongArgumentType.longArg(Long.MIN_VALUE))
                                .executes(ExpCommand::primitive)))
                .then(Commands.literal("probe").executes(ExpCommand::probe));
    }

    /** 自己的等级概况：主等级 + 每个职业各一行（{@code 等级 = 曲线(total_exp)}）。 */
    public static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        ProgressSpec.Definition main = ProgressService.mainDef();
        if (main == null) {
            src.sendFailure(Component.literal("[redtale] 没有等级定义（读 config/redtale_project/levels）"));
            return 0;
        }
        ProgressStore store = ProgressStore.get(src.getServer());
        String mainLine = lineOf(main, store.mainExp(player.getUUID()));
        src.sendSuccess(() -> Component.literal("[redtale] " + player.getGameProfile().getName()
                + "（合计 modifier 见 /attribute）"), false);
        src.sendSuccess(() -> Component.literal("  " + mainLine), false);
        List<ProgressSpec.Definition> jobs = new java.util.ArrayList<>(RedtaleCore.get().progress().jobs().values());
        for (ProgressSpec.Definition job : jobs) {
            String line = lineOf(job, store.jobExp(player.getUUID(), job.id()));
            src.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return 1;
    }

    /** 一条定义的现况：等级 · 累计 · 本级已积累/本级所需 · 到顶标记。 */
    private static String lineOf(ProgressSpec.Definition def, long total) {
        int level = ProgressService.levelOf(def, total);
        long floor = def.curve().totalExpForLevel(level);
        long need = def.curve().expForLevel(level);
        StringBuilder sb = new StringBuilder();
        sb.append(def.job() ? "职业 " : "等级 ").append(def.id()).append(" ").append(def.name())
                .append(" · Lv ").append(level).append("/").append(def.curve().maxLevel())
                .append(" · 累计 ").append(total);
        if (level >= def.curve().maxLevel()) {
            sb.append(" · 已满级");
        } else {
            sb.append(" · 本级 ").append(Math.max(0L, total - floor)).append("/").append(need)
                    .append(" · 还差 ").append(Math.max(0L, floor + need - total));
        }
        return sb.toString();
    }

    /** {@code command} 来源：给自己加经验（唯一写入口，走 {@link ProgressService#award}）。 */
    public static int add(CommandContext<CommandSourceStack> ctx) {
        long amount = LongArgumentType.getLong(ctx, "amount");
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        ProgressService.Award award = ProgressService.award(src.getServer(), player, amount,
                ProgressService.SRC_COMMAND);
        src.sendSuccess(() -> Component.literal("[redtale] " + award.describe()), false);
        return award.granted() > 0 ? 1 : 0;
    }

    /** 按当前曲线重算并重下发（改完配置又不想整服 reload 时用）。 */
    public static int sync(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        int n = ProgressService.applyAttributes(player);
        src.sendSuccess(() -> Component.literal("[redtale] 已按当前曲线重算 · modifier " + n + " 条"), false);
        return 1;
    }

    /**
     * 端到端探测（控制台可跑）：FakePlayer 走一遍「加经验 → 升等级 → 属性上涨 → 到顶 → 归零清场」。
     * 断言逐条打印 {@code ✓/✗}，返回值为通过条数（7 条全过 = 7）。
     */
    public static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var level = src.getServer().overworld();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        ProgressStore store = ProgressStore.get(src.getServer());
        ProgressSpec.Definition main = ProgressService.mainDef();
        if (main == null) {
            src.sendFailure(Component.literal("[redtale] 没有等级定义，探针跳过"));
            return 0;
        }
        int pass = 0;
        var health = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
        // 清场：固定 FakePlayer 可能是上一轮残留（经验只增不减，不清会一路累加到顶）
        store.clear(player.getUUID());
        ProgressService.applyAttributes(player);
        double baseline = player.getAttributeValue(health);
        src.sendSuccess(() -> Component.literal("[redtale] exp probe（FakePlayer）· 基线最大生命 " + baseline), false);

        // A：+1000（默认曲线 100/1.12 → Lv 7）→ 0.5×6 = 3.0 → 23.0
        ProgressService.Award a = ProgressService.award(src.getServer(), player, 1000L, ProgressService.SRC_COMMAND);
        int lvA = ProgressService.mainLevel(src.getServer(), player.getUUID());
        double hpA = player.getAttributeValue(health);
        double wantA = baseline + 0.5 * (lvA - 1);
        boolean okA = a.granted() == 1000L && lvA > 1 && Math.abs(hpA - wantA) < 1e-6;
        pass += okA ? 1 : 0;
        src.sendSuccess(() -> Component.literal("  A +1000 → Lv " + lvA + " · 最大生命 " + hpA
                + "（应 " + wantA + "）" + (okA ? " ✓" : " ✗")), false);

        // E：双累加器（§5）—— 同一份经验只进 exp_sources 命中的职业；command 来源不得写进只吃 kill 的职业
        long jobExpA = 0L;
        boolean okE = true;
        StringBuilder jobs = new StringBuilder();
        for (ProgressSpec.Definition job : RedtaleCore.get().progress().jobs().values()) {
            long got = store.jobExp(player.getUUID(), job.id());
            long want = job.expSources().contains(ProgressService.SRC_COMMAND) ? 1000L : 0L;
            jobExpA = Math.max(jobExpA, got);
            okE &= got == want;
            jobs.append(job.id()).append('=').append(got).append(' ');
        }
        pass += okE ? 1 : 0;
        String jobText = jobs.length() == 0 ? "（没有职业）" : jobs.toString().trim();
        boolean eOk = okE;
        src.sendSuccess(() -> Component.literal("  E 双累加器（command 来源）→ " + jobText
                + (eOk ? " ✓" : " ✗")), false);

        // F：kill 来源吃 mob_exp_multiplier（§5 唯一倍率），命中职业的 increment 与主等级同一个数
        long beforeMain = store.mainExp(player.getUUID());
        Map<String, Long> jobsBefore = new java.util.LinkedHashMap<>();
        for (ProgressSpec.Definition job : RedtaleCore.get().progress().jobs().values()) {
            jobsBefore.put(job.id(), store.jobExp(player.getUUID(), job.id()));
        }
        ProgressService.Award f = ProgressService.award(src.getServer(), player, 1000L, ProgressService.SRC_KILL);
        double mult = RedtaleCore.get().progressRules().mobExpMultiplier();
        long wantF = Math.round(1000.0 * (mult > 0.0 ? mult : 1.0));
        boolean okF = f.granted() == wantF && store.mainExp(player.getUUID()) == beforeMain + wantF;
        for (ProgressSpec.Definition job : RedtaleCore.get().progress().jobs().values()) {
            if (job.expSources().contains(ProgressService.SRC_KILL)) {
                okF &= store.jobExp(player.getUUID(), job.id()) == jobsBefore.getOrDefault(job.id(), 0L) + wantF;
            }
        }
        pass += okF ? 1 : 0;
        boolean fOk = okF;
        src.sendSuccess(() -> Component.literal("  F kill ×" + mult + " → 实发 " + f.granted()
                + "（应 " + wantF + "）" + (fOk ? " ✓" : " ✗")), false);

        // G：属性下发口径（§6）—— 每条声明过的属性，当前值 − 基值 必须等于「主等级 + 全部职业」plan 之和
        java.util.Map<String, Double> want = new java.util.LinkedHashMap<>();
        java.util.List<ProgressSpec.Definition> all = new java.util.ArrayList<>();
        all.add(main);
        all.addAll(RedtaleCore.get().progress().jobs().values());
        for (ProgressSpec.Definition def : all) {
            long total = def.job() ? store.jobExp(player.getUUID(), def.id())
                    : store.mainExp(player.getUUID());
            for (ProgressService.Planned p : ProgressService.plan(def, ProgressService.levelOf(def, total))) {
                want.merge(p.attribute(), p.amount(), Double::sum);
            }
        }
        boolean okG = true;
        String detail = "（声明属性 0 条）";
        for (java.util.Map.Entry<String, Double> e : want.entrySet()) {
            var holder = com.redtale.project.core.attr.RedtaleAttributes.holder(
                    com.redtale.project.core.attr.AttributeSpecs.resolve(e.getKey()));
            if (holder == null) {
                okG = false;
                detail = e.getKey() + " 不在注册表";
                break;
            }
            double delta = player.getAttributeValue(holder) - player.getAttributeBaseValue(holder);
            detail = e.getKey() + " Δ" + delta + "（应 " + e.getValue() + "）";
            if (Math.abs(delta - e.getValue()) > 1e-6) {
                okG = false;
                break;
            }
        }
        pass += okG ? 1 : 0;
        String gDetail = detail;
        boolean gOk = okG;
        src.sendSuccess(() -> Component.literal("  G 按级属性 Σplan → " + gDetail + (gOk ? " ✓" : " ✗")), false);

        // B：一次加满 → 到顶 100 级，modifier = 0.5×99 = 49.5，属性不再涨
        ProgressService.award(src.getServer(), player, ProgressStore.MAX_TOTAL_EXP, ProgressService.SRC_COMMAND);
        int lvB = ProgressService.mainLevel(src.getServer(), player.getUUID());
        double hpB = player.getAttributeValue(health);
        boolean okB = lvB == main.curve().maxLevel() && Math.abs(hpB - (baseline + 0.5 * (lvB - 1))) < 1e-6;
        pass += okB ? 1 : 0;
        src.sendSuccess(() -> Component.literal("  B 加满 → Lv " + lvB + "/" + main.curve().maxLevel()
                + " · 最大生命 " + hpB + (okB ? " ✓" : " ✗")), false);

        // C：到顶后再加 → 等级与属性都不动（K5）
        ProgressService.award(src.getServer(), player, 100_000L, ProgressService.SRC_COMMAND);
        double hpC = player.getAttributeValue(health);
        boolean okC = ProgressService.mainLevel(src.getServer(), player.getUUID()) == lvB
                && Math.abs(hpC - hpB) < 1e-9;
        pass += okC ? 1 : 0;
        src.sendSuccess(() -> Component.literal("  C 到顶再加 10 万 → Lv "
                + ProgressService.mainLevel(src.getServer(), player.getUUID())
                + " · 最大生命 " + hpC + (okC ? " ✓" : " ✗")), false);

        // D：经验归零 → 「先移除再加」把 modifier 清掉，属性回到基线（R20 的只增不减坑）
        store.setMainExp(player.getUUID(), 0L);
        for (String jobId : new java.util.ArrayList<>(store.jobExps(player.getUUID()).keySet())) {
            store.addJobExp(player.getUUID(), jobId, 0L);
        }
        store.clear(player.getUUID());
        ProgressService.applyAttributes(player);
        double hpD = player.getAttributeValue(health);
        boolean okD = Math.abs(hpD - baseline) < 1e-6;
        pass += okD ? 1 : 0;
        src.sendSuccess(() -> Component.literal("  D 归零 → 最大生命 " + hpD + "（应回到基线 " + baseline + "）"
                + (okD ? " ✓" : " ✗")), false);

        store.clear(player.getUUID());
        ProgressService.applyAttributes(player);
        int total = pass;
        src.sendSuccess(() -> Component.literal("[redtale] exp probe " + total + "/7 通过"), false);
        return total;
    }

    /** 职业快照现状（给 {@code status} 之外的排查用；空表返回 0）。 */
    public static Map<String, Long> jobsOf(ServerPlayer player) {
        return ProgressStore.get(player.server).jobExps(player.getUUID());
    }

    /**
     * {@code primitive} 来源的端到端探测（控制台可跑）：把 {@code grant_exp} 当一个**真实原语调用**跑，
     * 走 {@code ScriptEngine.runCalls} → 注册表 → 原语 → {@code ProgressService.award}，不绕过任何一层。
     *
     * <p>输出三行：前/后累计经验与主等级、职业是否被写（{@code primitive} 不进 {@code kill} 职业）。
     * 只读断言靠「前后差值」，不硬编码等级（R45-e）。
     */
    public static int primitive(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long amount = LongArgumentType.getLong(ctx, "amount");
        var level = src.getServer().overworld();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        ProgressStore store = ProgressStore.get(src.getServer());
        java.util.UUID id = player.getUUID();
        // 清场（R45-f）：FakePlayer 的 UUID 固定，不清会「上一轮加满 → 这一轮直接顶到顶」
        store.clear(id);
        ProgressService.applyAttributes(player);
        long before = store.mainExp(id);
        Map<String, Long> jobsBefore = store.jobExps(id);
        int lvBefore = ProgressService.mainLevel(src.getServer(), id);

        var args = new com.google.gson.JsonObject();
        args.addProperty("amount", amount);
        var calls = List.of(new com.redtale.project.core.script.ScriptCall("grant_exp", args));
        int done = RedtaleCore.get().engine().runCalls(calls, com.redtale.project.core.script.ScriptContext.ofPlayer(player),
                "grant_exp 探针");

        long after = store.mainExp(id);
        int lvAfter = ProgressService.mainLevel(src.getServer(), id);
        Map<String, Long> jobsAfter = store.jobExps(id);
        long jobDelta = 0L;
        for (var e : jobsAfter.entrySet()) {
            jobDelta += e.getValue() - jobsBefore.getOrDefault(e.getKey(), 0L);
        }
        long delta = after - before;
        // 期望值按「原语自己的保护规则」算：<=0 不写；超硬顶按硬顶发（R45-e：别硬编码期望）
        long want = amount <= 0L ? 0L : Math.min(amount, com.redtale.project.core.script.builtin.GrantExpEffect.HARD_CAP);
        boolean ok = delta == want;
        final long fDelta = delta;
        final long fJobDelta = jobDelta;
        final int fLvAfter = lvAfter;
        src.sendSuccess(() -> Component.literal("[redtale] exp primitive（grant_exp 原语 · 调用 " + done + "/1）"
                + " 累计 " + before + " → " + after + "（Δ" + fDelta + "，请求 " + amount + "，期望 " + want + "）"
                + (ok ? " ✓" : " ✗")), false);
        src.sendSuccess(() -> Component.literal("  主等级 " + lvBefore + " → " + fLvAfter
                + " · 职业合计 Δ" + fJobDelta + "（primitive 不进只吃 kill 的职业）"), false);
        store.clear(id);
        ProgressService.applyAttributes(player);
        return ok ? 1 : 0;
    }
}
