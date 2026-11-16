package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code heal} 原语：给一个活体回血（M4 的 {@code soul_reap} 执行体要用）。
 *
 * <p>参数（全部可选，全缺省 = 0 → 不做事 + 一条 WARN）：
 * <ul>
 *   <li>{@code target}：{@code caster}（缺省）或 {@code target}（本次触发的受害者 /
 *       {@link ScriptContext#target()}）。附魔 {@code post_attack} 的语义是「打中受害者，
 *       给施法者回血」，所以缺省回施法者。</li>
 *   <li>{@code ratio}：按目标**最大生命**的比例回血（{@code 0.08} = 8%）。</li>
 *   <li>{@code per_level}：每级追加的比例，等级取 {@code vars.enchant_level}，缺省 1
 *       （即等级 1 只吃 {@code ratio}，等级 N 追加 {@code (N-1) × per_level}）。</li>
 *   <li>{@code flat}：固定点数，与比例部分相加。</li>
 * </ul>
 *
 * <p><b>为什么按最大生命算而不是按「打出了多少伤害」</b>：{@code post_attack} 的原版组件
 * 签名里根本不带伤害数值（{@code EnchantmentEntityEffect#apply} 只有等级 / 物品 / 实体 / 位置），
 * 想按伤害抽血必须另开一个带伤害值的入口（伤害管线 → 脚本）。那是加原语之外的结构改动，
 * 排到 M5 技能层一起做；这一版先用「施法者最大生命 × 比例」，数值可预测、可验收。
 *
 * <p>异常保护（D16 口径）：负数归零不报错、{@code 1e9} 硬顶、目标已死只跳过。
 */
public final class HealEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 单次回血上限，D16 的「异常保护」硬顶（不是平衡钳制）。 */
    private static final double HARD_CAP = 1.0e9D;

    /** 只报一次的告警 id（按脚本来源粒度会刷屏，按文案去重）。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "heal";
    }

    @Override
    public String description() {
        return "args.target：caster（缺省）| target；args.ratio：按最大生命的比例；"
                + "args.per_level：每级追加比例（等级取 enchant_level）；args.flat：固定点数";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity picked = pick(ctx, args);
        if (!(picked instanceof LivingEntity who) || !who.isAlive()) {
            warnOnce("无目标", "heal 没有可用目标（target=caster 时需要玩家/施法者上下文）");
            return;
        }
        double ratio = num(args, "ratio", 0.0D);
        double perLevel = num(args, "per_level", 0.0D);
        double flat = num(args, "flat", 0.0D);
        int level = level(ctx);
        double amount = flat + who.getMaxHealth() * (ratio + perLevel * Math.max(0, level - 1));
        if (amount <= 0.0D) {
            warnOnce("零值", "heal 算出的回血量 <= 0（ratio/per_level/flat 全缺省？），已跳过");
            return;
        }
        if (amount > HARD_CAP) {
            LOG.warn("heal 算出 {} 超过硬顶 {}，按硬顶结算（D16 异常保护）", amount, HARD_CAP);
            amount = HARD_CAP;
        }
        float before = who.getHealth();
        who.heal((float) amount);
        LOG.info("[script] heal · {} {} → {}（+{}，等级 {}，目标 {}）",
                who.getName().getString(), String.format("%.2f", before),
                String.format("%.2f", who.getHealth()), String.format("%.2f", who.getHealth() - before),
                level, args.has("target") ? args.get("target").getAsString() : "caster");
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

    /** 等级取 {@code enchant_level}；缺失或非数字一律当 1（= 只吃 ratio，不吃 per_level）。 */
    private static int level(ScriptContext ctx) {
        Object raw = ctx.vars().get("enchant_level");
        if (raw instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return 1;
    }

    private static double num(JsonObject args, String key, double def) {
        if (!args.has(key)) {
            return def;
        }
        try {
            double v = args.get(key).getAsDouble();
            // 负数归零：D16 的「只留异常保护」
            return Double.isFinite(v) && v > 0.0D ? v : def;
        } catch (RuntimeException e) {
            LOG.warn("heal 的 args.{} 不是数字，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
