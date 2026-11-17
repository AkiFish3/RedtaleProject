package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
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
 * {@code knockback} 原语：把目标**推离**施法者（SKILLS §4 第 5 条，M5-C）。
 *
 * <p>位移一律走原版 {@link LivingEntity#knockback(double, double, double)}，不自己 {@code setDeltaMovement}：
 * <ul>
 *   <li>它会按 {@code Attributes.KNOCKBACK_RESISTANCE} 打折（玩家/穿重甲的怪天然更抗推），
 *       自己算就等于绕过抗性 —— 这是「击退强度不对」最常见的来源</li>
 *   <li>它自己置 {@code hasImpulse}，服务端才会把速度下发；我们只补 {@code hurtMarked}
 *       让 {@code ServerPlayer} 走运动包（与 {@code Explosion} 同做法）</li>
 * </ul>
 *
 * <p>参数：
 * <ul>
 *   <li>{@code strength}：缺省 {@code 0.4}（原版平砍的击退量）；{@code <=0} → 一条 WARN 跳过；
 *       不设上限（D16：只做负数归零 + {@code 1e9} 硬顶，超阈值记日志不拦）</li>
 *   <li>{@code radius}：缺省 {@code 0} = <b>只推受害者</b>；{@code >0} = 推圈心内所有活体
 *       （圈心 = 有受害者在受害者脚下，否则在施法者脚下；施法者永不被自己推）</li>
 * </ul>
 *
 * <p>方向取「施法者 → 受害者」的水平投影；两者水平重合时退化为 {@code +X}（有日志），
 * 不随机、不朝视线 —— 零向量归一化会得到 NaN 速度，那才是真正的 bug。
 *
 * <p>与 {@link PullEffect} 共用本类的 {@link #shove(ScriptContext, JsonObject, double, boolean)} 一份实现
 * （口径与 R32-d 相同：位移只有一处，改第三条位移原语时不会漏第二份）。
 */
public final class KnockbackEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    private static final double HARD_CAP = 1.0e9D;

    /** 半径上限：与 {@code damage_aoe} 同值，超过按上限（D16 异常保护，不是平衡钳制）。 */
    private static final double MAX_RADIUS = 32.0D;

    /** 水平方向长度小于它视为重合（再除就出 NaN）。 */
    private static final double EPS = 1.0e-4D;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "knockback";
    }

    @Override
    public String description() {
        return "args.strength：击退强度（缺省 0.4，原版平砍量，<=0 跳过）；"
                + "args.radius：半径（缺省 0 = 只推受害者，>0 推圈心内所有活体，上限 32）；"
                + "方向 = 施法者 → 目标，走原版 knockback（吃击退抗性）";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        shove(ctx, args, 0.0D, false);
    }

    /**
     * {@code knockback} / {@code pull} 的唯一实现（包内可见，{@link PullEffect} 直接调）。
     *
     * @param defaultRadius 缺省半径（击退 0 = 单体，拉扯 6 = 一圈）
     * @param towards       {@code true} = 拉向施法者（pull），{@code false} = 推离（knockback）
     */
    static void shove(ScriptContext ctx, JsonObject args, double defaultRadius, boolean towards) {
        String tag = towards ? "pull" : "knockback";
        double strength = raw(args, "strength", 0.4D, tag);
        if (!(strength > 0.0D)) {
            warnOnce(tag + "_zero", tag + " 的 args.strength 缺失或 <= 0，已跳过（本原语不会自己编数值）");
            return;
        }
        if (strength > HARD_CAP) {
            LOG.warn("{} 强度 {} 超过硬顶 {}，按硬顶执行（D16 异常保护）", tag, strength, HARD_CAP);
            strength = HARD_CAP;
        }
        Entity caster = DamageEffect.caster(ctx);
        Entity target = ctx.target();
        Entity centerEntity = target != null ? target : caster;
        if (centerEntity == null || centerEntity.level() == null) {
            warnOnce(tag + "_no_center", tag + " 找不到目标（既没有受害者上下文也没有施法者），已跳过");
            return;
        }
        Level level = centerEntity.level();
        Vec3 center = centerEntity.position();
        double radius = Math.min(Math.max(0.0D, raw(args, "radius", defaultRadius, tag)), MAX_RADIUS);

        List<LivingEntity> hits = new ArrayList<>();
        if (radius <= 0.0D) {
            if (target instanceof LivingEntity victim && victim.isAlive()) {
                hits.add(victim);
            } else {
                warnOnce(tag + "_no_victim", tag + " 的 radius 为 0 但没有受害者上下文，已跳过"
                        + "（想要「推周围一圈」请显式写 radius > 0）");
                return;
            }
        } else {
            AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (!candidate.isAlive() || candidate == caster || candidate.isSpectator()) {
                    continue;
                }
                if (candidate.position().distanceToSqr(center) <= radius * radius) {
                    hits.add(candidate);
                }
            }
            if (hits.isEmpty()) {
                warnOnce(tag + "_no_hit", tag + " 半径内没有可推目标（圈心在 " + fmt(center) + "）");
                return;
            }
            hits.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        }

        Vec3 origin = caster != null ? caster.position() : center;
        StringBuilder detail = new StringBuilder();
        int moved = 0;
        for (LivingEntity who : hits) {
            Vec3 delta = who.position().subtract(origin);
            double flat = Math.hypot(delta.x, delta.z);
            double sx;
            double sz;
            if (flat < EPS) {
                // 水平重合：没有方向可言，定死 +X 而不是随机/视线（NaN 速度会把实体甩到天上）
                sx = 1.0D;
                sz = 0.0D;
                warnOnce(tag + "_degenerate", tag + " 施法者与目标水平重合，方向退化为 +X（不随机、不朝视线）");
            } else {
                sx = delta.x / flat;
                sz = delta.z / flat;
            }
            Vec3 before = who.getDeltaMovement();
            // 原版 knockback 的入参是「被推方向的反向」：它算的是 -normalize(x, z)
            who.knockback(strength, towards ? sx : -sx, towards ? sz : -sz);
            who.hurtMarked = true;
            Vec3 after = who.getDeltaMovement();
            double gained = after.subtract(before).horizontalDistance();
            if (gained > EPS) {
                moved++;
            }
            detail.append(' ').append(who.getName().getString())
                    .append('(').append(String.format("%.1f", flat)).append("格 Δ")
                    .append(String.format("%.2f", gained)).append(')');
        }
        LOG.info("[script] {} · 源 {} · 圈心 {} · 半径 {} · 强度 {} · 推动 {}/{} ·{}",
                tag, fmt(origin), fmt(center), String.format("%.1f", radius),
                String.format("%.2f", strength), moved, hits.size(), detail);
    }

    /**
     * 读一个数值参数：缺键 / {@code null} / 非数字 / {@code NaN} / {@code Inf} → 缺省值；
     * <b>但负数与 0 原样返回</b>（不换成缺省值）。
     *
     * <p>与 {@link DamageEffect#num(JsonObject, String, double)} 的差别是刻意留的：那个方法把
     * 非正数也换成缺省值（适合「半径/衰减」这类有意义的正数量），而 {@code strength: 0} 在这两条原语里
     * 必须是「我不想要位移」——被静默换成 0.4 就等于配置写 0 反而推得更狠。所以这里走自己的读法，
     * 非正数交给调用方判跳过（R33-c）。
     */
    private static double raw(JsonObject args, String key, double def, String tag) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            return def;
        }
        try {
            double v = args.get(key).getAsDouble();
            return Double.isFinite(v) ? v : def;
        } catch (RuntimeException e) {
            LOG.warn("{} 的 args.{} 不是数字，按缺省 {} 处理", tag, key, def);
            return def;
        }
    }

    private static String fmt(Vec3 v) {        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
