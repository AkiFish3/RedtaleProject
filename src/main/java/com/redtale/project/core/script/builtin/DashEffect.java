package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code dash} 原语：**施法者自己**冲出去（SKILLS §4 位移第 2 条，M5-C，卡 T-0031）。
 *
 * <p>与「把别人推走」那两条的分界：那两条走原版 {@code LivingEntity#knockback}，吃目标的击退抗性；
 * 这里是自己位移，不吃自己的抗性，所以只写速度 + {@code hurtMarked}（不走推人那条路径）。
 *
 * <p>数学只有 {@link #motion(double, double, double, double)} 一份，{@code run} 只负责取参、取视线、落地。
 */
public final class DashEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 水平方向长度小于它视为重合（再除就出 NaN）。 */
    public static final double EPS = 1.0e-4D;

    public static final double MAX_POWER = 5.0D;
    public static final double MIN_UP = -1.0D;
    public static final double MAX_UP = 2.0D;

    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    @Override
    public String id() {
        return "dash";
    }

    @Override
    public String description() {
        return "args.power：冲刺力度（缺省 0.8，<=0 告警跳过，上限 5）；"
                + "args.up：垂直分量（缺省 0.2，夹到 -1~2）；"
                + "args.direction：look（缺省，沿视线）| back（视线反向）；"
                + "位移只打施法者自己，不吃击退抗性";
    }

    /**
     * 纯函数：算出要写进速度的三分量，下标 0/1/2 = x/y/z。无任何 MC import，可离线断言。
     *
     * <p>夹取本身不告警（D16 异常保护）；{@code power} 非有限或 {@code <= 0} → 全零。
     * 水平分量退化为 {@code +X} 而不是随机/朝视线（R33-b 同思路）。
     */
    public static double[] motion(double lookX, double lookZ, double power, double up) {
        double p = Double.isFinite(power) ? Math.max(0.0D, Math.min(MAX_POWER, power)) : 0.0D;
        double u = Double.isFinite(up) ? Math.max(MIN_UP, Math.min(MAX_UP, up)) : 0.0D;
        if (!(p > 0.0D)) {
            return new double[] {0.0D, 0.0D, 0.0D};
        }
        double h = Math.hypot(lookX, lookZ);
        if (!(h >= EPS)) {
            return new double[] {p, u, 0.0D};
        }
        return new double[] {lookX / h * p, u, lookZ / h * p};
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity raw = DamageEffect.caster(ctx);
        if (!(raw instanceof LivingEntity caster)) {
            warnOnce("dash_no_caster", "dash 的施法者不是活体（控制台 / 命令方块 / null），已跳过");
            return;
        }
        JsonObject a = args != null ? args : new JsonObject();
        double power = num(a, "power", 0.8D);
        if (!(power > 0.0D)) {
            warnOnce("dash_zero", "dash 的 args.power <= 0，已跳过（不会退化成按 0.8 冲，R33-c）");
            return;
        }
        double up = num(a, "up", 0.2D);
        String dir = direction(a);
        var look = caster.getLookAngle();
        boolean back = "back".equals(dir);
        double[] v = motion(back ? -look.x : look.x, back ? -look.z : look.z, power, up);
        caster.setDeltaMovement(v[0], v[1], v[2]);
        caster.hurtMarked = true;
        if (LOG.isInfoEnabled()) {
            LOG.info("[script] dash · {} · {} · 力度 {} · 上抛 {} · 速度 ({}, {}, {})",
                    caster.getName().getString(), dir,
                    String.format("%.2f", power), String.format("%.2f", up),
                    String.format("%.3f", v[0]), String.format("%.3f", v[1]), String.format("%.3f", v[2]));
        }
    }

    /**
     * 读数值参数：缺键 / {@code null} / 非数字 / {@code NaN,Inf} → 缺省值（非数字补一条去重 WARN）；
     * 负数与 0 原样返回，交给调用方判跳过（写 {@code up: 0} 是合法意图，不能被换成缺省）。
     */
    private static double num(JsonObject args, String key, double def) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            return def;
        }
        try {
            double v = args.get(key).getAsDouble();
            return Double.isFinite(v) ? v : def;
        } catch (RuntimeException e) {
            warnOnce("dash_num_" + key, "dash 的 args." + key + " 不是数字，按缺省 " + def + " 处理");
            return def;
        }
    }

    /** {@code direction} 只认 {@code look} / {@code back}；{@code away} 等近义词不收，避免两种写法一种行为。 */
    private static String direction(JsonObject args) {
        String d;
        try {
            d = args.has("direction") && !args.get("direction").isJsonNull()
                    ? args.get("direction").getAsString().trim() : "";
        } catch (RuntimeException e) {
            d = "";
        }
        if ("look".equals(d) || "back".equals(d)) {
            return d;
        }
        if (!d.isEmpty()) {
            warnOnce("dash_dir", "dash 的 args.direction 只认 look|back（away 等近义词不收），按 look 处理");
        }
        return "look";
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
