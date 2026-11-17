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
        COOLDOWN,
        /** 法力不够（M5-B 收口，R35）：**一条 effect 都没跑、冷却没记**，缺多少见 {@link #manaText(ServerPlayer, double)}。 */
        NO_MANA
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

    /**
     * 法力池（M5 收口，R35）：**跨 reload 存活**（挂在引擎上，引擎是 {@code RedtaleCore} 的 final 字段）。
     * 上限/回复都由属性面板驱动（{@code max_mana} / {@code mana_regeneration}），本类只做「取数 + 交给池」。
     */
    private final ManaPool mana = new ManaPool();

    /** 冷却表（只读观测用：命令与排障）。 */
    public SkillCooldowns cooldowns() {
        return cooldowns;
    }

    /** 法力池（观测 / 运维写回用）。当前法力是**运行期状态**，M5 收口第二步才接 {@code PlayerData} 持久化。 */
    public ManaPool mana() {
        return mana;
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
        //    先只「查」不「记」：法力不够的那次不该吃掉冷却（否则空蓝连按把技能锁死）
        long remaining = cooldowns.remaining(player.getUUID(), def.id(), now);
        if (remaining > 0L) {
            return new Result(Status.COOLDOWN, def.id(), (int) remaining, 0, null);
        }

        // ④b 门槛：法力（R35）。扣蓝在前、记冷却在后：免费技能（mana_cost 0）不建账、不改任何状态
        syncMax(player);
        ManaPool.Payment pay = mana.spend(player.getUUID(), def.manaCost(), manaCostReduction(player));
        if (!pay.paid()) {
            return new Result(Status.NO_MANA, def.id(), 0, 0, null);
        }
        cooldowns.start(player.getUUID(), def.id(), def.cooldownTicks(), cooldownReduction(player), now);

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

    // ------------------------------------------------------------------ 法力（M5 收口，R35）

    /**
     * 读玩家身上的 {@code redtale_project:max_mana}。
     *
     * <p>口径（§13.38 R35-a）：自建属性基值全 {@code 0}（R5 事实），{@code 0} 在这里的含义是
     * 「**没配**上限」而不是「上限为零」—— 一个上限 0 的法师永远放不出技能，那种状态没有玩法意义。
     * 所以 {@code <=0} / 非有限一律回落到 {@link ManaPool#DEFAULT_MAX}；配了正数就**完全听属性的**（不设软上限，D16）。
     */
    public static double maxManaOf(ServerPlayer player) {
        Holder<Attribute> attr = RedtaleAttributes.custom("max_mana");
        if (attr == null || player == null) {
            return ManaPool.DEFAULT_MAX;
        }
        double value = player.getAttributeValue(attr);
        return Double.isFinite(value) && value > 0.0D ? value : ManaPool.DEFAULT_MAX;
    }

    /**
     * 读 {@code mana_regeneration}（每秒）。{@code <=0} / 非有限 → {@code 0} = **不回蓝**。
     *
     * <p>这里**不给缺省回蓝**：回蓝速度是内容配置（装备 / 药水 / 套装下发 modifier），
     * 凭空给一个「每秒 5 点」会让「没配回蓝」与「配了 5 点」在面板上完全一样（D18 同思路：数值只有一个来源）。
     */
    public static double manaRegenOf(ServerPlayer player) {
        Holder<Attribute> attr = RedtaleAttributes.custom("mana_regeneration");
        if (attr == null || player == null) {
            return 0.0D;
        }
        double value = player.getAttributeValue(attr);
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    /** 读 {@code mana_cost_reduction}（与 {@code cooldown_reduction} 分轴）；负数 / 非有限归零，上限 0.9 在 {@link ManaPool#effectiveCost}。 */
    public static double manaCostReduction(ServerPlayer player) {
        Holder<Attribute> attr = RedtaleAttributes.custom("mana_cost_reduction");
        if (attr == null || player == null) {
            return 0.0D;
        }
        double value = player.getAttributeValue(attr);
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    /**
     * 把属性上限同步进池（装等变了上限就变）。只在真的变了才写，避免每 tick 建一堆等值记录。
     *
     * <p>抬高上限**不补蓝**（T-0029 验收口径：{@code setMax} 只夹取当前值）—— 换装瞬间白送一管蓝
     * 等于「脱穿装备回蓝」，那是刷子不是玩法。
     */
    public void syncMax(ServerPlayer player) {
        if (player == null) {
            return;
        }
        double want = maxManaOf(player);
        if (Math.abs(want - mana.max(player.getUUID())) > 1.0e-6D) {
            mana.setMax(player.getUUID(), want);
        }
    }

    /**
     * 每 tick 调（{@code GameHooks#onPlayerTick}）：同步上限 + 按属性回蓝。
     *
     * <p>用 {@code ticks = 1} 而不是每 20 tick 一次 {@code ticks = 20}：小数余额留在池里，
     * 「每秒 0.5 蓝」这种配置才不会因为取整永远回 0（T-0029 行为 5 的同一条纪律）。
     *
     * @return 本 tick 实际回复量（0 表示满蓝 / 没配回蓝）
     */
    public double tickMana(ServerPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        syncMax(player);
        return mana.regen(player.getUUID(), manaRegenOf(player), 1L);
    }

    /** {@code 法力 42.0/100.0}（一位小数的观测面，不参与结算）。 */
    public String manaText(ServerPlayer player) {
        if (player == null) {
            return "法力 -";
        }
        return String.format(java.util.Locale.ROOT, "法力 %.1f/%.1f", mana.current(player.getUUID()), mana.max(player.getUUID()));
    }

    /**
     * 法力不够的文案（SKILLS §6 写死便于 grep）：{@code 法力不足：需要 <cost>，当前 <cur>/<max>}。
     * {@code cost} 是**减免后**的实际消耗（与 {@link ManaPool#spend} 同一口径），别在这里再算一遍公式。
     */
    public String noManaText(ServerPlayer player, double rawCost) {
        if (player == null) {
            return "法力不足";
        }
        double cost = ManaPool.effectiveCost(rawCost, manaCostReduction(player));
        return String.format(java.util.Locale.ROOT, "法力不足：需要 %.1f，当前 %s", cost, manaText(player));
    }
}
