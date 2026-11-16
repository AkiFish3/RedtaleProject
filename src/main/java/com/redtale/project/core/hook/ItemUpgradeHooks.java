package com.redtale.project.core.hook;

import com.redtale.project.items.ItemUpgrader;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * D33 物品更新的三个「被动」刷新点（{@code docs/ITEM-UPDATE.md} §4.1 的 ①②③）。
 *
 * <p>为什么是这三个点（而不是「物品被移出容器」）：原版没有那个事件，玩家从箱子里拖进背包
 * 在服务端只是 {@code AbstractContainerMenu#clicked} 里的一次 {@code setItem}，没有钩子。
 * 所以把迁移压在更可靠的边界上——登录 / 开容器 / 区块加载，再靠读路径惰性兜底。
 *
 * <p>第 ④ 个点（reload 后全量分帧）不在这里：那要跨 tick 排程，归 {@code /redtale reload} 之后
 * 的管理员动作，另有实现。本类只做「顺手扫一遍」，扫不到就等下一次，**绝不为了扫物品卡主线程**。
 */
public final class ItemUpgradeHooks {

    private static final Logger LOG = LoggerFactory.getLogger("redtale/item-update");

    /** 单例：{@code GameHooks} 注册它，{@link UpgradeSweep} 也复用同一个计数器。 */
    private static final ItemUpgradeHooks INSTANCE = new ItemUpgradeHooks();

    public static ItemUpgradeHooks get() {
        return INSTANCE;
    }

    /** 累计升级件数，给 {@code status} 与排障用；只增不减（重启归零）。 */
    private volatile long upgradedTotal;

    public long upgradedTotal() {
        return upgradedTotal;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            scanPlayer(player, "login");
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            scanPlayer(player, "dimension");
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            scanPlayer(player, "respawn");
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)) {
            return;
        }
        int changed = 0;
        for (var slot : event.getContainer().slots) {
            if (ensure(slot.getItem())) {
                changed++;
                slot.setChanged();
            }
        }
        report("container", changed);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return; // 客户端也会收到这个事件，只认服务端
        }
        int changed = 0;
        for (var pos : event.getChunk().getBlockEntitiesPos()) {
            if (event.getChunk().getBlockEntity(pos) instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    if (ensure(container.getItem(i))) {
                        container.setChanged();
                        changed++;
                    }
                }
            }
        }
        report("chunk[" + level.dimension().location() + "]", changed);
    }

    /** 扫一名在线玩家的背包（{@code Inventory} 的 size 已含护甲与副手，不自己算槽位）。 */
    public int scanPlayer(net.minecraft.server.level.ServerPlayer player, String reason) {
        var inv = player.getInventory();
        int changed = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ensure(inv.getItem(i))) {
                changed++;
            }
        }
        report(reason, changed);
        return changed;
    }

    /** 单件：返回「是否真的写了东西」，供调用方决定要不要 {@code setChanged()}。同包内给 {@link UpgradeSweep} 复用。 */
    boolean ensure(ItemStack stack) {
        var result = ItemUpgrader.ensure(stack);
        if (result.status() == ItemUpgrader.Status.UPGRADED) {
            upgradedTotal++;
            for (String w : result.warnings()) {
                LOG.info("[{}] {} rev {}→{}：{}", result.status(), result.contentId(),
                        result.fromRev(), result.toRev(), w);
            }
            return true;
        }
        return false;
    }

    private void report(String reason, int changed) {
        // 只在真的动了东西时打日志：这三个钩子每 tick 都可能触发，静默路径不能刷屏。
        if (changed > 0) {
            LOG.info("刷新点 {} 更新 {} 件（累计 {}）", reason, changed, upgradedTotal);
        }
    }
}
