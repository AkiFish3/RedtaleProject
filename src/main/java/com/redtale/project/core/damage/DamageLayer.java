package com.redtale.project.core.damage;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.math.DamageRules;
import com.redtale.project.core.math.Settle;
import com.redtale.project.items.ItemUpgrader;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * L2 乘区层（M1 的最后一块）：在别人把伤害算完之后，**乘**一次我们的因子。
 *
 * <pre>
 * 进场 amount ──► 我们的因子 = settle(amount, Σpercent, conds) / amount
 *                ──► amount × 因子
 * </pre>
 *
 * <p><b>为什么写成「因子」而不是直接 {@code setAmount(结算值)}</b>：
 * 两者数学上等价，但语义差别是这个项目最硬的一条约束（{@code DAMAGE-PIPELINE.md} §2）——
 * 一旦代码长成「我给个绝对值」，后面维护的人就会顺手把别人的加成抹掉。
 * 因子形式下，别人的 {@code +12} 永远还在，我们只改倍数。
 *
 * <p><b>优先级 LOWEST</b>：跑到最后，读到的就是所有人算完的数。
 * 这条与 {@code DAMAGE-PIPELINE.md} §10 的待验证项对应，本轮已在测试服实测
 * （见 {@code docs/ROUND-M4-DAMAGE.md}）。
 *
 * <p><b>白名单</b>：只处理 {@code redtale_project:*} 的伤害类型（{@link MultiplierRules}）。
 * 别人的伤害连同它们的无敌帧/护甲减免一起原样放行。
 */
public final class DamageLayer {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Damage");

    /**
     * 进场伤害（护甲/抗性减免之前）。改这里影响的是「进最终结算的数」，
     * 而不是 {@code LivingDamageEvent} 那种「已经减完」的数 —— 与三层模型一致：
     * L2 乘区应当在 L0/L1 之后、L3 兜底之前。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        RedtaleCore core = RedtaleCore.get();
        if (!core.ready()) {
            return;
        }

        DamageSource source = event.getSource();
        String typeId = typeId(source);
        if (typeId == null) {
            return;
        }

        DamageRules rules = core.damageRules();
        MultiplierRules multiplier = rules.multiplier();

        if (!multiplier.appliesTo(typeId)) {
            // 别人的伤害：默认只记一笔（passthrough），绝不改值
            if (multiplier.passthroughExternal()) {
                DamageTrace.skip(typeId, event.getAmount());
            }
            return;
        }

        // 惰性兜底 ⑤（D33）：结算前先把攻击者主手那件过一遍 ensure。
        // 为什么不放 tooltip：ItemTooltipEvent 客户端也会触发，在客户端改物品等于凭空造数据。
        ensureAttackerMainHand(source.getEntity());

        double base = event.getAmount();
        if (!(base > 0)) {
            // 0 或负数不乘：负数在 L3 里归零，除零会变 NaN，两条都不该走到这里
            return;
        }

        // 类别池 first-match（R26-b）：物理技能吃 physical_damage，法术技能吃 magical_damage，
        // 未命中落回 percent_attributes。条件乘区两类共用，不跟着分轴。
        double percent = sumAttributes(source.getEntity(), multiplier.attributesFor(typeId))
                + sumConditional(source.getEntity(), event.getEntity(), multiplier);
        Settle.Result result = Settle.settle(base, percent, List.of(), rules.caps(), rules.sanity());
        double value = result.value();
        double factor = Double.isFinite(value) ? value / base : 1.0;

        DamageTrace.record(new DamageTrace.Entry(typeId, base, percent, factor, value, result.overflow()));

        if (!Double.isFinite(factor) || factor == 1.0) {
            return;
        }

        event.setAmount((float) (base * factor));

        if (Settle.shouldLog(result, rules.sanity(), Math.random())) {
            LOG.warn("伤害超阈值：{} {} → {}（×{}，percent {}，来源 {}）",
                    typeId, base, value, factor, percent, source.getEntity());
        }
    }

    /**
     * 惰性兜底 ⑤（D33）：结算前把攻击者**主手**那件过一遍 {@link ItemUpgrader#ensure}。
     *
     * <p>四个主动刷新点（登录 / 开容器 / 区块加载 / reload 分帧）都会有漏网之鱼——
     * 比如本 tick 刚由配方产出的物品，或刷新点排队还没轮到它。结算路径是这些物品
     * 唯一的「一定会经过」的地方，所以在这里补一次。
     *
     * <p><b>只处理主手</b>：L2 乘区只读攻击者的属性，别的槽位读了也没用；
     * 一律遍历会把这个热路径变成背包扫描。
     *
     * <p><b>为什么不放 tooltip</b>：{@code ItemTooltipEvent} 客户端也会触发，
     * 在客户端改物品数据等于让客户端凭空造属性（R10-e：只服务端推，不做客户端上报）。
     *
     * <p>成本：{@link ItemUpgrader#ensure} 的快路径是一次 int 比较（实测 20 万次 &lt; 200ms），
     * 绝大多数攻击落在这条路上，不写盘、不分配。
     */
    private static void ensureAttackerMainHand(Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = living.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemUpgrader.Result result;
        try {
            result = ItemUpgrader.ensure(stack);
        } catch (RuntimeException ex) {
            // 结算路径上一次异常就是一次「打不出伤害」，宁可让物品旧一版
            LOG.warn("惰性兜底：主手物品更新失败（{}），按旧版结算", ex.toString());
            return;
        }
        if (result.status() == ItemUpgrader.Status.UPGRADED && living instanceof ServerPlayer player) {
            // ensure 已就地改了这个 ItemStack，但客户端仍是旧组件 → 主动推一次（R10-e）
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * 伤害类型 id。取 {@code typeHolder()} 的 key 而不是 {@code type()} ——
     * 后者只给对象，拿不到 id；拿不到 key（理论上只有动态构造的 source 才这样）就直接放行。
     */    private static String typeId(DamageSource source) {
        if (source == null) {
            return null;
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse(null);
    }

    /**
     * 百分比池：把配置里列出的属性值相加。
     *
     * <p>只读**攻击者**的属性（防御侧走 M2 的 {@code pve_defense} / {@code damage_reduction}，
     * 那时才需要区分条件乘区）。属性缺失（例如怪物没有该条）按 0 处理 ——
     * {@code getAttribute} 返回 null 是常态，不是错误。
     */
    private static double sumAttributes(Entity attacker, List<String> attributeNames) {
        if (!(attacker instanceof LivingEntity living) || attributeNames.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (String name : attributeNames) {
            AttributeSpec spec = AttributeSpecs.resolve(name);
            if (spec == null) {
                continue;
            }
            Holder<Attribute> holder = RedtaleAttributes.holder(spec);
            if (holder == null) {
                continue;
            }
            AttributeInstance instance = living.getAttribute(holder);
            if (instance == null) {
                continue;
            }
            double v = instance.getValue();
            if (Double.isFinite(v)) {
                sum += v;
            }
        }
        return sum;
    }

    /**
     * 条件乘区（M2）：按目标分类追加属性。
     *
     * <p><b>进同一个百分比池相加，不连乘</b>：{@code settle()} 的 L2 是
     * {@code base × (1 + Σpercent)}。物理 +20% 与「对亡灵 +30%」打到僵尸时是
     * {@code ×1.5}（0.2 + 0.3），不是 {@code ×1.56}。这条与 {@code DAMAGE-PIPELINE} §2
     * 「先加后乘」一致，也是上游 MMOItems 的观感（玩家对账时按加法理解）。
     *
     * <p>目标分类只有一个实现：{@link TargetClass}（玩家 → pvp；亡灵 → pve+undead；其它 → pve）。
     */
    private static double sumConditional(Entity attacker, LivingEntity target, MultiplierRules rules) {
        if (rules.conditionalAttributes().isEmpty()) {
            return 0;
        }
        List<String> keys = TargetClass.of(target);
        if (keys.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (String key : keys) {
            List<String> names = rules.conditionalAttributes().get(key);
            if (names != null) {
                sum += sumAttributes(attacker, names);
            }
        }
        return sum;
    }

    /** 给 {@code /redtale damage} 用：某个伤害类型会不会被我们乘。 */
    public static boolean wouldApply(String damageTypeId) {
        return RedtaleCore.get().damageRules().multiplier().appliesTo(damageTypeId);
    }

    /** 给命令与文档用：本层是否可能生效（白名单为空时永远不生效）。 */
    public static boolean active() {
        return RedtaleCore.get().ready()
                && !RedtaleCore.get().damageRules().multiplier().damageTypes().isEmpty();
    }
}
