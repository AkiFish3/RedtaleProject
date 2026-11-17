package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.script.ScriptCall;
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
                                .executes(ctx -> cd(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("use")
                        .then(Commands.argument("content", StringArgumentType.string())
                                .executes(ctx -> useProbe(ctx, StringArgumentType.getString(ctx, "content")))));
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

    /**
     * 右键链路的无客户端探针（K7，M5-B）：FakePlayer 手里拿一件**真实内容物品**，
     * 走 {@code ItemStack#use} 的完整路径（{@code ContentItem#use} → 技能分支）。
     *
     * <p>为什么绕这一圈而不是直接 {@code cast}：这要验的正是「配置里的 {@code abilities}
     * 有没有真的变成物品的右键行为」—— 三段任何一段断掉都不报错：解析没读到 / {@code ContentData}
     * 没写 / {@code use} 没接，现象全是「右键没反应」。
     *
     * <p>四组输出（A 带技能武器 / B 同件立刻再按 / C 无 abilities 的武器 / 空手不适用）：
     * A 应 {@code CONSUME} 且木桩掉血、B 应 {@code FAIL} 且剩余冷却 = 定义值、
     * C 应 {@code PASS} 且**一条冷却都没记**（证明不是「凡右键都放技能」）。
     */
    static int useProbe(CommandContext<CommandSourceStack> ctx, String contentId) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        var core = RedtaleCore.get();
        var chosen = core.contentItems().get(contentId);
        if (chosen == null) {
            src.sendFailure(Component.literal("[redtale] 没有内容 '" + contentId + "'"));
            return 0;
        }
        if (chosen.abilities().isEmpty()) {
            src.sendFailure(Component.literal("[redtale] " + contentId
                    + " 没有 abilities 段（右键会走 use/repair/什么都不做；本探针只验技能分支）"));
            return 0;
        }
        var level = src.getServer().overworld();
        var fp = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        Mob cow = net.minecraft.world.entity.EntityType.COW.create(level);
        if (cow == null) {
            src.sendFailure(Component.literal("[redtale] 造不出探测目标"));
            return 0;
        }
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
        String skillId = chosen.abilities().get(0);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        try {
            // random=null：不摇词条，同一件内容每次探针都逐字相同
            var stack = com.redtale.project.items.ItemFactory.build(chosen, null);
            fp.setItemInHand(hand, stack);
            fp.removeAllEffects();
            fp.getCooldowns().tick();
            core.skillEngine().cooldowns().clear(fp.getUUID());
            lookAt(fp, cow);

            src.sendSuccess(() -> Component.literal("[redtale] 右键探针 " + chosen.id()
                    + "（FakePlayer 真调 ItemStack#use；第一条技能 " + skillId + "）："), false);
            src.sendSuccess(() -> Component.literal("  定义 abilities " + chosen.abilities().size()
                    + " 条 " + chosen.abilities()), false);
            var data = fp.getItemInHand(hand).get(com.redtale.project.items.ContentComponents.CONTENT.get());
            src.sendSuccess(() -> Component.literal("  组件 abilities "
                    + (data == null ? "（无组件）" : data.abilities())
                    + " · cooldown_reduction " + SkillEngine.cooldownReduction(fp)), false);

            float beforeA = cow.getHealth();
            var retA = fp.getItemInHand(hand).use(level, fp, hand);
            double dealtA = beforeA - cow.getHealth();
            long nowA = level.getGameTime();
            long leftA = core.skillEngine().cooldowns().remaining(fp.getUUID(), skillId, nowA);
            SkillSpec.Definition defA = core.skills().get(skillId);
            src.sendSuccess(() -> Component.literal("  A 组 首次右键：返回 " + retA.getResult()
                    + " · 木桩掉血 " + com.redtale.project.core.attr.AttributeBreakdown.num(dealtA)
                    + " · " + skillId + " 剩余冷却 " + leftA + " tick（定义 "
                    + (defA == null ? "缺失！" : defA.cooldownTicks()) + "）"), false);

            cow.invulnerableTime = 0;
            float beforeB = cow.getHealth();
            var retB = fp.getItemInHand(hand).use(level, fp, hand);
            double dealtB = beforeB - cow.getHealth();
            src.sendSuccess(() -> Component.literal("  B 组 立刻再按：返回 " + retB.getResult()
                    + " · 木桩掉血 " + com.redtale.project.core.attr.AttributeBreakdown.num(dealtB)
                    + "（冷却期必须 FAIL 且 0 掉血）"), false);

            // C 组：无 abilities 的武器 —— 右键不应记任何冷却
            core.skillEngine().cooldowns().clear(fp.getUUID());
            var plain = firstWithoutAbilities(core);
            int plainRc = 0;
            if (plain != null) {
                fp.setItemInHand(hand, com.redtale.project.items.ItemFactory.build(plain, null));
                lookAt(fp, cow);
                var retC = fp.getItemInHand(hand).use(level, fp, hand);
                long leftC = core.skillEngine().cooldowns().remaining(fp.getUUID(), skillId, level.getGameTime());
                plainRc = leftC <= 0L ? 1 : 0;
                src.sendSuccess(() -> Component.literal("  C 组 无 abilities 的 " + plain.id()
                        + "：返回 " + retC.getResult() + " · 冷却记录 " + leftC
                        + "（应 PASS / 0，证明不是凡右键都放技能）"), false);
            }
            boolean a = retA.getResult() == net.minecraft.world.InteractionResult.CONSUME && leftA > 0L;
            boolean b = retB.getResult() == net.minecraft.world.InteractionResult.FAIL && dealtB == 0.0D;
            final boolean plainOk = plain == null || plainRc == 1;

            // D 组（M5-B 后半）：给施法者挂 cooldown_reduction +0.5 → 同一条技能剩余冷却应 ≈ 定义值的一半。
            // FakePlayer 不 tick，所以装备上的 modifier 刷不进 AttributeMap（R5 事实）—— 只能显式挂瞬时 modifier。
            var cdAttr = com.redtale.project.core.attr.RedtaleAttributes.custom("cooldown_reduction");
            var cdInst = cdAttr == null ? null : fp.getAttribute(cdAttr);
            long leftD = -1L;
            if (cdInst != null && defA != null) {
                var probeId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "redtale_project", "probe/cooldown_reduction");
                cdInst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        probeId, 0.5D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                try {
                    fp.setItemInHand(hand, com.redtale.project.items.ItemFactory.build(chosen, null));
                    core.skillEngine().cooldowns().clear(fp.getUUID());
                    lookAt(fp, cow);
                    var retD = fp.getItemInHand(hand).use(level, fp, hand);
                    leftD = core.skillEngine().cooldowns().remaining(fp.getUUID(), skillId, level.getGameTime());
                    final long shown = leftD;
                    src.sendSuccess(() -> Component.literal("  D 组 cooldown_reduction +0.5：返回 "
                            + retD.getResult() + " · 剩余冷却 " + shown + " tick（定义 "
                            + defA.cooldownTicks() + " 的 50% 应 ≈ " + (defA.cooldownTicks() / 2) + "）"), false);
                } finally {
                    cdInst.removeModifier(probeId);
                }
            }
            boolean d = leftD < 0L || Math.abs(leftD - (defA == null ? 0 : defA.cooldownTicks() * 0.5D)) <= 2.0D;
            src.sendSuccess(() -> Component.literal((a && b && plainOk && d)
                    ? "  ✓ K7 成立：abilities → 组件 → 右键施放 → 冷却拦截 → 冷却减免生效"
                    : "  ✗ K7 不成立（看上面几组的返回与掉血 / 剩余冷却）"), false);
            return a && b && d ? 1 : 0;
        } finally {
            fp.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
            cow.discard();
        }
    }

    /** C 组样本：第一件**非护甲、无 use/repair、无 abilities** 的内容（右键什么都不该发生）。 */
    private static com.redtale.project.items.ItemContent firstWithoutAbilities(RedtaleCore core) {
        for (var candidate : core.contentItems().all()) {
            if (!candidate.base().isArmor() && !candidate.isConsumable() && !candidate.isRepairItem()
                    && candidate.abilities().isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 单条技能的全貌（只读）：条件 / 效果**逐条标注册状态** + 谁绑了它。
     *
     * <p>{@code list} 给的是「有几条、多大」；这条给的是「这条技能放出去会经过哪些原语、缺哪个」——
     * {@code validate} 只报「有问题」，不告诉运营是哪份配置、哪件武器在用。
     * 注册状态用 {@code core.registries()}（启动期冻结的那份表），与执行期判断**同源**，不另写一份白名单。
     */
    static int info(CommandContext<CommandSourceStack> ctx, String skillId) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        SkillSpec.Definition def = core.skills().get(skillId);
        if (def == null) {
            String have = String.join(" · ", core.skills().sortedIds());
            src.sendFailure(Component.literal("[redtale] 没有技能 " + skillId
                    + "（现有 " + core.skills().size() + " 条：" + (have.isEmpty() ? "（空）" : have) + "）"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] 技能 " + def.id() + " · " + def.name()
                + " · 冷却 " + def.cooldownTicks() + " tick · 图标 " + def.icon()), false);
        src.sendSuccess(() -> Component.literal("  来源 " + def.source()), false);

        int missing = 0;
        List<ScriptCall> conds = def.conditions();
        src.sendSuccess(() -> Component.literal("  conditions " + conds.size() + " 条："), false);
        if (conds.isEmpty()) {
            src.sendSuccess(() -> Component.literal("    （无 —— 无条件放行）"), false);
        }
        for (ScriptCall call : conds) {
            boolean ok = core.registries().condition(call.type()) != null;
            src.sendSuccess(() -> Component.literal("    " + (ok ? "✅ " : "✗ 未注册 ") + call.type()
                    + argsBrief(call)), false);
        }
        List<ScriptCall> effects = def.effects();
        src.sendSuccess(() -> Component.literal("  effects " + effects.size() + " 条（按序执行）："), false);
        if (effects.isEmpty()) {
            src.sendSuccess(() -> Component.literal("    （无 —— 放出去什么都不做）"), false);
        }
        for (ScriptCall call : effects) {
            boolean ok = core.registries().effect(call.type()) != null;
            if (!ok) {
                missing++;
            }
            src.sendSuccess(() -> Component.literal("    " + (ok ? "✅ " : "✗ 未注册 ") + call.type()
                    + argsBrief(call)), false);
        }
        for (ScriptCall call : conds) {
            if (core.registries().condition(call.type()) == null) {
                missing++;
            }
        }

        List<String> bound = new java.util.ArrayList<>();
        for (var content : core.contentItems().all()) {
            if (content.abilities() != null && content.abilities().contains(skillId)) {
                bound.add(content.id());
            }
        }
        String boundText = bound.isEmpty() ? "（没有内容绑定 —— 只能 /redtale skill cast 手动放）"
                : bound.size() + " 件：" + String.join(" · ", bound);
        src.sendSuccess(() -> Component.literal("  被内容绑定 " + boundText), false);
        int miss = missing;
        src.sendSuccess(() -> Component.literal(miss == 0
                ? "  ✓ 全部原语已注册（放得出去）"
                : "  ✗ " + miss + " 条原语未注册（放了只会 WARN；/redtale validate 也报这几条）"), false);
        return miss == 0 ? 1 : 0;
    }

    /** {@code args} 的一行摘要：空参数不打，超长截断（只影响显示，不动数据）。 */
    private static String argsBrief(ScriptCall call) {
        var args = call.argsOrEmpty();
        if (args.isEmpty()) {
            return "";
        }
        String text = args.toString();
        return " " + (text.length() <= 80 ? text : text.substring(0, 80) + "…");
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
