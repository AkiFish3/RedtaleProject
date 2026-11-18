package com.redtale.project.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.progression.ProgressStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * {@code /redtale progress} —— M7 第三层（{@link ProgressStore}）的观测面。
 * <p>
 * 只做存档层：读 / 加 / 落盘验证。**等级是派生值**（PROGRESSION §2），这里一律只报 {@code total_exp}，
 * 不查曲线、不下发属性（L4 才做）。{@code probe} 用固定测试 UUID，控制台也能跑。
 */
public final class ProgressCommand {
    /** 探针固定账号：不占真人 UUID，重跑可幂等累加。 */
    public static final UUID PROBE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private ProgressCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("progress")
                .then(Commands.literal("status").executes(ProgressCommand::status))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                .executes(ProgressCommand::add)))
                .then(Commands.literal("probe").executes(ProgressCommand::probe));
    }

    /** 存档概况：文件名 + 玩家数（{@code computeIfAbsent} 走一遍，等于证明存档可加载）。 */
    public static int status(CommandContext<CommandSourceStack> ctx) {
        ProgressStore store = ProgressStore.get(ctx.getSource().getServer());
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("[redtale] 进度存档 " + ProgressStore.FILE_NAME
                + ".dat · 玩家 " + store.size() + " 名 · 格式 v" + ProgressStore.VERSION), false);
        if (src.getEntity() instanceof ServerPlayer player) {
            src.sendSuccess(() -> Component.literal("  你 · main_exp " + store.mainExp(player.getUUID())
                    + " · 职业 " + store.jobExps(player.getUUID()).size() + " 个"), false);
        }
        return store.size();
    }

    /** 给执行者自己加主经验（运营 / 验收入口；控制台无 UUID → 报错不写）。 */
    public static int add(CommandContext<CommandSourceStack> ctx) {
        long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "amount");
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        ProgressStore store = ProgressStore.get(src.getServer());
        long now = store.addMainExp(player.getUUID(), amount);
        src.sendSuccess(() -> Component.literal("[redtale] main_exp +" + amount + " → " + now
                + "（存档已标脏，随 save-all 落盘）"), false);
        return 1;
    }

    /** 探针：固定 UUID 加 1000 并读回；控制台可跑，证明「写 → 标脏 → 读回」闭环。 */
    public static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ProgressStore store = ProgressStore.get(src.getServer());
        long before = store.mainExp(PROBE_ID);
        long after = store.addMainExp(PROBE_ID, 1000L);
        boolean expect = after == before + 1000L && after > before;
        src.sendSuccess(() -> Component.literal("[redtale] probe " + PROBE_ID
                + " · main_exp " + before + " → " + after
                + (expect ? " · 累加正确 ✓" : " · 累加异常 ✗")), false);
        return expect ? 1 : 0;
    }
}
