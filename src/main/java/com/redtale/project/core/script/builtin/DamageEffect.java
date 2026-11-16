package com.redtale.project.core.script.builtin;

import com.google.gson.JsonObject;
import com.redtale.project.core.script.EffectType;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code damage} 原语：对目标打一次白值伤害（SKILLS §4 第 1 条，M5-A 的第一块）。
 *
 * <p><b>只给白值，绝不 {@code setAmount}</b>（R25-c / SKILLS §5 红线 1）：这里只调
 * {@link LivingEntity#hurt(DamageSource, float)}，倍率全部由 {@code DamageLayer} 在
 * {@code LivingIncomingDamageEvent} 上乘。写绝对值会让 {@code physical_damage} / 条件乘区
 * 对技能失效，同一技能在有无属性时数值不可对账。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code amount}（**必填**）：白值，正数；缺省或 {@code <= 0} → 打一行 WARN 跳过</li>
 *   <li>{@code damage_type}：缺省 {@code redtale_project:skill_physical}；必须含 {@code :}，
 *       否则回缺省 + WARN。法术技能**必须显式写** {@code redtale_project:skill_magical}，
 *       否则会去吃物理百分比属性（SKILLS §5 第 4 条）</li>
 *   <li>{@code radius}：**本原语不支持**（只做单体）。>0 时打一行 WARN 并忽略 ——
 *       范围伤害是 {@code damage_aoe} 的语义（列表第 2 条），两个原语共用一个参数只会让人写错</li>
 * </ul>
 *
 * <p>打谁：{@link ScriptContext#target()}（技能释放时的受害者 / 被命中实体）。没有再退
 * {@code ctx.player()}（自己打自己没意义，所以不退）。施法者取 {@code player()}，生物施法者取
 * {@code vars.attacker}（与 {@link HealEffect} 同一口径）—— 施法者只影响「谁记的账、死亡文案」，
 * 不影响数值。
 *
 * <p>异常保护（D16）：负数/非数字归零跳过、{@code 1e9} 硬顶、目标已死只跳过，全程不抛。
 */
public final class DamageEffect implements EffectType {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Script");

    /** 单次伤害白值硬顶，D16 的「异常保护」硬顶（不是平衡钳制）。 */
    private static final double HARD_CAP = 1.0e9D;

    /** 缺省伤害类型：物理轴（SKILLS §4 第 1 条）。 */
    public static final String DEFAULT_TYPE = "redtale_project:skill_physical";

    /** 只报一次的告警（按文案去重，避免每个技能每次释放刷一行）。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "damage";
    }

    @Override
    public String description() {
        return "args.amount：白值（必填，只给白值，倍率由伤害乘区统一乘）；"
                + "args.damage_type：缺省 redtale_project:skill_physical（法术请显式写 skill_magical）；"
                + "args.radius：不支持，>0 会被忽略";
    }

    @Override
    public void run(ScriptContext ctx, JsonObject args) {
        Entity picked = ctx.target();
        if (!(picked instanceof LivingEntity who) || !who.isAlive()) {
            warnOnce("no_target", "damage 没有可用目标（需要 target 上下文：技能释放 / 命中实体），已跳过");
            return;
        }
        if (args.has("radius") && num(args, "radius", 0.0D) > 0.0D) {
            warnOnce("radius", "damage 原语只做单体，args.radius 已被忽略（范围伤害请用 damage_aoe）");
        }
        double amount = num(args, "amount", 0.0D);
        if (amount <= 0.0D) {
            warnOnce("zero", "damage 的 args.amount 缺失或 <= 0，已跳过（本原语不会自己编数值）");
            return;
        }
        if (amount > HARD_CAP) {
            LOG.warn("damage 白值 {} 超过硬顶 {}，按硬顶结算（D16 异常保护）", amount, HARD_CAP);
            amount = HARD_CAP;
        }
        Level level = who.level();
        if (level == null) {
            warnOnce("no_level", "damage 的目标没有世界（实体未入世），已跳过");
            return;
        }
        DamageSource source = source(level, ctx, args);
        float before = who.getHealth();
        boolean hurt = who.hurt(source, (float) amount);
        LOG.info("[script] damage · {} {} → {}（白值 {}，类型 {}，{}）",
                who.getName().getString(), String.format("%.2f", before),
                String.format("%.2f", who.getHealth()), String.format("%.2f", amount),
                source.getMsgId(), hurt ? "已结算" : "被原版拦下（无敌帧/免疫/创造）");
    }

    /**
     * 造一个 {@code redtale_project:} 下的伤害源。
     *
     * <p>为什么必须是我们自己的 damage_type：{@code DamageLayer} 的白名单是
     * {@code redtale_project:*}（R4），写 {@code minecraft:generic} 的技能伤害**不会被乘**，
     * 等于绕过整套属性（SKILLS §5 红线 2）。
     *
     * <p>类型 id 查不到注册表 → 退回缺省类型；连缺省也查不到（数据包没加载）→ 退回
     * {@code level.damageSources().generic()} 并 WARN 一次，宁可少乘也不崩。
     */
    private static DamageSource source(Level level, ScriptContext ctx, JsonObject args) {
        Entity caster = caster(ctx);
        String typeId = DEFAULT_TYPE;
        if (args.has("damage_type")) {
            try {
                String raw = args.get("damage_type").getAsString().trim();
                if (raw.contains(":")) {
                    typeId = raw;
                } else {
                    warnOnce("bad_type", "damage 的 args.damage_type 不含命名空间，已回缺省 " + DEFAULT_TYPE);
                }
            } catch (RuntimeException e) {
                warnOnce("bad_type", "damage 的 args.damage_type 不是字符串，已回缺省 " + DEFAULT_TYPE);
            }
        }
        Holder<DamageType> holder = holder(level, typeId);
        if (holder == null && !DEFAULT_TYPE.equals(typeId)) {
            warnOnce("unknown_type", "damage 的伤害类型 " + typeId + " 不在注册表里，已回缺省 " + DEFAULT_TYPE);
            holder = holder(level, DEFAULT_TYPE);
        }
        if (holder == null) {
            warnOnce("no_registry", "damage 找不到任何 redtale_project 伤害类型（数据包没加载？），退回原版 generic，本次不吃属性乘区");
            return level.damageSources().generic();
        }
        return caster == null ? new DamageSource(holder) : new DamageSource(holder, caster, caster);
    }

    private static Holder<DamageType> holder(Level level, String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return null;
            }
            return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, rl)).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 施法者：玩家优先，其次生物施法者（附魔挂在怪物武器上是 {@code vars.attacker}）。 */
    private static Entity caster(ScriptContext ctx) {
        if (ctx.hasPlayer()) {
            return ctx.player();
        }
        Object attacker = ctx.vars().get("attacker");
        return attacker instanceof Entity e ? e : null;
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
            LOG.warn("damage 的 args.{} 不是数字，按缺省 {} 处理", key, def);
            return def;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOG.warn(message);
        }
    }
}
