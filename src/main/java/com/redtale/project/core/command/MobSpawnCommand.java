package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.mobs.MobSpec;
import com.redtale.project.mobs.MobSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /redtale mob spawn <id> [x y z]} + {@code /redtale mob list}（M6 第三步第 2 小步）。
 *
 * <p>这是「配置 → 实体」的**唯一玩家/控制台入口**：属性、六槽装备、体积、名称、标签全部由
 * {@link MobSpawner} 一次落到位，因此验收只要看一只真实体。
 *
 * <p>位置缺省 = 命令源位置（控制台拿世界出生点；玩家拿脚下），所以坐标三参是给验收用的
 * —— 测试服地表 y≈60、空中靶台要自己 fill 出来（R34 事实）。
 */
public final class MobSpawnCommand {

    private MobSpawnCommand() {
    }

    /** 命令节点：{@code /redtale mob [list|spawn <id> [x y z]]}（与 MobCheckCommand 的同名 mob 节点合并）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("mob")
                .then(Commands.literal("list").executes(MobSpawnCommand::list))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> spawn(ctx, null, null, null))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> spawn(ctx,
                                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                                DoubleArgumentType.getDouble(ctx, "z"))))))));
    }

    /** 全部怪物定义一览（id / 原型 / 等级 / 装备件数 / 技能引用数）。 */
    public static int list(CommandContext<CommandSourceStack> ctx) {
        RedtaleCore core = RedtaleCore.get();
        var mobs = core.mobs();
        if (mobs.size() == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("[redtale] 没有怪物内容（读 "
                    + core.config().root().resolve("mobs") + "/*.json5）"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[redtale] 怪物定义 " + mobs.size() + " 只（原型 "
                        + mobs.countByBase().size() + " 种）："), false);
        for (String id : mobs.sortedIds()) {
            MobSpec.Definition def = mobs.get(id);
            int gear = 0;
            for (String slot : new String[]{"mainhand", "offhand", "head", "chest", "legs", "feet"}) {
                if (!def.equipment().slot(slot).isEmpty()) {
                    gear++;
                }
            }
            String line = "  " + def.name() + " " + id + " · 原型 " + def.base()
                    + " · 等级 " + def.level() + " · 装备 " + gear + " 件 · 技能 " + def.skills().size() + " 条"
                    + " · 体积 ×" + String.format("%.2f", def.scale());
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return mobs.size();
    }

    /** 生成一只；坐标缺省取命令源位置。返回 1 成功 / 0 失败（Brigadier 口径）。 */
    public static int spawn(CommandContext<CommandSourceStack> ctx, Double x, Double y, Double z) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        RedtaleCore core = RedtaleCore.get();
        var def = core.mobs().get(id);
        if (def == null) {
            src.sendFailure(Component.literal("[redtale] 没有怪物 " + id + "（现有 "
                    + core.mobs().size() + " 只：" + String.join(", ", core.mobs().sortedIds()) + "）"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        var pos = src.getPosition();
        MobSpawner.Result result = MobSpawner.spawn(level,
                x == null ? pos.x : x, y == null ? pos.y : y, z == null ? pos.z : z, def);
        if (!result.ok()) {
            src.sendFailure(Component.literal("[redtale] " + result.message()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] " + result.message()), true);
        for (String skip : result.skips()) {
            src.sendSuccess(() -> Component.literal("  ⚠ " + skip), false);
        }
        return 1;
    }
}
