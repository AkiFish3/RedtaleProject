package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code debuff} 原语：给一个活体挂**负面**原版状态效果（SKILLS §4 第 19 条，M5-C）。
 *
 * <p>与 {@code buff} 的差别只有两条，都写死在这里、不做「智能判断」：
 * <ol>
 *   <li><b>缺省目标是受害者</b>（{@link ScriptContext#target()}），不是施法者 ——
 *       减益的默认语义就是「打在挨打的那个身上」。没有受害者上下文才退回施法者</li>
 *   <li><b>只接受 {@link MobEffectCategory#HARMFUL}</b>：写正面效果（如 {@code minecraft:strength}）
 *       直接 WARN 跳过，让作者改用 {@code buff} —— 否则「debuff 能加血」这种配置会静默通过，
 *       以后排查数值时没人会想到是这里挂的</li>
 * </ol>
 *
 * <p>参数：{@code target}（缺省 {@code target}，可写 {@code caster}）· {@code effect}（**必填**）·
 * {@code amplifier}（缺省 0，负数归 0、上限 255）· {@code duration_ticks}（缺省 200，{@code <=0} 跳过、上限 72000）。
 *
 * <p>异常保护与告警去重口径与 {@link BuffEffect} 一致（按文案去重，不按脚本名）。
 */
public final class DebuffEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    private static final int MAX_AMPLIFIER = 255;

    private static final int MAX_DURATION = 72000;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "debuff";
    }

    @Override
    public String description() {
        return "args.target：target（缺省，本次受害者）| caster；args.effect：原版**负面**效果 id（必填，如 minecraft:slowness）；"
                + "args.amplifier：等级偏移（0 = I 级，0~255）；args.duration_ticks：时长 tick（缺省 200，1~72000）";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity picked = pick(ctx, args);
        if (!(picked instanceof LivingEntity who) || !who.isAlive()) {
            warnOnce("debuff 没有可用目标");
            return;
        }
        String raw = effectId(args);
        if (raw == null) {
            warnOnce("debuff 缺 effect 参数");
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            warnOnce("debuff 的 effect 不是合法 id：" + raw);
            return;
        }
        Holder<MobEffect> holder = who.level().registryAccess()
                .registryOrThrow(Registries.MOB_EFFECT).getHolder(id).orElse(null);
        if (holder == null) {
            warnOnce("debuff 找不到效果：" + id);
            return;
        }
        if (holder.value().getCategory() != MobEffectCategory.HARMFUL) {
            warnOnce("debuff 的 effect 不是负面效果：" + id + "（正面效果请用 buff 原语）");
            return;
        }
        int duration = intOf(args, "duration_ticks", 200);
        if (duration <= 0) {
            warnOnce("debuff 的 duration_ticks <= 0，已跳过");
            return;
        }
        duration = Math.min(duration, MAX_DURATION);
        int amplifier = Math.min(Math.max(intOf(args, "amplifier", 0), 0), MAX_AMPLIFIER);
        who.addEffect(new MobEffectInstance(holder, duration, amplifier));
        LOG.info("[script] debuff · {} + {} {}t×{}", who.getName().getString(), id, duration, amplifier);
    }

    /** 目标：缺省受害者（{@code target}），显式 {@code caster} 才打施法者。 */
    private static Entity pick(ScriptContext ctx, JsonObject args) {
        String which = "target";
        if (args.has("target")) {
            try {
                which = args.get("target").getAsString();
            } catch (RuntimeException e) {
                warnOnce("debuff 的 args.target 不是字符串，按缺省 target 处理");
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
        // 生物施法者（附魔挂在怪物武器上时）：ScriptedEnchantmentEffect 把 item.owner() 放这里
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof Entity e ? e : null;
    }

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
            LOG.warn("debuff 的 args.{} 不是整数，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static void warnOnce(String message) {
        if (WARNED.add(message)) {
            LOG.warn(message);
        }
    }
}
