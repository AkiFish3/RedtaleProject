package com.redtale.project.core.datapack;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.List;

/**
 * 让「运行期新写出来的数据包」立刻生效（R7 遗留的唯一缺口）。
 *
 * <p>症状：reload 时我们往 {@code <world>/datapacks/redtale_project/} 写文件，
 * 但 {@link PackRepository} 的可用列表是启动时扫的，写完之后它既不知道有新包，
 * 也不会自动选中 → 生成的东西（武器动作 / 附魔）要等下一次重启才进注册表。
 *
 * <p>做法三步，缺一步都不生效：
 * <ol>
 *   <li>{@code repository.reload()} —— 重扫磁盘，让新目录进 available；</li>
 *   <li>{@code repository.addPack(id)} —— 选中它（只改选中状态，不动资源）；</li>
 *   <li>{@code server.reloadResources(selectedIds)} —— 真正重载数据包，
 *       与原版 {@code /reload} 同一个入口。</li>
 * </ol>
 *
 * <p><b>启动期不触发重载</b>：{@code ServerStartedEvent} 里还会调一次本类，
 * 那时资源已加载完、重载没有意义且会拖慢开服，只做「选中」并留给下次原版重载。
 *
 * <p>只认一个 id，不做通用包管理 —— 本 mod 只生成一个包（{@code file/redtale_project}），
 * 多包场景没有需求就不写，避免变成一个半成品 API。
 */
public final class PackEnabler {

    /** 包 id 在 {@code PackRepository} 里的形态：世界目录下的数据包前缀固定是 {@code file/}。 */
    public static final String WORLD_PACK_PREFIX = "file/";

    /** 结果用于日志与 {@code /redtale status}，不参与逻辑分支。 */
    public enum Outcome {
        /** 已经在选中列表里，且本次没有资源级改动，什么都不用做（重启后的常态）。 */
        ALREADY_SELECTED,
        /** 已经在选中列表里，但本次**改写了资源文件**（tag / 战利品表 / 附魔 JSON），已顺手重载。 */
        ALREADY_SELECTED_AND_RELOADED,
        /** 磁盘上有目录但仓库扫不到 —— 通常是生成器没写出文件。 */
        NOT_AVAILABLE,
        /** 新选中并在运行期完成重载 —— 生成物已进注册表。 */
        ENABLED_AND_RELOADED,
        /** 新选中但没重载（启动期，或上一轮刚重载过）。 */
        ENABLED_PENDING_RELOAD,
        /** {@code addPack} 被拒。 */
        FAILED
    }

    private static final long RELOAD_GUARD_MS = 2_000L;
    private static long lastReloadAt = 0L;

    private PackEnabler() {
    }

    public static Outcome ensureEnabled(MinecraftServer server, String packId, List<String> warnings) {
        return ensureEnabled(server, packId, warnings, false);
    }

    /**
     * 同上，但允许「包已选中时也重载资源」。
     *
     * <p>为什么需要这一条（R39 实测）：{@code /redtale reload} 会把 tag 文件重写到磁盘上，
     * 但包<b>早就在选中列表里</b> → 老逻辑走 {@code ALREADY_SELECTED} 直接返回，一个字节都没重载。
     * 现象：`data/minecraft/tags/entity_type/undead.json` 明明写着 {@code redtale_project:caster}，
     * {@code @e[type=#minecraft:undead]} 却匹配不到，必须再敲一次原版 {@code /reload} 才生效 ——
     * 这正是 D14「全内容可配 + 热重载」的漏点。
     *
     * @param resourcesChanged 本次 reload 是否真的写了文件（各生成器的写出计数 &gt; 0）；
     *                         为 {@code false} 时行为与三参版本完全一致（幂等，不白重载）
     */
    public static Outcome ensureEnabled(MinecraftServer server, String packId, List<String> warnings,
                                        boolean resourcesChanged) {
        if (server == null || packId == null || packId.isBlank()) {
            return Outcome.NOT_AVAILABLE;
        }
        String id = packId.startsWith(WORLD_PACK_PREFIX) ? packId : WORLD_PACK_PREFIX + packId;

        PackRepository repository = server.getPackRepository();
        // ① 重扫：刚写完的目录此刻才会出现在 available 里
        repository.reload();

        if (repository.getSelectedIds().contains(id)) {
            if (!resourcesChanged || !server.isRunning()) {
                return Outcome.ALREADY_SELECTED;
            }
            if (!passGuard()) {
                // 2s 内刚重载过：同一批文件已经进去了，别再来一次
                return Outcome.ALREADY_SELECTED;
            }
            server.reloadResources(repository.getSelectedIds());
            return Outcome.ALREADY_SELECTED_AND_RELOADED;
        }
        if (!repository.getAvailableIds().contains(id)) {
            warnings.add("数据包 " + id + " 未出现在可用列表（生成器没有写出文件？）");
            return Outcome.NOT_AVAILABLE;
        }

        // ② 选中
        if (!repository.addPack(id)) {
            warnings.add("数据包 " + id + " 选中失败（addPack 被拒）");
            return Outcome.FAILED;
        }

        // ③ 重载。启动期不重载：资源已加载，且原版会在下次 /reload 时带上它
        if (!server.isRunning()) {
            return Outcome.ENABLED_PENDING_RELOAD;
        }
        if (!passGuard()) {
            // 同一 tick 内连着来两次（启动事件 + 手动 reload）只重载一次
            return Outcome.ENABLED_PENDING_RELOAD;
        }
        server.reloadResources(repository.getSelectedIds());
        return Outcome.ENABLED_AND_RELOADED;
    }

    /** 2 秒去重窗：通过则记时并返回 true。 */
    private static boolean passGuard() {
        long now = System.currentTimeMillis();
        if (now - lastReloadAt < RELOAD_GUARD_MS) {
            return false;
        }
        lastReloadAt = now;
        return true;
    }
}
