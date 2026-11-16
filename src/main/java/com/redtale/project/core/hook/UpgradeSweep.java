package com.redtale.project.core.hook;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;

/**
 * D33 的第 ④ 个刷新点：{@code /redtale reload} 之后把**已加载**的世界全量刷一遍
 * （{@code docs/ITEM-UPDATE.md} §4.1 ④）。
 *
 * <p>为什么不能顺手做：①②③（登录 / 开容器 / 区块加载）都只覆盖「刚刚被碰到的那一小块」。
 * 改完配置，箱子里的老物品不会自己更新，要等玩家开箱或区块重载。第 ④ 个点是唯一能
 * 一次性补齐的手段，代价是 O(已加载区块)，所以**必须分帧**——每 tick 只吃
 * {@link #CHUNKS_PER_TICK} 个区块，绝不为了扫物品卡主线程。
 *
 * <p>队列里存 {@code (level, ChunkPos)} 而不是 {@code LevelChunk} 对象：排队期间区块可能被卸载，
 * 拿到手里再 {@code getChunkNow} 一次，取不到就跳过（下次区块加载时 ③ 会补）。
 *
 * <p>触发点只有两处：{@code RedtaleCore.reload()}（服务端在跑时）与 {@code /redtale item sweep}。
 * 启动期不触发——那时的 {@code ServerStartedEvent} 刚加载完资源，没有「配置换了」这件事。
 */
public final class UpgradeSweep {

    private static final Logger LOG = LoggerFactory.getLogger("redtale/upgrade-sweep");

    /** 每 tick 处理的区块数。8 块/tick ≈ 400 块/秒，可视距离内的世界几秒扫完。 */
    private static final int CHUNKS_PER_TICK = 8;

    private static final UpgradeSweep INSTANCE = new UpgradeSweep();

    /** 排队中的区块（服务端线程独占，不加锁）。 */
    private final ArrayDeque<Target> queue = new ArrayDeque<>();
    /** 反射拿到的 {@code ChunkMap#getChunks()}；拿不到就只扫实体与背包。 */
    private Method chunkGetter;
    private boolean chunkGetterResolved;

    private boolean active;
    private int chunksTotal;
    private int chunksDone;
    private int chunksSkipped;
    private int upgraded;
    private int entitiesScanned;
    private long startedAt;
    private int lastUpgraded;

    private UpgradeSweep() {
    }

    public static UpgradeSweep get() {
        return INSTANCE;
    }

    /**
     * 排一次全量刷新：玩家背包立即扫（很便宜），掉落物 / 展示框立即扫，容器按区块排队分帧扫。
     *
     * @return 排进队列的区块数；服务端没在跑时返回 0 且什么都不做
     */
    public static int schedule(MinecraftServer server) {
        return INSTANCE.begin(server);
    }

    /** 供 {@code /redtale status} 与排障用。 */
    public static String status() {
        UpgradeSweep s = INSTANCE;
        if (s.active) {
            return "全量刷新进行中 " + s.chunksDone + "/" + s.chunksTotal + " 区块，已升级 " + s.upgraded + " 件";
        }
        return s.chunksTotal == 0 ? "全量刷新：未跑过"
                : "全量刷新：上次 " + s.chunksTotal + " 区块 / 升级 " + s.upgraded + " 件";
    }

    public static int lastUpgraded() {
        return INSTANCE.lastUpgraded;
    }

    private int begin(MinecraftServer server) {
        if (server == null || !server.isRunning()) {
            return 0;
        }
        // 重入即重排：上一次没扫完的区块换成新定义下的新队列，不会把旧结果留在队列里。
        queue.clear();
        chunksTotal = 0;
        chunksDone = 0;
        chunksSkipped = 0;
        upgraded = 0;
        entitiesScanned = 0;
        startedAt = System.currentTimeMillis();

        // ① 在线玩家背包：41 格 × 在线数，成本可忽略，不必排队。
        ItemUpgradeHooks hooks = ItemUpgradeHooks.get();
        int fromPlayers = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            fromPlayers += hooks.scanPlayer(player, "sweep");
        }
        upgraded += fromPlayers;

        // ② 实体：掉落物与展示框（§4.1 ③ 里点过名，但它们不在方块实体表里）
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item) {
                    entitiesScanned++;
                    if (hooks.ensure(item.getItem())) {
                        upgraded++;
                    }
                } else if (entity instanceof ItemFrame frame) {
                    entitiesScanned++;
                    if (hooks.ensure(frame.getItem())) {
                        upgraded++;
                    }
                }
            }
            // ③ 容器：按区块排队，分帧处理
            for (ChunkHolder holder : holders(level)) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    // 强制加载 / 出生点区块只到「可发送」档，不参与实体 tick，但容器照样要扫
                    chunk = holder.getChunkToSend();
                }
                if (chunk == null) {
                    chunksSkipped++;
                    continue;
                }
                queue.add(new Target(level, chunk.getPos().x, chunk.getPos().z));
            }
        }
        chunksTotal = queue.size();
        active = !queue.isEmpty();
        lastUpgraded = upgraded;

        if (!active) {
            LOG.info("全量刷新：无待扫区块（{} 个实体，背包升级 {} 件）", entitiesScanned, fromPlayers);
            return 0;
        }
        LOG.info("全量刷新已排程：{} 个区块 / {} 个实体 / 背包 {} 件（每 tick {} 块）",
                chunksTotal, entitiesScanned, fromPlayers, CHUNKS_PER_TICK);
        return chunksTotal;
    }

    /** 由 {@code GameHooks#onServerTickPost} 每 tick 调一次。 */
    public void tick() {
        if (!active) {
            return;
        }
        ItemUpgradeHooks hooks = ItemUpgradeHooks.get();
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            Target target = queue.poll();
            if (target == null) {
                break;
            }
            LevelChunk chunk = target.level().getChunkSource().getChunkNow(target.x(), target.z());
            if (chunk == null) {
                // 排队期间被卸载了：不算失败，下次区块加载时第 ③ 个钩子会补
                chunksSkipped++;
                continue;
            }
            chunksDone++;
            for (var pos : chunk.getBlockEntitiesPos()) {
                if (chunk.getBlockEntity(pos) instanceof Container container) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        if (hooks.ensure(container.getItem(slot))) {
                            container.setChanged();
                            upgraded++;
                        }
                    }
                }
            }
        }
        lastUpgraded = upgraded;
        if (queue.isEmpty()) {
            finish();
        }
    }

    private void finish() {
        active = false;
        LOG.info("全量刷新完成：{} 区块（跳过 {}）/ {} 实体 / 升级 {} 件，耗时 {} ms",
                chunksDone, chunksSkipped, entitiesScanned, upgraded, System.currentTimeMillis() - startedAt);
    }

    /**
     * 已加载区块的 {@link ChunkHolder} 迭代器。{@code ChunkMap#getChunks()} 是 {@code protected}，
     * 1.21.1 没有公开的「枚举已加载区块」接口，所以反射取一次并缓存；
     * 取不到就退化成「只扫实体 + 背包」（容器靠 ②③ 两个钩子慢慢补），**不抛异常、不停摆**。
     */
    @SuppressWarnings("unchecked")
    private Iterable<ChunkHolder> holders(ServerLevel level) {
        Method getter = chunkGetter();
        if (getter == null) {
            return List.of();
        }
        try {
            Object value = getter.invoke(level.getChunkSource().chunkMap);
            return value instanceof Iterable<?> iterable ? (Iterable<ChunkHolder>) iterable : List.of();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.warn("区块枚举失败（{}），本次全量刷新只覆盖实体与背包", ex.toString());
            return List.of();
        }
    }

    private Method chunkGetter() {
        if (!chunkGetterResolved) {
            chunkGetterResolved = true;
            try {
                Method method = Class.forName("net.minecraft.server.level.ChunkMap")
                        .getDeclaredMethod("getChunks");
                method.setAccessible(true);
                chunkGetter = method;
            } catch (Throwable t) {
                LOG.warn("区块枚举不可用（{}），全量刷新只覆盖实体与背包", t.toString());
            }
        }
        return chunkGetter;
    }

    /** 排队项：只存坐标，不存区块对象（区块可能中途卸载）。 */
    private record Target(ServerLevel level, int x, int z) {
    }

    /** 排障用：当前队列长度。 */
    public int pending() {
        return queue.size();
    }

    /** 排障用：本次 / 上次扫描的区块总数。 */
    public int chunksTotal() {
        return chunksTotal;
    }

    /** 排障用：已升级件数。 */
    public int upgraded() {
        return upgraded;
    }

    /** 排障用：实体扫描数。 */
    public int entitiesScanned() {
        return entitiesScanned;
    }
}
