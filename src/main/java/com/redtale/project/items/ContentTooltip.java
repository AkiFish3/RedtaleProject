package com.redtale.project.items;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.config.DisplaySpec;
import com.redtale.project.core.math.AttackSpeedCurve;
import com.redtale.project.core.math.DamageRules;
import com.redtale.project.core.math.Settle;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 武器/装备的数值行（D20）：**单次伤害 + 攻速（tick / 次每秒）**，DPS 默认不写。
 *
 * <p>两条硬约束：
 * <ol>
 *   <li>与实打共用 {@link Settle}（D20「显示与结算共用同一个 settle()」）。
 *       这里绝不自己写一遍乘算 —— 一旦有两份实现，就会出现「写着 12.4 打出 9」。</li>
 *   <li>攻速走 {@link AttackSpeedCurve}，与 mixin 用的是同一条曲线（含 soft_cap 与 1 tick 地板）。</li>
 * </ol>
 *
 * <p>输出是**纯字符串**，由调用方决定变成 {@code Component} 还是命令输出：
 * 这样服务端（{@code /redtale tooltip}，无客户端）能验同一段逻辑，不用开客户端核对。
 */
public final class ContentTooltip {

    /**
     * 玩家的攻击伤害**基值**是 1.0，不是注册表默认值 2.0。
     *
     * <p>证据：{@code Player#createAttributes()} 字节码里 {@code dconst_1} 直接塞给
     * {@code Attributes.ATTACK_DAMAGE}（即 1.0）；注册表的 2.0 是给生物用的默认值。
     * 用错会让 tooltip 每把武器凭空多 1 点（实测：demo_longsword 写成 10.8 而非 9.6）。
     */
    public static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

    /** 玩家的攻速**基值** 4.0（注册表默认值即 4.0，两者一致，这里写死是为了可读）。 */
    public static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    private ContentTooltip() {
    }

    /** @param holder 持有者，可为 null（控制台验收时没有实体） */
    public static List<String> lines(ItemStack stack, LivingEntity holder) {
        ContentData data = stack.get(ContentComponents.CONTENT.get());
        if (data == null) {
            return List.of();
        }
        DamageRules rules = RedtaleCore.get().damageRules();

        // ---- 单次伤害：进场的平加 × (1 + 百分比池)，与 L2 同一个 settle()
        double baseDamage = PLAYER_BASE_ATTACK_DAMAGE + sum(stack, Attributes.ATTACK_DAMAGE);
        double percent = 0;
        List<String> percentParts = new ArrayList<>();
        for (String key : rules.multiplier().percentAttributes()) {
            Double v = data.rolledStats().get(resolveKey(key, data));
            if (v == null || v == 0) {
                continue;
            }
            percent += v;
            percentParts.add(key + " +" + StatFormat.num(v * 100) + "%");
        }
        Settle.Result settled = Settle.settle(baseDamage, percent, List.of(),
                rules.caps(), rules.sanity());

        // ---- 攻速：属性值 → 次/秒 → (soft_cap / 地板) → tick
        double perSecond;
        boolean held = holder != null && holder.getMainHandItem() == stack;
        if (held) {
            // 拿着这件东西时用玩家的真实属性（含药水、急迫规则、BetterCombat 双持…）
            perSecond = holder.getAttributeValue(Attributes.ATTACK_SPEED);
        } else {
            perSecond = PLAYER_BASE_ATTACK_SPEED + sum(stack, Attributes.ATTACK_SPEED);
        }
        perSecond = AttackSpeedCurve.effectivePerSecond(perSecond, RedtaleCore.get().attackSpeedRules().softCap());
        double delay = AttackSpeedCurve.delayTicks(perSecond,
                RedtaleCore.get().attackSpeedRules().minDelayTicks());

        List<String> out = new ArrayList<>(2);
        // 护甲 / 饰品不打印「单次伤害」：它不是拿来打的，写上去只是噪声。
        // 判据优先看基底（内容还在配置里时），配置被删掉的旧物品退回「有没有攻击属性」。
        ItemContent content = RedtaleCore.get().contentItems().get(data.contentId());
        boolean weapon = content != null && content.base().kind() == ItemBase.Kind.WEAPON;
        double attackDamage = sum(stack, Attributes.ATTACK_DAMAGE);
        double attackSpeed = sum(stack, Attributes.ATTACK_SPEED);

        if (weapon || attackDamage != 0) {
            StringBuilder hit = new StringBuilder("≈ ").append(StatFormat.num(settled.value())).append(" /次");
            if (!percentParts.isEmpty()) {
                hit.append("（").append(String.join(" · ", percentParts)).append("）");
            }
            out.add(hit.toString());
        }
        if (weapon || attackSpeed != 0) {
            out.add("攻速 " + StatFormat.num(delay) + " tick · " + StatFormat.num(20.0 / delay) + " 次/秒"
                    + (held ? "" : "（未持有，不含药水/急迫）"));
        }
        // 显示口径（D20 / Q-023 默认 A，R69）：两个开关都在 display.json5，默认全关。
        // DPS 用的是上面那两行**同一个** settle 值与同一个 perSecond ——
        // 自己再算一遍就会出「写着 30 实际 22」（D20 同源纪律）。
        DisplaySpec.Settings display = RedtaleCore.get().display();
        if (display.showDps() && (weapon || attackDamage != 0)) {
            out.add("DPS ≈ " + StatFormat.num(settled.value() * perSecond)
                    + "（假想目标 " + display.assumeTarget() + "，不含护甲/暴击）");
        }
        if (display.showAttributeBreakdown()) {
            List<String> parts = breakdown(stack);
            if (!parts.isEmpty()) {
                out.add("属性 " + parts.size() + " 项：" + String.join(" · ", parts));
            }
        }
        // 词条（D22）：只列 id 与条数。数值已经并进 rolledStats，上面那两行已体现，
        // 这里再写一遍数值会有两份真相（D20「显示与结算同源」）。
        if (!data.affixes().isEmpty()) {
            out.add("词条 " + data.affixes().size() + "：" + String.join(" · ", data.affixes()));
        }
        // 绑定技能（M5-B §7）：只列 id —— 技能数值在 skills/*.json5，不在这儿重复第二份真相
        if (!data.abilities().isEmpty()) {
            out.add("技能 " + data.abilities().size() + "：" + String.join(" · ", data.abilities()));
        }
        // 孔位与宝石（M3）：孔序按定义布局逐孔写，空孔写「空」。
        // 数值已经在上面那两行里（宝石走 ATTRIBUTE_MODIFIERS，与内容数值同一个面板），
        // 这里再写一遍数值就是两份真相（D20 同口径）。
        if (content != null && content.sockets().count() > 0) {
            List<String> sockets = Socketer.aligned(data.sockets(), content.sockets().count());
            List<String> parts = new ArrayList<>(sockets.size());
            for (int i = 0; i < sockets.size(); i++) {
                String type = content.sockets().types().get(i);
                String gem = sockets.get(i);
                parts.add("[" + type + "] " + (gem == null || gem.isEmpty() ? "空" : gem));
            }
            out.add("孔位 " + sockets.size() + "：" + String.join(" · ", parts));
        }
        // 这件东西本身是宝石时，写清类型与互斥组 —— 镶之前要能一眼看出能不能进某个孔
        if (content != null && content.gem() != null) {
            var gem = content.gem();
            out.add("宝石 " + gem.type() + " · T" + gem.tier()
                    + (gem.exclusiveGroup().isEmpty() ? "" : " · 组 " + gem.exclusiveGroup()));
        }
        // 套装（M3 §5.4）：只写「属于哪套 / 当前几件 / 哪几档生效」。
        // 数值不在这里重复 —— 生效档的属性已经进了玩家属性面板（SetBonus 下发），
        // 写第二遍就是两份真相（D20 同口径）。
        if (content != null && !content.setId().isEmpty()) {
            SetSpec.Definition set = RedtaleCore.get().sets().get(content.setId());
            int worn = holder instanceof net.minecraft.world.entity.player.Player p
                    ? SetBonus.countOf(p, content.setId()) : 0;
            String name = set == null ? content.setId() : set.name();
            int total = set == null ? 0 : set.pieces().size();
            List<SetSpec.Bonus> active = holder instanceof net.minecraft.world.entity.player.Player p
                    ? SetBonus.activeOf(p, content.setId()) : List.of();
            out.add("套装 " + name + " " + worn + "/" + total
                    + (active.isEmpty() ? "" : " · 生效 " + active.size() + " 档")
                    + (set == null ? "（定义缺失）" : ""));
        }
        // 武器动作（D15）：只报「组件确实挂上了」，不报能不能挥 —— 后者要客户端。
        // 这一行是 R7-1 的服务端证据：生成数据包只给定义，物品还得有这个引用才生效。
        var preset = ItemFactory.weaponPresetOf(stack);
        if (preset != null) {
            out.add("动作 " + preset);
        }
        return out;
    }

    /**
     * 属性明细（{@code show_attribute_breakdown}）：把 {@code forEachModifier} 的**每一条** modifier
     * 单独列出来 —— 基底 / 宝石 / 词条各是一条，所以这一行天然带来源拆分。
     *
     * <p>刻意不复用 {@code rolledStats}：那是「配置里写了多少」，这里是「物品组件里实际挂了多少」，
     * 后者才是玩家面板上看到的（D21 白板化后两者会不一致）。数值格式走 {@link StatFormat#signed}，
     * 与属性面板同一个口径。
     */
    private static List<String> breakdown(ItemStack stack) {
        List<String> out = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            var key = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            String id = key == null ? "?" : key.toString();
            var spec = com.redtale.project.core.attr.AttributeSpecs.resolve(id);
            String name = spec == null ? id : spec.zh();
            String value = spec == null ? String.valueOf(modifier.amount()) : StatFormat.signed(spec, modifier.amount());
            out.add(name + " " + value + "（" + modifier.operation().getSerializedName() + "）");
        });
        return out;
    }

    /** 让配置里的逻辑 id（{@code physical_damage}）对得上 {@code rolledStats} 的全名 key。 */    private static String resolveKey(String key, ContentData data) {
        if (data.rolledStats().containsKey(key)) {
            return key;
        }
        var spec = com.redtale.project.core.attr.AttributeSpecs.resolve(key);
        if (spec != null && data.rolledStats().containsKey(spec.id())) {
            return spec.id();
        }
        return key;
    }

    private static double sum(ItemStack stack, Holder<Attribute> attr) {
        return modifierAmount(stack, EquipmentSlot.MAINHAND, attr);
    }

    /**
     * 一件物品在指定槽位上给某属性的**平加总量**，走 {@code ItemStack#forEachModifier}。
     *
     * <p>刻意不用 {@code stack.get(ATTRIBUTE_MODIFIERS)}：{@code forEachModifier} 是**原版自己**
     * 处理「组件非空 → 只用组件；为空 → 回落 getDefaultAttributeModifiers」那条分支的入口，
     * 所以 D21 的白板化在这里能被如实观测（{@code /redtale whitening} 也用它）。
     */
    public static double modifierAmount(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attr) {
        double[] acc = {0};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder.value() == attr.value()) {
                acc[0] += modifier.amount();
            }
        });
        return acc[0];
    }

    /** 调试用：把内容里所有属性按「显示单位」列出来（{@code /redtale items} 用）。 */
    static List<String> statSummary(ContentData data) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : data.rolledStats().entrySet()) {
            var spec = com.redtale.project.core.attr.AttributeSpecs.resolve(e.getKey());
            out.add((spec == null ? e.getKey() : spec.zh()) + " " + StatFormat.signed(spec, e.getValue()));
        }
        return out;
    }
}
