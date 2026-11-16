package com.redtale.project.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.items.ItemContent;
import com.redtale.project.items.SetBonus;
import com.redtale.project.items.SetSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /redtale set check} —— 套装引用与可达性只读校验（M3 配套，T-0016）。
 *
 * <p>扫四类「套装配了但永远不生效」的静默坑：{@code pieces} 写了不存在的内容、piece 的 {@code set_id}
 * 指向别的套装、档位要求件数超过同槽互斥后能同时穿上的数量、同一 {@code count} 写两次
 * （modifier id {@code set/<setId>/<count>} 相同，后者覆盖前者）。它们都不报错，只是不生效。
 *
 * <p><b>只读</b>：不写盘、不改内存、不下发/移除任何 modifier。返回「问题」条数，不是检查项总数。
 * 输出顺序固定（套按 {@code sets()} 迭代序，套内先 pieces 顺序、再档位升序），两次运行逐字相同。
 */
public final class SetCheckCommand {

    private SetCheckCommand() {
    }

    /** 命令节点：{@code /redtale set check}（挂到已存在的 {@code set} 节点下，Brigadier 按同名合并）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("set")
                .then(Commands.literal("check").executes(SetCheckCommand::check));
    }

    /** 返回「问题」条数（不是检查项总数）。 */
    public static int check(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        Map<String, SetSpec.Definition> sets = core.sets();
        List<String> problems = new ArrayList<>();
        int pieces = 0;
        for (SetSpec.Definition def : sets.values()) {
            pieces += def.pieces().size();
            List<SetSpec.Bonus> bonuses = new ArrayList<>(def.bonuses());
            bonuses.sort(Comparator.comparingInt(SetSpec.Bonus::count));
            // 检查 1 / 2：piece 不存在、piece 的 set_id 不匹配（按 pieces 顺序）
            List<ItemContent> matched = new ArrayList<>();
            for (String pieceId : def.pieces()) {
                ItemContent content = core.contentItems().get(pieceId);
                if (content == null) {
                    problems.add("  " + def.id() + ": piece " + pieceId + " 不存在");
                    continue;
                }
                if (!def.id().equals(content.setId())) {
                    String actual = content.setId() == null || content.setId().isEmpty()
                            ? "空" : content.setId();
                    problems.add("  " + def.id() + ": piece " + pieceId + " 的 set_id 是 " + actual
                            + "（应为 " + def.id() + "）");
                    continue;
                }
                matched.add(content);
            }
            // 检查 3：可达上限 = 存在且归属正确的 piece，按实际槽位去重后的数量
            Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
            for (ItemContent content : matched) {
                slots.add(SetBonus.slotOf(content.base()));
            }
            int cap = slots.size();
            if (cap == 0) {
                problems.add("  " + def.id() + ": pieces 一件都穿不上（可达上限 0）");
            }
            // 检查 3（逐档）+ 检查 4（同档重复），按档位升序
            Set<Integer> seen = new HashSet<>();
            Set<Integer> duplicated = new LinkedHashSet<>();
            for (SetSpec.Bonus bonus : bonuses) {
                if (!seen.add(bonus.count())) {
                    if (duplicated.add(bonus.count())) {
                        problems.add("  " + def.id() + ": " + bonus.count()
                                + " 件档重复（同一 modifier id 会互相覆盖）");
                    }
                    continue;
                }
                if (cap > 0 && bonus.count() > cap) {
                    problems.add("  " + def.id() + ": " + bonus.count() + " 件档要求 "
                            + bonus.count() + " 件，但 pieces 最多能同时穿 " + cap + " 件（同槽基底互斥）");
                }
            }
        }
        final int setCount = sets.size();
        final int pieceCount = pieces;
        final int k = problems.size();
        src.sendSuccess(() -> Component.literal("套装校验：" + setCount + " 个定义 · " + pieceCount
                + " 件 pieces · 问题 " + k + " 条"), false);
        if (k == 0) {
            src.sendSuccess(() -> Component.literal("  （套装引用完整）"), false);
        } else {
            for (String line : problems) {
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return k;
    }
}
