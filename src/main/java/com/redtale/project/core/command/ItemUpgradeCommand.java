package com.redtale.project.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.redtale.project.items.ItemUpgrader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /redtale item upgrade} —— 给执行者自己的背包与末影箱跑一遍物品更新（D33 的「敢写」那一半）。
 *
 * <p>{@code item scan} 只读、{@code ItemUpgrader} 只写单件，本类只做这三件胶水：
 * <b>遍历 → 计数 → 让客户端立刻看到</b>。所有判定都在 {@code ensure} 里，这里不重复判断
 * （尤其不 {@code copy()} 一份再写回——那会把「写回唯一入口」拆成两份）。
 *
 * <p>单格失败不影响整轮：{@code ensure} 自己吞大部分异常，漏出来的 {@link RuntimeException}
 * 在这里按格兜住并记一条提示，继续下一格。整轮中断一次就是「背包里剩下的物品永远不升级」。
 */
public final class ItemUpgradeCommand {

    /** 提示最多逐条打 3 行，剩余的只报条数，避免一条命令刷屏。 */
    private static final int MAX_WARNING_LINES = 3;

    private ItemUpgradeCommand() {
    }

    public static int upgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> warnings = new ArrayList<>();
        int scanned = 0;
        int upgraded = 0;
        int unchanged = 0;
        int orphan = 0;
        int failed = 0;

        // ---- 背包：size 已含护甲与副手，不要自己算槽位 ----
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            scanned++;
            ItemUpgrader.Result result;
            try {
                result = ItemUpgrader.ensure(stack);
            } catch (RuntimeException ex) {
                failed++;
                warnings.add("背包第 " + slot + " 格更新失败（" + ex + "），已跳过");
                continue;
            }
            // NOT_CONTENT（普通原版物品）不计任何一类，按卡面口径。
            ItemUpgrader.Status status = result.status();
            if (status == ItemUpgrader.Status.UPGRADED) {
                upgraded++;
            } else if (status == ItemUpgrader.Status.UNCHANGED) {
                unchanged++;
            } else if (status == ItemUpgrader.Status.ORPHAN) {
                orphan++;
            }
            warnings.addAll(result.warnings());
        }

        // ---- 末影箱：同样逐格，但不 setChanged（下次打开时自然同步） ----
        int enderUpgraded = 0;
        int enderOrphan = 0;
        Container enderChest = player.getEnderChestInventory();
        for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
            ItemStack stack = enderChest.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            try {
                ItemUpgrader.Result result = ItemUpgrader.ensure(stack);
                ItemUpgrader.Status status = result.status();
                if (status == ItemUpgrader.Status.UPGRADED) {
                    enderUpgraded++;
                } else if (status == ItemUpgrader.Status.ORPHAN) {
                    enderOrphan++;
                }
                warnings.addAll(result.warnings());
            } catch (RuntimeException ex) {
                failed++;
                warnings.add("末影箱第 " + slot + " 格更新失败（" + ex + "），已跳过");
            }
        }

        // ---- 真有升级才推一次菜单同步；不 broadcastFullState、不逐格 setRemoteSlot ----
        if (upgraded > 0 || enderUpgraded > 0) {
            player.inventoryMenu.broadcastChanges();
        }

        StringBuilder summary = new StringBuilder("物品更新：扫描 ")
                .append(scanned)
                .append(" / 升级 ").append(upgraded)
                .append(" / 已最新 ").append(unchanged)
                .append(" / 孤儿 ").append(orphan);
        if (enderUpgraded > 0 || enderOrphan > 0) {
            summary.append(" · 末影箱 升级 ").append(enderUpgraded)
                    .append("/孤儿 ").append(enderOrphan);
        }
        String line = summary.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);

        int shown = Math.min(warnings.size(), MAX_WARNING_LINES);
        for (int i = 0; i < shown; i++) {
            String warning = warnings.get(i);
            player.sendSystemMessage(Component.literal(warning));
        }
        if (warnings.size() > shown) {
            String rest = "…另有 " + (warnings.size() - shown) + " 条更新提示未显示";
            player.sendSystemMessage(Component.literal(rest));
        }
        return upgraded + enderUpgraded;
    }
}
