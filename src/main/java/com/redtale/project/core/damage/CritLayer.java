package com.redtale.project.core.damage;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 近战暴击入口（M2）：在 {@code CriticalHitEvent} 上把「属性暴击率 / 暴击倍率」落到**倍率**上。
 *
 * <p><b>为什么用这个事件，而不是在 {@code LivingIncomingDamageEvent} 里自己 roll</b>：
 * <ol>
 *   <li>它是原版 {@code Player#attack} 里唯一的暴击判定点，BetterCombat 也不自己算暴击
 *       （2.4.0 的 jar 里 {@code CriticalHitEvent} 零引用），一个点覆盖两种挥动；</li>
 *   <li>它给了 {@code setCriticalHit(boolean)} 与 {@code setDamageMultiplier(float)}，
 *       所以「原版没判暴击但掷中了暴击率」也能成真 —— 这是属性暴击率唯一的落点；</li>
 *   <li>它发生在伤害事件之前，暴击是**乘在最终伤害上**的，与 L2 乘区不抢同一个数。</li>
 * </ol>
 *
 * <p><b>只写倍率，不写绝对值</b>（与 {@link DamageLayer} 同一条硬约束）：
 * 这里改的是 {@code event.setDamageMultiplier(...)}，也就是「这一刀乘几倍」，
 * 而不是「这一刀打多少」。
 *
 * <p>原始倍率（{@code event.getDamageMultiplier()}）在配置里可能带上原版 1.5；
 * 是否替换由 {@code apply_to_vanilla_crit} 决定，实测结论见 {@code docs/ROUND-R9-CRIT.md}。
 */
public final class CritLayer {

    private static final int CAPACITY = 8;

    private static final Deque<Entry> RECENT = new ArrayDeque<>(CAPACITY);
    private static long seen;
    private static long crits;
    private static long forced;

    /**
     * 一次暴击判定摘要。
     *
     * @param chance        归一后的暴击率（属性值）
     * @param power         暴击倍率属性值（{@code <= 0} = 没配，保留进场倍率）
     * @param vanillaCrit   原版是否判了暴击（跳跃/下落）
     * @param rolled        本次是否掷中暴击率
     * @param inMultiplier  进场倍率（原版给的是 1.5 或 1.0）
     * @param outMultiplier 出场倍率（我们改成的值；没改就是进场值）
     */
    public record Entry(double chance, double power, boolean vanillaCrit, boolean rolled,
                        float inMultiplier, float outMultiplier) {
        public boolean changed() {
            return outMultiplier != inMultiplier;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CriticalHitEvent event) {
        RedtaleCore core = RedtaleCore.get();
        if (!core.ready()) {
            return;
        }
        CritRules rules = core.critRules();
        if (!rules.enabled()) {
            return;
        }

        Player attacker = event.getEntity();
        double chance = rules.clampChance(attribute(attacker, rules.chanceAttribute()));
        boolean vanillaCrit = event.isCriticalHit();
        boolean rolled = chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
        boolean crit = vanillaCrit || (rules.forceOnRoll() && rolled);

        float inMultiplier = event.getDamageMultiplier();
        float outMultiplier = inMultiplier;

        if (crit) {
            double power = attribute(attacker, rules.powerAttribute());
            boolean mayReplace = power > 0 && Double.isFinite(power)
                    && (rules.applyToVanillaCrit() || !vanillaCrit);
            if (mayReplace) {
                outMultiplier = (float) power;
                event.setDamageMultiplier(outMultiplier);
            }
            // 原版没判、我们掷中了 → 必须显式置真，否则这一刀只是「倍率被改了但不算暴击」
            if (!vanillaCrit) {
                event.setCriticalHit(true);
                synchronized (CritLayer.class) {
                    forced++;
                }
            }
        }

        synchronized (CritLayer.class) {
            seen++;
            if (crit) {
                crits++;
            }
            if (RECENT.size() >= CAPACITY) {
                RECENT.removeFirst();
            }
            RECENT.addLast(new Entry(chance, attribute(attacker, rules.powerAttribute()),
                    vanillaCrit, rolled, inMultiplier, outMultiplier));
        }
    }

    /**
     * 读一条自建属性的当前值。属性缺失（没挂到该 EntityType / 没在册）按 0 处理 ——
     * {@code getAttribute} 返回 null 是常态，不是错误。
     */
    private static double attribute(Player player, String name) {
        AttributeSpec spec = AttributeSpecs.resolve(name);
        if (spec == null) {
            return 0;
        }
        Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            return 0;
        }
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) {
            return 0;
        }
        double v = instance.getValue();
        return Double.isFinite(v) ? v : 0;
    }

    public static synchronized List<Entry> recent() {
        return new ArrayList<>(RECENT);
    }

    public static synchronized long seen() {
        return seen;
    }

    public static synchronized long crits() {
        return crits;
    }

    public static synchronized long forced() {
        return forced;
    }

    public static synchronized void reset() {
        RECENT.clear();
        seen = 0;
        crits = 0;
        forced = 0;
    }
}
