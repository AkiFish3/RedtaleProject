package com.redtale.project.items;

import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.script.ScriptContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 非护甲基底的载体类（14 个基底里的武器 + 饰品）。**只有一层行为**：右键用药（D23）。
 *
 * <p>为什么要有这个类：{@code Item#use} 是<b>行为</b>，而行为不能进配置。
 * 14 个基底里有 4 件护甲用 {@code ArmorItem}（右键 = 穿戴，不能抢），其余 10 件都需要
 * 「右键 = 使用消耗品」这一个入口 —— 一个共享子类解决，不破坏「内容即数据组件」（基石 1）。
 *
 * <p>三条口径：
 * <ol>
 *   <li><b>服务端唯一执行点</b>：客户端只返回 {@code CONSUME} 让手臂动一下，一切副作用
 *       （加效果 / 加饱食度 / 冷却 / 扣物品 / 脚本）只在服务端跑。</li>
 *   <li><b>定义现查、不缓存</b>：按 {@code contentId} 每次去 {@code contentItems()} 捞定义，
 *       所以改 {@code use} 段 + {@code /redtale reload} 对存量物品立刻生效（不用 rev+1）。</li>
 *   <li><b>找不到定义就当普通物品</b>：{@code PASS}，不报错、不吞物品 —— 孤儿物品仍然可丢可存。</li>
 * </ol>
 */
public class ContentItem extends Item {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject/Use");

    private static final String ONLY_WHEN_HUNGRY = "only_when_hungry";

    public ContentItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 客户端不做任何判定：有没有 use 段、够不够饿都只有服务端知道
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        ConsumableSpec.Spec spec = specOf(stack);
        if (spec != null) {
            return useConsumable(serverPlayer, stack, spec);
        }
        RepairSpec.Spec repair = Repairer.specOf(stack);
        if (repair != null) {
            return useRepair(serverPlayer, stack, repair);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** 消耗品分支（D23 第一半）：效果 / 饱食 / 冷却 / 扣件 / 脚本。 */
    private InteractionResultHolder<ItemStack> useConsumable(ServerPlayer serverPlayer,
                                                             ItemStack stack, ConsumableSpec.Spec spec) {
        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (ONLY_WHEN_HUNGRY.equals(spec.consume()) && !serverPlayer.getFoodData().needsFood()) {
            return InteractionResultHolder.fail(stack);
        }

        applyEffects(serverPlayer, spec);
        if (spec.restoreFood() > 0) {
            serverPlayer.getFoodData().eat(spec.restoreFood(), (float) spec.saturation());
        }
        if (spec.cooldownTicks() > 0) {
            serverPlayer.getCooldowns().addCooldown(this, spec.cooldownTicks());
        }
        if (!spec.script().isEmpty()) {
            RedtaleCore.get().engine().runCalls(spec.script(), ScriptContext.ofPlayer(serverPlayer));
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 修理分支（D23 第二半）：修<b>副手那件</b>，副手空着则修<b>背包里损伤最重的可修物品</b>。
     *
     * <p>为什么不是「点一下就随便修一件」：右键必须<b>可预期</b>，而磨刀石是有代价的消耗品。
     * 「副手优先」给精确控制，「最破的那件」给不想切手的顺路便利，两条合起来一句话说得清。
     *
     * <p>失败一律 {@code PASS} 而不是 {@code FAIL}：目标不可修不等于「这次右键作废」。
     * 扣件只在 {@code APPLIED} 且 {@code consume_item} 时发生。
     */
    private InteractionResultHolder<ItemStack> useRepair(ServerPlayer serverPlayer, ItemStack stack,
                                                         RepairSpec.Spec spec) {
        Repairer.Target target = Repairer.pickTarget(serverPlayer, spec, stack);
        if (target == null) {
            notify(serverPlayer, "修理失败：没有可修的目标（放副手，或背包里带一件受损的可修物品）");
            return InteractionResultHolder.pass(stack);
        }
        Repairer.Result result = Repairer.apply(spec, target.stack());
        if (result.applied()) {
            if (spec.consumeItem() && !serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }
            serverPlayer.inventoryMenu.broadcastChanges();
            notify(serverPlayer, "已修好 " + target.stack().getHoverName().getString()
                    + " +" + result.repaired() + " 耐久（耐久损伤 " + result.before()
                    + " → " + result.after() + "）");
            return InteractionResultHolder.consume(stack);
        }
        notify(serverPlayer, "修理失败：" + Repairer.describe(result.status()));
        return InteractionResultHolder.pass(stack);
    }

    /** FakePlayer 没有 {@code connection}，直接发消息会 NPE（R15 踩过）：先判再发。 */
    private static void notify(ServerPlayer player, String message) {
        if (player.connection != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[赤物语] " + message));
        }
    }

    /** 物品上的内容定义 → {@code use} 段；任何一环缺失都返回 {@code null}（= 不是消耗品）。 */
    private static ConsumableSpec.Spec specOf(ItemStack stack) {
        ContentData data = stack.get(ContentComponents.CONTENT.get());
        if (data == null) {
            return null;
        }
        ItemContent definition = RedtaleCore.get().contentItems().get(data.contentId());
        return definition == null ? null : definition.consumable();
    }

    /**
     * 按 {@code chance} 逐条掷；{@code chance >= 1.0} 不掷（省一次随机数）。
     * 效果 id 不存在 / 不合法 → 记一次 WARN 并跳过这一条，不影响其它效果与扣物品。
     */
    private static void applyEffects(ServerPlayer player, ConsumableSpec.Spec spec) {
        if (spec.effects().isEmpty()) {
            return;
        }
        var effects = player.level().registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        for (ConsumableSpec.Effect effect : spec.effects()) {
            if (effect.chance() < 1.0 && player.getRandom().nextDouble() >= effect.chance()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(effect.id());
            if (id == null) {
                LOG.warn("消耗品效果 id 非法：{}（已跳过）", effect.id());
                continue;
            }
            Holder<MobEffect> holder = effects.getHolder(id).orElse(null);
            if (holder == null) {
                LOG.warn("消耗品效果 {} 不在效果注册表里（已跳过）", id);
                continue;
            }
            player.addEffect(new MobEffectInstance(holder, effect.duration(), effect.amplifier()));
        }
    }
}
