package com.redtale.project.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.mobs.SpawnReplacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * {@code /redtale spawn list|check|replace} —— 刷怪规则快照与执行层的只读观测面（D26 / M6）。
 *
 * <p>{@code list} / {@code check} 只读 {@code RedtaleCore.spawnRules()} / {@code mobs()} 两份现成快照：
 * 不解析配置、不生成实体、不读 Level（红线见 SPAWN-RULES §8）。输出逐字固定，两次运行完全一致。
 *
 * <p>{@code replace}（R48）是第三层 {@link SpawnReplacer} 的入口：开关 + 决策树探针 + 真事件演练。
 */
public final class SpawnCommand {

    private SpawnCommand() {
    }

    /** 命令节点：{@code /redtale spawn [list|check|replace]}。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("spawn")
                .then(Commands.literal("list").executes(SpawnCommand::list))
                .then(Commands.literal("check").executes(SpawnCommand::check))
                .then(Commands.literal("replace")
                        .executes(SpawnCommand::replaceStatus)
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx, false)))
                        .then(Commands.literal("cap")
                                .then(Commands.argument("per_tick", IntegerArgumentType.integer(1, 4096))
                                        .executes(SpawnCommand::cap)))
                        .then(Commands.literal("probe")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(SpawnCommand::replaceProbe))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(SpawnCommand::replaceFire)))));
    }

    /** 快照清单：头行 + 按 {@code ordered()} 每条一行；空表只打一行。返回规则条数 N。 */
    public static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        var rules = core.spawnRules();
        if (rules.size() == 0) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有刷怪规则（读 "
                    + core.config().root().resolve("spawn_rules") + "/*.json5）"), false);
            return 0;
        }
        HashSet<String> referenced = new HashSet<>();
        int capped = 0;
        for (var def : rules.ordered()) {
            referenced.add(def.mob());
            if (def.perChunkCap() > 0) capped++;
        }
        final String head = "[redtale] 刷怪规则 " + rules.size() + " 条 · 引用怪物 "
                + referenced.size() + " 只 · 每区块限量 " + capped + " 条";
        src.sendSuccess(() -> Component.literal(head), false);
        for (var def : rules.ordered()) {
            String cap = def.perChunkCap() > 0 ? String.valueOf(def.perChunkCap()) : "不限";
            final String line = "  " + def.id() + " · " + def.action() + " · → " + def.mob()
                    + " · 概率 " + String.format(Locale.ROOT, "%.2f", def.chance())
                    + " · 优先级 " + def.priority() + " · 上限 " + cap;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return rules.size();
    }

    /**
     * 引用对账：mob 未定义 / match 未知实体计问题（⚠），chance 0 只提示（○）。
     * 返回问题数 M；{@code M == 0} 时第二行起打「引用完整 ✓」。
     */
    public static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        var rules = core.spawnRules();
        var mobs = core.mobs();
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (var def : rules.ordered()) {
            if (!mobs.has(def.mob())) {
                problems.add("  ⚠ " + def.id() + ": mob " + def.mob() + " 未定义");
            }
            for (String e : def.match()) {
                if ("*".equals(e)) continue;
                ResourceLocation rl = ResourceLocation.tryParse(e);
                if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                    problems.add("  ⚠ " + def.id() + ": match 引用了未知实体 " + e);
                }
            }
            if (def.chance() == 0.0) {
                notes.add("  ○ " + def.id() + ": chance 0（永不生效）");
            }
        }
        final int m = problems.size();
        final String head = "[redtale] 刷怪规则 " + rules.size() + " 条 · 问题 " + m + " 个";
        src.sendSuccess(() -> Component.literal(head), false);
        for (String line : problems) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        if (m == 0) {
            src.sendSuccess(() -> Component.literal("  引用完整 ✓"), false);
        }
        for (String line : notes) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return m;
    }

    // ── spawn replace（M6 第三层，R48）────────────────────────────────────────────

    /** {@code /redtale spawn replace} —— 开关、每 tick 上限与已命中规则数。 */
    public static int replaceStatus(CommandContext<CommandSourceStack> ctx) {
        SpawnReplacer r = SpawnReplacer.get();
        final String line = "[redtale] 刷怪替换：" + (r.enabled() ? "开" : "关")
                + " · 每 tick 上限 " + r.perTickCap()
                + " · 已命中规则 " + r.hitRules() + " 条 · 钩子 MobSpawnEvent.PositionCheck";
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return r.enabled() ? 1 : 0;
    }

    /** {@code /redtale spawn replace cap <n>} —— 改每 tick 补生成上限并落盘（R55）。 */
    public static int cap(CommandContext<CommandSourceStack> ctx) {
        int value = IntegerArgumentType.getInteger(ctx, "per_tick");
        SpawnReplacer r = SpawnReplacer.get();
        int now = r.setPerTickCap(value);
        boolean saved = r.persist();
        final String line = "[redtale] 每 tick 上限已改为 " + now
                + " · " + (saved ? "已写入 spawn.json5" : "落盘失败（内存已生效，重启会回缺省 8）");
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return now;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) {
        SpawnReplacer r = SpawnReplacer.get();
        boolean now = r.setEnabled(on);
        // R53：开关必须落盘 —— 只改内存的话重启即回「开」，运营以为关了其实没关
        boolean saved = r.persist();
        final String line = "[redtale] 刷怪替换已" + (now ? "开启" : "关闭") + "（只影响后续刷怪，已拦下的仍会补生成）"
                + " · " + (saved ? "已写入 spawn.json5" : "落盘失败（内存已生效，重启会回缺省）");
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return now ? 1 : 0;
    }

    /** {@code /redtale spawn replace probe <实体 id> <x> <y> <z>} —— 只读决策树，不改任何状态。 */
    public static int replaceProbe(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        EntityType<?> type = entityType(ctx);
        if (type == null) return 0;
        Vec3 p = vec(ctx);
        for (String line : SpawnReplacer.get().explain(level, type, p.x, p.y, p.z).split("\n")) {
            final String l = line;
            ctx.getSource().sendSuccess(() -> Component.literal(l), false);
        }
        return 1;
    }

    /**
     * {@code /redtale spawn replace fire <实体 id> <x> <y> <z>} —— 造一个**真事件** POST 到总线。
     *
     * <p>无玩家在线时自然刷怪根本不跑（R37-b / R47），这是唯一能跑通「监听器挂上了 + 决策 + 下一 tick 补生成」的路。
     */
    public static int replaceFire(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        EntityType<?> type = entityType(ctx);
        if (type == null) return 0;
        Vec3 p = vec(ctx);
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            ctx.getSource().sendFailure(Component.literal("[redtale] " + type.toShortString() + " 不是 Mob，走不了刷怪链路"));
            return 0;
        }
        mob.moveTo(p.x, p.y, p.z, 0.0f, 0.0f);
        MobSpawnEvent.PositionCheck event =
                new MobSpawnEvent.PositionCheck(mob, level, MobSpawnType.NATURAL, null);
        NeoForge.EVENT_BUS.post(event);
        boolean failed = event.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL;
        final String line = "[redtale] 事件已 POST：" + type.toShortString() + " @ "
                + BlockPos.containing(p).toShortString() + " → 结果 " + event.getResult()
                + (failed ? "（拦下原版，下一 tick 补生成）" : "（放行原版）");
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** 实体 id 参数：`ResourceLocationArgument` 吃冒号（`word()` 不吃），查不到回文案、不抛。 */
    private static EntityType<?> entityType(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, "entity");
        // 必须 containsKey 再 get：ENTITY_TYPE 的注册表默认值是 pig，get 未知键会静默返回 pig（本轮实测踩到）
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            ctx.getSource().sendFailure(Component.literal("[redtale] 未知实体 " + rl + "（要注册表里的实体 id）"));
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("[redtale] 未知实体 " + rl + "（要注册表里的实体 id）"));
        }
        return type;
    }

    /** 坐标参数 → 方块中心（srg 编译面 `getBlockPos` 不声明受检异常）。 */
    private static Vec3 vec(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
