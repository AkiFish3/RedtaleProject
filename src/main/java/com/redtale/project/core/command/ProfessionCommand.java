package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.progression.ProfessionRegistry;
import com.redtale.project.core.progression.ProfessionService;
import com.redtale.project.core.progression.ProfessionSpec;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /redtale profession —— 专业定义只读观测面（D24 L5）：{@code list} 列全部、{@code info} 看单个。
 * 只读 {@link ProfessionRegistry} 快照，不解析配置、不写存档、不下发属性。
 * 输出逐字钉死（卡面 T-0063 / R67），格式化拆成纯函数便于离线断言。
 */
public final class ProfessionCommand {

    private ProfessionCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("profession")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))));
    }

    private static int list(CommandSourceStack src) {
        var reg = RedtaleCore.get().professions();
        String text = listText(reg);
        src.sendSuccess(() -> Component.literal(text), false);
        return reg.size();
    }

    /** {@code profession list} 全文（纯函数：只读快照）。 */
    public static String listText(ProfessionRegistry reg) {
        StringBuilder sb = new StringBuilder("[redtale] 专业 ").append(reg.size()).append(" 个");
        if (reg.size() == 0) {
            return sb.append("\n  （没有专业定义：读 config/redtale_project/professions）").toString();
        }
        sb.append(" · 属性 ").append(reg.attributeTotal()).append(" 条");
        for (String id : reg.sortedIds()) {
            var def = reg.get(id);
            sb.append("\n  ").append(def.id()).append(" · ").append(def.name())
                    .append(" · 上限 ").append(def.curve().maxLevel())
                    .append(" · 属性 ").append(def.attributes().size()).append(" 条")
                    .append(" · grants ").append(grantCounts(def));
        }
        return sb.toString();
    }

    private static int info(CommandSourceStack src, String raw) {
        var def = RedtaleCore.get().professions().find(raw);
        if (def == null) {
            String miss = "[redtale] 没有这个专业：" + raw + "（用 /redtale profession list 看清单）";
            src.sendSuccess(() -> Component.literal(miss), false);
            return 0;
        }
        String playerName = null;
        long total = 0L;
        if (src.getEntity() instanceof ServerPlayer player) {
            playerName = player.getGameProfile().getName();
            total = ProfessionService.expOf(src.getServer(), player.getUUID(), def.id());
        }
        String text = infoText(def, playerName, total);
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** {@code profession info} 全文（纯函数：{@code playerName == null} 即控制台，不追加玩家行）。 */
    public static String infoText(ProfessionSpec.Definition def, String playerName, long total) {
        StringBuilder sb = new StringBuilder("[redtale] 专业 ").append(def.id())
                .append(" · ").append(def.name()).append(" · 上限 ").append(def.curve().maxLevel());
        Map<String, Double> attrs = def.attributes();
        sb.append("\n  属性 ").append(attrs.size()).append(" 条");
        if (!attrs.isEmpty()) {
            sb.append("：");
            boolean first = true;
            for (Map.Entry<String, Double> e : attrs.entrySet()) {
                if (!first) sb.append(" · ");
                first = false;
                sb.append(e.getKey()).append(" +").append(String.valueOf(e.getValue())).append("/级");
            }
        }
        sb.append("\n  grants：mine ").append(grantCount(def, "mine"))
                .append(" 条 / craft ").append(grantCount(def, "craft"))
                .append(" 条 / smelt ").append(grantCount(def, "smelt")).append(" 条");
        if (playerName != null) {
            sb.append("\n  玩家 ").append(playerName).append("：")
                    .append(ExpCommand.professionLine(def, total));
        }
        return sb.toString();
    }

    /** grants 摘要 {@code mine/craft/smelt}（顺序固定，不含 default 条目）。 */
    public static String grantCounts(ProfessionSpec.Definition def) {
        return grantCount(def, "mine") + "/" + grantCount(def, "craft") + "/" + grantCount(def, "smelt");
    }

    /** 某源下非 default 的 grant 条数。 */
    public static int grantCount(ProfessionSpec.Definition def, String source) {
        int n = 0;
        for (ProfessionSpec.Grant g : ProfessionSpec.grantsOf(def, source)) {
            if (!"default".equals(g.token())) n++;
        }
        return n;
    }
}
