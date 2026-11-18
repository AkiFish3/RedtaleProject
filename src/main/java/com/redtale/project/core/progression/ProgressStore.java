package com.redtale.project.core.progression;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 进度存档（M7 第三层）：{@code world/data/redtale_project_progress.dat}。
 * <p>
 * 契约：{@code docs/PROGRESSION.md} §7。**只存 {@code total_exp}，不存等级**（等级是派生值，§2）；
 * 主等级一份 + 每职业各一份，互不换算。纯数据层：不解析配置、不算曲线、不下发属性、不打日志。
 * <p>
 * 存档格式 v1（{@code version=1}）：
 * <pre>
 * { version: 1, players: [ { id: "&lt;uuid&gt;", main: &lt;long&gt;, jobs: { "&lt;jobId&gt;": &lt;long&gt; } } ] }
 * </pre>
 * UUID 用字符串存（不落 int 数组，跨版本最稳）；坏条目整条跳过，不抛、不删别人。
 */
public final class ProgressStore extends SavedData {
    /** 存档文件名（不含 {@code .dat}）。 */
    public static final String FILE_NAME = "redtale_project_progress";
    /** 存档格式版本；改结构时 +1 并在 {@link #load} 里加迁移分支。 */
    public static final int VERSION = 1;
    /** 累计经验硬顶（D16 异常保护：负归零、超顶截断，不做平衡钳制）。 */
    public static final long MAX_TOTAL_EXP = 1_000_000_000_000_000L;

    private static final String KEY_VERSION = "version";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_ID = "id";
    private static final String KEY_MAIN = "main";
    private static final String KEY_JOBS = "jobs";

    /** 一个玩家的两份累加器。 */
    private final Map<UUID, Player> players = new LinkedHashMap<>();

    /** 建一份空存档（{@link SavedData.Factory} 的 constructor）。 */
    public ProgressStore() {
    }

    // ---- 工厂 / 取用 ----

    /** {@code DimensionDataStorage#computeIfAbsent} 用的工厂。 */
    public static SavedData.Factory<ProgressStore> factory() {
        return new SavedData.Factory<>(ProgressStore::new, ProgressStore::load, DataFixTypes.LEVEL);
    }

    /** 取（或首次建）本世界的进度存档；{@code server == null} → 一份内存态空存档（测试用，不落盘）。 */
    public static ProgressStore get(MinecraftServer server) {
        if (server == null) {
            return new ProgressStore();
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_NAME);
    }

    // ---- 读 ----

    /** 主等级累计经验；没有记录 → 0。 */
    public long mainExp(UUID id) {
        Player p = id == null ? null : players.get(id);
        return p == null ? 0L : p.main;
    }

    /** 某职业累计经验；没有记录 → 0。 */
    public long jobExp(UUID id, String jobId) {
        Player p = id == null ? null : players.get(id);
        if (p == null || jobId == null) {
            return 0L;
        }
        Long v = p.jobs.get(jobId);
        return v == null ? 0L : v;
    }

    /** 某玩家的全部职业经验（不可变副本，保写入顺序）。 */
    public Map<String, Long> jobExps(UUID id) {
        Player p = id == null ? null : players.get(id);
        return p == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(p.jobs));
    }

    /** 有记录的玩家数。 */
    public int size() {
        return players.size();
    }

    /** 有记录的玩家 UUID（不可变副本，保插入顺序）。 */
    public Set<UUID> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(players.keySet()));
    }

    // ---- 写（经验只增不减；delta <= 0 直接忽略） ----

    /** 主等级加经验；返回加完后的累计值。 */
    public long addMainExp(UUID id, long delta) {
        if (id == null || delta <= 0L) {
            return mainExp(id);
        }
        Player p = players.computeIfAbsent(id, k -> new Player());
        p.main = clamp(p.main + delta);
        setDirty();
        return p.main;
    }

    /** 某职业加经验；返回加完后的累计值。 */
    public long addJobExp(UUID id, String jobId, long delta) {
        if (id == null || jobId == null || jobId.isEmpty() || delta <= 0L) {
            return jobExp(id, jobId);
        }
        Player p = players.computeIfAbsent(id, k -> new Player());
        long now = p.jobs.getOrDefault(jobId, 0L);
        long next = clamp(now + delta);
        p.jobs.put(jobId, next);
        setDirty();
        return next;
    }

    /** 直接设主等级累计经验（运营入口）；{@code value <= 0} → 归零。 */
    public void setMainExp(UUID id, long value) {
        if (id == null) {
            return;
        }
        players.computeIfAbsent(id, k -> new Player()).main = clamp(value);
        setDirty();
    }

    /** 清掉一个玩家的全部进度（不常用，留给洗点/删号）。 */
    public boolean clear(UUID id) {
        if (id == null || players.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    // ---- 序列化 ----

    /** {@code BiFunction} 目标：反序列化。坏条目跳过，永不抛。 */
    public static ProgressStore load(CompoundTag tag, HolderLookup.Provider provider) {
        ProgressStore store = new ProgressStore();
        if (tag == null || !tag.contains(KEY_PLAYERS, Tag.TAG_LIST)) {
            return store;
        }
        ListTag list = tag.getList(KEY_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            UUID id = uuid(row.getString(KEY_ID));
            if (id == null) {
                continue;
            }
            Player p = new Player();
            p.main = clamp(row.getLong(KEY_MAIN));
            if (row.contains(KEY_JOBS, Tag.TAG_COMPOUND)) {
                CompoundTag jobs = row.getCompound(KEY_JOBS);
                for (String jobId : jobs.getAllKeys()) {
                    if (jobId.isEmpty()) {
                        continue;
                    }
                    p.jobs.put(jobId, clamp(jobs.getLong(jobId)));
                }
            }
            store.players.put(id, p);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag root = tag == null ? new CompoundTag() : tag;
        root.putInt(KEY_VERSION, VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Player> e : players.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_ID, e.getKey().toString());
            row.putLong(KEY_MAIN, e.getValue().main);
            CompoundTag jobs = new CompoundTag();
            for (Map.Entry<String, Long> j : e.getValue().jobs.entrySet()) {
                jobs.putLong(j.getKey(), j.getValue());
            }
            row.put(KEY_JOBS, jobs);
            list.add(row);
        }
        root.put(KEY_PLAYERS, list);
        return root;
    }

    // ---- 内部 ----

    private static long clamp(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return Math.min(value, MAX_TOTAL_EXP);
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 一个玩家的可变累加器（只在 {@link #save} 时读出去）。 */
    private static final class Player {
        private long main;
        private final Map<String, Long> jobs = new LinkedHashMap<>();
    }
}
