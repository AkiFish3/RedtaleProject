package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code stun} 原语：把目标按「停 AI」的方式定住（SKILLS §4 第 7 条，M5-C）。
 *
 * <p>为什么不用 {@code slowness 255}：那是**移动速度属性**上的乘数，会被任何速度修正
 * （抗性、装备、药水、第三方 mod 的 speed modifier）抵消成「还在慢慢挪」。而「定住」的语义是
 * <b>不移动、不攻击、不放技能</b>，只有停 AI 能同时满足这三条。
 *
 * <p>三条实现口径（都写死在这里，不做「智能判断」）：
 * <ol>
 *   <li><b>{@link Mob#setNoAi(boolean)}</b>：只对 {@link Mob} 有效。停 AI 后实体不跑
 *       {@code travel}、不寻路、不选目标，因此也不出手 —— 这是 1.21.1 上唯一「真定住」的手段</li>
 *   <li><b>非 {@code Mob} 的活体（玩家）走退路</b>：挂 {@code minecraft:slowness} 6 级
 *       （移动速度 −105% 后夹到 0），并只在首次记录一条 INFO —— 服务器端没有任何
 *       「禁止玩家移动」的合法接口，这条退路是玩法近似，不是等价替代</li>
 *   <li><b>恢复用「截止时刻」，不用延时任务</b>：{@link #tick()} 每 tick 扫一遍在册表
 *       （通常 0~5 条），到点就恢复。这样重启、`/reload`、实体被移除都不会留下
 *       永久 {@code NoAI} 的怪；另外 {@link #onEntityJoin(Entity)} 兜底清一次重启残留</li>
 * </ol>
 *
 * <p>参数：{@code target}（缺省 {@code target} = 本次受害者，可写 {@code caster}）·
 * {@code radius}（缺省 0 = 只定住目标；{@code >0} = 圈内全部活体，上限 32）·
 * {@code duration_ticks}（缺省 60，{@code <=0} 跳过，上限 1200）。
 */
public final class StunEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 定住上限 60 秒：再长就是「卡怪」，不是技能。 */
    private static final int MAX_DURATION = 1200;

    private static final double MAX_RADIUS = 32.0D;

    /** 玩家退路用的减速等级（amplifier 6 = 7 级，移动速度 −105%）。 */
    private static final int PLAYER_SLOW_AMPLIFIER = 6;

    /** 在册标记写在实体 scoreboard tag 上（1.21.1 已无 `getPersistentData()`），随 NBT 保存、`/data get` 可读。 */
    private static final String STUN_TAG = "redtale_stunned";

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 在册的「被定住的怪」：UUID → 恢复信息。只在服务端主线程读写。 */
    private static final Map<UUID, Frozen> FROZEN = new ConcurrentHashMap<>();

    /** 一条在册记录：谁、什么时候恢复、原本是否已停 AI。 */
    private record Frozen(Mob mob, long until, boolean wasNoAi) {
    }

    @Override
    public String id() {
        return "stun";
    }

    @Override
    public String description() {
        return "args.target：target（缺省，本次受害者）| caster；args.radius：范围（缺省 0 = 只定住目标，上限 32）；"
                + "args.duration_ticks：定住时长 tick（缺省 60，1~1200）。停 AI：不移动 / 不攻击 / 不施法";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity picked = pick(ctx, args);
        if (!(picked instanceof LivingEntity victim) || !victim.isAlive()) {
            warnOnce("stun 没有可用目标");
            return;
        }
        int duration = intOf(args, "duration_ticks", 60);
        if (duration <= 0) {
            warnOnce("stun 的 duration_ticks <= 0，已跳过");
            return;
        }
        duration = Math.min(duration, MAX_DURATION);
        double radius = doubleOf(args, "radius", 0.0D);
        if (!(radius > 0.0D)) {
            freeze(victim, duration);
            return;
        }
        radius = Math.min(radius, MAX_RADIUS);
        // 施法者永不被自己的控场圈进来（R34-b）：否则「打一拳定住 3 格」会把自己也定住，
        // 现象是怪打一下自己站 6 秒不动 —— 实测第一版就踩了这个坑
        Entity caster = casterOf(ctx);
        int hit = 0;
        for (LivingEntity who : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != victim && e != caster)) {
            if (who.distanceTo(victim) <= radius) {
                freeze(who, duration);
                hit++;
            }
        }
        freeze(victim, duration);
        if (hit == 0) {
            LOG.info("[script] stun · 半径 {} 内只有目标本身", fmt(radius));
        }
    }

    /** 定住一个活体。{@code Mob} 停 AI；其它（玩家）走减速退路。 */
    private static void freeze(LivingEntity who, int duration) {
        if (who instanceof Mob mob) {
            // 续定时必须**沿用首次记录的 wasNoAi**：连击会一次次重入 freeze，
            // 若每次都读当前状态，第二次读到的就是「已被我们置 true」，解除时把怪永久定住
            Frozen old = FROZEN.get(mob.getUUID());
            boolean wasNoAi = old != null ? old.wasNoAi() : mob.isNoAi();
            FROZEN.put(mob.getUUID(),
                    new Frozen(mob, mob.level().getGameTime() + duration, wasNoAi));
            mob.setNoAi(true);
            mob.getNavigation().stop();
            mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
            mob.addTag(STUN_TAG);
            LOG.info("[script] stun · {} {}t{}", mob.getName().getString(), duration,
                    old == null ? "" : "（续定，原截止已覆盖）");
            return;
        }
        who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, PLAYER_SLOW_AMPLIFIER));
        LOG.info("[script] stun · {} {}t（非 Mob，退路 = slowness {} 级）",
                who.getName().getString(), duration, PLAYER_SLOW_AMPLIFIER + 1);
    }

    /**
     * 每 tick 扫在册表：到点恢复；实体已死 / 已移除则直接销账（不碰已消失的对象）。
     * 由 {@code GameHooks#onServerTickPost} 调用。
     */
    public static void tick() {
        if (FROZEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Frozen f = it.next().getValue();
            Mob mob = f.mob();
            if (mob.isRemoved() || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if (mob.level().getGameTime() < f.until()) {
                continue;
            }
            it.remove();
            restore(mob, f.wasNoAi(), "到点");
        }
    }

    /**
     * 重启残留兜底：实体入世时若带着 {@code redtale_stunned} 标记（上次关服时还定着），
     * 立刻解除并清标记。恢复目标值取 {@code false} —— 原来的 {@code wasNoAi} 已随重启丢失，
     * 「能动」比「永久定住」安全。
     */
    public static void onEntityJoin(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.getTags().contains(STUN_TAG)) {
            return;
        }
        FROZEN.remove(mob.getUUID());
        restore(mob, false, "重启残留");
    }

    private static void restore(Mob mob, boolean noAi, String why) {
        mob.setNoAi(noAi);
        mob.removeTag(STUN_TAG);
        LOG.info("[script] stun 解除 · {}（{}）", mob.getName().getString(), why);
    }

    /** 在册条数（调试与验收用）。 */
    public static int tracked() {
        return FROZEN.size();
    }

    /** 目标：缺省受害者（{@code target}），显式 {@code caster} 才打施法者。 */
    private static Entity pick(ScriptContext ctx, JsonObject args) {
        String which = "target";
        if (args.has("target")) {
            try {
                which = args.get("target").getAsString();
            } catch (RuntimeException e) {
                warnOnce("stun 的 args.target 不是字符串，按缺省 target 处理");
            }
        }
        if (!"caster".equals(which)) {
            Entity victim = ctx.target();
            if (victim != null) {
                return victim;
            }
        }
        if (ctx.hasPlayer()) {
            return ctx.player();
        }
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof Entity e ? e : null;
    }

    /** 施法者：玩家施法取玩家；附魔 / 怪物武器触发时取 {@code vars.attacker}。 */
    private static Entity casterOf(ScriptContext ctx) {
        if (ctx.hasPlayer()) {
            return ctx.player();
        }
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof Entity e ? e : null;
    }

    private static double doubleOf(JsonObject args, String key, double def) {
        if (!args.has(key)) {
            return def;
        }
        try {
            double v = args.get(key).getAsDouble();
            return Double.isFinite(v) ? v : def;
        } catch (RuntimeException e) {
            LOG.warn("stun 的 args.{} 不是数字，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static int intOf(JsonObject args, String key, int def) {
        if (!args.has(key)) {
            return def;
        }
        try {
            return args.get(key).getAsInt();
        } catch (RuntimeException e) {
            LOG.warn("stun 的 args.{} 不是整数，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static void warnOnce(String message) {
        if (WARNED.add(message)) {
            LOG.warn(message);
        }
    }

    /** 供探针/调试读取在册实体名（不改状态）。 */
    public static List<String> trackedNames() {
        List<String> out = new ArrayList<>();
        for (Frozen f : FROZEN.values()) {
            out.add(f.mob().getName().getString());
        }
        return out;
    }
}
