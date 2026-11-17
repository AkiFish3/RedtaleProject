package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code projectile} 原语：沿施法者视线射出一个投射物（SKILLS §4 第 12 条，M5-C，卡 T-0033）。
 *
 * <p>v1 只做「射出去」。命中回调要把伤害递回脚本层，属伤害管线，{@code on_hit} 读到即告警忽略。
 * <p>数学只有 {@link #velocity} 一份，{@code run} 只负责取参、造实体、写速度、落地。
 */
public final class ProjectileEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 视线长度小于它视为零向量（否则归一化出 NaN）。 */
    public static final double EPS = 1.0e-6D;
    /** 速度上限（格/刻）。D16：夹取本身不告警。 */
    public static final double MAX_SPEED = 5.0D;

    private static final double DEFAULT_SPEED = 2.0D;
    private static final String DEFAULT_ENTITY = "minecraft:arrow";

    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    @Override
    public String id() {
        return "projectile";
    }

    @Override
    public String description() {
        return "args.entity：投射物实体 id（缺省 minecraft:arrow）；"
                + "args.speed：初速（缺省 2.0，<=0 告警跳过，夹到 5）；"
                + "沿施法者视线射出（三维归一，含 y）；on_hit 未支持（M5-C v1），读到即忽略";
    }

    /**
     * 纯函数：把视线方向归一后乘速度，返回 {vx, vy, vz}（下标 0/1/2）。无任何 MC import，可离线断言。
     *
     * <p>{@code speed} 非有限或 {@code <= 0} → 全零；零向量也全零（不朝 +X 兜底：投射物朝随机方向没意义，
     * 与 {@code dash} 的退化处理刻意不同）。
     */
    public static double[] velocity(double lookX, double lookY, double lookZ, double speed) {
        if (!Double.isFinite(speed) || !(speed > 0.0D)) {
            return new double[] {0.0D, 0.0D, 0.0D};
        }
        double s = Math.min(MAX_SPEED, speed);
        double len = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (!Double.isFinite(len) || !(len >= EPS)) {
            return new double[] {0.0D, 0.0D, 0.0D};
        }
        return new double[] {lookX / len * s, lookY / len * s, lookZ / len * s};
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity raw = DamageEffect.caster(ctx);
        if (!(raw instanceof LivingEntity caster)) {
            warnOnce("proj_no_caster", "projectile 的施法者不是活体（控制台 / 命令方块 / null），已跳过");
            return;
        }
        JsonObject a = args != null ? args : new JsonObject();
        ResourceLocation rl = entityOf(a);
        if (rl == null) {
            return;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            warnOnce("proj_entity_missing", "projectile 的 args.entity 不是已注册的实体类型（" + rl + "），已跳过");
            return;
        }
        double speed = speedOf(a);
        if (!(speed > 0.0D)) {
            warnOnce("proj_zero_speed", "projectile 的 args.speed <= 0，已跳过（不会退化成按 2.0 射，R33-c）");
            return;
        }
        if (a.has("on_hit")) {
            warnOnce("proj_on_hit", "projectile 的 on_hit 未支持（M5-C v1），已忽略");
        }
        var type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        Entity spawned = type == null ? null : type.create(caster.level());
        if (spawned == null) {
            warnOnce("proj_create_null", "projectile 造不出实体（" + rl + "），已跳过");
            return;
        }
        spawned.moveTo(caster.getEyePosition());
        var look = caster.getLookAngle();
        double[] v = velocity(look.x, look.y, look.z, speed);
        spawned.setDeltaMovement(v[0], v[1], v[2]);
        if (spawned instanceof Projectile p) {
            p.setOwner(caster);
        }
        caster.level().addFreshEntity(spawned);
        if (LOG.isInfoEnabled()) {
            LOG.info("[script] projectile · {} · {} · 速度 {}",
                    caster.getName().getString(), rl, String.format("%.2f", speed));
        }
    }

    /** 读 {@code entity}：缺键 / {@code null} → 缺省箭；非字符串或不可解析 → 一条去重 WARN + 返回 null。 */
    private static ResourceLocation entityOf(JsonObject args) {
        if (!args.has("entity") || args.get("entity").isJsonNull()) {
            return ResourceLocation.tryParse(DEFAULT_ENTITY);
        }
        var node = args.get("entity");
        if (!node.isJsonPrimitive() || !node.getAsJsonPrimitive().isString()) {
            warnOnce("proj_entity_type", "projectile 的 args.entity 不是字符串，已跳过");
            return null;
        }
        String text = node.getAsString().trim();
        ResourceLocation rl = ResourceLocation.tryParse(text);
        if (rl == null) {
            warnOnce("proj_entity_id", "projectile 的 args.entity 不是合法 id（" + text + "），已跳过");
        }
        return rl;
    }

    /**
     * 读 {@code speed}：缺键 / {@code null} / 非数字 / {@code NaN,Inf} → 缺省 2.0（非数字补一条去重 WARN）；
     * 负数与 0 原样返回，交给调用方判跳过（写 {@code speed: 0} 是「我不想射」，不能被换成缺省）。
     */
    private static double speedOf(JsonObject args) {
        if (!args.has("speed") || args.get("speed").isJsonNull()) {
            return DEFAULT_SPEED;
        }
        try {
            double v = args.get("speed").getAsDouble();
            return Double.isFinite(v) ? v : DEFAULT_SPEED;
        } catch (RuntimeException e) {
            warnOnce("proj_speed_num", "projectile 的 args.speed 不是数字，按缺省 2.0 处理");
            return DEFAULT_SPEED;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
