package com.redtale.project.mobs;

import com.redtale.project.core.RedtaleCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D26 / M6 第三层（R48）：自然刷怪替换。挂 {@link MobSpawnEvent.PositionCheck}，
 * 命中规则时 {@code setResult(FAIL)} 拦下原版那只，**下一 tick** 在同一个坐标用 {@link MobSpawner} 生成自建怪。
 *
 * <p>红线（`docs/SPAWN-RULES.md` §8）：①事件里不同步生成（会撞 NaturalSpawner 的循环）；②`FAIL` 之后必须自己补生成；
 * ③不碰刷怪笼（`getSpawner() != null` 直接放行）；④**先判可行性再 FAIL**（`mob` 查不到就不拦）。
 *
 * <p>否分支全部「不动作」：事件结果保持 {@code DEFAULT}，原版照常结算（`checkSpawnRules` + `checkSpawnObstruction`）。
 */
public final class SpawnReplacer {

    private static final Logger LOG = LoggerFactory.getLogger("RedtaleProject");
    private static final SpawnReplacer INSTANCE = new SpawnReplacer();
    /** 每 tick 最多补生成几 只：一批自然刷怪可能同时命中好几只，不设上限会在同一 tick 里堆实体。 */
    private static final int DRAIN_PER_TICK = 8;

    /** 一次决策的结果（事件与命令探针共用，避免两份判定）。 */
    public record Verdict(boolean replace, boolean add, String ruleId, String mobId, String reason) {
        static Verdict none(String reason) {
            return new Verdict(false, false, null, null, reason);
        }
    }

    private record Pending(ServerLevel level, double x, double y, double z, String mobId, String ruleId) {}

    private final Deque<Pending> queue = new ArrayDeque<>();
    private final Set<String> hitLogged = ConcurrentHashMap.newKeySet();
    private final Set<String> failLogged = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;

    private SpawnReplacer() {}

    public static SpawnReplacer get() {
        return INSTANCE;
    }

    /** 由 {@code GameHooks} 构造期调用一次。 */
    public static void install() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public boolean enabled() {
        return enabled;
    }

    /** 切换开关；返回切换后的状态。关掉时队列里已排的补生成仍然执行（它们是被我们拦下来的）。 */
    public boolean setEnabled(boolean value) {
        boolean old = enabled;
        enabled = value;
        if (old != value) {
            LOG.info("刷怪替换开关：{} → {}", old ? "开" : "关", value ? "开" : "关");
        }
        return enabled;
    }

    /**
     * 按 {@code spawn.json5} 的结果设开关（{@code /redtale reload} 调）。
     *
     * <p>走 {@link #setEnabled} 而不是直接赋值：状态真的变了要留一行日志，重启后「谁把它关了」能追。
     */
    public void applySettings(SpawnSettings settings) {
        if (settings != null) {
            setEnabled(settings.replaceEnabled());
        }
    }

    /**
     * 把当前开关写回 {@code config/redtale_project/spawn.json5}（只有 {@code replace on|off} 调）。
     *
     * <p>返回值 = 有没有落盘成功；失败只 WARN 不抛 —— 开关在内存里已经生效，落盘失败不该让命令报错。
     * 写的是完整文件（覆盖），所以注释跟着一起重写，内容与默认件同构。
     */
    public boolean persist() {
        java.nio.file.Path file = RedtaleCore.get().config().root().resolve("spawn.json5");
        String text = "// 刷怪替换（D26 / M6 第三层）—— 运行期开关的落盘位置。\n"
                + "// 由 `/redtale spawn replace on|off` 改写；手改也可以，下一次 `/redtale reload` 生效。\n"
                + "// 缺这个文件 = 开关按「开」处理（replace_enabled 缺省 true），不告警。\n"
                + "{\n  replace_enabled: " + enabled + ",\n}\n";
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
            return true;
        } catch (java.io.IOException e) {
            LOG.warn("刷怪替换开关落盘失败（内存里已生效）：{}", e.toString());
            return false;
        }
    }

    /** 已命中过的规则数（观测用）。 */
    public int hitRules() {
        return hitLogged.size();
    }

    // ── 事件入口 ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!enabled || event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT) return;
        if (event.getSpawner() != null) return;                       // 刷怪笼 / 试炼笼不归本层
        if (!(event.getEntity() instanceof Mob mob)) return;
        ServerLevel level = event.getLevel().getLevel();
        Verdict v = decide(level, mob.getType(), event.getX(), event.getY(), event.getZ(), level.random::nextDouble);
        if (!v.replace() && !v.add()) return;
        if (v.replace()) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
        queue.addLast(new Pending(level, event.getX(), event.getY(), event.getZ(), v.mobId(), v.ruleId()));
        if (hitLogged.add(v.ruleId())) {
            LOG.info("刷怪替换首次命中：{} → {}（{} · {}）", v.ruleId(), v.mobId(),
                    v.replace() ? "拦下原版" : "额外叠加", mob.getType().toShortString());
        }
    }

    /** 每 tick 收尾（{@code GameHooks} 调）：把上一 tick 拦下的补生成落地。 */
    public void tick(MinecraftServer server) {
        for (int i = 0; i < DRAIN_PER_TICK && !queue.isEmpty(); i++) {
            Pending p = queue.pollFirst();
            if (p == null) return;
            MobSpec.Definition def = RedtaleCore.get().mobs().get(p.mobId());
            if (def == null) {
                warnOnce(p.ruleId(), "怪物 " + p.mobId() + " 在快照里消失（reload 后规则仍指向它）");
                continue;
            }
            MobSpawner.Result r = MobSpawner.spawn(p.level(), p.x(), p.y(), p.z(), def);
            if (!r.ok()) {
                warnOnce(p.ruleId(), r.message());
            }
        }
    }

    private void warnOnce(String ruleId, String reason) {
        if (failLogged.add(ruleId)) {
            LOG.warn("刷怪替换首次失败：{} · {}", ruleId, reason);
        }
    }

    // ── 决策（唯一判定点，命令探针与事件共用） ─────────────────────────────────────

    /**
     * 按 {@code ordered()} 顺序逐条判：match → conditions → 可行性 → 每区块上限 → 掷骰。
     * 第一条全过的规则生效；都没有则 {@link Verdict#none}。
     *
     * @param roll 掷骰函数（0,1)，事件用 {@code level.random::nextDouble}，探针可传固定值
     */
    public Verdict decide(ServerLevel level, EntityType<?> type, double x, double y, double z,
                          java.util.function.DoubleSupplier roll) {
        if (level == null || type == null) return Verdict.none("缺少世界或实体类型");
        BlockPos pos = BlockPos.containing(x, y, z);
        String typeId = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        List<SpawnRuleSpec.Definition> rules = RedtaleCore.get().spawnRules().ordered();
        if (rules.isEmpty()) return Verdict.none("没有刷怪规则");
        for (SpawnRuleSpec.Definition rule : rules) {
            if (!matchesType(rule.match(), typeId)) continue;
            if (!conditionsMet(rule.conditions(), level, pos)) continue;
            if (RedtaleCore.get().mobs().get(rule.mob()) == null) {
                warnOnce(rule.id(), "mob " + rule.mob() + " 未定义，本条不拦");
                continue;
            }
            if (rule.perChunkCap() > 0 && countInChunk(level, pos, rule.mob()) >= rule.perChunkCap()) continue;
            if (roll.getAsDouble() >= rule.chance()) continue;
            boolean replace = !"add".equals(rule.action());
            return new Verdict(replace, !replace, rule.id(), rule.mob(), replace ? "命中·替换" : "命中·叠加");
        }
        return Verdict.none("没有规则命中");
    }

    /** 决策树逐条文字化（给命令探针用；不改状态）。 */
    public String explain(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        String typeId = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        List<SpawnRuleSpec.Definition> rules = RedtaleCore.get().spawnRules().ordered();
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(typeId).append(" @ ").append(pos.toShortString())
                .append(" · 规则 ").append(rules.size()).append(" 条 · 开关 ").append(enabled ? "开" : "关");
        for (SpawnRuleSpec.Definition rule : rules) {
            sb.append("\n  · ").append(rule.id()).append(" → ").append(rule.mob()).append("：");
            if (!matchesType(rule.match(), typeId)) {
                sb.append("match 不符");
                continue;
            }
            if (!conditionsMet(rule.conditions(), level, pos)) {
                sb.append("条件不满足");
                continue;
            }
            if (RedtaleCore.get().mobs().get(rule.mob()) == null) {
                sb.append("mob 未定义");
                continue;
            }
            int cap = rule.perChunkCap();
            int now = countInChunk(level, pos, rule.mob());
            if (cap > 0 && now >= cap) {
                sb.append("每区块上限已满（").append(now).append('/').append(cap).append("）");
                continue;
            }
            sb.append("可达 · 概率 ").append(String.format(Locale.ROOT, "%.2f", rule.chance()))
                    .append(" · 区块现存 ").append(now);
        }
        return sb.toString();
    }

    private static boolean matchesType(List<String> match, String typeId) {
        for (String m : match) {
            if ("*".equals(m) || typeId.equals(m)) return true;
        }
        return false;
    }

    /** 全部条件 AND，未写的维度不参与（SPAWN-RULES §5）；求值失败视为不通过。 */
    private static boolean conditionsMet(SpawnRuleSpec.Conditions c, ServerLevel level, BlockPos pos) {
        if (c == null) return true;
        try {
            if (!c.worlds().isEmpty() && !c.worlds().contains(level.dimension().location().toString())) return false;
            if (!c.biomes().isEmpty() && !biomeListed(level, pos, c.biomes())) return false;
            if (c.lightMin() != null || c.lightMax() != null) {
                int light = level.getMaxLocalRawBrightness(pos);
                if (c.lightMin() != null && light < c.lightMin()) return false;
                if (c.lightMax() != null && light > c.lightMax()) return false;
            }
            if (c.time() != null && !"any".equals(c.time())) {
                long t = level.getDayTime() % 24000L;
                boolean day = t < 13000L;
                if ("day".equals(c.time()) != day) return false;
            }
            if (c.yMin() != null && pos.getY() < c.yMin()) return false;
            if (c.yMax() != null && pos.getY() > c.yMax()) return false;
            if (c.outside() != null && c.outside() != level.canSeeSky(pos)) return false;
            if (!c.difficulties().isEmpty()) {
                Difficulty d = level.getDifficulty();
                if (!c.difficulties().contains(d.getKey())) return false;
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean biomeListed(ServerLevel level, BlockPos pos, List<String> wanted) {
        var holder = level.getBiome(pos);
        var key = holder.unwrapKey();
        String id = key.map(k -> k.location().toString()).orElse("");
        for (String w : wanted) {
            if (w.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(w.substring(1));
                if (rl != null && holder.is(TagKey.create(Registries.BIOME, rl))) return true;
            } else if (w.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** 该区块现存同种自建怪的数量（§6：不维护计数器）。 */
    private static int countInChunk(ServerLevel level, BlockPos pos, String mobId) {
        String tag = MobSpawner.TAG_PREFIX + mobId;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        AABB box = new AABB(cx << 4, level.getMinBuildHeight(), cz << 4,
                (cx << 4) + 16.0, level.getMaxBuildHeight(), (cz << 4) + 16.0);
        return level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(tag)).size();
    }
}
