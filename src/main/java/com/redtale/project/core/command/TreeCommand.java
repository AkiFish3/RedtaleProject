package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.redtale.project.core.RedtaleCore;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /redtale tree list|info —— 技能树静态定义的只读观测面（M7 / D25）。 */
public final class TreeCommand {

    private TreeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("tree")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))));
    }

    private static int list(CommandSourceStack src) {
        var trees = RedtaleCore.get().skillTrees();
        StringBuilder sb = new StringBuilder("[redtale] 技能树 ")
                .append(trees.size()).append(" 棵");
        if (trees.size() == 0) {
            sb.append("\n  （没有技能树定义：读 config/redtale_project/skill_trees）");
        } else {
            sb.append(" · 节点 ").append(trees.nodeCount())
                    .append(" 个 · 点数上限 ").append(trees.pointTotal());
            for (String id : trees.sortedIds()) {
                var def = trees.get(id);
                sb.append("\n  ").append(id).append(" · ").append(def.name())
                        .append(" · 职业 ").append(classes(def.classes()))
                        .append(" · 节点 ").append(def.nodes().size())
                        .append(" · 点数 ").append(def.maxPoints());
            }
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return trees.size();
    }

    private static int info(CommandSourceStack src, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        var trees = RedtaleCore.get().skillTrees();
        var def = trees.get(id);
        if (def == null) {
            String miss = "[redtale] 没有技能树 " + id + "（现有 " + trees.size()
                    + " 棵：" + String.join(",", trees.sortedIds()) + "）";
            src.sendSuccess(() -> Component.literal(miss), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[redtale] ").append(id)
                .append(" · ").append(def.name())
                .append(" · 上限 ").append(def.maxPoints()).append(" 点")
                .append(" · 每级 ").append(def.pointsPerLevel()).append(" 点")
                .append(" · 节点 ").append(def.nodes().size())
                .append("\n  职业：").append(classes(def.classes()))
                .append(" · 来源 ").append(def.source());
        for (var node : def.nodes().values()) {
            int[] pos = node.pos();
            sb.append("\n  节点 ").append(node.id()).append(" · ").append(node.name())
                    .append(" · cost ").append(node.cost())
                    .append(" · pos ").append(pos[0]).append(",").append(pos[1])
                    .append(" · 需 ").append(node.requires().size())
                    .append(" · 效果 ").append(node.effects().size());
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return def.nodes().size();
    }

    private static String classes(List<String> list) {
        return list == null || list.isEmpty() ? "全职业" : String.join(",", list);
    }
}
