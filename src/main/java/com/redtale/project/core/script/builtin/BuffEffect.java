package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code buff} 原语：给一个活体挂原版状态效果（M5-C 增益类第一条，SKILLS §4 第 15 条）。
 *
 * <p>参数：{@code target}（{@code caster} 缺省 / {@code target}）、{@code effect}（必填，
 * 原版效果 id）、{@code amplifier}（缺省 0 = I 级，负数归 0、上限 255）、
 * {@code duration_ticks}（缺省 200，{@code <=0} 跳过，上限 72000）。
 *
 * <p>异常保护：参数不合法一律 WARN 后 return，不抛异常；告警按文案去重（同 HealEffect）。
 */
public final class BuffEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 等级偏移上限（原版等级上限口径）。 */
    private static final int MAX_AMPLIFIER = 255;

    /** 时长上限：原版瞬时效果硬顶 72000 tick。 */
    private static final int MAX_DURATION = 72000;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "buff";
    }

    @Override
    public String description() {
        return "args.target：caster（缺省）| target；args.effect：原版效果 id（必填，如 minecraft:speed）；"
                + "args.amplifier：等级偏移（0 = I 级，0~255）；args.duration_ticks：时长 tick（缺省 200，1~72000）";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity picked = pick(ctx, args);
        if (!(picked instanceof LivingEntity who) || !who.isAlive()) {
            warnOnce("buff 没有可用目标");
            return;
        }
        String raw = effectId(args);
        if (raw == null) {
            warnOnce("buff 缺 effect 参数");
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            warnOnce("buff 的 effect 不是合法 id：" + raw);
            return;
        }
        Holder<MobEffect> holder = who.level().registryAccess()
                .registryOrThrow(Registries.MOB_EFFECT).getHolder(id).orElse(null);
        if (holder == null) {
            warnOnce("buff 找不到效果：" + id);
            return;
        }
        int duration = intOf(args, "duration_ticks", 200);
        if (duration <= 0) {
            warnOnce("buff 的 duration_ticks <= 0，已跳过");
            return;
        }
        duration = Math.min(duration, MAX_DURATION);
        int amplifier = Math.min(Math.max(intOf(args, "amplifier", 0), 0), MAX_AMPLIFIER);
        who.addEffect(new MobEffectInstance(holder, duration, amplifier));
        LOG.info("[script] buff · {} + {} {}t×{}", who.getName().getString(), id, duration, amplifier);
    }

    private static Entity pick(ScriptContext ctx, JsonObject args) {
        String which = args.has("target") ? args.get("target").getAsString() : "caster";
        if ("target".equals(which)) {
            return ctx.target();
        }
        if (ctx.hasPlayer()) {
            return ctx.player();
        }
        // 生物施法者（附魔挂在怪物武器上时）：ScriptedEnchantmentEffect 把 item.owner() 放这里
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof Entity e ? e : null;
    }

    /** {@code effect} 必须是字符串且非空，否则 null（交给调用方报一条 WARN）。 */
    private static String effectId(JsonObject args) {
        if (!args.has("effect") || !args.get("effect").isJsonPrimitive()
                || !args.getAsJsonPrimitive("effect").isString()) {
            return null;
        }
        String s = args.get("effect").getAsString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOf(JsonObject args, String key, int def) {
        if (!args.has(key)) {
            return def;
        }
        try {
            return args.get(key).getAsInt();
        } catch (RuntimeException e) {
            LOG.warn("buff 的 args.{} 不是整数，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static void warnOnce(String message) {
        if (WARNED.add(message)) {
            LOG.warn(message);
        }
    }
}
