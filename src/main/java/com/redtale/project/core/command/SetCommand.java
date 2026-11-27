package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.items.ContentData;
import com.redtale.project.items.ContentComponents;
import com.redtale.project.items.ItemContent;
import com.redtale.project.items.SetBonus;
import com.redtale.project.items.SetSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * {@code /redtale set …}（M3 第 6 步）：套装定义的可见性 + 件数下发的可见性。
 *
 * <p>三条命令，全部**只读**（唯一写属性的是 {@code sync}，它调的也是结算路径本身）：
 * <ul>
 *   <li>{@code /redtale set} —— 我身上每套几件、哪几档生效、每档下发了什么 id。没有在线玩家时列定义。</li>
 *   <li>{@code /redtale set list} —— 全部套装定义（id / 名称 / pieces 数 / 档位）。</li>
 *   <li>{@code /redtale set sync} —— 立刻给全部在线玩家重算（清签名 + 重下发）。</li>
 * </ul>
 *
 * <p>为什么要有 {@code sync}：件数是每 20 tick 复核的，验收时不想等；
 * 而且「改了 sets/ + reload」之后要能一眼确认属性真的换了 id（{@code set/<id>/<count>}）。
 */
public final class SetCommand {

    private SetCommand() {
    }

    /** 命令节点：{@code /redtale set [list|sync]}。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("set")
                .executes(SetCommand::self)
                .then(Commands.literal("list").executes(SetCommand::list))
                .then(Commands.literal("sync").executes(SetCommand::sync))
                // 端到端探测：FakePlayer 真穿 0→1→2→1→0 件，看件数与 modifier id（S7 验收用）
                .then(Commands.literal("probe")
                        .executes(ctx -> probe(ctx, null))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> probe(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "id")))));
    }

    /** 我自己身上的套装状态（无在线玩家 → 退回定义清单，便于控制台用）。 */
    public static int self(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "套装（控制台无在线玩家，列出定义）："), false);
            return list(ctx);
        }
        Map<String, Integer> counts = SetBonus.countsOf(player);
        Map<String, SetSpec.Definition> sets = RedtaleCore.get().sets();
        ctx.getSource().sendSuccess(() -> Component.literal("套装：" + sets.size() + " 个定义 · "
                + player.getName().getString() + " 命中 " + counts.size() + " 套"), false);
        if (counts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  （没有穿任何带 set_id 的内容物品；口径 = 护甲 4 + 主手 + 副手，按内容 id 去重）"), false);
            return 0;
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            SetSpec.Definition def = sets.get(e.getKey());
            String name = def == null ? e.getKey() + "（定义缺失）" : def.name();
            List<SetSpec.Bonus> active = SetBonus.activeOf(player, e.getKey());
            String detail = active.isEmpty() ? "无生效档" : "生效 " + active.size() + " 档";
            ctx.getSource().sendSuccess(() -> Component.literal("  " + e.getKey() + " · " + name
                    + " · " + e.getValue() + "/" + (def == null ? 0 : def.pieces().size())
                    + " 件 · " + detail), false);
            for (SetSpec.Bonus bonus : active) {
                StringBuilder line = new StringBuilder("    " + bonus.count() + " 件档"
                        + (bonus.name().isEmpty() ? "" : "「" + bonus.name() + "」")
                        + " → " + SetBonus.modifierId(e.getKey(), bonus.count()) + "：");
                boolean first = true;
                for (Map.Entry<String, com.redtale.project.core.math.StatValue> stat : bonus.stats().entrySet()) {
                    if (!first) {
                        line.append(" · ");
                    }
                    first = false;
                    line.append(stat.getKey()).append(' ')
                            .append(com.redtale.project.items.StatFormat.num(stat.getValue().base()));
                }
                line.append(bonus.stats().isEmpty() ? "（纯展示档，无属性）" : "");
                ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
            }
        }
        return counts.size();
    }

    /** 全部套装定义（按 reload 后的迭代序）。 */
    public static int list(CommandContext<CommandSourceStack> ctx) {
        Map<String, SetSpec.Definition> sets = RedtaleCore.get().sets();
        ctx.getSource().sendSuccess(() -> Component.literal("套装定义 " + sets.size() + " 个："), false);
        if (sets.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  （config/redtale_project/sets/*.json5 为空；默认集里有一份 emberforged）"), false);
            return 0;
        }
        for (SetSpec.Definition def : sets.values()) {
            StringBuilder line = new StringBuilder("  " + def.id() + " · " + def.name()
                    + " · " + def.pieces().size() + " 件 · 档位 ");
            if (def.bonuses().isEmpty()) {
                line.append("（无）");
            } else {
                for (int i = 0; i < def.bonuses().size(); i++) {
                    if (i > 0) {
                        line.append('/');
                    }
                    line.append(def.bonuses().get(i).count());
                }
            }
            ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
        }
        return sets.size();
    }

    /** 一个套装定义的明细（pieces 是否真的有对应内容 —— 静默失效的常见来源）。 */
    public static int info(CommandContext<CommandSourceStack> ctx, String id) {
        SetSpec.Definition def = RedtaleCore.get().sets().get(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("没有套装 '" + id + "'"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("套装 " + def.id() + " · " + def.name()
                + " · " + def.pieces().size() + " 件 · " + def.bonuses().size() + " 档"), false);
        for (String piece : def.pieces()) {
            ItemContent content = RedtaleCore.get().contentItems().get(piece);
            // pieces 写的是**内容 id**（与 set_id 同一套命名），不是物品注册 id；不存在就是配错了
            ctx.getSource().sendSuccess(() -> Component.literal("  " + piece
                    + (content == null ? "（内容不存在）" : " · 基底 " + content.base().id()
                    + (def.id().equals(content.setId()) ? "" : " · 但它的 set_id 是 "
                    + (content.setId().isEmpty() ? "空" : content.setId())))), false);
        }
        for (SetSpec.Bonus bonus : def.bonuses()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + bonus.count() + " 件档"
                    + (bonus.name().isEmpty() ? "" : "「" + bonus.name() + "」")
                    + " · " + bonus.stats().size() + " 条属性"), false);
        }
        return def.bonuses().size();
    }

    /** 立刻给全部在线玩家重算（清签名 → 强制重下发）。 */
    public static int sync(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("没有服务端实例"));
            return 0;
        }
        int players = server.getPlayerList().getPlayers().size();
        int written = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SetBonus.clear(player);
            written += SetBonus.apply(player);
        }
        final int w = written;
        ctx.getSource().sendSuccess(() -> Component.literal("套装重算：" + players
                + " 名在线玩家 · 改写 " + w + " 个 modifier"), false);
        return w;
    }

    /** 供 {@code /redtale tooltip} 与验收用的摘要行（不带颜色，纯文本便于 grep）。 */
    public static String describe(ItemStack stack) {
        ContentData data = stack.get(ContentComponents.CONTENT.get());
        if (data == null || data.setId().isEmpty()) {
            return "";
        }
        SetSpec.Definition def = RedtaleCore.get().sets().get(data.setId());
        return "set=" + data.setId() + (def == null ? "(缺失)" : "(" + def.name() + ")");
    }

    /** 内容定义里有 set_id 但套装定义不存在 → 一条问题（{@code /redtale validate} 将来接）。 */
    public static List<String> danglingSets() {
        List<String> out = new java.util.ArrayList<>();
        for (ItemContent content : RedtaleCore.get().contentItems().all()) {
            if (!content.setId().isEmpty() && !RedtaleCore.get().sets().containsKey(content.setId())) {
                out.add(content.id() + " 的 set_id " + content.setId() + " 没有对应套装定义");
            }
        }
        return out;
    }

    /**
     * 端到端探测（S7 验收用）：{@code FakePlayer} 真穿 0 → 1 → 2 → 1 → 0 件，
     * 每一步打「件数 / 档位 modifier 在不在 / 属性值变了多少」。
     *
     * <p>不用真人：件数统计与下发全在服务端，{@code getAttributeValue} 就是客户端读到的那个值。
     * 唯一验不到的是「客户端面板刷不刷新」——那条永远要真人（挂账）。
     */
    public static int probe(CommandContext<CommandSourceStack> ctx, String setId) {
        CommandSourceStack src = ctx.getSource();
        var level = src.getServer() == null ? null : src.getServer().overworld();
        if (level == null) {
            src.sendFailure(Component.literal("主世界还没加载"));
            return 0;
        }
        String id = setId;
        if (id == null) {
            for (SetSpec.Definition candidate : RedtaleCore.get().sets().values()) {
                if (!candidate.pieces().isEmpty()) {
                    id = candidate.id();
                    break;
                }
            }
        }
        SetSpec.Definition def = id == null ? null : RedtaleCore.get().sets().get(id);
        if (def == null) {
            src.sendFailure(Component.literal("没有可用的套装定义（sets/*.json5 为空）"));
            return 0;
        }
        List<ItemContent> pieces = new java.util.ArrayList<>();
        for (String piece : def.pieces()) {
            ItemContent content = RedtaleCore.get().contentItems().get(piece);
            if (content != null) {
                pieces.add(content);
            }
        }
        if (pieces.size() < 2) {
            src.sendFailure(Component.literal("套装 " + def.id() + " 的有效件数不足 2（pieces "
                    + def.pieces().size() + " → 内容命中 " + pieces.size() + "）"));
            return 0;
        }
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        // 从头开始：清装备 + 清签名（上一次的残留签名会让 apply 直接返回 0）
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        SetBonus.clear(player);
        SetBonus.apply(player);

        String setIdFinal = def.id();
        List<Integer> tiers = SetBonus.declaredCounts(def);
        // 档位 -> 该档声明的属性（按配置 key 现解析，不假定「最高档给攻击伤害」）
        java.util.Map<Integer, java.util.List<com.redtale.project.core.attr.AttributeSpec>> declared =
                new java.util.LinkedHashMap<>();
        for (SetSpec.Bonus bonus : def.bonuses()) {
            java.util.List<com.redtale.project.core.attr.AttributeSpec> specs = new java.util.ArrayList<>();
            for (String key : bonus.stats().keySet()) {
                com.redtale.project.core.attr.AttributeSpec spec =
                        com.redtale.project.core.attr.AttributeSpecs.resolve(key);
                if (spec != null) {
                    specs.add(spec);
                }
            }
            declared.put(bonus.count(), specs);
        }

        src.sendSuccess(() -> Component.literal("套装探测 " + setIdFinal + "（FakePlayer 真穿真脱）："), false);
        src.sendSuccess(() -> Component.literal("  定义：pieces " + def.pieces().size()
                + " / 档位 " + tiers + " / 命中内容 " + pieces.size() + " 件"), false);

        int pass = 0;
        int fail = 0;

        // ① 0 件：不穿就不该有任何档位 modifier
        java.util.List<String> issues0 = tierMismatches(player, setIdFinal, tiers, declared, 0);
        if (SetBonus.countOf(player, setIdFinal) == 0 && issues0.isEmpty()) {
            pass++;
            src.sendSuccess(() -> Component.literal("  ✓ 0 件：件数 0 · 无残留档位 modifier"), false);
        } else {
            fail++;
            final String detail0 = issues0.isEmpty()
                    ? "件数 " + SetBonus.countOf(player, setIdFinal) : issues0.toString();
            src.sendFailure(Component.literal("  ✗ 0 件：" + detail0));
        }

        // ②~③ 逐件真穿：每加一件，count<=件数 的档必须全在位、更高档必须全不在位
        int equipped = 0;
        int reached = Math.min(tiers.size(), pieces.size());
        for (int i = 0; i < reached; i++) {
            ItemStack stack = com.redtale.project.items.ItemFactory.build(pieces.get(i), null);
            player.setItemSlot(SetBonus.slotOf(pieces.get(i).base()), stack);
            double hpBefore = player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            SetBonus.apply(player);
            double hpAfter = player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            equipped++;
            final int worn = equipped;
            java.util.List<String> issues = tierMismatches(player, setIdFinal, tiers, declared, worn);
            int count = SetBonus.countOf(player, setIdFinal);
            if (count == worn && issues.isEmpty()) {
                pass++;
                src.sendSuccess(() -> Component.literal("  ✓ " + worn + " 件：件数 " + worn
                        + " · 在档 " + inPlay(tiers, worn)
                        + " · 最大生命 " + hpBefore + " → " + hpAfter), false);
            } else {
                fail++;
                final String detail = issues.isEmpty() ? "件数 " + count : issues.toString();
                src.sendFailure(Component.literal("  ✗ " + worn + " 件：件数 " + count + " · " + detail));
            }
        }

        // ④ 脱回 1 件：更高档必须消失、最低档必须保留（只增不减是这套机制最容易漏的地方）
        for (int i = 1; i < pieces.size(); i++) {
            player.setItemSlot(SetBonus.slotOf(pieces.get(i).base()), ItemStack.EMPTY);
        }
        SetBonus.apply(player);
        int c3 = SetBonus.countOf(player, setIdFinal);
        java.util.List<String> issues1 = tierMismatches(player, setIdFinal, tiers, declared, 1);
        if (c3 == 1 && issues1.isEmpty()) {
            pass++;
            src.sendSuccess(() -> Component.literal("  ✓ 脱回 1 件：件数 1 · 在档 " + inPlay(tiers, 1)), false);
        } else {
            fail++;
            final String detail1 = issues1.isEmpty() ? "件数 " + c3 : issues1.toString();
            src.sendFailure(Component.literal("  ✗ 脱回 1 件：" + detail1));
        }

        // ⑤ clear：全部消失（登出 / 关掉套装走的路径）
        SetBonus.clear(player);
        player.setItemSlot(SetBonus.slotOf(pieces.get(0).base()), ItemStack.EMPTY);
        java.util.List<String> issuesClear = tierMismatches(player, setIdFinal, tiers, declared, 0);
        if (issuesClear.isEmpty()) {
            pass++;
            src.sendSuccess(() -> Component.literal("  ✓ clear：全部档位 modifier 已移除"), false);
        } else {
            fail++;
            final String detailClear = issuesClear.toString();
            src.sendFailure(Component.literal("  ✗ clear：仍有残留 " + detailClear));
        }

        final int p = pass;
        final int f = fail;
        src.sendSuccess(() -> Component.literal("  结论：" + (f == 0 ? "全部通过" : "有失败项")
                + "（pass " + p + " / fail " + f + "）"), false);
        return f == 0 ? 1 : 0;
    }

    /** worn 件时 count<=worn 的档必须全在位、count>worn 的档必须全不在位；返回差异清单。 */
    private static java.util.List<String> tierMismatches(net.minecraft.world.entity.player.Player player,
            String setId, List<Integer> tiers,
            java.util.Map<Integer, java.util.List<com.redtale.project.core.attr.AttributeSpec>> declared,
            int worn) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int tier : tiers) {
            boolean shouldHave = tier <= worn;
            for (com.redtale.project.core.attr.AttributeSpec spec : declared.getOrDefault(tier, java.util.List.of())) {
                double value = SetBonus.amount(player, setId, tier, spec);
                if (shouldHave && value == 0.0) {
                    out.add(tier + " 件档缺 " + spec.id());
                } else if (!shouldHave && value != 0.0) {
                    out.add(tier + " 件档残留 " + spec.id() + "=" + value);
                }
            }
        }
        return out;
    }

    /** 「[1, 2]」这种在当前件数下生效的档位串，用于回显。 */
    private static String inPlay(List<Integer> tiers, int worn) {
        java.util.List<Integer> hit = new java.util.ArrayList<>();
        for (int tier : tiers) {
            if (tier <= worn) {
                hit.add(tier);
            }
        }
        return hit.toString();
    }
}
