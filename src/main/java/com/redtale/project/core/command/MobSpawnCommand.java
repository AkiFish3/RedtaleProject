package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.mobs.MobSkillCaster;
import com.redtale.project.mobs.MobSpec;
import com.redtale.project.mobs.MobSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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

    /** 命令节点：{@code /redtale mob [list|spawn <id> [x y z]|cast <id> [skill]]}（与 MobCheckCommand 的同名 mob 节点合并）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("mob")
                .then(Commands.literal("list").executes(MobSpawnCommand::list))
                .then(Commands.literal("cast")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> cast(ctx, Mode.FORCE))
                                .then(Commands.literal("roll").executes(ctx -> cast(ctx, Mode.ROLL)))
                                .then(Commands.argument("skill", StringArgumentType.string())
                                        .executes(ctx -> cast(ctx, Mode.EXPLICIT)))))
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

    /** 施法入口的三种模式（验收要能分别打「掷骰」「门槛」「执行」三条路径）。 */
    public enum Mode {
        /** 忽略 chance，按配置顺序试到放出去为止（验执行链与冷却覆盖）。 */
        FORCE,
        /** 只放点名的那条（验单技能，不被其它技能的冷却干扰）。 */
        EXPLICIT,
        /** 走真实掷骰路径（与周期入口 {@code MobSkillCaster#tick} 同一个方法，验 chance 过滤）。 */
        ROLL
    }

    /**
     * 验收入口（M6 第三步第 3 小步）：找一只同内容 id 的怪（没有才现生成），对 8 格内最近的活体放一次技能。
     *
     * <p><b>复用优先</b>（R40 实测教训）：每次现生成新怪会拿到新 UUID，而冷却键是 {@code (UUID, 技能 id)}，
     * 于是「连放第二次应被冷却拦下」永远验不出来（第二只是新施法者，冷却表里干净）。
     * 16 格内有同类怪就直接用它，输出里标 {@code 复用} / {@code 新生} 以示区别。
     *
     * <p>三种 {@link Mode} 分开验：{@code ROLL} 掷骰 → {@code FORCE} 门槛 → {@code EXPLICIT} 单技能执行。
     * 三条混在一起时，「没放出来」到底是概率还是故障就分不清了。
     *
     * <p>生成的怪<b>不 discard</b>：验收要接着 {@code /data get entity} 看目标血量与效果，杀掉会丢证据。
     */
    public static int cast(CommandContext<CommandSourceStack> ctx, Mode mode) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        RedtaleCore core = RedtaleCore.get();
        MobSpec.Definition def = core.mobs().get(id);
        if (def == null) {
            src.sendFailure(Component.literal("[redtale] 没有怪物 " + id + "（现有 "
                    + core.mobs().size() + " 只：" + String.join(", ", core.mobs().sortedIds()) + "）"));
            return 0;
        }
        if (def.skills().isEmpty()) {
            src.sendFailure(Component.literal("[redtale] " + id + " 没配 skills，放不了技能"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        Mob mob = nearestExisting(level, pos, id);
        String how = "复用";
        if (mob == null) {
            MobSpawner.Result spawned = MobSpawner.spawn(level, pos.x, pos.y, pos.z, def);
            if (!spawned.ok()) {
                src.sendFailure(Component.literal("[redtale] " + spawned.message()));
                return 0;
            }
            mob = spawned.mob();
            how = "新生";
        }
        final Mob caster = mob;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(8.0D),
                e -> e != caster && e.isAlive() && !isContentMob(e));
        if (near.isEmpty()) {
            src.sendFailure(Component.literal("[redtale] " + id + " 8 格内没有活体目标（先 /summon cow）"));
            return 0;
        }
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        final LivingEntity target = near.get(0);
        MobSkillCaster.Result result;
        if (mode == Mode.EXPLICIT) {
            String skill = StringArgumentType.getString(ctx, "skill");
            int ticks = 0;
            for (MobSpec.SkillRef ref : def.skills()) {
                if (ref.skillId().equals(skill)) {
                    ticks = ref.cooldownTicks();
                    break;
                }
            }
            var er = core.skillEngine().castMob(caster, target, skill, ticks);
            // 失败时把状态与剩余冷却一起带出来 —— 只回一个 null 的话，「冷却中」与「没这个技能」看着一样
            String detail = er.cast() ? null
                    : er.status() + (er.remainingTicks() > 0 ? " " + er.remainingTicks() + "t" : "")
                            + (er.failedCondition() != null ? " " + er.failedCondition() : "");
            result = new MobSkillCaster.Result(er.cast(), skill, er.status(), er.effects(), detail);
        } else if (mode == Mode.ROLL) {
            result = MobSkillCaster.tryCast(caster, def, target);
        } else {
            result = MobSkillCaster.forceCast(caster, def, target);
        }
        String line = "[redtale] " + def.id() + "（" + how + "）→ " + target.getName().getString() + "："
                + (result.cast()
                        ? "施放 " + result.skillId() + " · " + result.status() + " · " + result.effects() + " 条 effect"
                        : "未施放（" + result.detail() + "）")
                + " · 目标血量 " + String.format("%.1f/%.1f",
                        target.getHealth(), target.getMaxHealth());
        src.sendSuccess(() -> Component.literal(line), true);
        return result.cast() ? 1 : 0;
    }

    /** 16 格内最近的同类内容怪（按 scoreboard tag 认），没有则 null。 */
    private static Mob nearestExisting(ServerLevel level, Vec3 pos, String contentId) {
        List<Mob> found = level.getEntitiesOfClass(Mob.class,
                new AABB(pos.x - 16.0D, pos.y - 16.0D, pos.z - 16.0D,
                        pos.x + 16.0D, pos.y + 16.0D, pos.z + 16.0D),
                e -> e.isAlive() && e.getTags().contains(MobSpawner.TAG_PREFIX + contentId));
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : found) {
            double d = m.distanceToSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /** 内容怪（带 {@code redtale_mob/} 标签）不当靶子 —— 否则「怪打怪」会掩盖数值问题。 */
    private static boolean isContentMob(net.minecraft.world.entity.Entity e) {
        for (String tag : e.getTags()) {
            if (tag.startsWith(MobSpawner.TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
