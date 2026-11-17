package com.redtale.project.core.skill;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.script.ConditionType;
import com.redtale.project.core.script.ScriptCall;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 技能执行器（M5-A 第三层）：{@code 条件 → 冷却 → effects 顺序跑}。
 *
 * <p>契约见 {@code docs/SKILLS.md} §2（五步链路）与 §6（冷却）。三层分工：
 * {@link SkillSpec} 纯解析 / {@link SkillRegistry} 不可变快照 / 本类只做「放一次」。
 *
 * <p><b>不新建触发器体系</b>（SKILLS §2）：技能没有 {@code trigger} 字段，入口是命令（v1）、
 * 物品右键（M5-B）、怪物 AI（M6）。
 *
 * <p><b>冷却按 (UUID, 技能 id)</b>：记账在 {@link SkillCooldowns}，与脚本层
 * {@code ScriptEngine.cooldownTicks}（按玩家名）**互不借道** —— 改名不该成为清冷却的手段。
 *
 * <p>只在服务端主线程调用，不加锁。
 */
public final class SkillEngine {

    /** 释放结果状态。命令层按它选文案，不自己拼判断。 */
    public enum Status {
        /** 条件全过、冷却通过、effects 已跑。 */
        CAST,
        /** 注册表里没有这条技能。 */
        NO_SKILL,
        /** 玩家为 null（命令控制台调用 / 生物施法尚未接）。 */
        NO_PLAYER,
        /** 有一条 condition 判 false，**一条 effect 都没跑**，冷却未记账。 */
        CONDITION,
        /** 冷却未到，**一条 effect 都没跑**，剩余 tick 见 {@link Result#remainingTicks()}。 */
        COOLDOWN
    }

    /**
     * @param effects          实际成功执行的 effect 条数（CAST 之外恒 0）
     * @param remainingTicks   COOLDOWN 时的剩余 tick，其余 0
     * @param failedCondition  CONDITION 时判否的 condition type，其余为 null
     */
    public record Result(Status status, String skillId, int remainingTicks, int effects, String failedCondition) {

        public boolean cast() {
            return status == Status.CAST;
        }
    }

    /** 索敌距离（格）：技能释放时「看向谁就打谁」，超出即无目标（原语自己会跳过并 WARN）。 */
    public static final double AIM_RANGE = 8.0D;

    private final SkillCooldowns cooldowns = new SkillCooldowns();

    /** 冷却表（只读观测用：命令与排障）。 */
    public SkillCooldowns cooldowns() {
        return cooldowns;
    }

    /**
     * 释放一次技能：条件 → 冷却 → effects。
     *
     * <p>顺序是硬约束：条件不过**不记账**（否则「条件不满足」也会吃掉冷却），
     * 冷却不过**不跑 effects**。未知 condition type 与 {@code ScriptEngine#passConditions} 同口径
     * —— 跳过（解析层已按 R28-d 不校验原语存在，校验归 {@code /redtale validate}）。
     */
    public Result cast(ServerPlayer player, String skillId) {
        if (player == null) {
            return new Result(Status.NO_PLAYER, skillId, 0, 0, null);
        }
        RedtaleCore core = RedtaleCore.get();
        SkillRegistry skills = core.skills();
        SkillSpec.Definition def = skills.get(skillId);
        if (def == null) {
            return new Result(Status.NO_SKILL, skillId, 0, 0, null);
        }
        long now = player.server.overworld().getGameTime();

        // ③ 条件：全部 true 才继续；判 false 的那条 type 带回去给命令层说清楚
        ScriptContext ctx = ScriptContext.ofPlayer(player).target(aimedTarget(player));
        for (ScriptCall call : def.conditions()) {
            ConditionType condition = core.registries().condition(call.type());
            if (condition == null) {
                continue;
            }
            boolean ok;
            try {
                ok = condition.test(ctx, call.argsOrEmpty());
            } catch (Exception e) {
                // 判定炸了按不通过处理（与 ScriptEngine#passConditions 一致），但不吞证据
                return new Result(Status.CONDITION, def.id(), 0, 0, call.type() + "（判定异常）");
            }
            if (!ok) {
                return new Result(Status.CONDITION, def.id(), 0, 0, call.type());
            }
        }

        // ④ 门槛：冷却（M5-B 起吃 cooldown_reduction 属性，公式与钳制在 SkillCooldowns 里）
        if (!cooldowns.tryStart(player.getUUID(), def.id(), def.cooldownTicks(),
                cooldownReduction(player), now)) {
            return new Result(Status.COOLDOWN, def.id(), (int) cooldowns.remaining(player.getUUID(), def.id(), now), 0, null);
        }

        // ⑤ 执行：复用脚本层 runCalls（单条失败只记日志，不影响后续）
        int done = core.engine().runCalls(def.effects(), ctx, "技能 " + def.id());
        return new Result(Status.CAST, def.id(), 0, done, null);
    }

    /** 冷却文案（SKILLS §6 写死便于 grep）：{@code 技能 <id> 冷却中：<剩余> tick}。 */
    public static String cooldownText(String skillId, int remainingTicks) {
        return "技能 " + skillId + " 冷却中：" + remainingTicks + " tick";
    }

    /**
     * 读玩家身上的 {@code redtale_project:cooldown_reduction}（M5-B）。
     *
     * <p>口径：**读属性面板，不读装备**。属性由装备 modifier / 药水 / 套装下发，重复计算的责任
     * 在 {@code AttributeMap} 侧（D18 同思路）；这里只做「取一个数、异常归零」。
     * 钳制上限 {@code 0.9} 与「地板 1 tick」都在 {@link SkillCooldowns#effectiveTicks} 里，
     * 这个方法是唯一入口，别在别处再换算一次。
     */
    public static double cooldownReduction(ServerPlayer player) {
        Holder<Attribute> attr = RedtaleAttributes.custom("cooldown_reduction");
        if (attr == null || player == null) {
            return 0.0D;
        }
        double value = player.getAttributeValue(attr);
        // 负数 / NaN / Inf 一律归零（D16 的异常保护，不是平衡钳制）
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    /**
     * 视线索敌：从眼睛沿视线 {@link #AIM_RANGE} 格做一次实体 raycast，命中活体即目标。
     * 打不中就是 {@code null}（{@code damage} 原语会按「无目标」跳过并 WARN，不会瞎打）。
     */
    public static Entity aimedTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(AIM_RANGE));
        AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(AIM_RANGE)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, reach, box,
                e -> e instanceof LivingEntity && e != player && e.isAlive(),
                AIM_RANGE * AIM_RANGE);
        return hit == null ? null : hit.getEntity();
    }

    /** 技能定义里引用到的原语种类（排障用；不改变技能快照）。 */
    public static List<String> primitiveTypesOf(SkillSpec.Definition def) {
        return def == null ? List.of() : List.copyOf(def.effects().stream().map(ScriptCall::type).toList());
    }
}
