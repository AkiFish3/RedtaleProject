package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code damage_aoe} 原语：以一个**显式中心**结算一圈白值伤害（SKILLS §4 第 2 条，M5-C）。
 *
 * <p>与 {@link DamageEffect} 分开的原因（SKILLS §4 备注）：单体原语读到 {@code radius} 只会 WARN，
 * 范围伤害的「圈心在哪」必须是显式语义 —— 本原语定死：
 * <ul>
 *   <li>有 {@link ScriptContext#target()} → **圈心 = 受害者脚下**（技能命中点，最常用）</li>
 *   <li>没有 target → 圈心 = 施法者脚下（自爆 / 光环式技能）</li>
 * </ul>
 *
 * <p>参数：
 * <ul>
 *   <li>{@code amount}（**必填**）：白值，正数；缺失或 {@code <= 0} → 一条 WARN 跳过</li>
 *   <li>{@code radius}：缺省 {@code 4.0}，上限 {@code 32}（超过按 32，D16 异常保护不是平衡钳制）</li>
 *   <li>{@code falloff}：缺省 {@code 0.5}，夹到 {@code 0~1}；边缘伤害 = {@code amount × (1 - falloff)}，
 *       中间线性插值。{@code 0} = 不衰减</li>
 *   <li>{@code max_targets}：缺省 {@code 0} = 不限；{@code >0} 时**按距离由近到远**取前 N 个</li>
 *   <li>{@code damage_type}：与 {@code damage} 完全同一口径（缺省 {@code skill_physical}，
 *       法术必须显式写 {@code skill_magical}）</li>
 * </ul>
 *
 * <p><b>只给白值，绝不 {@code setAmount}</b>（SKILLS §5 红线 1）：每个目标都走
 * {@link LivingEntity#hurt(DamageSource, float)}，衰减只乘在**白值**上，倍率仍由 {@code DamageLayer} 统一乘。
 * 施法者自己**不在命中集合里**（自伤要靠显式内容，不靠范围误伤）。
 *
 * <p>异常保护（D16）：数值非法回缺省、{@code amount > 1e9} 硬顶、目标已死跳过、无世界跳过，全程不抛。
 */
public final class DamageAoeEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    private static final double HARD_CAP = 1.0e9D;

    /** 半径上限：32 格已经覆盖「一发打整个战场」，再大只是把全服实体扫一遍。 */
    private static final double MAX_RADIUS = 32.0D;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "damage_aoe";
    }

    @Override
    public String description() {
        return "args.amount：白值（必填，只给白值）；args.radius：半径（缺省 4，上限 32）；"
                + "args.falloff：边缘衰减（缺省 0.5 = 边缘打一半，0 = 不衰减）；"
                + "args.max_targets：最多命中几个（缺省 0 = 不限，按距离由近到远）；"
                + "args.damage_type：缺省 redtale_project:skill_physical（法术请写 skill_magical）；"
                + "圈心 = 有受害者在受害者脚下，否则在施法者脚下";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        double amount = DamageEffect.num(args, "amount", 0.0D);
        if (amount <= 0.0D) {
            warnOnce("zero", "damage_aoe 的 args.amount 缺失或 <= 0，已跳过（本原语不会自己编数值）");
            return;
        }
        if (amount > HARD_CAP) {
            LOG.warn("damage_aoe 白值 {} 超过硬顶 {}，按硬顶结算（D16 异常保护）", amount, HARD_CAP);
            amount = HARD_CAP;
        }
        Entity caster = DamageEffect.caster(ctx);
        Entity target = ctx.target();
        Entity centerEntity = target != null ? target : caster;
        if (centerEntity == null || centerEntity.level() == null) {
            warnOnce("no_center", "damage_aoe 找不到圈心（既没有受害者上下文也没有施法者），已跳过");
            return;
        }
        Level level = centerEntity.level();
        Vec3 center = centerEntity.position();
        double radius = Math.min(DamageEffect.num(args, "radius", 4.0D), MAX_RADIUS);
        double falloff = clamp01(DamageEffect.num(args, "falloff", 0.5D));
        int maxTargets = (int) Math.max(0.0D, DamageEffect.num(args, "max_targets", 0.0D));

        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> hits = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!candidate.isAlive() || candidate == caster || candidate.isSpectator()) {
                continue;
            }
            if (candidate.position().distanceToSqr(center) <= radius * radius) {
                hits.add(candidate);
            }
        }
        if (hits.isEmpty()) {
            warnOnce("no_hit", "damage_aoe 半径内没有可命中目标（圈心在 " + fmt(center) + "）");
            return;
        }
        hits.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        if (maxTargets > 0 && hits.size() > maxTargets) {
            hits = hits.subList(0, maxTargets);
        }

        DamageSource source = DamageEffect.source(level, ctx, args);
        StringBuilder detail = new StringBuilder();
        int landed = 0;
        for (LivingEntity who : hits) {
            double dist = Math.sqrt(who.position().distanceToSqr(center));
            // 线性衰减：圈心满伤，边缘打 (1 - falloff)
            double factor = Math.max(0.0D, 1.0D - falloff * (dist / radius));
            double dealt = amount * factor;
            float before = who.getHealth();
            who.hurt(source, (float) dealt);
            if (who.getHealth() < before) {
                landed++;
            }
            detail.append(' ').append(who.getName().getString())
                    .append('(').append(String.format("%.1f", dist)).append("格×")
                    .append(String.format("%.2f", factor)).append(')');
        }
        int total = hits.size();
        LOG.info("[script] damage_aoe · 圈心 {} · 半径 {} · 命中 {}/{} · 白值 {} · 类型 {} ·{}",
                fmt(center), String.format("%.1f", radius), landed, total,
                String.format("%.2f", amount), source.getMsgId(), detail);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) {
            return 0.5D;
        }
        return Math.min(1.0D, Math.max(0.0D, v));
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
