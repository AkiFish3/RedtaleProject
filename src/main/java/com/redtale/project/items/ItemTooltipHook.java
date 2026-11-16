package com.redtale.project.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 把 {@link ContentTooltip} 的行贴到物品 tooltip 上（D20）。
 *
 * <p>挂在 {@code ItemTooltipEvent} 而不是覆写 {@code Item#appendHoverText}：
 * 后者要给 14 个基底各写一遍/继承，而这个事件一处覆盖全部——
 * 也正因为「一个新事件点 = 换 jar」，这里只碰这一个点。
 *
 * <p>客户端与服务端都会触发；服务端那次给 {@code /redtale tooltip} 之外的自动化检查留余地。
 */
public final class ItemTooltipHook {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!stack.has(ContentComponents.CONTENT.get())) {
            return;
        }
        for (String line : ContentTooltip.lines(stack, event.getEntity())) {
            event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
