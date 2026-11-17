package com.redtale.project.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.items.ItemContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * {@code /redtale content list} —— 已载入内容的只读清单（排障 / 验收用）。
 *
 * <p>与 {@code /redtale items} 的区别：那条按属性逐行展开，看的是「一件装备写了什么」；
 * 这条一行一件，看的是「服上有哪些内容、各写了多少条」——内容是配置驱动的，
 * 条数对不上就是配置或被跳过的那条出了问题。
 */
public final class ContentListCommand {

    private ContentListCommand() {
    }

    public static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        RedtaleCore core = RedtaleCore.get();
        List<ItemContent> all = core.contentItems().all();

        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[redtale] 没有装备内容（读 "
                    + core.config().root().resolve("items") + "/*.json5；先跑 /redtale reload）"), false);
            return 0;
        }

        int statTotal = 0;
        int withWeaponAttributes = 0;
        for (ItemContent content : all) {
            statTotal += content.stats().size();
            if (!content.weaponAttributes().isEmpty()) {
                withWeaponAttributes++;
            }
        }
        int count = all.size();
        int stats = statTotal;
        int weapons = withWeaponAttributes;
        int pools = core.affixPools().size();
        int sets = core.sets().size();
        src.sendSuccess(() -> Component.literal("[redtale] 内容 " + count + " 件 · 数值共 "
                + stats + " 条 · 带 weapon_attributes " + weapons + " 件 · 词条池 " + pools
                + " 个 · 套装定义 " + sets + " 个"), false);

        for (ItemContent content : all) {
            String base = content.base() == null ? "-" : content.base().id();
            String setId = content.setId() == null || content.setId().isBlank() ? "-" : content.setId();
            int sockets = content.sockets() == null ? 0 : content.sockets().count();
            String line = "  " + content.id()
                    + " · base " + base
                    + " · 数值 " + content.stats().size() + " 条"
                    + " · 孔位 " + sockets
                    + " · 套装 " + setId
                    + " · 消耗品 " + (content.consumable() != null ? "是" : "否")
                    + " · 修理 " + (content.repair() != null ? "是" : "否")
                    + " · rev " + content.rev();
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return count;
    }
}
