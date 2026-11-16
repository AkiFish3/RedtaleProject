package com.redtale.project.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.items.ContentComponents;
import com.redtale.project.items.ContentData;
import com.redtale.project.items.ItemContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code /redtale item scan} —— 在线玩家背包里内容物品的只读分布（D33 运营可见性）。
 *
 * <p>为什么要有它：内容 id 与 rev 都在组件里，服上看不出「有多少件老物品、有多少件已经
 * 定义被删了」。先看清分布，再谈批量升级；所以本命令**严格只读**——不 {@code set}、
 * 不 {@code remove}、不写盘、不打日志，扫完的物品与扫前逐字节一致。
 *
 * <p>孤儿（定义已不存在）与待更新（rev 落后）**互斥**：定义都没有就谈不上 rev 落后，
 * 所以先判孤儿。两行数相加才等于「有问题」的总件数，不会重复计数。
 */
public final class ItemScanCommand {

    /** 孤儿 id 最多列 5 个，剩余的折成「…等 Q 个」，避免一条命令刷屏。 */
    private static final int MAX_ORPHAN_IDS = 5;

    private ItemScanCommand() {
    }

    public static int scan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getServer() == null) {
            src.sendFailure(Component.literal("扫描需要服务端上下文"));
            return 0;
        }
        List<ServerPlayer> players = src.getServer().getPlayerList().getPlayers();

        int contentCount = 0;
        Map<Integer, Integer> byRev = new TreeMap<>();
        Set<String> orphanIds = new LinkedHashSet<>();
        int orphanCount = 0;
        int pendingCount = 0;

        for (ServerPlayer player : players) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ContentData data = stack.get(ContentComponents.CONTENT.get());
                if (data == null) {
                    continue;
                }
                contentCount++;
                byRev.merge(data.contentRev(), 1, Integer::sum);
                ItemContent definition = RedtaleCore.get().contentItems().get(data.contentId());
                if (definition == null) {
                    orphanCount++;
                    orphanIds.add(data.contentId());
                } else if (data.contentRev() != definition.rev()) {
                    pendingCount++;
                }
            }
        }

        int playerCount = players.size();
        int total = contentCount;
        src.sendSuccess(() -> Component.literal(
                "扫描 " + playerCount + " 名玩家：" + total + " 件内容物品"), false);
        if (contentCount == 0) {
            src.sendSuccess(() -> Component.literal("（无内容物品）"), false);
            return 0;
        }

        for (Map.Entry<Integer, Integer> entry : byRev.entrySet()) {
            String line = "rev=" + entry.getKey() + "  " + entry.getValue() + " 件";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        String orphans = orphanLine(orphanCount, orphanIds);
        src.sendSuccess(() -> Component.literal(orphans), false);
        int pending = pendingCount;
        src.sendSuccess(() -> Component.literal("待更新（rev 落后）：" + pending + " 件"), false);
        return contentCount;
    }

    /** {@code 孤儿（定义已不存在）：P 件} + 有孤儿时追加 {@code ：id1, id2 …等 Q 个}。 */
    private static String orphanLine(int count, Set<String> ids) {
        StringBuilder line = new StringBuilder("孤儿（定义已不存在）：").append(count).append(" 件");
        if (count <= 0 || ids.isEmpty()) {
            return line.toString();
        }
        List<String> all = new ArrayList<>(ids);
        line.append("：").append(String.join(", ", all.subList(0, Math.min(MAX_ORPHAN_IDS, all.size()))));
        if (all.size() > MAX_ORPHAN_IDS) {
            line.append(" …等 ").append(all.size() - MAX_ORPHAN_IDS).append(" 个");
        }
        return line.toString();
    }
}
