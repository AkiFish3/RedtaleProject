package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.progression.ProgressRegistry;
import com.redtale.project.core.progression.ProgressSpec;
import com.redtale.project.core.progression.ProgressStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /redtale level list|info —— 等级 / 职业定义的只读对账面（M7 观测面）。 */
public final class LevelCommand {

    private LevelCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("level")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))));
    }

    private static int list(CommandSourceStack src) {
        ProgressRegistry progress = RedtaleCore.get().progress();
        StringBuilder sb = new StringBuilder("[redtale] 主等级 ")
                .append(progress.levelCount()).append(" 条 · 职业 ").append(progress.jobCount())
                .append(" 条 · 按级属性 ").append(progress.attributeCount()).append(" 条");
        if (progress.size() == 0) {
            sb.append("\n  （没有等级定义：读 config/redtale_project/levels 与 /jobs）");
            String empty = sb.toString();
            src.sendSuccess(() -> Component.literal(empty), false);
            return 0;
        }
        for (String id : progress.sortedIds(false)) {
            var def = progress.get(id);
            sb.append("\n  主等级 ").append(id).append(" · ").append(def.name())
                    .append(" · 上限 ").append(def.curve().maxLevel())
                    .append(" · L1→L2 ").append(def.curve().expForLevel(1))
                    .append(" · L2→L3 ").append(step(def.curve().maxLevel(), def.curve().expForLevel(2)))
                    .append(" · 属性 ").append(def.attributes().size()).append(" 条");
        }
        for (String id : progress.sortedIds(true)) {
            var def = progress.get(id);
            sb.append("\n  职业 ").append(id).append(" · ").append(def.name())
                    .append(" · 上限 ").append(def.curve().maxLevel())
                    .append(" · L1→L2 ").append(def.curve().expForLevel(1))
                    .append(" · L2→L3 ").append(step(def.curve().maxLevel(), def.curve().expForLevel(2)))
                    .append(" · 属性 ").append(def.attributes().size()).append(" 条")
                    .append(" · 经验 ").append(sources(def.expSources()));
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return progress.size();
    }

    private static int info(CommandSourceStack src, String id) {
        ProgressRegistry progress = RedtaleCore.get().progress();
        var def = progress.get(id);
        if (def == null) {
            String miss = "[redtale] 没有等级定义 " + id + "（现有 " + progress.size()
                    + " 条：" + String.join(",", allIds(progress)) + "）";
            src.sendSuccess(() -> Component.literal(miss), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[redtale] ").append(id)
                .append(def.job() ? " [职业] " : " [等级] ").append(def.name())
                .append(" · 上限 ").append(def.curve().maxLevel())
                .append(" · 来源 ").append(def.source())
                .append("\n  曲线：L1→L2 ").append(def.curve().expForLevel(1))
                .append(" · L2→L3 ").append(step(def.curve().maxLevel(), def.curve().expForLevel(2)))
                .append(" · 满级累计 ").append(def.curve().totalExpAtMax());
        if (def.attributes().isEmpty()) {
            sb.append("\n  属性 0 条：（无）");
        } else {
            sb.append("\n  属性 ").append(def.attributes().size()).append(" 条：");
            boolean first = true;
            for (var e : def.attributes().entrySet()) {
                if (!first) sb.append(" · ");
                first = false;
                sb.append(e.getKey()).append(" ").append(fmt(e.getValue())).append("/级");
            }
        }
        if (def.job()) sb.append("\n  经验来源：").append(sources(def.expSources()));
        ServerPlayer player = src.getPlayer();
        if (player != null) sb.append(current(player, id, def));
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * 纯函数形态（R49-a）：等级派生 + 本级进度文案，**零 MC 依赖** → 探针可直测。
     * 等级是派生值（R42-b），一律现算不读缓存；满级不出「/0」。
     */
    public static String currentLine(com.redtale.project.core.progression.ExpCurve curve, long totalExp) {
        int level = curve.levelFor(totalExp);
        if (level >= curve.maxLevel()) {
            return "Lv" + level + " · 本级已满 · 累计 " + totalExp;
        }
        long cur = totalExp - curve.totalExpForLevel(level);
        return "Lv" + level + " · 本级 " + cur + "/" + curve.expForLevel(level) + " · 累计 " + totalExp;
    }

    /**
     * 「当前」段（R49）：只在该定义有玩家上下文时追加——控制台执行保持原输出不变（既有验收文案零回归）。
     */
    private static String current(ServerPlayer player, String id, ProgressSpec.Definition def) {
        ProgressStore store = ProgressStore.get(player.server);
        long total = def.job() ? store.jobExp(player.getUUID(), id) : store.mainExp(player.getUUID());
        return "\n  当前 " + currentLine(def.curve(), total);
    }

    private static String step(int maxLevel, long exp) {
        return maxLevel == 2 ? "-" : String.valueOf(exp);
    }

    private static String sources(List<String> list) {
        return list.isEmpty() ? "（无）" : String.join(",", list);
    }

    private static List<String> allIds(ProgressRegistry progress) {
        List<String> ids = new ArrayList<>(progress.sortedIds(false));
        ids.addAll(progress.sortedIds(true));
        Collections.sort(ids);
        return ids;
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return v > 0.0 ? "+" + s : s;
    }
}
