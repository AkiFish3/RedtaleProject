package com.redtale.project.core.attr;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.RedtaleCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 把 {@link AttackSpeedRules} 声明的东西真正写进 {@code ATTACK_SPEED} 属性（D18）。
 *
 * <p>为什么走属性而不是自己去乘冷却：
 * <ul>
 *   <li>其它 mod（SimplySwords 的 Ferocity、Farmer's Respite 的咖啡因、BetterCombat 的双持）
 *       读的是同一个 {@code BASE_ATTACK_SPEED}，写属性就等于进了同一个池子，数值天然一致；</li>
 *   <li>{@code /attribute} 与 {@link AttributeBreakdown} 能看见，不需要我们另开一套账本；</li>
 *   <li>卸下/效果结束只要删 modifier，不会留下「残留加成」。</li>
 * </ul>
 *
 * <p><b>modifier 用 transient</b>（{@code addTransientModifier}）：它跟着实体走、不进 NBT 存档，
 * 重登/重启后由本类重新算一遍。否则旧等级的值会被存进存档，效果结束后仍留在身上。
 */
public final class AttackSpeedApplier {

    /** 固定 id（D18 约束 2）：等级变化先移除再加，绝不叠加泄漏。 */
    public static final ResourceLocation HASTE_ID =
            ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, "haste_attack_speed");

    /** 每 5 tick 重算一次（0.25s）—— 效果到期最多晚 0.25s 掉，换掉每 tick 写属性的开销。 */
    private static final int PERIOD_TICKS = 5;

    /** 去重日志只打一次，避免每 5 tick 刷屏。 */
    private static final java.util.Set<String> DEDUPE_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject/Speed");

    private AttackSpeedApplier() {
    }

    public static void onPlayerTick(Player player) {
        if (player.tickCount % PERIOD_TICKS != 0) {
            return;
        }
        apply(player, RedtaleCore.get().attackSpeedRules());
    }

    /**
     * 对任意活体应用规则。玩家每 5 tick 调一次；怪物的 buff 后续由技能原语调。
     *
     * @return 本次实际写入的 modifier 个数（诊断用）
     */
    public static int apply(LivingEntity entity, AttackSpeedRules rules) {
        AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_SPEED);
        if (instance == null) {
            // 绝大多数怪物没有 attack_speed（R2 实测僵尸报「没有属性」），静默跳过
            return 0;
        }
        int written = 0;

        // 注意命名坑：1.21.1 的急迫字段叫 MobEffects.DIG_SPEED，注册表 id 却是 minecraft:haste
        // （javap 实测 21.1.257：只有 DIG_SPEED，没有 HASTE 字段）
        MobEffectInstance haste = entity.getEffect(MobEffects.DIG_SPEED);
        double hasteAmount = rules.hasteAmount(haste == null ? -1 : haste.getAmplifier());
        if (hasteAmount != 0 && alreadyCounted(instance, "minecraft:haste")) {
            // 1.21.1 的急迫**自身**就挂着 ATTACK_SPEED 的 modifier（见 alreadyCounted 注释），
            // 我们再写一遍就是 +20%/级的双计。让位给原版，规则仍以 0.10/级生效。
            hasteAmount = 0;
        }
        written += set(instance, HASTE_ID, hasteAmount);

        for (Map.Entry<String, AttackSpeedRules.EffectRule> entry : rules.effectRules().entrySet()) {
            String effectId = entry.getKey();
            if (effectId.equals("minecraft:haste")) {
                continue; // 急迫只有一条路径（见 AttackSpeedRules.parse 的告警）
            }
            AttackSpeedRules.EffectRule rule = entry.getValue();
            if (rule.isZero()) {
                continue; // 默认表里别人已经给过的来源都是 0，避免重复计
            }
            ResourceLocation modifierId = modifierIdFor(effectId);
            if (modifierId == null) {
                continue;
            }
            int amount = effectAmount(entity, effectId);
            if (amount >= 0 && alreadyCounted(instance, effectId)) {
                amount = -1;
            }
            written += set(instance, modifierId, amount < 0 ? 0 : rule.amount(amount));
        }
        return written;
    }

    /**
     * 该效果自己是否已经在 {@code ATTACK_SPEED} 上挂了 modifier（= 已经算过一次）。
     *
     * <p>起因（2026-11-15，R3 实测）：1.21.1 的 {@code MobEffects.DIG_SPEED} 注册时带
     * {@code addAttributeModifier(ATTACK_SPEED, minecraft:effect.haste, 0.1, ADD_MULTIPLIED_TOTAL)}
     * （server jar 字节码实读）—— **原版急迫本身就加攻速**，D19 的「1.21.1 原版不加」这条前提
     * 在 1.21.1 上不成立（它只在 1.20.1 成立）。所以按 D18 铁律「同一来源只算一次」，
     * 我们检测到原版已给就让位，而不是硬加第二份。
     *
     * <p>命名规则：原版效果用 {@code <ns>:effect.<effect path>}（如 {@code minecraft:effect.haste}）。
     */
    private static boolean alreadyCounted(AttributeInstance instance, String effectId) {
        ResourceLocation vanillaId = vanillaEffectModifierId(effectId);
        if (vanillaId == null || instance.getModifier(vanillaId) == null) {
            return false;
        }
        if (DEDUPE_LOGGED.add(effectId)) {
            LOG.info("攻速去重：{} 自带的 modifier {} 已计入 ATTACK_SPEED，赤物语规则不再叠加"
                    + "（把 attack_speed.json5 的对应值改 0 可消除这条日志）", effectId, vanillaId);
        }
        return true;
    }

    /** 原版效果挂在属性上的 modifier id：{@code minecraft:haste} → {@code minecraft:effect.haste}。 */
    static ResourceLocation vanillaEffectModifierId(String effectId) {
        ResourceLocation id = parseId(effectId);
        return id == null ? null
                : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "effect." + id.getPath());
    }

    /** 移除本类发放的全部 modifier（reload 关掉规则、或实体卸装时用）。 */
    public static void clear(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        instance.removeModifier(HASTE_ID);
        for (String effectId : RedtaleCore.get().attackSpeedRules().effectRules().keySet()) {
            ResourceLocation id = modifierIdFor(effectId);
            if (id != null) {
                instance.removeModifier(id);
            }
        }
    }

    private static int set(AttributeInstance instance, ResourceLocation id, double amount) {
        if (amount == 0) {
            instance.removeModifier(id);
            return 0;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null
                && existing.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                && Double.compare(existing.amount(), amount) == 0) {
            return 0; // 没变，不碰属性（避免每 5 tick 触发一次重算/同步）
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return 1;
    }

    /** 返回效果的 amplifier（0 基）；没有该效果 → -1。 */
    private static int effectAmount(LivingEntity entity, String effectId) {
        ResourceLocation id = parseId(effectId);
        if (id == null) {
            return -1;
        }
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
        if (holder.isEmpty()) {
            return -1;
        }
        MobEffectInstance instance = entity.getEffect(holder.get());
        return instance == null ? -1 : instance.getAmplifier();
    }

    /** 固定的 modifier id：{@code redtale_project:effect_attack_speed/<效果路径>}。 */
    static ResourceLocation modifierIdFor(String effectId) {
        ResourceLocation id = parseId(effectId);
        if (id == null) {
            return null;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID,
                "effect_attack_speed/" + path);
    }

    private static ResourceLocation parseId(String raw) {
        try {
            return raw.contains(":") ? ResourceLocation.parse(raw)
                    : ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
