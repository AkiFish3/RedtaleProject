package com.redtale.project.core.command;

import com.redtale.project.core.RedtaleCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /redtale display —— 显示口径生效快照只读观测面（D20 / Q-023 收口）。文本拆 text() 便于离线断言。 */
public final class DisplayCommand {

    private DisplayCommand() {}

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> node() {
        return Commands.literal("display").executes(ctx -> display(ctx.getSource()));
    }

    private static int display(CommandSourceStack src) {
        var core = RedtaleCore.get();
        var settings = core.display();
        String text = text(settings, core.configWarnings().size());
        src.sendSuccess(() -> Component.literal(text), false);
        return settings.showDps() ? 1 : 0;
    }

    /** 显示口径全文（纯函数：只吃快照；warningCount = configWarnings().size()）。 */
    public static String text(com.redtale.project.core.config.DisplaySpec.Settings settings, int warningCount) {
        String dps = settings.showDps() ? "开" : "关";
        String ab = settings.showAttributeBreakdown() ? "开" : "关";
        String source = settings.source().isEmpty() ? "未载入（默认值）" : settings.source();
        StringBuilder sb = new StringBuilder("[redtale] 显示口径：DPS ").append(dps)
                .append(" · 属性明细 ").append(ab)
                .append(" · 假想目标 ").append(settings.assumeTarget())
                .append(" · 来源 ").append(source);
        if (warningCount > 0)
            sb.append("\n  本次 reload 告警 ").append(warningCount).append(" 条（/redtale reload 看详情）");
        return sb.toString();
    }
}
