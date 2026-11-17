package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.skill.SkillEngine;
import com.redtale.project.core.skill.SkillSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * {@code /redtale skill [list|cast|probe|cd]} —— M5-A 的技能观测面与 v1 触发入口（SKILLS §7）。
 *
 * <p>为什么先上命令而不是右键：右键要先把 {@code abilities[]} 写进物品（{@code ItemContent} 15→16，
 * 换一次 jar，SKILLS §9 的 M5-B），而引擎是否成立不该被绑在那次改动后面。
 *
 * <p>三种验法分工：
 * <ul>
 *   <li>{@code list} —— K1：只写一份 {@code skills/*.json5} + reload 就能被看见（不换 jar）</li>
 *   <li>{@code cast <id>} —— 真人玩家释放，K2 冷却 / K3 条件靠它连按两次</li>
 *   <li>{@code probe <id>} —— 无客户端时用 {@code FakePlayer} 造一个施法者 + 一只木桩牛，
 *       把 {@link SkillEngine.Result} 与掉血一起打出来（K5：技能伤害走 L2 乘区）</li>
 * </ul>
 *
 * <p>只读命令：{@code list} / {@code cd} 不改任何状态；{@code cast} 只动冷却表与技能自己的 effects。
 */
public final class SkillCommand {

    /** 探测木桩的最大血量（打不死的木桩，好对账）。 */
    private static final double PROBE_MAX_HEALTH = 1000.0D;

    private SkillCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("skill")
                .then(Commands.literal("list").executes(SkillCommand::list))
                .then(Commands.literal("cast")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> cast(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("probe")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> probe(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("cd")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> cd(ctx, StringArgumentType.getString(ctx, "id")))));
    }

    /** K1：技能快照逐条打印。行尾给 {@code source} 便于对「改的那份文件生效了没」。 */
    static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        Map<String, SkillSpec.Definition> all = core.skills().all();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[redtale] 还没有任何技能（写 skills/*.json5 后 /redtale reload）"), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] 技能 " + all.size() + " 条 · 共 "
                + core.skills().effectCount() + " 条 effect · 原语 " + core.skills().primitiveTypes().size() + " 种："), false);
        for (SkillSpec.Definition def : all.values()) {
            String line = "  " + def.id() + " · " + def.name()
                    + " · 冷却 " + def.cooldownTicks() + " tick"
                    + " · conditions " + def.conditions().size()
                    + " · effects " + def.effects().size()
                    + " · " + def.source();
            src.sendSuccess(() -> Component.literal(line), false);
        }
        List<String> warnings = core.skills().warnings();
        if (!warnings.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[redtale] 解析告警 " + warnings.size() + " 条（详见 /redtale status）"), false);
        }
        return all.size();
    }

    /** 真人释放：返回 1 = 放出去了，0 = 没放（无技能 / 条件不满足 / 冷却中）。 */
    static int cast(CommandContext<CommandSourceStack> ctx, String skillId) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 这条命令要玩家执行（控制台没有 UUID，冷却记不住）"));
            return 0;
        }
        SkillEngine.Result result = RedtaleCore.get().skillEngine().cast(player, skillId);
        return report(src, result, null);
    }

    /**
     * 无客户端探针：FakePlayer 当施法者 + 一只 1000 血 {@code NoAI} 的牛当目标，**连放两次**并打出
     * {@link SkillEngine.Result} 与目标掉血。
     *
     * <p>两组的差别只有「施法者身上有没有 {@code physical_damage +0.25}」：
     * <ul>
     *   <li>A 组：零属性 → 掉血应等于技能白值（证明 {@code damage} 原语没自己乘，D12 的第一层）</li>
     *   <li>B 组：{@code physical_damage +0.25} → 比值应 ≈ 1.25（证明技能伤害真的过 {@code DamageLayer}，
     *       而不是被 {@code setAmount} 写死）。物理技能给 1.25，法术技能给 1.00 —— 比值本身就是判据</li>
     * </ul>
     * 两次之间要 {@code cooldowns().clear(...)}：冷却按 (UUID, 技能 id) 记，FakePlayer 的 UUID 是固定的，
     * 不清就第二次直接「冷却中」（那正是 K2 的验法，但这里要的是数值对比）。
     */
    static int probe(CommandContext<CommandSourceStack> ctx, String skillId) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        var level = src.getServer().overworld();
        var fp = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        Mob cow = net.minecraft.world.entity.EntityType.COW.create(level);
        if (cow == null) {
            src.sendFailure(Component.literal("[redtale] 造不出探测目标（EntityType.COW.create 返回 null）"));
            return 0;
        }
        // 站在施法者正前方 1 格：视线索敌（SkillEngine#aimedTarget）要真的能打到
        cow.moveTo(fp.getX() + 1.0D, fp.getY(), fp.getZ(), 0.0F, 0.0F);
        var maxHealth = cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(PROBE_MAX_HEALTH);
        }
        cow.setHealth((float) PROBE_MAX_HEALTH);
        cow.setNoAi(true);
        if (!level.addFreshEntity(cow)) {
            src.sendFailure(Component.literal("[redtale] 探测目标没能进入世界"));
            return 0;
        }
        net.minecraft.resources.ResourceLocation physId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("redtale_project", "probe/skill_physical");
        var physHolder = com.redtale.project.core.attr.RedtaleAttributes.holder(
                com.redtale.project.core.attr.AttributeSpecs.byId("physical_damage"));
        var physInst = physHolder == null ? null : fp.getAttribute(physHolder);
        try {
            lookAt(fp, cow);
            src.sendSuccess(() -> Component.literal("[redtale] skill probe " + skillId + "（FakePlayer 施法，木桩牛 "
                    + (int) PROBE_MAX_HEALTH + " 血）："), false);
            // A 组：零属性
            RedtaleCore.get().skillEngine().cooldowns().clear(fp.getUUID());
            float beforeA = cow.getHealth();
            SkillEngine.Result a = RedtaleCore.get().skillEngine().cast(fp, skillId);
            double dealtA = beforeA - cow.getHealth();
            int rc = 0;
            rc += report(src, a, dealtA);
            if (!a.cast() || physInst == null) {
                return rc;
            }
            // B 组：施法者堆 physical_damage +0.25，清冷却后再放一次
            physInst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    physId, 0.25D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            try {
                RedtaleCore.get().skillEngine().cooldowns().clear(fp.getUUID());
                cow.invulnerableTime = 0;
                cow.setHealth((float) PROBE_MAX_HEALTH);
                lookAt(fp, cow);
                float beforeB = cow.getHealth();
                SkillEngine.Result b = RedtaleCore.get().skillEngine().cast(fp, skillId);
                double dealtB = beforeB - cow.getHealth();
                src.sendSuccess(() -> Component.literal("  B 组 · physical_damage +0.25 → 掉血 "
                        + com.redtale.project.core.attr.AttributeBreakdown.num(dealtB)
                        + (a.effects() == 0 ? "（A 组没跑 effect，比值无意义）"
                        : "，比值 " + com.redtale.project.core.attr.AttributeBreakdown.num(
                                dealtA == 0.0D ? 0.0D : dealtB / dealtA))), false);
                if (b.cast() && dealtA > 0.0D) {
                    double ratio = dealtB / dealtA;
                    boolean physical = Math.abs(ratio - 1.25D) < 1.0e-3D;
                    boolean magical = Math.abs(ratio - 1.0D) < 1.0e-3D;
                    src.sendSuccess(() -> Component.literal(physical
                            ? "  ✓ 物理技能：技能伤害确实过 L2 乘区（×1.25）"
                            : magical ? "  ○ 比值 1.00：法术技能不吃 physical_damage（分轴正常，R26）"
                            : "  ✗ 比值 " + com.redtale.project.core.attr.AttributeBreakdown.num(ratio)
                                    + " 既不是 1.25 也不是 1.00（有东西在 setAmount？）"), false);
                }
            } finally {
                physInst.removeModifier(physId);
            }
            return rc;
        } finally {
            cow.discard();
        }
    }

    /** 剩余冷却（只读）：用来验「剩余 tick 递减」，不记账。 */
    static int cd(CommandContext<CommandSourceStack> ctx, String skillId) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 这条命令要玩家执行"));
            return 0;
        }
        long now = player.server.overworld().getGameTime();
        long left = RedtaleCore.get().skillEngine().cooldowns().remaining(player.getUUID(), skillId, now);
        src.sendSuccess(() -> Component.literal(left <= 0L
                ? "[redtale] 技能 " + skillId + " 就绪"
                : "[redtale] " + SkillEngine.cooldownText(skillId, (int) left)), false);
        return left <= 0L ? 1 : 0;
    }

    /** 统一的 Result → 文案；{@code dealt != null} 时追打一行掉血（探测用）。 */
    private static int report(CommandSourceStack src, SkillEngine.Result result, Double dealt) {
        String line;
        int rc;
        switch (result.status()) {
            case CAST -> {
                line = "[redtale] 释放 " + result.skillId() + "：跑了 " + result.effects() + " 条 effect";
                rc = 1;
            }
            case COOLDOWN -> {
                line = "[redtale] " + SkillEngine.cooldownText(result.skillId(), result.remainingTicks());
                rc = 0;
            }
            case CONDITION -> {
                line = "[redtale] 技能 " + result.skillId() + " 条件不满足（" + result.failedCondition() + "），一条 effect 都没跑";
                rc = 0;
            }
            case NO_SKILL -> {
                line = "[redtale] 没有技能 " + result.skillId() + "（先 /redtale skill list 看有哪些）";
                rc = 0;
            }
            case NO_PLAYER -> {
                line = "[redtale] 没有施法者";
                rc = 0;
            }
            default -> {
                line = "[redtale] 未知结果 " + result.status();
                rc = 0;
            }
        }
        src.sendSuccess(() -> Component.literal(line), false);
        if (dealt != null) {
            double d = dealt;
            src.sendSuccess(() -> Component.literal("  目标掉血 " + com.redtale.project.core.attr.AttributeBreakdown.num(d)), false);
        }
        return rc;
    }

    /** 只有转向用的极小工具（{@code Entity#lookAt} 1.21.1 只吃 {@code Anchor + Vec3}，故自己算 yaw/pitch）。 */
    private static void lookAt(net.minecraft.world.entity.Entity from, net.minecraft.world.entity.Entity to) {
        Vec3 eye = from.getEyePosition();
        Vec3 aim = to.position().add(0.0D, to.getBbHeight() * 0.5D, 0.0D);
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        from.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F);
        from.setXRot((float) (-Math.toDegrees(Math.atan2(dy, horiz))));
        from.setYHeadRot(from.getYRot());
    }
}
