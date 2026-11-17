package com.redtale.project.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeBreakdown;
import com.redtale.project.core.attr.AttributeGroup;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.AttackSpeedApplier;
import com.redtale.project.core.attr.AttackSpeedRules;
import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.damage.DamageTrace;
import com.redtale.project.core.damage.MultiplierRules;
import com.redtale.project.core.math.AttackSpeedCurve;
import com.redtale.project.core.math.DamageRules;
import com.redtale.project.core.math.Settle;
import com.redtale.project.core.script.ScriptAction;
import com.redtale.project.core.script.ScriptContext;
import com.redtale.project.items.ContentTooltip;
import com.redtale.project.items.ItemContent;
import com.redtale.project.items.ItemFactory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * /redtale 命令树。全部要 op（permission 2）。
 */
public final class RedtaleCommand {

    private RedtaleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("redtale")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(RedtaleCommand::reload))
                .then(Commands.literal("status").executes(RedtaleCommand::status))
                .then(Commands.literal("list").executes(RedtaleCommand::list))
                .then(Commands.literal("fire")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(RedtaleCommand::fire)))
                .then(Commands.literal("speed")
                        .executes(RedtaleCommand::speed)
                        .then(Commands.literal("probe").executes(RedtaleCommand::speedProbe)))
                .then(Commands.literal("attr")
                        // /redtale attr           → 同 attr list
                        .executes(RedtaleCommand::attrList)
                        .then(Commands.literal("list").executes(RedtaleCommand::attrList))
                        .then(Commands.argument("attribute", StringArgumentType.string())
                                .executes(ctx -> attrOne(ctx, null))
                                // 控制台没有实体上下文，必须能指定目标才能离线验收
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> attrOne(ctx, EntityArgument.getEntity(ctx, "target"))))))
                .then(Commands.literal("damage")
                        .executes(RedtaleCommand::damage)
                        .then(Commands.literal("probe").executes(RedtaleCommand::damageProbe))
                        // /redtale damage hit probe [amount]：M5 的 damage 原语端到端（白值 → 乘区）
                        .then(Commands.literal("hit")
                                .then(Commands.literal("probe")
                                        .executes(ctx -> damageHitProbe(ctx, 10.0D))
                                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1D, 1.0e9D))
                                                .executes(ctx -> damageHitProbe(ctx,
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount"))))))
                        .then(Commands.literal("check")
                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                        .executes(RedtaleCommand::damageCheck))))
                .then(Commands.literal("crit")
                        .executes(RedtaleCommand::crit)
                        .then(Commands.literal("probe").executes(RedtaleCommand::critProbe)))
                .then(Commands.literal("items").executes(RedtaleCommand::items))
                .then(Commands.literal("whitening").executes(RedtaleCommand::whitening))
                .then(Commands.literal("give")
                        .then(Commands.argument("content", StringArgumentType.word())
                                .executes(ctx -> give(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                // /redtale content list（ContentListCommand 与本类同包，无需 import）
                .then(Commands.literal("content").then(Commands.literal("list").executes(ContentListCommand::list)))
                // /redtale content check（rev 漏加检测，T-0012；同包无需 import；Brigadier 按同名合并子节点，list 不受影响）
                .then(Commands.literal("content").then(Commands.literal("check").executes(ContentCheckCommand::check)))
                .then(Commands.literal("validate").executes(ValidateCommand::validate))
                // /redtale item <scan|sweep|upgrade>（ItemScanCommand / ItemUpgradeCommand 与本类同包，无需 import）
                .then(Commands.literal("item")
                        .then(Commands.literal("scan").executes(ItemScanCommand::scan))
                        // D33 的「敢写」那一半：给自己背包 + 末影箱跑一遍 ensure（T-0009）
                        .then(Commands.literal("upgrade").executes(ItemUpgradeCommand::upgrade))
                        // D33 第 ④ 个刷新点：手动重排一次全量刷新（reload 之后已自动排一次）
                        .then(Commands.literal("sweep").executes(RedtaleCommand::itemSweep)))
                .then(Commands.literal("tooltip")
                        .executes(ctx -> tooltip(ctx, null))
                        .then(Commands.argument("content", StringArgumentType.word())
                                .executes(ctx -> tooltip(ctx, StringArgumentType.getString(ctx, "content")))))
                // /redtale use probe [内容id]：D23 消耗品端到端（FakePlayer 真调 ItemStack#use）
                .then(Commands.literal("use")
                        .then(Commands.literal("probe")
                                .executes(ctx -> useProbe(ctx, null))
                                .then(Commands.argument("content", StringArgumentType.word())
                                        .executes(ctx -> useProbe(ctx, StringArgumentType.getString(ctx, "content"))))))
                // /redtale repair <slot>：管理员/铁匠 NPC 用的修理入口（D23 第二半）
                // /redtale repair probe [内容id]：磨刀石端到端（FakePlayer 主手工具 + 副手受损目标）
                .then(Commands.literal("repair")
                        .executes(ctx -> repairSlot(ctx, 0))
                        .then(Commands.literal("probe")
                                .executes(ctx -> repairProbe(ctx, null))
                                .then(Commands.argument("content", StringArgumentType.word())
                                        .executes(ctx -> repairProbe(ctx, StringArgumentType.getString(ctx, "content")))))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 40))
                                .executes(ctx -> repairSlot(ctx, IntegerArgumentType.getInteger(ctx, "slot")))))
                // /redtale socket …：孔位与宝石（M3 第 4 步），作用于**主手**那件物品
                .then(socketNode())
                // /redtale set [list|sync|info <id>]：套装件数与下发可见性（M3 第 6 步）
                .then(SetCommand.node())
                .then(MobCheckCommand.node())
                .then(MobSpawnCommand.node())
                // /redtale set check（套装引用与可达性校验，T-0016；同包无需 import；Brigadier 按同名 set 节点合并）
                .then(SetCheckCommand.node())
                // /redtale enchant list（已落盘附魔只读清单，T-0019；同包无需 import）
                .then(EnchantListCommand.node())
                // /redtale enchant obtain（三条可获取性 tag 只读对账，T-0023；同包无需 import）
                .then(EnchantObtainCommand.node())
                // /redtale skill [list|cast|probe|cd]（M5-A 引擎观测面与 v1 触发入口；同包无需 import）
                .then(SkillCommand.node()));
    }

    /**
     * 消耗品端到端探测（D23）：{@code FakePlayer} 真的走一次 {@code ItemStack#use}。
     *
     * <p>为什么不看代码/日志猜：右键路径有五个副作用（状态效果 / 饱食度 / 物品冷却 / 扣件 / 脚本），
     * 任何一个没接上都不会报错 —— 只会静默无效。这里把五项逐项打到屏幕上，再跑第二遍验冷却拦截。
     *
     * <p>饥饿度可以随便改：{@code FoodData#setFoodLevel} 是 public（{@code readAdditionalSaveData}
     * 才需要 NBT），所以两组（够饿 / 不饿）都能在同一进程里造出来。
     */
    private static int useProbe(CommandContext<CommandSourceStack> ctx, String contentId) {
        CommandSourceStack src = ctx.getSource();
        try {
            var core = RedtaleCore.get();
            ItemContent target = null;
            if (contentId != null) {
                target = core.contentItems().get(contentId);
                if (target == null) {
                    src.sendFailure(Component.literal("[redtale] 没有内容 '" + contentId + "'"));
                    return 0;
                }
                if (!target.isConsumable()) {
                    src.sendFailure(Component.literal("[redtale] " + contentId + " 没有 use 段（不是消耗品）"));
                    return 0;
                }
            } else {
                for (ItemContent candidate : core.contentItems().all()) {
                    if (candidate.isConsumable()) {
                        target = candidate;
                        break;
                    }
                }
                if (target == null) {
                    src.sendFailure(Component.literal("[redtale] 没有任何消耗品内容（items/*.json5 里缺 use 段）"));
                    return 0;
                }
            }

            // 落到 final 局部量：下面的 lambda 要捕获它（target 在 if/else 里被赋值，不是 effectively final）
            final ItemContent chosen = target;
            var level = src.getServer().overworld();
            if (level == null) {
                src.sendFailure(Component.literal("[redtale] 主世界还没加载"));
                return 0;
            }
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.removeAllEffects();
            player.getFoodData().setFoodLevel(0);
            player.getFoodData().setSaturation(0.0f);
            player.getFoodData().setExhaustion(0.0f);
            player.getCooldowns().tick();

            ItemStack stack = ItemFactory.build(chosen, null).copyWithCount(2);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            var spec = chosen.consumable();

            int foodBefore = player.getFoodData().getFoodLevel();
            // 饱和要在这里取：下面那几行是 lambda，评估时机在 eat() 之后
            double satBefore = player.getFoodData().getSaturationLevel();
            int countBefore = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            var result = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                    .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int foodAfter = player.getFoodData().getFoodLevel();
            int countAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            boolean cooling = player.getCooldowns().isOnCooldown(player.getItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND).getItem());
            int effectCount = player.getActiveEffects().size();

            src.sendSuccess(() -> Component.literal("[redtale] 消耗品探测 " + chosen.id()
                    + "（FakePlayer，服务端真调 ItemStack#use；返回值 " + result.getResult() + "）："), false);
            src.sendSuccess(() -> Component.literal("  use 段：效果 " + spec.effects().size()
                    + " 条 / 回复饱食 " + spec.restoreFood() + " / 饱和 " + spec.saturation()
                    + " / 冷却 " + spec.cooldownTicks() + "t / 脚本 " + spec.script().size()
                    + " 条 / consume=" + spec.consume()), false);
            src.sendSuccess(() -> Component.literal("  使用前：饱食 " + foodBefore
                    + " 饱和 " + satBefore + " 效果 0 条 手中 " + countBefore + " 件"), false);
            StringBuilder applied = new StringBuilder();
            for (var active : player.getActiveEffects()) {
                if (!applied.isEmpty()) {
                    applied.append(", ");
                }
                applied.append(active.getEffect().unwrapKey()
                                .map(key -> key.location().toString()).orElse("?"))
                        .append(' ').append(active.getDuration()).append("t×")
                        .append(active.getAmplifier());
            }
            int finalFoodAfter = foodAfter;
            int finalCountAfter = countAfter;
            boolean finalCooling = cooling;
            int finalEffects = effectCount;
            src.sendSuccess(() -> Component.literal("  使用后：饱食 " + finalFoodAfter
                    + " 效果 " + finalEffects + " 条（" + applied + "）手中 " + finalCountAfter
                    + " 件 冷却 " + (finalCooling ? "已进入" : "无")), false);

            // 第二遍：冷却期必须拦住（饱食与件数都不许变）
            int foodBefore2 = player.getFoodData().getFoodLevel();
            int countBefore2 = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                    .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int foodAfter2 = player.getFoodData().getFoodLevel();
            int countAfter2 = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            boolean blocked = foodBefore2 == foodAfter2 && countBefore2 == countAfter2;
            src.sendSuccess(() -> Component.literal("  冷却拦截：饱食 " + foodBefore2 + " → " + foodAfter2
                    + "，件数 " + countBefore2 + " → " + countAfter2
                    + "（" + (blocked ? "成立" : "不成立") + "）"), false);

            // 第三组（只有 only_when_hungry 才有意义）：吃饱了必须不生效、不扣件
            if ("only_when_hungry".equals(spec.consume())) {
                player.getCooldowns().tick();
                player.getCooldowns().removeCooldown(
                        player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getItem());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0f);
                int fullBefore = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
                player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                        .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
                int fullAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
                boolean hungryGate = fullBefore == fullAfter;
                src.sendSuccess(() -> Component.literal("  饱食门槛（consume=only_when_hungry，饱食 20/20）：件数 "
                        + fullBefore + " → " + fullAfter + "（" + (hungryGate ? "挡住" : "没挡住") + "）"), false);
                if (!hungryGate) {
                    src.sendSuccess(() -> Component.literal("  结论：饱食门槛 ✗ → 有项不过"), false);
                    return effectCount;
                }
            }

            boolean effectsOk = spec.effects().isEmpty() ? effectCount == 0 : effectCount > 0;
            boolean foodOk = spec.restoreFood() <= 0 || foodAfter > foodBefore;
            boolean consumeOk = countAfter == countBefore - 1;
            boolean cooldownOk = spec.cooldownTicks() <= 0 || cooling;
            src.sendSuccess(() -> Component.literal("  结论：效果 " + (effectsOk ? "✓" : "✗")
                    + " 饱食 " + (foodOk ? "✓" : "✗")
                    + " 扣件 " + (consumeOk ? "✓" : "✗")
                    + " 冷却 " + (cooldownOk ? "✓" : "✗")
                    + " 拦截 " + (blocked ? "✓" : "✗")
                    + (effectsOk && foodOk && consumeOk && cooldownOk && blocked
                            ? " → 五项全过" : " → 有项不过")), false);
            return effectCount;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 消耗品探测失败：" + e));
            return 0;
        }
    }

    /**
     * {@code /redtale repair <slot>}：管理员 / 铁匠 NPC 用的修理入口（D23 第二半）。
     *
     * <p>结算走 {@link com.redtale.project.items.Repairer#apply}，与右键路径**同一段代码** ——
     * 一份数值两处入口最容易漂移，这里刻意不给命令留「管理员版逻辑」。
     * 工具取背包里**第一件**带 {@code repair} 段的内容物品（排除目标格自身），
     * 是否扣件按定义的 {@code consume_item}（创造模式不扣）。
     */
    private static int repairSlot(CommandContext<CommandSourceStack> ctx, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        var inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            src.sendFailure(Component.literal("[redtale] 槽位 " + slot + " 越界（0~"
                    + (inventory.getContainerSize() - 1) + "）"));
            return 0;
        }
        ItemStack target = inventory.getItem(slot);
        if (target.isEmpty()) {
            src.sendFailure(Component.literal("[redtale] 第 " + slot + " 格是空的"));
            return 0;
        }

        int toolSlot = -1;
        com.redtale.project.items.RepairSpec.Spec spec = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == slot) {
                continue;
            }
            com.redtale.project.items.RepairSpec.Spec candidate =
                    com.redtale.project.items.Repairer.specOf(inventory.getItem(i));
            if (candidate != null) {
                toolSlot = i;
                spec = candidate;
                break;
            }
        }
        if (spec == null) {
            src.sendFailure(Component.literal("[redtale] 背包里没有修理物品（带 repair 段的内容）"));
            return 0;
        }

        com.redtale.project.items.Repairer.Result result =
                com.redtale.project.items.Repairer.apply(spec, target);
        if (!result.applied()) {
            String line = "[redtale] 修理失败：" + com.redtale.project.items.Repairer.describe(result.status())
                    + "（耐久 " + result.before() + " → " + result.after() + "，物品未变）";
            src.sendFailure(Component.literal(line));
            return 0;
        }

        ItemStack tool = inventory.getItem(toolSlot);
        boolean consumed = false;
        if (spec.consumeItem() && !player.getAbilities().instabuild) {
            tool.shrink(1);
            consumed = true;
        }
        player.inventoryMenu.broadcastChanges();
        int repaired = result.repaired();
        int before = result.before();
        int after = result.after();
        String line = "[redtale] 已修 " + target.getHoverName().getString()
                + "（第 " + slot + " 格）：耐久损伤 " + before + " → " + after
                + "（+" + repaired + "）· 工具第 " + toolSlot + " 格" + (consumed ? "已消耗 1" : "未消耗");
        src.sendSuccess(() -> Component.literal(line), false);
        return repaired;
    }

    /**
     * 磨刀石端到端探测（D23 第二半，M2 出口的最后一块）：{@code FakePlayer} 真的右键一次。
     *
     * <p>三组对照都要过，缺一条就说明「结算与扣件之间有漏洞」：
     * <ol>
     *   <li>受损目标 → 修好了、工具 −1</li>
     *   <li>满耐久目标 → 一件都不扣（失败不许收钱）</li>
     *   <li>不可损坏目标（原版石头）→ 一件都不扣</li>
     * </ol>
     * 走的是 {@code ItemStack#use}，即玩家右键的**同一条路径**，不是直接调结算函数。
     */
    private static int repairProbe(CommandContext<CommandSourceStack> ctx, String contentId) {
        CommandSourceStack src = ctx.getSource();
        try {
            var core = RedtaleCore.get();
            ItemContent tool = null;
            if (contentId != null) {
                tool = core.contentItems().get(contentId);
                if (tool == null) {
                    src.sendFailure(Component.literal("[redtale] 没有内容 '" + contentId + "'"));
                    return 0;
                }
                if (!tool.isRepairItem()) {
                    src.sendFailure(Component.literal("[redtale] " + contentId + " 没有 repair 段（不是修理物品）"));
                    return 0;
                }
            } else {
                for (ItemContent candidate : core.contentItems().all()) {
                    if (candidate.isRepairItem()) {
                        tool = candidate;
                        break;
                    }
                }
                if (tool == null) {
                    src.sendFailure(Component.literal("[redtale] 没有任何修理物品内容（items/*.json5 里缺 repair 段）"));
                    return 0;
                }
            }
            final ItemContent chosenTool = tool;

            ItemContent targetContent = core.contentItems().get("demo_longsword");
            if (targetContent == null || targetContent.durability() <= 0) {
                targetContent = null;
                for (ItemContent candidate : core.contentItems().all()) {
                    if (candidate.durability() > 0) {
                        targetContent = candidate;
                        break;
                    }
                }
            }
            if (targetContent == null) {
                src.sendFailure(Component.literal("[redtale] 找不到有耐久的内容当修理目标"));
                return 0;
            }
            final ItemContent chosenTarget = targetContent;

            var level = src.getServer().overworld();
            if (level == null) {
                src.sendFailure(Component.literal("[redtale] 主世界还没加载"));
                return 0;
            }
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            var spec = chosenTool.repair();
            // FakePlayer 默认创造模式（instabuild），不扣件 —— 探测要验「扣件」就必须先按生存模式跑
            boolean originalInstabuild = player.getAbilities().instabuild;
            player.getAbilities().instabuild = false;
            src.sendSuccess(() -> Component.literal("[redtale] 修理探测 " + chosenTool.id()
                    + "（FakePlayer 主手工具 + 副手目标，服务端真调 ItemStack#use）："), false);
            src.sendSuccess(() -> Component.literal("  repair 段：mode " + spec.mode()
                    + " / amount " + spec.amount() + " / percent " + spec.percent()
                    + " / targets " + spec.targets().size() + " 项 / consume_item " + spec.consumeItem()), false);

            // ---- 第 1 组：受损目标 ----
            ItemStack damaged = ItemFactory.build(chosenTarget);
            int max = damaged.getMaxDamage();
            int startDamage = Math.min(max - 1, Math.max(1, max / 2));
            damaged.setDamageValue(startDamage);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    ItemFactory.build(chosenTool).copyWithCount(2));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, damaged);
            int toolBefore = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            // 副手读 inventory.offhand：FakePlayer 上 getItemInHand(OFF_HAND) 恒返回 air（已实测）
            var r1 = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                    .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int afterDamage = player.getInventory().offhand.get(0).getDamageValue();
            int toolAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            int expected = Math.min(spec.amount(), startDamage);
            boolean repairedOk = afterDamage == startDamage - expected;
            boolean consumedOk = toolAfter == toolBefore - 1;
            String l1 = "  受损目标（" + chosenTarget.id() + "，损伤 " + startDamage + "/" + max + "）：损伤 "
                    + startDamage + " → " + afterDamage + "（应 −" + expected + "），工具 "
                    + toolBefore + " → " + toolAfter
                    + "（修好 " + (repairedOk ? "✓" : "✗") + "，扣件 " + (consumedOk ? "✓" : "✗")
                    + "，返回 " + r1.getResult() + "）";
            src.sendSuccess(() -> Component.literal(l1), false);

            // ---- 第 2 组：满耐久（不许扣件） ----
            ItemStack full = ItemFactory.build(chosenTarget);
            full.setDamageValue(0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    ItemFactory.build(chosenTool).copyWithCount(2));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, full);
            int fullToolBefore = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                    .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int fullToolAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            boolean fullOk = fullToolAfter == fullToolBefore
                    && player.getInventory().offhand.get(0).getDamageValue() == 0;
            String l2 = "  满耐久目标：工具 " + fullToolBefore + " → " + fullToolAfter
                    + "（" + (fullOk ? "未扣件 ✓" : "被扣了 ✗") + "）";
            src.sendSuccess(() -> Component.literal(l2), false);

            // ---- 第 3 组：不可损坏目标（原版石头，不许扣件） ----
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    ItemFactory.build(chosenTool).copyWithCount(2));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                    new ItemStack(net.minecraft.world.item.Items.STONE));
            int stoneToolBefore = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                    .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int stoneToolAfter = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount();
            boolean stoneOk = stoneToolAfter == stoneToolBefore;
            String l3 = "  不可损坏目标（原版石头）：工具 " + stoneToolBefore + " → " + stoneToolAfter
                    + "（" + (stoneOk ? "未扣件 ✓" : "被扣了 ✗") + "）";
            src.sendSuccess(() -> Component.literal(l3), false);

            boolean allOk = repairedOk && consumedOk && fullOk && stoneOk;
            src.sendSuccess(() -> Component.literal("  结论：修好 " + (repairedOk ? "✓" : "✗")
                    + " 扣件 " + (consumedOk ? "✓" : "✗")
                    + " 满耐久不扣 " + (fullOk ? "✓" : "✗")
                    + " 不可修不扣 " + (stoneOk ? "✓" : "✗")
                    + (allOk ? " → 全过" : " → 有项不过")), false);
            // 清场：别让探测留下手里那两件东西，并把创造模式开关还原
            player.getAbilities().instabuild = originalInstabuild;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return allOk ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 修理探测失败：" + e));
            return 0;
        }
    }

    /** {@code /redtale item sweep}：手动重排一次 D33 第 ④ 个刷新点（分帧，不阻塞命令返回）。 */    private static int itemSweep(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int queued = com.redtale.project.core.hook.UpgradeSweep.schedule(src.getServer());
        if (queued == 0) {
            src.sendSuccess(() -> Component.literal("[redtale] " + com.redtale.project.core.hook.UpgradeSweep.status()), true);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] 全量刷新已排程：" + queued
                + " 个区块（每 tick 8 块，完成后日志有汇总）"), true);
        return queued;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        RedtaleCore.ReloadResult result = RedtaleCore.get().reload();
        CommandSourceStack src = ctx.getSource();
        if (result.ok()) {
            // D33 第 ④ 个刷新点：配置换完代之后，把已加载世界分帧刷一遍（docs/ITEM-UPDATE.md §4.1 ④）。
            // 只挂在「管理员显式 reload」上：启动期那次 reload 没有「配置换了」这件事，不该扫世界。
            int swept = com.redtale.project.core.hook.UpgradeSweep.schedule(src.getServer());
            src.sendSuccess(() -> Component.literal("[redtale] " + result.summary()
                    + (swept > 0 ? "；全量刷新已排程 " + swept + " 个区块" : "")), true);
            return 1;
        }
        src.sendFailure(Component.literal("[redtale] " + result.summary()));
        for (String line : result.errors()) {
            src.sendFailure(Component.literal("  - " + line));
        }
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        RedtaleCore core = RedtaleCore.get();
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(
                "[redtale] 状态：" + (core.ready() ? "已就绪" : "未就绪")
                        + " · 脚本 " + core.engine().size() + " 条"
                        + " · 原语 effect " + core.registries().effects().size()
                        + " / trigger " + core.registries().triggers().size()
                        + " / condition " + core.registries().conditions().size()
                        + " · 属性 " + RedtaleAttributes.describe()
                        + " · 内容 " + core.contentItems().size() + " 件装备 / "
                        + com.redtale.project.items.RedtaleItems.count() + " 个基底物品"), false);
        if (!core.lastError().isEmpty()) {
            src.sendFailure(Component.literal("[redtale] 最近一次重载有问题：" + core.lastError()));
            for (String line : core.lastErrorLines()) {
                src.sendFailure(Component.literal("  - " + line));
            }
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        RedtaleCore core = RedtaleCore.get();
        CommandSourceStack src = ctx.getSource();
        if (core.engine().size() == 0) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有已载入的脚本"), false);
            return 1;
        }
        for (ScriptAction action : core.engine().actions()) {
            String line = "  " + action.id() + " · " + action.trigger()
                    + " · " + action.source();
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int fire(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        ScriptContext scriptCtx = player != null
                ? ScriptContext.ofPlayer(player)
                : ScriptContext.ofServer(src.getServer());
        boolean found = RedtaleCore.get().engine().fireById(id, scriptCtx);
        if (!found) {
            src.sendFailure(Component.literal("[redtale] 没有脚本 '" + id + "'"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] 已执行 " + id), false);
        return 1;
    }

    // ------------------------------------------------------------------ speed

    /**
     * 攻速自检（D18 / D19 验收件）：规则内容 + mixin 是否真的注入 + 样例换算。
     *
     * <p>存在的理由：攻速是唯一「玩家体感与代码不一致」过的属性（1.20.1 上急迫被当攻速卖）。
     * 一条命令把「配置写了什么 / mixin 生效没有 / 换算出来几 tick」三件事一次说完，
     * 不用翻日志、不用猜。
     */
    private static int speed(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        AttackSpeedRules rules = core.attackSpeedRules();

        src.sendSuccess(() -> Component.literal("[redtale] 攻速规则：" + rules.describe()), false);
        src.sendSuccess(() -> Component.literal("  伤害规则：" + core.damageRules().describe()), false);
        src.sendSuccess(() -> Component.literal("  mixin：" + mixinStatus()), false);

        src.sendSuccess(() -> Component.literal("  样例（次/秒 → 实际 tick，soft_cap "
                + (rules.softCap() <= 0 ? "关闭" : AttributeBreakdown.num(rules.softCap())) + "）："), false);
        for (double rawPerSecond : new double[]{0.8, 1.6, 2.4, 4.0, 8.0, 1024.0}) {
            double eff = AttackSpeedCurve.effectivePerSecond(rawPerSecond, rules.softCap());
            double delay = AttackSpeedCurve.delayTicks(eff, rules.minDelayTicks());
            String line = "    " + AttributeBreakdown.num(rawPerSecond) + " → "
                    + AttributeBreakdown.num(delay) + " tick"
                    + (eff != rawPerSecond ? "（饱和后 " + AttributeBreakdown.num(eff) + "）" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }

        ServerPlayer player = src.getPlayer();
        if (player != null) {
            int written = AttackSpeedApplier.apply(player, rules);
            src.sendSuccess(() -> Component.literal("  已对你重算攻速 modifier，本次写入 " + written + " 条"), false);
            AttributeBreakdown breakdown = AttributeBreakdown.of(player,
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                    AttributeSpecs.resolve("minecraft:generic.attack_speed"));
            if (breakdown != null) {
                for (String line : breakdown.lines()) {
                    src.sendSuccess(() -> Component.literal(line), false);
                }
            }
        }
        return 1;
    }

    /**
     * 反射确认 mixin 真的被应用到 {@code Player} 字节码上。
     *
     * <p>@Inject 的处理方法会被合并进目标类，但 Mixin 会给它套一层前缀
     * （实测 21.1.257 + Mixin 0.8.7：{@code handler$zdi000$redtale_project$redtale$applyAttackSpeedRules}）。
     * 所以判据是「方法名里含 {@code redtale}」而不是「以 redtale$ 开头」——
     * 这正是第一次写成 {@code startsWith} 时报「未注入」的原因。
     */
    private static String mixinStatus() {
        for (java.lang.reflect.Method m : net.minecraft.world.entity.player.Player.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase(java.util.Locale.ROOT).contains("redtale")) {
                return "已注入 Player#getCurrentItemAttackStrengthDelay（命中 " + m.getName() + "）";
            }
        }
        return "未注入（Player 里找不到含 redtale 的方法 → 检查 redtale_project.mixins.json / mods.toml）";
    }

    /**
     * 用 {@code FakePlayer} 做端到端验证：**真的调一次被 mixin 改过的
     * {@code getCurrentItemAttackStrengthDelay()}**，看返回值是否被地板钳住。
     *
     * <p>为什么需要它：测试服没有客户端，没有在线玩家就碰不到 {@code Player} 实例，
     * 「mixin 生效」就只能靠日志与反射猜。FakePlayer 由 NeoForge 提供、不需要客户端，
     * 是唯一能在纯服务端跑通玩家方法的路子。
     *
     * <p>做法是直接改 {@code ATTACK_SPEED} 的基值（不挂 modifier、不碰物品），
     * 跑完还原成 4.0，避免污染。
     */
    private static int speedProbe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        var level = src.getServer().overworld();
        if (level == null) {
            src.sendFailure(Component.literal("[redtale] 主世界还没加载"));
            return 0;
        }
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var instance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (instance == null) {
            src.sendFailure(Component.literal("[redtale] FakePlayer 没有 attack_speed 属性"));
            return 0;
        }
        double original = instance.getBaseValue();
        try {
            src.sendSuccess(() -> Component.literal("[redtale] mixin 端到端探测（改 attack_speed 基值，读真实 delay）："), false);
            for (double base : new double[]{1.6, 4.0, 8.0, 1024.0, 0.4}) {
                instance.setBaseValue(base);
                double expected = 20.0 / base;
                double actual = player.getCurrentItemAttackStrengthDelay();
                double floor = Math.max(RedtaleCore.get().attackSpeedRules().minDelayTicks(),
                        com.redtale.project.core.math.AttackSpeedCurve.ABSOLUTE_MIN_DELAY);
                String verdict;
                if (Math.abs(actual - expected) < 1e-4) {
                    verdict = "原样";
                } else if (Math.abs(actual - floor) < 1e-4) {
                    verdict = "触地板 " + AttributeBreakdown.num(floor) + " tick";
                } else {
                    verdict = "被 soft_cap 压到 " + AttributeBreakdown.num(actual) + " tick";
                }
                String line = "  attack_speed " + AttributeBreakdown.num(base) + " → 理论 "
                        + AttributeBreakdown.num(expected) + " tick，实际 "
                        + AttributeBreakdown.num(actual) + " tick（" + verdict + "）";
                src.sendSuccess(() -> Component.literal(line), false);
            }
        } finally {
            instance.setBaseValue(original);
        }

        // ---- 急迫 → 攻速（D18/D19 的端到端）：真给效果、真写 modifier、真读出 delay
        try {
            AttackSpeedRules rules = RedtaleCore.get().attackSpeedRules();
            src.sendSuccess(() -> Component.literal("  急迫探测（规则：" + rules.describe() + "）："), false);
            double before = instance.getValue();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DIG_SPEED, 600, 1, false, false));
            String applied = "写入 " + AttackSpeedApplier.apply(player, rules) + " 条";
            double withHaste = instance.getValue();
            double delayWith = player.getCurrentItemAttackStrengthDelay();
            String l1 = "    急迫 II + " + applied + "：" + AttributeBreakdown.num(before) + " → "
                    + AttributeBreakdown.num(withHaste) + "（期望 ×1.2 = "
                    + AttributeBreakdown.num(before * 1.2) + "），实际 delay "
                    + AttributeBreakdown.num(delayWith) + " tick";
            src.sendSuccess(() -> Component.literal(l1), false);
            // 诊断：把此刻 ATTACK_SPEED 上的所有 modifier 原样列出来（谁贡献的那 20%）
            for (var mod : instance.getModifiers()) {
                String line = "      [" + mod.id() + "] " + mod.operation() + " " + mod.amount();
                src.sendSuccess(() -> Component.literal(line), false);
            }
            for (var eff : player.getActiveEffects()) {
                String line = "      effect " + eff.getDescriptionId() + " amp=" + eff.getAmplifier();
                src.sendSuccess(() -> Component.literal(line), false);
            }

            player.removeEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
            AttackSpeedApplier.apply(player, rules);
            double after = instance.getValue();
            String l2 = "    移除急迫后：" + AttributeBreakdown.num(after)
                    + (Math.abs(after - before) < 1e-9 ? "（已还原，无残留加成 ✓）" : "（有残留，需排查 ✗）");
            src.sendSuccess(() -> Component.literal(l2), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 急迫探测失败：" + e));
        }
        return 1;
    }

    // ------------------------------------------------------------------ attr

    /**
     * 列出 80 条属性（预留 16 条按文档不列）并打印当前实体上的值。
     *
     * <p>没有实体上下文（控制台）时只打印 id 表与基值——数值必须挂在实体上才有意义。
     */
    private static int attrList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        LivingEntity entity = src.getEntity() instanceof LivingEntity living ? living : null;

        AttributeGroup current = null;
        int shown = 0;
        for (AttributeSpec spec : AttributeSpecs.ALL) {
            if (spec.reserved()) {
                continue;
            }
            if (spec.group() != current) {
                current = spec.group();
                String header = "—— " + current.zh() + " ——";
                src.sendSuccess(() -> Component.literal(header), false);
            }
            Holder<Attribute> holder = RedtaleAttributes.holder(spec);
            String value;
            if (holder == null) {
                value = "未注册";
            } else if (entity != null) {
                AttributeBreakdown b = AttributeBreakdown.of(entity, holder, spec);
                value = b == null ? "该实体无此属性"
                        : AttributeBreakdown.num(b.value()) + "（" + b.entries().size() + " 层）";
            } else {
                // 没有实体时显示注册表里的真实默认值（原版行的默认值不是 0，例如 attack_speed = 4）
                value = "默认 " + AttributeBreakdown.num(holder.value().getDefaultValue());
            }
            String line = "  " + spec.zh() + "  " + spec.id() + "  =  " + value;
            src.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
        int total = shown;
        src.sendSuccess(() -> Component.literal("[redtale] 共 " + total + " 条（另有 "
                + (AttributeSpecs.ALL.size() - total) + " 条预留不列）"
                + (entity == null ? " · 当前无实体上下文，未显示实际值" : " · 实体：" + entity.getName().getString())), false);
        return 1;
    }

    /** 单个属性的来源拆解（D18 验收项）。 */
    private static int attrOne(CommandContext<CommandSourceStack> ctx, Entity target) {
        String token = StringArgumentType.getString(ctx, "attribute");
        CommandSourceStack src = ctx.getSource();

        AttributeSpec spec = AttributeSpecs.resolve(token);
        if (spec == null) {
            src.sendFailure(Component.literal("[redtale] 没有属性 '" + token
                    + "'（试 /redtale attr list）"));
            return 0;
        }
        Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            src.sendFailure(Component.literal("[redtale] 属性 '" + spec.id()
                    + "' 在注册表里找不到（原版 ID 可能写错了）"));
            return 0;
        }

        Entity resolved = target != null ? target : src.getEntity();
        if (!(resolved instanceof LivingEntity living)) {
            src.sendFailure(Component.literal("[redtale] 需要活体实体上下文：控制台请追加目标，"
                    + "例 /redtale attr " + spec.path() + " @e[type=zombie,limit=1]"));
            return 0;
        }

        AttributeBreakdown breakdown = AttributeBreakdown.of(living, holder, spec);
        if (breakdown == null) {
            src.sendFailure(Component.literal("[redtale] " + living.getName().getString()
                    + " 没有属性 " + spec.id() + "（这类实体本来就没有该属性，例如怪物没有 attack_speed；"
                    + "也可能是属性没挂到该 EntityType 上）"));
            return 0;
        }

        List<String> lines = breakdown.lines();
        src.sendSuccess(() -> Component.literal("[redtale] " + living.getName().getString()
                + " · " + spec.id()), false);
        for (String line : lines) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ damage

    /**
     * L2 乘区自检（M1 验收件）：规则内容 + 白名单 + 最近几次真实结算。
     *
     * <p>与 {@code /redtale speed} 同一思路：把「配置写了什么 / 实际乘了多少 / 谁被放行」
     * 三条一次说完，不用翻日志。
     */
    private static int damage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        DamageRules rules = RedtaleCore.get().damageRules();
        MultiplierRules multiplier = rules.multiplier();

        src.sendSuccess(() -> Component.literal("[redtale] 伤害规则：" + rules.describe()), false);
        src.sendSuccess(() -> Component.literal("  乘区：" + multiplier.describe()), false);
        src.sendSuccess(() -> Component.literal("  观测：进场 " + DamageTrace.seen()
                + " 次 · 白名单内 " + DamageTrace.inWhitelist()
                + " 次 · 实际改值 " + DamageTrace.changed() + " 次"), false);
        String pass = DamageTrace.lastPassthrough();
        if (!"-".equals(pass)) {
            src.sendSuccess(() -> Component.literal("  最近一次放行：" + pass + "（别人的伤害，原样通过）"), false);
        }
        List<Double> passAmounts = DamageTrace.recentPassthrough();
        if (!passAmounts.isEmpty()) {
            // 数值对账用（R22）：附魔/词条到底有没有进结算，看这串进场值差多少
            src.sendSuccess(() -> Component.literal("  最近放行进场值（旧→新）：" + passAmounts), false);
        }

        List<DamageTrace.Entry> recent = DamageTrace.recent();
        if (recent.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  （还没有白名单内的伤害事件：用 "
                    + "/damage <目标> <数值> redtale_project:<类型> [by <攻击者>] 触发）"), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("  最近 " + recent.size() + " 次结算："), false);
        for (DamageTrace.Entry e : recent) {
            String line = "    " + e.damageType() + "  " + AttributeBreakdown.num(e.base())
                    + " × " + AttributeBreakdown.num(e.factor()) + " = "
                    + AttributeBreakdown.num(e.value())
                    + "（百分比池 " + AttributeBreakdown.num(e.percent()) + "）"
                    + (e.overflow() ? " · 碰 1e9 硬顶" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * 纯离线演算：给定进场值与百分比，打印 {@code settle()} 的结果与因子。
     *
     * <p>没有玩家在线 / 没有客户端时，这是唯一能一眼看到「因子怎么来的」的入口；
     * 与实打走的是同一个 {@link Settle#settle}。
     */
    private static int damageProbe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        DamageRules rules = RedtaleCore.get().damageRules();
        src.sendSuccess(() -> Component.literal("[redtale] settle() 演算（闸门："
                + (rules.caps().percentLimited() || rules.caps().damageLimited() ? "已开启" : "全关")
                + "，与实打同一份实现）："), false);
        for (double base : new double[]{10.0, 100.0}) {
            for (double percent : new double[]{0.0, 0.2, 1.0}) {
                Settle.Result r = Settle.settle(base, percent, List.of(), rules.caps(), rules.sanity());
                double factor = base == 0 ? 1.0 : r.value() / base;
                String line = "  进场 " + AttributeBreakdown.num(base)
                        + " · 百分比池 " + AttributeBreakdown.num(percent)
                        + " → " + AttributeBreakdown.num(r.value())
                        + "（×" + AttributeBreakdown.num(factor) + "）";
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    /**
     * {@code /redtale damage hit probe [amount]}：{@code damage} 原语的端到端探测（M5-A）。
     *
     * <p>验的是**三层模型**这条链，而不是「打出去了」：
     * <ol>
     *   <li>A 组：白值直接进场，攻击者零属性 → 掉血量 = 白值（原语只给白值，不自己算倍率）</li>
     *   <li>B 组：攻击者 {@code physical_damage +0.25} → 掉血 = 白值 × 1.25（倍率由 L2 乘区乘）</li>
     *   <li>C 组：{@code skill_magical} + {@code magical_damage +0.5} → ×1.5（分轴，R26）</li>
     *   <li>D 组：{@code skill_magical} 但只堆物理 → ×1.0（反证：法术不吃物理百分比）</li>
     * </ol>
     * A 组若等于「白值×属性」，说明原语自己乘了；B 组若等于白值，说明 {@code DamageLayer} 没接上。
     * 两组一起才卡得住 D12。
     *
     * <p>目标用一只 {@code MAX_HEALTH = 1000} 的牛（不还手、设 {@code NoAI}），每次打之前把血与
     * {@code invulnerableTime} 归零 —— 否则 1.21 的 10 tick 无敌帧会让第二次命中变成 0。
     * 攻击者用 {@code FakePlayer}（纯服务端，不需要客户端）。
     */
    private static int damageHitProbe(CommandContext<CommandSourceStack> ctx, double amount) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        var level = src.getServer().overworld();
        if (level == null) {
            src.sendFailure(Component.literal("[redtale] 主世界还没加载"));
            return 0;
        }
        var fp = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        net.minecraft.world.entity.animal.Cow cow = net.minecraft.world.entity.EntityType.COW.create(level);
        if (cow == null) {
            src.sendFailure(Component.literal("[redtale] 造不出探测目标（EntityType.COW.create 返回 null）"));
            return 0;
        }
        cow.moveTo(fp.getX() + 1.0D, fp.getY(), fp.getZ(), 0.0F, 0.0F);
        var maxHealth = cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(1000.0D);
        }
        cow.setHealth(1000.0F);
        cow.setNoAi(true);
        if (!level.addFreshEntity(cow)) {
            src.sendFailure(Component.literal("[redtale] 探测目标没能进入世界"));
            return 0;
        }

        net.minecraft.resources.ResourceLocation physId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("redtale_project", "probe/physical");
        net.minecraft.resources.ResourceLocation magId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("redtale_project", "probe/magical");
        Holder<Attribute> phys = RedtaleAttributes.holder(AttributeSpecs.byId("physical_damage"));
        Holder<Attribute> mag = RedtaleAttributes.holder(AttributeSpecs.byId("magical_damage"));
        if (phys == null || mag == null) {
            src.sendFailure(Component.literal("[redtale] physical_damage / magical_damage 未注册"));
            return 0;
        }
        var physInst = fp.getAttribute(phys);
        var magInst = fp.getAttribute(mag);
        if (physInst == null || magInst == null) {
            src.sendFailure(Component.literal("[redtale] FakePlayer 上没有这两条属性（EntityAttributeModificationEvent 没挂上？）"));
            return 0;
        }

        int bad = 0;
        try {
            src.sendSuccess(() -> Component.literal("[redtale] damage 原语端到端（白值 " + AttributeBreakdown.num(amount)
                    + "，目标：牛 1000 血 / NoAI，攻击者：FakePlayer）："), false);
            bad += hitOnce(src, level, fp, cow, amount, null, 1.0D, "A 白值 · 零属性");
            physInst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    physId, 0.25D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            bad += hitOnce(src, level, fp, cow, amount, null, 1.25D, "B 白值 · physical_damage +0.25");
            bad += hitOnce(src, level, fp, cow, amount, "redtale_project:skill_magical", 1.0D,
                    "D 法术类型 · 只堆物理（应不吃）");
            magInst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    magId, 0.5D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            bad += hitOnce(src, level, fp, cow, amount, "redtale_project:skill_magical", 1.5D,
                    "C 法术类型 · magical_damage +0.5");
            // 不存在的类型：应回缺省物理轴（= 吃 physical 的 0.25，不吃 magical）
            bad += hitOnce(src, level, fp, cow, amount, "redtale_project:no_such_type", 1.25D,
                    "E 不存在的类型 · 回缺省物理轴");
        } finally {
            physInst.removeModifier(physId);
            magInst.removeModifier(magId);
            cow.discard();
        }
        int failures = bad;
        src.sendSuccess(() -> Component.literal("[redtale] 五组里 " + (5 - failures) + " 组符合预期"
                + (failures == 0 ? " ✓" : "（" + failures + " 组不符，见上面标记）")), false);
        return failures == 0 ? 1 : 0;
    }

    /** 打一次并核对掉血量；返回 0 = 符合预期，1 = 不符。 */
    private static int hitOnce(CommandSourceStack src, net.minecraft.server.level.ServerLevel level,
                               LivingEntity attacker, LivingEntity target, double amount,
                               String damageType, double expectedFactor, String label) {
        // 无敌帧归零：1.21 的 10 tick 无敌窗口会让连续探测的第二下变成 0
        target.invulnerableTime = 0;
        target.setHealth(1000.0F);
        com.google.gson.JsonObject args = new com.google.gson.JsonObject();
        args.addProperty("amount", amount);
        if (damageType != null) {
            args.addProperty("damage_type", damageType);
        }
        List<com.redtale.project.core.script.ScriptCall> calls =
                List.of(new com.redtale.project.core.script.ScriptCall("damage", args));
        double before = target.getHealth();
        int ran = RedtaleCore.get().engine().runCalls(calls, ScriptContext.ofEntity(attacker, target));
        double dealt = before - target.getHealth();
        double expected = amount * expectedFactor;
        boolean ok = ran == 1 && Math.abs(dealt - expected) < 1.0e-3D;
        String line = (ok ? "  ✓ " : "  ✗ ") + label + "：白值 " + AttributeBreakdown.num(amount)
                + " → 实际掉血 " + AttributeBreakdown.num(dealt)
                + "（期望 " + AttributeBreakdown.num(expected) + "，×" + AttributeBreakdown.num(expectedFactor) + "）";
        src.sendSuccess(() -> Component.literal(line), false);
        return ok ? 0 : 1;
    }

    /** 查一个伤害类型会不会被我们乘（白名单判定，只读配置，不产生伤害）。 */
    private static int damageCheck(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.resources.ResourceLocation type = ResourceLocationArgument.getId(ctx, "type");
        CommandSourceStack src = ctx.getSource();
        MultiplierRules multiplier = RedtaleCore.get().damageRules().multiplier();
        boolean applies = multiplier.appliesTo(type.toString());
        String verdict = applies ? "会乘（走我们的 L2 因子）" : "放行（不改值，仅观测）";
        src.sendSuccess(() -> Component.literal("[redtale] " + type + " → " + verdict), false);
        return applies ? 1 : 0;
    }

    // ------------------------------------------------------------------ crit

    /**
     * 暴击自检（M2）：规则内容 + 判定计数 + 最近几次判定的进出倍率。
     *
     * <p>与 {@code /redtale speed}、{@code /redtale damage} 同一思路 —— 暴击是最容易
     * 「玩家说暴了/没暴」而服务端无从对账的一环，所以把「配置怎么写的 / 掷没掷中 /
     * 倍率从多少改到多少」直接打出来。
     */
    private static int crit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var rules = RedtaleCore.get().critRules();
        src.sendSuccess(() -> Component.literal("[redtale] 暴击规则：" + rules.describe()), false);
        src.sendSuccess(() -> Component.literal("  观测：判定 " + com.redtale.project.core.damage.CritLayer.seen()
                + " 次 · 暴击 " + com.redtale.project.core.damage.CritLayer.crits()
                + " 次（其中强制 " + com.redtale.project.core.damage.CritLayer.forced() + " 次）"), false);

        List<com.redtale.project.core.damage.CritLayer.Entry> recent =
                com.redtale.project.core.damage.CritLayer.recent();
        if (recent.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  （还没有判定：玩家平砍一次，或跑 /redtale crit probe）"), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("  最近 " + recent.size() + " 次判定（进场倍率 → 出场倍率）："), false);
        for (var e : recent) {
            String line = "    暴击率 " + AttributeBreakdown.num(e.chance())
                    + " · 倍率属性 " + AttributeBreakdown.num(e.power())
                    + " · 原版暴击 " + (e.vanillaCrit() ? "是" : "否")
                    + " · 掷中 " + (e.rolled() ? "是" : "否")
                    + " · ×" + AttributeBreakdown.num(e.inMultiplier())
                    + (e.changed() ? " → ×" + AttributeBreakdown.num(e.outMultiplier()) : "（未改）");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * 暴击端到端探测：{@code FakePlayer} 真打一只木桩僵尸，只改暴击率/倍率两个属性的基值，
     * 读木桩掉血 —— 这是**纯服务端**唯一能证「{@code setCriticalHit}/{@code setDamageMultiplier}
     * 到底被 {@code Player#attack} 采纳了没有」的办法。
     *
     * <p>四组对照（攻击力固定、木桩护甲 0、血量 1000）：
     * <pre>
     * 暴击率 0 / 倍率 2.0   → 基线（不暴击）
     * 暴击率 1 / 倍率 2.0   → 强制暴击 ×2
     * 暴击率 1 / 倍率 1.0   → 强制暴击 ×1（探「原版 1.5 有没有叠上去」）
     * </pre>
     * 倍率 1.0 那组是关键：若伤害回到基线，说明我们的倍率**替换**了进场值；
     * 若仍是 1.5 倍，说明原版的 1.5 在事件外面又乘了一次，我们的倍率只能当乘数用。
     */
    private static int critProbe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("[redtale] 没有服务端上下文"));
            return 0;
        }
        var level = src.getServer().overworld();
        if (level == null) {
            src.sendFailure(Component.literal("[redtale] 主世界还没加载"));
            return 0;
        }

        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var dummy = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (dummy == null) {
            src.sendFailure(Component.literal("[redtale] 生成木桩失败"));
            return 0;
        }

        var chanceHolder = RedtaleAttributes.holder(AttributeSpecs.resolve("critical_strike_chance"));
        var powerHolder = RedtaleAttributes.holder(AttributeSpecs.resolve("critical_strike_power"));
        var attackDamage = player.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        var chanceInstance = chanceHolder == null ? null : player.getAttribute(chanceHolder);
        var powerInstance = powerHolder == null ? null : player.getAttribute(powerHolder);
        if (attackDamage == null || chanceInstance == null || powerInstance == null) {
            src.sendFailure(Component.literal("[redtale] FakePlayer 缺 attack_damage / 暴击属性"));
            return 0;
        }

        double originalDamage = attackDamage.getBaseValue();
        double originalChance = chanceInstance.getBaseValue();
        double originalPower = powerInstance.getBaseValue();

        try {
            player.setPos(level.getSharedSpawnPos().getX() + 0.5,
                    level.getSharedSpawnPos().getY(),
                    level.getSharedSpawnPos().getZ() + 0.5);
            dummy.setPos(player.getX(), player.getY(), player.getZ());
            dummy.setNoAi(true);
            dummy.setSilent(true);
            dummy.setPersistenceRequired();
            var maxHealth = dummy.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            var armor = dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (maxHealth != null) {
                maxHealth.setBaseValue(1000);
            }
            if (armor != null) {
                armor.setBaseValue(0);
            }
            dummy.setHealth(1000);
            level.addFreshEntity(dummy);

            attackDamage.setBaseValue(100.0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.ItemStack.EMPTY);

            src.sendSuccess(() -> Component.literal(
                    "[redtale] 暴击探测（FakePlayer 空手，攻击力 100，木桩护甲 0 / 血量 1000）："), false);

            double baseline = 0;
            double[][] cases = {{0.0, 2.0}, {1.0, 2.0}, {1.0, 1.0}, {0.0, 2.0}};
            double[] result = new double[cases.length];
            for (int i = 0; i < cases.length; i++) {
                chanceInstance.setBaseValue(cases[i][0]);
                powerInstance.setBaseValue(cases[i][1]);

                dummy.setHealth(1000);
                dummy.invulnerableTime = 0;
                dummy.hurtTime = 0;
                dummy.setRemainingFireTicks(0);
                player.resetAttackStrengthTicker();
                setAttackStrengthTicker(player, 200);

                double before = dummy.getHealth();
                player.attack(dummy);
                result[i] = before - dummy.getHealth();
                if (i == 0) {
                    baseline = result[i];
                }
                int idx = i;
                String line = "  暴击率 " + AttributeBreakdown.num(cases[i][0])
                        + " · 倍率 " + AttributeBreakdown.num(cases[i][1])
                        + " → 掉血 " + AttributeBreakdown.num(result[i]);
                src.sendSuccess(() -> Component.literal(line), false);
            }

            double ratioPower2 = baseline > 0 ? result[1] / baseline : 0;
            double ratioPower1 = baseline > 0 ? result[2] / baseline : 0;
            String verdict;
            boolean forceOn = RedtaleCore.get().critRules().forceOnRoll();
            if (!forceOn) {
                // 配置主动关掉「掷中强制暴击」时，×1.0 就是正确结果 —— 别把配置判成故障
                verdict = ratioPower2 < 1.05
                        ? "✓ 配置 force_on_roll=false → 掷中也不强制暴击（符合预期）"
                        : "△ force_on_roll=false 却仍暴击了，检查是否原版跳跃暴击（木桩探测不该出现）";
            } else if (ratioPower2 < 1.05) {
                verdict = "✗ 强制暴击没生效（事件结果未被 Player#attack 采纳，需改在伤害事件里自己 roll）";
            } else if (Math.abs(ratioPower1 - 1.0) < 0.05) {
                verdict = "✓ 我们的倍率是**替换**进场值（原版 1.5 不额外叠加）";
            } else if (ratioPower1 > 1.4 && ratioPower1 < 1.6) {
                verdict = "△ 原版 1.5 在事件外又乘了一次（我们的倍率是乘数，实际暴击 = 1.5 × 属性值）";
            } else {
                verdict = "? 倍率 1.0 实测 ×" + AttributeBreakdown.num(ratioPower1) + "，需人工判断";
            }
            src.sendSuccess(() -> Component.literal("  比值：×2.0 → " + AttributeBreakdown.num(ratioPower2)
                    + " · ×1.0 → " + AttributeBreakdown.num(ratioPower1)), false);
            src.sendSuccess(() -> Component.literal("  结论：" + verdict), false);
            boolean pass = forceOn ? ratioPower2 >= 1.05 : ratioPower2 < 1.05;
            return pass ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[redtale] 暴击探测失败：" + e));
            return 0;
        } finally {
            attackDamage.setBaseValue(originalDamage);
            chanceInstance.setBaseValue(originalChance);
            powerInstance.setBaseValue(originalPower);
            dummy.discard();
        }
    }

    /**
     * 把 {@code attackStrengthTicker} 顶到满值：{@code Player#attack} 里
     * {@code f = attack_damage × (0.2 + scale² × 0.8)}，且原版暴击要求 {@code scale > 0.9}，
     * scale 不满会让两次对照的基数不同，比值就不可信。
     *
     * <p>这个字段在 1.21.1 的 {@code LivingEntity} 里是 {@code protected}，
     * 命令类不在同一包，只能反射。字段名取 Mojang 官方映射（运行期就是官方名）。
     */
    private static void setAttackStrengthTicker(net.minecraft.world.entity.player.Player player, int value) {
        Class<?> c = player.getClass();
        while (c != null) {
            try {
                var field = c.getDeclaredField("attackStrengthTicker");
                field.setAccessible(true);
                field.setInt(player, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ items

    /**
     * 列出已载入的装备内容。
     *
     * <p>为什么需要它：14 个基底共用 14 个物品 id，{@code /give} 只能给到「基底」，
     * 看不出服上有哪些内容。这一条就是 {@code HOT-UPDATE §6} 里「代价」一栏的第一个补充入口。
     */
    private static int items(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        if (core.contentItems().size() == 0) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有装备内容（读 "
                    + core.config().root().resolve("items") + "/*.json5）"), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("[redtale] " + core.contentItems().size()
                + " 件装备内容 · 基底 " + com.redtale.project.items.RedtaleItems.count()
                + " 个 · 组件 redtale_project:content"), false);
        for (ItemContent content : core.contentItems().all()) {
            src.sendSuccess(() -> Component.literal("  " + content.describe()), false);
            ItemContent c = content;
            for (var entry : c.stats().entrySet()) {
                var spec = AttributeSpecs.resolve(entry.getKey());
                String line = "      " + (spec == null ? entry.getKey()
                        : spec.zh() + " " + com.redtale.project.items.StatFormat.signed(spec, entry.getValue()));
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    /**
     * 白板化端到端探测（D21 / M2 验收件）：读 {@code ItemStack#forEachModifier} 的**净结果**。
     *
     * <p>为什么用这条而不是「FakePlayer 穿上再读 ARMOR」：装备属性是**每 tick**
     * {@code collectEquipmentChanges} 才刷进 {@code AttributeMap} 的，FakePlayer 不 tick，
     * 于是三组都读成 0（本轮实测踩过，见 {@code docs/ROUND-R5-ITEMS.md}）。
     * 而 {@code forEachModifier} 正是「组件非空 → 只用组件；空 → 回落
     * {@code getDefaultAttributeModifiers}」这条分支的入口 —— 白板化的判据就在这里。
     *
     * <p>三组对照：原版皮革胸甲（3）→ 我们的 20 护甲胸甲 → 我们的 0 属性布袍。
     * 期望 3 / 20 / 0：第三组证明「空列表会回落底材」的坑被 +0 占位堵住了。
     */
    private static int whitening(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        net.minecraft.world.item.ItemStack vanilla = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.LEATHER_CHESTPLATE);
        double vanillaArmor = armorOf(vanilla);

        ItemContent plate = RedtaleCore.get().contentItems().get("demo_chestplate");
        ItemContent robe = RedtaleCore.get().contentItems().get("demo_robe");
        if (plate == null || robe == null) {
            src.sendFailure(Component.literal("[redtale] 配置里没有 demo_chestplate / demo_robe"
                    + "（跑一次 /redtale reload 让默认配置落地）"));
            return 0;
        }
        double plateArmor = armorOf(ItemFactory.build(plate));
        double robeArmor = armorOf(ItemFactory.build(robe));

        String line1 = "  原版皮革胸甲          → " + AttributeBreakdown.num(vanillaArmor)
                + "（这是要被打掉的底材数值）";        String verdictPlate = Math.abs(plateArmor - 20) < 1e-6 ? "  [OK] 白板化生效" : "  [FAIL] 与配置不符";
        String line2 = "  demo_chestplate（写 20）→ " + AttributeBreakdown.num(plateArmor) + verdictPlate;
        String verdictRobe = Math.abs(robeArmor) < 1e-6 ? "  [OK] +0 占位堵住了回落" : "  [FAIL] 回落到皮革底材";
        String line3 = "  demo_robe（0 属性）    → " + AttributeBreakdown.num(robeArmor) + verdictRobe;

        src.sendSuccess(() -> Component.literal(
                "[redtale] 白板化探测（ItemStack.forEachModifier @CHEST，原版装备属性的同一入口）："), false);
        src.sendSuccess(() -> Component.literal(line1), false);
        src.sendSuccess(() -> Component.literal(line2), false);
        src.sendSuccess(() -> Component.literal(line3), false);
        return Math.abs(plateArmor - 20) < 1e-6 && Math.abs(robeArmor) < 1e-6 ? 1 : 0;
    }

    /** 一件物品穿在胸上给多少护甲（走 forEachModifier，即原版的组件/回落分支）。 */
    private static double armorOf(net.minecraft.world.item.ItemStack stack) {
        return ContentTooltip.modifierAmount(stack,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
    }

    /** 按内容 id 发放（身份在组件里，所以不能靠 {@code /give redtale_project:xxx}）。 */    private static int give(CommandContext<CommandSourceStack> ctx, int count) {
        String id = StringArgumentType.getString(ctx, "content");
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[redtale] give 需要玩家上下文"));
            return 0;
        }
        ItemContent content = RedtaleCore.get().contentItems().get(id);
        if (content == null) {
            src.sendFailure(Component.literal("[redtale] 没有内容 '" + id + "'（试 /redtale items）"));
            return 0;
        }
        int given = 0;
        for (int i = 0; i < count; i++) {
            net.minecraft.world.item.ItemStack stack = ItemFactory.build(content);
            if (stack.isEmpty() || !player.getInventory().add(stack)) {
                break;
            }
            given++;
        }
        int n = given;
        if (given == 0) {
            src.sendFailure(Component.literal("[redtale] 背包满了，没发出去"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] 已发放 " + id + " ×" + n
                + "（基底 " + content.base().id() + "）"), true);
        return 1;
    }

    /**
     * 打印 D20 的数值行（单次伤害 + 攻速）。
     *
     * <p>这是**没有客户端时的 tooltip 验收入口**：它调的是
     * {@link ContentTooltip#lines}，与客户端 tooltip 同一段代码、同一个 {@code settle()}。
     */
    private static int tooltip(CommandContext<CommandSourceStack> ctx, String contentId) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        net.minecraft.world.item.ItemStack stack;
        if (contentId != null) {
            ItemContent content = RedtaleCore.get().contentItems().get(contentId);
            if (content == null) {
                src.sendFailure(Component.literal("[redtale] 没有内容 '" + contentId + "'"));
                return 0;
            }
            stack = ItemFactory.build(content);
        } else if (player != null) {
            stack = player.getMainHandItem();
        } else {
            src.sendFailure(Component.literal("[redtale] 控制台请给内容 id：/redtale tooltip demo_longsword"));
            return 0;
        }
        if (stack.isEmpty()) {
            src.sendFailure(Component.literal("[redtale] 主手是空的，也没有指定内容 id"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[redtale] " + stack.getHoverName().getString()
                + "（" + stack.getItem() + "）"), false);
        if (!stack.has(com.redtale.project.items.ContentComponents.CONTENT.get())) {
            src.sendSuccess(() -> Component.literal("  （没有 redtale_project:content 组件 → 不是我们的内容）"), false);
            return 0;
        }
        List<String> lines = ContentTooltip.lines(stack, player);
        if (lines.isEmpty()) {
            // 护甲 / 饰品没有「单次伤害 / 攻速」两行，它们的数值走原版属性行（+20 护甲 …）
            src.sendSuccess(() -> Component.literal("  （无武器数值行：这件是护甲/饰品，数值看上面的属性行）"), false);
            return 1;
        }
        for (String line : lines) {
            src.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ 孔位与宝石（M3）

    /** {@code /redtale socket …} 的命令子树（单独一个方法，避免注册块里套五层括号）。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> socketNode() {
        return Commands.literal("socket")
                .executes(RedtaleCommand::socketList)
                .then(Commands.literal("list").executes(RedtaleCommand::socketList))
                .then(Commands.literal("insert")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 5))
                                .then(Commands.argument("gem", StringArgumentType.string())
                                        .executes(ctx -> socketInsert(ctx,
                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                StringArgumentType.getString(ctx, "gem"))))))
                .then(Commands.literal("extract")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 5))
                                .executes(ctx -> socketExtract(ctx,
                                        IntegerArgumentType.getInteger(ctx, "index")))))
                // 端到端探测：不需要玩家/客户端，服务端自己跑一遍镶嵌全流程
                .then(Commands.literal("probe").executes(RedtaleCommand::socketProbe));
    }

    /** {@code /redtale socket [list]}：主手物品的孔位、宝石与宝石数值。 */
    private static int socketList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            src.sendFailure(Component.literal("[redtale] 这条命令要玩家执行"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            src.sendFailure(Component.literal("[redtale] 主手是空的"));
            return 0;
        }
        var data = stack.get(com.redtale.project.items.ContentComponents.CONTENT.get());
        if (data == null) {
            src.sendFailure(Component.literal("[redtale] 主手物品没有内容组件"));
            return 0;
        }
        ItemContent def = RedtaleCore.get().contentItems().get(data.contentId());
        if (def == null) {
            src.sendFailure(Component.literal("[redtale] 内容定义已不存在（孤儿）：" + data.contentId()));
            return 0;
        }
        int count = def.sockets().count();
        src.sendSuccess(() -> Component.literal("[redtale] " + def.id() + " 孔位 " + count
                + " · 已镶 " + data.sockets().stream().filter(x -> x != null && !x.isEmpty()).count()), false);
        if (count <= 0) {
            src.sendSuccess(() -> Component.literal("  （定义里没有 sockets 段，镶不了）"), false);
            return 0;
        }
        List<String> sockets = com.redtale.project.items.Socketer.aligned(data.sockets(), count);
        for (int i = 0; i < sockets.size(); i++) {
            int idx = i;
            String gem = sockets.get(i);
            String type = def.sockets().types().get(i);
            src.sendSuccess(() -> Component.literal("  [" + idx + "] " + type + " → "
                    + (gem == null || gem.isEmpty() ? "空" : gem)), false);
        }
        var gemStats = data.gemStats();
        if (gemStats.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  宝石数值：无"), false);
        } else {
            for (var e : gemStats.entrySet()) {
                src.sendSuccess(() -> Component.literal("  宝石数值 " + e.getKey() + " +"
                        + com.redtale.project.items.StatFormat.num(e.getValue())), false);
            }
        }
        return sockets.size();
    }

    /** {@code /redtale socket insert <index> <gem>}：镶一颗宝石（该孔旧宝石被替换，按原物退回）。 */
    private static int socketInsert(CommandContext<CommandSourceStack> ctx, int index, String gemId) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            src.sendFailure(Component.literal("[redtale] 这条命令要玩家执行"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        var result = com.redtale.project.items.Socketer.insert(stack, index, gemId);
        if (!result.applied()) {
            src.sendFailure(Component.literal("[redtale] 镶嵌失败（" + result.status() + "）：" + result.message()));
            return 0;
        }
        if (!result.affectedGemId().isEmpty()) {
            // 被替换下来的旧宝石必须还给玩家：无声吞掉一件内容物品是资产事故
            giveBack(player, result.affectedGemId());
        }
        player.inventoryMenu.broadcastChanges();
        src.sendSuccess(() -> Component.literal("[redtale] " + result.message()), false);
        reportGemStats(src, result.gemStats());
        return 1;
    }

    /** {@code /redtale socket extract <index>}：取下一颗宝石，宝石物品退回背包。 */
    private static int socketExtract(CommandContext<CommandSourceStack> ctx, int index) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            src.sendFailure(Component.literal("[redtale] 这条命令要玩家执行"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        var result = com.redtale.project.items.Socketer.extract(stack, index);
        if (!result.applied()) {
            src.sendFailure(Component.literal("[redtale] 取出失败（" + result.status() + "）：" + result.message()));
            return 0;
        }
        giveBack(player, result.affectedGemId());
        player.inventoryMenu.broadcastChanges();
        src.sendSuccess(() -> Component.literal("[redtale] " + result.message() + "（宝石已退回背包）"), false);
        reportGemStats(src, result.gemStats());
        return 1;
    }

    /** 按内容 id 造一件宝石还给玩家；背包满了就丢在脚下（绝不静默丢掉）。 */
    private static void giveBack(ServerPlayer player, String contentId) {
        ItemContent content = RedtaleCore.get().contentItems().get(contentId);
        if (content == null) {
            return;
        }
        ItemStack gemStack = ItemFactory.build(content, null);
        if (gemStack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(gemStack)) {
            player.drop(gemStack, false);
        }
    }

    /**
     * {@code /redtale socket probe}：镶嵌链路的端到端探测（服务端自跑，不需要玩家/客户端）。
     *
     * <p>为什么必须有它：M3 的镶嵌入口是「玩家手持物品」，而验收环境没有客户端。
     * 这里直接用 {@link ItemFactory#build} 造一件 {@code demo_longsword}，把
     * 类型匹配 / 同组互斥 / 替换 / 取出 / 面板 id 分离 / tooltip 六件事全跑一遍。
     */
    private static int socketProbe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ItemContent def = RedtaleCore.get().contentItems().get("demo_longsword");
        if (def == null || def.sockets().count() <= 0) {
            src.sendFailure(Component.literal("[redtale] demo_longsword 没有 sockets 段（先 /redtale reload）"));
            return 0;
        }
        if (RedtaleCore.get().contentItems().get("gem_ruby") == null
                || RedtaleCore.get().contentItems().get("gem_sapphire") == null) {
            src.sendFailure(Component.literal("[redtale] 缺少宝石内容 gem_ruby / gem_sapphire"));
            return 0;
        }
        ItemStack stack = ItemFactory.build(def, null);
        var data0 = stack.get(com.redtale.project.items.ContentComponents.CONTENT.get());
        int pass = 0;
        int total = 0;
        src.sendSuccess(() -> Component.literal("[redtale] 镶嵌探测：demo_longsword 孔位 "
                + def.sockets().count() + " · 类型 " + def.sockets().types()), false);

        // 1 新发放的物品必须已经按定义填好空孔
        total++;
        boolean first = data0 != null && data0.sockets().size() == def.sockets().count()
                && data0.sockets().stream().allMatch(String::isEmpty) && data0.gemStats().isEmpty();
        pass += first ? 1 : 0;
        line(src, first, "新发放孔序 = " + def.sockets().count() + " 个空孔",
                data0 == null ? "无组件" : data0.sockets() + " / gemStats=" + data0.gemStats());

        // 2 镶 offensive 宝石进 offensive 孔
        var r1 = com.redtale.project.items.Socketer.insert(stack, 0, "gem_ruby");
        total++;
        Double pd = r1.gemStats().get("physical_damage");
        Double ad = r1.gemStats().get("minecraft:generic.attack_damage");
        boolean ok1 = r1.applied() && pd != null && Math.abs(pd - 0.05) < 1e-9
                && ad != null && Math.abs(ad - 2.1) < 1e-9;
        pass += ok1 ? 1 : 0;
        line(src, ok1, "镶 gem_ruby → physical_damage 0.05 / attack_damage 2.1（2 + 0.1×1）",
                r1.status() + " " + r1.gemStats());

        // 3 面板上「内容」与「宝石」的 modifier id 必须分开（同 id 会互相覆盖）
        total++;
        java.util.List<String> ids = new java.util.ArrayList<>();
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (holder, mod) ->
                ids.add(mod.id().toString()));
        long gemIds = ids.stream().filter(x -> x.contains(":gem/")).count();
        long contentIds = ids.stream().filter(x -> x.contains(":content/generic.attack_damage")).count();
        boolean ok3 = gemIds == 2 && contentIds == 1;
        pass += ok3 ? 1 : 0;
        line(src, ok3, "面板 gem/ 两条 + content/generic.attack_damage 一条（同属性不互相覆盖）", ids.toString());

        // 3b 两条同属性 modifier 必须**相加**（7 + 2.1 = 9.1），而不是互相顶掉一条
        total++;
        double total8 = ContentTooltip.modifierAmount(stack,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        boolean ok3b = Math.abs(total8 - 9.1) < 1e-6;
        pass += ok3b ? 1 : 0;
        line(src, ok3b, "面板攻击伤害 = 7（内容）+ 2.1（宝石）= 9.1", String.valueOf(total8));

        // 4 同组互斥：第二颗 ruby 必须被拦
        var r2 = com.redtale.project.items.Socketer.insert(stack, 1, "gem_ruby");
        total++;
        pass += r2.status() == com.redtale.project.items.Socketer.Status.EXCLUSIVE_CONFLICT ? 1 : 0;
        line(src, r2.status() == com.redtale.project.items.Socketer.Status.EXCLUSIVE_CONFLICT,
                "同组第二颗 gem_ruby → EXCLUSIVE_CONFLICT", r2.status() + " " + r2.message());

        // 5 类型不匹配：offensive 孔塞 defensive 宝石必须被拦
        var r3 = com.redtale.project.items.Socketer.insert(stack, 0, "gem_sapphire");
        total++;
        pass += r3.status() == com.redtale.project.items.Socketer.Status.TYPE_MISMATCH ? 1 : 0;
        line(src, r3.status() == com.redtale.project.items.Socketer.Status.TYPE_MISMATCH,
                "offensive 孔 + defensive 宝石 → TYPE_MISMATCH", r3.status() + " " + r3.message());

        // 6 any 孔收 defensive 宝石
        var r4 = com.redtale.project.items.Socketer.insert(stack, 1, "gem_sapphire");
        total++;
        Double crit = r4.gemStats().get("critical_strike_chance");
        boolean ok6 = r4.applied() && r4.gemStats().size() == 3
                && crit != null && Math.abs(crit - 0.03) < 1e-9;
        pass += ok6 ? 1 : 0;
        line(src, ok6, "any 孔收 gem_sapphire → gemStats 3 条（含 critical_strike_chance 0.03）",
                r4.status() + " " + r4.gemStats());

        // 7 tooltip 有孔位行，且两孔各显示自己的宝石
        total++;
        java.util.List<String> lines = ContentTooltip.lines(stack, null);
        String socketLine = lines.stream().filter(x -> x.startsWith("孔位")).findFirst().orElse("");
        boolean ok7 = socketLine.contains("gem_ruby") && socketLine.contains("gem_sapphire");
        pass += ok7 ? 1 : 0;
        line(src, ok7, "tooltip 孔位行同时列出两颗宝石", socketLine.isEmpty() ? "（没有孔位行）" : socketLine);

        // 8 取出第一颗 → gemStats 只剩另一颗的数（重算，不是做减法）
        var r5 = com.redtale.project.items.Socketer.extract(stack, 0);
        total++;
        boolean ok8 = r5.applied() && r5.gemStats().size() == 1
                && r5.gemStats().containsKey("critical_strike_chance") && !r5.gemStats().containsKey("physical_damage");
        pass += ok8 ? 1 : 0;
        line(src, ok8, "取出孔 0 → gemStats 只剩 sapphire 的 1 条（重算而非相减）",
                r5.status() + " " + r5.gemStats());

        // 9 空孔再取 → EMPTY_SLOT，且物品未被改坏
        var r6 = com.redtale.project.items.Socketer.extract(stack, 0);
        total++;
        pass += r6.status() == com.redtale.project.items.Socketer.Status.EMPTY_SLOT ? 1 : 0;
        line(src, r6.status() == com.redtale.project.items.Socketer.Status.EMPTY_SLOT,
                "空孔再取 → EMPTY_SLOT", r6.status() + " " + r6.message());

        // 10 越界
        var r7 = com.redtale.project.items.Socketer.insert(stack, 5, "gem_ruby");
        total++;
        pass += r7.status() == com.redtale.project.items.Socketer.Status.INDEX_OUT_OF_RANGE ? 1 : 0;
        line(src, r7.status() == com.redtale.project.items.Socketer.Status.INDEX_OUT_OF_RANGE,
                "孔位下标 5 → INDEX_OUT_OF_RANGE", r7.status() + " " + r7.message());

        int finalPass = pass;
        int finalTotal = total;
        if (pass == total) {
            src.sendSuccess(() -> Component.literal("  结论：镶嵌链路 " + finalPass + "/" + finalTotal
                    + " 全过 ✓（gemStats 重算 / 类型 / 互斥 / 面板 id / tooltip / 空孔 / 越界）"), false);
            return finalPass;
        }
        src.sendFailure(Component.literal("  结论：镶嵌链路 " + finalPass + "/" + finalTotal + " 有项不过 ✗"));
        return finalPass;
    }

    private static void line(CommandSourceStack src, boolean ok, String name, String actual) {
        src.sendSuccess(() -> Component.literal((ok ? "  ✓ " : "  ✗ ") + name + " — " + actual), false);
    }

    private static void reportGemStats(CommandSourceStack src, java.util.Map<String, Double> gemStats) {
        if (gemStats.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  宝石数值：无"), false);
            return;
        }
        for (var e : gemStats.entrySet()) {
            src.sendSuccess(() -> Component.literal("  宝石数值 " + e.getKey() + " +"
                    + com.redtale.project.items.StatFormat.num(e.getValue())), false);
        }
    }
}
