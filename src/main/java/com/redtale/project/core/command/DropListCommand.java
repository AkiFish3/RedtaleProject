package com.redtale.project.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.mobs.DropSpec;
import com.redtale.project.mobs.MobSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /redtale drop list —— 掉落表只读清单（M6 第四层，T-0037）：列全部表 + 反查引用它的怪。只读快照，不解析、不写盘。 */
public final class DropListCommand {
    private static final int MAX_LISTED = 5;

    private DropListCommand() {
    }

    /** 命令节点：挂到已存在的 redtale 树（同包无需 import，Brigadier 按同名合并）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("drop")
                .then(Commands.literal("list").executes(DropListCommand::list));
    }

    /** 返回表张数（不是问题条数，与 mob check 口径不同，卡面写死）。 */
    public static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        Map<String, DropSpec.Definition> tables = core.drops();
        if (tables.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有掉落表（读 "
                    + core.config().root().resolve("drops") + "/*.json5）"), false);
            return 0;
        }
        List<String> ids = new ArrayList<>(tables.keySet());
        ids.sort(Comparator.naturalOrder());
        Map<String, List<String>> refs = new LinkedHashMap<>();
        for (MobSpec.Definition mob : core.mobs().all().values()) {
            String ref = mob.drops();
            if (ref == null || ref.isEmpty()) continue;
            refs.computeIfAbsent(ref, k -> new ArrayList<>()).add(mob.name());
        }
        int entries = 0;
        for (String id : ids) entries += tables.get(id).drops().size();
        int cited = 0;
        for (String id : ids) if (!refs.getOrDefault(id, List.of()).isEmpty()) cited++;
        String head = "[redtale] 掉落表 " + tables.size() + " 张 · 条目合计 "
                + entries + " 条 · 被引用 " + cited + " 张";
        src.sendSuccess(() -> Component.literal(head), false);
        for (String id : ids) {
            DropSpec.Definition def = tables.get(id);
            List<String> names = refs.getOrDefault(id, List.of());
            String tail = names.isEmpty() ? "未被引用"
                    : "引用 " + names.size() + " 只："
                            + String.join(", ", names.subList(0, Math.min(MAX_LISTED, names.size())))
                            + (names.size() > MAX_LISTED ? "…" : "");
            String line = "  " + id + " · xp " + def.xp() + " · 条目 "
                    + def.drops().size() + " · " + tail;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return tables.size();
    }
}
