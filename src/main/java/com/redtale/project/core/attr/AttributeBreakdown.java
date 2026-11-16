package com.redtale.project.core.attr;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.math.AttackSpeedCurve;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 单个属性的「来源拆解」——一个属性当前值由哪些 modifier 构成。
 *
 * <p>这是 D18 的验收件：tooltip、{@code /redtale attr}、将来的伤害结算必须读**同一份**拆解，
 * 否则会出现「tooltip 写 12.4、实际打 9」。它遍历的是 {@code AttributeMap} 里的真实
 * modifier 集合，所以别人加的（BetterCombat 双持、SimplySwords Ferocity、咖啡因）也会出现，
 * 我们不需要知道它们叫什么。
 *
 * <p>计算顺序照抄原版 {@code AttributeInstance#calculateValue}：
 * <pre>value = (base + Σ ADD_VALUE) × (1 + Σ ADD_MULTIPLIED_BASE) × (1 + Σ ADD_MULTIPLIED_TOTAL)</pre>
 * 最终值以 {@code instance.getValue()} 为准（原版自己也缓存这份），拆解只用于解释。
 */
public record AttributeBreakdown(
        AttributeSpec spec,
        ResourceLocation attributeId,
        String label,
        double base,
        double addValue,
        double mulBase,
        double mulTotal,
        double value,
        List<Entry> entries) {

    /** 一条 modifier 的拆解。{@code contribution} 是「没有它，最终值会少多少」。 */
    public record Entry(ResourceLocation id, AttributeModifier.Operation operation, double amount,
                        double contribution) {
    }

    /**
     * @param entity    被查的实体
     * @param holder    属性
     * @param spec      表里的声明（可为 null，例如不在 80 条表里的第三方属性）
     * @return 实体没有这个属性时返回 null
     */
    public static AttributeBreakdown of(LivingEntity entity, Holder<Attribute> holder, AttributeSpec spec) {
        AttributeInstance instance = entity.getAttribute(holder);
        if (instance == null) {
            return null;
        }

        double base = instance.getBaseValue();
        double add = 0;
        double mulB = 0;
        double mulT = 0;
        List<AttributeModifier> mods = new ArrayList<>(instance.getModifiers());
        for (AttributeModifier m : mods) {
            switch (m.operation()) {
                case ADD_VALUE -> add += m.amount();
                case ADD_MULTIPLIED_BASE -> mulB += m.amount();
                case ADD_MULTIPLIED_TOTAL -> mulT += m.amount();
            }
        }

        double scaleBase = base + add;
        double scaleTotal = scaleBase * (1 + mulB);
        List<Entry> entries = new ArrayList<>(mods.size());
        for (AttributeModifier m : mods) {
            double contribution = switch (m.operation()) {
                case ADD_VALUE -> m.amount();
                case ADD_MULTIPLIED_BASE -> scaleBase * m.amount();
                case ADD_MULTIPLIED_TOTAL -> scaleTotal * m.amount();
            };
            entries.add(new Entry(m.id(), m.operation(), m.amount(), contribution));
        }
        // 先按乘区（平加 → 乘基 → 乘总），同区内按 id 排，输出稳定可比对
        entries.sort(Comparator
                .comparing((Entry e) -> e.operation().ordinal())
                .thenComparing(e -> e.id().toString()));

        ResourceLocation id = holder.unwrapKey().map(k -> k.location())
                .orElseGet(() -> spec != null ? ResourceLocation.parse(spec.id()) : null);
        String label = spec != null ? spec.zh() : String.valueOf(holder.value().getDescriptionId());
        return new AttributeBreakdown(spec, id, label, base, add, mulB, mulT, instance.getValue(), entries);
    }

    /** 一行摘要：{@code 攻击伤害 = 12.40（基础 1 · 平加 +8 · 乘基 ×1.2 · 乘总 ×1.3）}。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" = ").append(num(value))
                .append("（基础 ").append(num(base));
        if (addValue != 0) {
            sb.append(" · 平加 ").append(signed(addValue));
        }
        if (mulBase != 0) {
            sb.append(" · 乘基 ×").append(num(1 + mulBase));
        }
        if (mulTotal != 0) {
            sb.append(" · 乘总 ×").append(num(1 + mulTotal));
        }
        sb.append("）");
        return sb.toString();
    }

    /** 多行明细：每条 modifier 一行，外加攻速专属的「tick / 次每秒」换算（D19 / D20）。 */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(summary());
        out.add("  id: " + attributeId);
        if (entries.isEmpty()) {
            out.add("  （没有任何 modifier，全部来自基值）");
        } else {
            for (Entry e : entries) {
                out.add("  " + signed(e.amount()) + "  " + opName(e.operation())
                        + "  → 贡献 " + signed(e.contribution())
                        + "  [" + e.id() + "]");
            }
        }
        if (attributeId != null && attributeId.equals(Attributes.ATTACK_SPEED.unwrapKey()
                .map(k -> k.location()).orElse(null))) {
            // 与 mixin 共用同一份曲线：属性值 → 次/秒 → （饱和/地板）→ 实际 tick
            AttackSpeedRules rules = RedtaleCore.get().attackSpeedRules();
            double perSecond = Math.max(0.0, value);
            double effective = AttackSpeedCurve.effectivePerSecond(perSecond, rules.softCap());
            double delay = AttackSpeedCurve.delayTicks(effective, rules.minDelayTicks());
            out.add("  攻速换算：属性 " + num(perSecond) + " 次/秒 → 实际 " + num(delay) + " tick / 次"
                    + "（" + num(20.0 / delay) + " 次/秒；soft_cap "
                    + (rules.softCap() <= 0 ? "关闭" : num(rules.softCap()))
                    + "，地板 " + num(rules.minDelayTicks()) + " tick，见 D19）");
        }
        return out;
    }

    private static String opName(AttributeModifier.Operation op) {
        return switch (op) {
            case ADD_VALUE -> "平加";
            case ADD_MULTIPLIED_BASE -> "乘基";
            case ADD_MULTIPLIED_TOTAL -> "乘总";
        };
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "") + num(v);
    }

    /** 最多 3 位小数并去掉尾零，避免把 12.400000000000002 这种浮点噪声丢给玩家。 */
    public static String num(double v) {
        return com.redtale.project.core.math.AttributeNumbers.num(v);
    }
}
