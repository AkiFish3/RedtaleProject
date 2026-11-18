package com.redtale.project.core.progression;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.RedtaleCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 等级 / 经验 / 职业的**服务层**（M7 L4）：把「事件 → 定量 → 分配 → 升级 → 下发」五步接起来。
 *
 * <p>分层（{@code PROGRESSION.md} §9）：L1 {@link ExpCurve} 纯曲线、L2 {@link ProgressRegistry} 快照、
 * L3 {@link ProgressStore} 存档、本类 L4 只做「串起来 + 写属性」，L5 是命令面。
 *
 * <p>三条口径：
 * <ul>
 *   <li><b>等级是派生值</b>：只往 {@link ProgressStore} 写 {@code total_exp}，等级每次现算（R42-b）；</li>
 *   <li><b>属性只写 factor 之外的 amount</b>：{@code ADD_VALUE} + 固定 id，<b>先按 id 移除再加</b>（§6）；
 *       值 = {@code per_level × (level-1)}，到顶不再涨但 modifier 保留；</li>
 *   <li><b>不新建事件面</b>：经验入口只有 {@code kill}（{@code MobDrops} 同一处）/ {@code command} / {@code primitive}。</li>
 * </ul>
 *
 * <p>线程：只在服务端主线程调用（命令、事件、reload 同线程）。
 */
public final class ProgressService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject");

    /** 经验来源名（{@code PROGRESSION.md} §5 只认这三个）。 */
    public static final String SRC_KILL = "kill";
    public static final String SRC_COMMAND = "command";
    public static final String SRC_PRIMITIVE = "primitive";

    /** 一次发放的结果（命令与日志共用；{@code applied} = 有没有真的写进在线玩家属性）。 */
    public record Award(long requested, long granted, int mainLevelBefore, int mainLevelAfter,
                        Map<String, Integer> jobLevelsBefore, Map<String, Integer> jobLevelsAfter,
                        int modifiers) {

        public boolean levelUp() {
            if (mainLevelAfter > mainLevelBefore) {
                return true;
            }
            for (String id : jobLevelsAfter.keySet()) {
                if (jobLevelsAfter.get(id) > jobLevelsBefore.getOrDefault(id, 1)) {
                    return true;
                }
            }
            return false;
        }

        public List<String> levelUpJobs() {
            List<String> out = new ArrayList<>();
            for (String id : jobLevelsAfter.keySet()) {
                if (jobLevelsAfter.get(id) > jobLevelsBefore.getOrDefault(id, 1)) {
                    out.add(id);
                }
            }
            return out;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("经验 +").append(granted).append("（请求 ").append(requested).append("）")
                    .append(" · 主等级 ").append(mainLevelBefore).append("→").append(mainLevelAfter);
            List<String> ups = levelUpJobs();
            if (!ups.isEmpty()) {
                sb.append(" · 职业升级 ").append(String.join(",", ups));
            }
            sb.append(" · modifier ").append(modifiers).append(" 条");
            return sb.toString();
        }
    }

    private ProgressService() {
    }

    // ---- 读 ----

    /** {@code levels/} 里的「主等级」定义：优先 id path 为 {@code main}，否则升序第一条；没有定义回 null。 */
    public static ProgressSpec.Definition mainDef() {
        ProgressRegistry reg = RedtaleCore.get().progress();
        for (String id : reg.sortedIds(false)) {
            if (id.endsWith(":main")) {
                return reg.get(id);
            }
        }
        List<String> ids = reg.sortedIds(false);
        return ids.isEmpty() ? null : reg.get(ids.get(0));
    }

    /** 某条定义在某份累计经验下的等级（到顶夹住；{@code total <= 0} → 1 级）。 */
    public static int levelOf(ProgressSpec.Definition def, long totalExp) {
        if (def == null) {
            return 1;
        }
        return Math.max(1, Math.min(def.curve().maxLevel(), def.curve().levelFor(totalExp)));
    }

    public static int mainLevel(MinecraftServer server, UUID id) {
        ProgressSpec.Definition def = mainDef();
        return levelOf(def, ProgressStore.get(server).mainExp(id));
    }

    // ---- 写：经验 ----

    /**
     * 发放一份经验：主等级 + 所有 {@code exp_sources} 命中该来源的职业**各加一份**（D24 双累加器）。
     *
     * <p>{@code kill} 走 {@code mob_exp_multiplier}（§5 唯一倍率），其余来源按 1.0 原样。
     * {@code amount <= 0} 或没有主等级定义 → 不写、返回全 0 的结果（不抛、不告警刷屏）。
     */
    public static Award award(MinecraftServer server, ServerPlayer player, long amount, String source) {
        ProgressSpec.Definition main = mainDef();
        if (server == null || player == null || main == null || amount <= 0L) {
            return new Award(Math.max(0L, amount), 0L, 1, 1, Map.of(), Map.of(), 0);
        }
        String src = source == null ? SRC_COMMAND : source;
        long granted = scale(src, amount);
        UUID id = player.getUUID();
        ProgressStore store = ProgressStore.get(server);
        ProgressRegistry reg = RedtaleCore.get().progress();

        int mainBefore = levelOf(main, store.mainExp(id));
        Map<String, Integer> jobsBefore = jobLevels(reg, store, id);

        long mainAfter = store.addMainExp(id, granted);
        for (ProgressSpec.Definition job : reg.jobs().values()) {
            if (job.expSources().contains(src)) {
                store.addJobExp(id, job.id(), granted);
            }
        }

        int mainAfterLevel = levelOf(main, mainAfter);
        Map<String, Integer> jobsAfter = jobLevels(reg, store, id);
        int modifiers = applyAttributes(player);
        if (mainAfterLevel > mainBefore) {
            LOG.info("等级提升：{} 主等级 {} → {}（累计 {}）", player.getGameProfile().getName(),
                    mainBefore, mainAfterLevel, mainAfter);
        }
        return new Award(amount, granted, mainBefore, mainAfterLevel, jobsBefore, jobsAfter, modifiers);
    }

    /** {@code kill} 是唯一吃 {@code mob_exp_multiplier} 的来源（§5）。负数/NaN 倍率 → 1.0。 */
    private static long scale(String source, long amount) {
        if (!SRC_KILL.equals(source)) {
            return amount;
        }
        double mult = RedtaleCore.get().progressRules().mobExpMultiplier();
        if (!(mult > 0.0) || Double.isNaN(mult) || Double.isInfinite(mult)) {
            mult = 1.0;
        }
        long out = Math.round(amount * mult);
        return Math.max(0L, Math.min(ProgressStore.MAX_TOTAL_EXP, out));
    }

    private static Map<String, Integer> jobLevels(ProgressRegistry reg, ProgressStore store, UUID id) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ProgressSpec.Definition job : reg.jobs().values()) {
            out.put(job.id(), levelOf(job, store.jobExp(id, job.id())));
        }
        return out;
    }

    // ---- 写：属性下发 ----

    /**
     * 按当前等级重下发主等级 + 全部职业的属性 modifier：<b>先按声明 id 全部移除，再加本级该有的</b>。
     *
     * <p>「先移除」不能省：件数/等级往下降时只加不减会把高档 modifier 留在属性上（R20 的坑同源）。
     * 返回本次真正挂上的 modifier 条数（{@code level < 2} 时是 0，但移除照做）。
     */
    public static int applyAttributes(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        ProgressRegistry reg = RedtaleCore.get().progress();
        ProgressStore store = ProgressStore.get(player.server);
        ProgressSpec.Definition main = mainDef();
        if (main == null) {
            return 0;
        }
        int count = 0;
        count += applyOne(player, main, store.mainExp(player.getUUID()));
        for (ProgressSpec.Definition job : reg.jobs().values()) {
            count += applyOne(player, job, store.jobExp(player.getUUID(), job.id()));
        }
        return count;
    }

    /** 一条定义该下发的一项：modifier id（无命名空间）+ 属性全名 + 数值。 */
    public record Planned(String id, String attribute, double amount) {
    }

    /**
     * {@code PROGRESSION.md} §6 的 id 拼法：主等级 {@code level/<path>}、职业 {@code job/<job>/<path>}，
     * {@code <path>} 是属性全名去掉命名空间（{@code minecraft:generic.max_health} → {@code generic.max_health}）。
     */
    public static String idOf(ProgressSpec.Definition def, String attribute) {
        String path = attribute == null ? "" : attribute;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        if (def == null) {
            return "level/" + path;
        }
        if (!def.job()) {
            return "level/" + path;
        }
        String jobId = def.id() == null ? "" : def.id();
        int sep = jobId.indexOf(':');
        String jobPath = sep >= 0 ? jobId.substring(sep + 1) : jobId;
        return "job/" + jobPath + "/" + path;
    }

    /** 定义里声明过的全部 id（不论等级）——「先移除再加」在 {@code level < 2} 时也要靠它清场。 */
    public static List<String> declaredIds(ProgressSpec.Definition def) {
        List<String> out = new ArrayList<>();
        if (def == null) {
            return out;
        }
        for (String attribute : def.attributes().keySet()) {
            out.add(idOf(def, attribute));
        }
        return out;
    }

    /**
     * 本级该下发什么：{@code amount = per_level × (level - 1)}，四舍五入到 6 位（防浮点尾巴进面板）。
     * {@code level} 先夹到 {@code [1, maxLevel]}；夹完 {@code < 2} → 空列表（不是 0 值 modifier）。
     */
    public static List<Planned> plan(ProgressSpec.Definition def, int level) {
        List<Planned> out = new ArrayList<>();
        if (def == null) {
            return out;
        }
        int capped = Math.max(1, Math.min(def.curve().maxLevel(), level));
        if (capped < 2) {
            return out;
        }
        for (Map.Entry<String, Double> e : def.attributes().entrySet()) {
            double perLevel = e.getValue() == null ? 0.0 : e.getValue();
            double amount = Math.round(perLevel * (capped - 1) * 1_000_000.0) / 1_000_000.0;
            out.add(new Planned(idOf(def, e.getKey()), e.getKey(), amount));
        }
        return out;
    }

    /** 单条定义：声明 id 全移除 → 按等级的 plan 重加。 */
    private static int applyOne(ServerPlayer player, ProgressSpec.Definition def, long totalExp) {
        int level = levelOf(def, totalExp);
        for (String raw : declaredIds(def)) {
            AttributeInstance instance = instance(player, raw, def);
            if (instance != null) {
                instance.removeModifier(modifierId(raw));
            }
        }
        int added = 0;
        for (Planned m : plan(def, level)) {
            AttributeInstance instance = instance(player, m.id(), def);
            if (instance == null) {
                continue;
            }
            instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId(m.id()), m.amount(),
                    AttributeModifier.Operation.ADD_VALUE));
            added++;
        }
        return added;
    }

    /**
     * {@code level/generic.max_health} 或 {@code job/miner/mining_efficiency} → 属性实例。
     * 走 {@link LevelModifiers.Modifier#attribute()} 的全名查注册表；查不到就跳过（不告警刷屏）。
     */
    private static AttributeInstance instance(ServerPlayer player, String rawId, ProgressSpec.Definition def) {
        String attribute = attributeOf(def, rawId);
        if (attribute == null) {
            return null;
        }
        // 属性键两种形态：原版是 `minecraft:generic.max_health`（带命名空间），
        // 自建是**裸 path**（`mining_efficiency`，80 条表里就是这么记的）→ 一律走 AttributeSpecs 解析，
        // 不能直接 ResourceLocation.parse（裸 path 会被补成 `minecraft:mining_efficiency`，永远查不到 —— R45 实测）
        com.redtale.project.core.attr.AttributeSpec spec =
                com.redtale.project.core.attr.AttributeSpecs.resolve(attribute);
        net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder =
                com.redtale.project.core.attr.RedtaleAttributes.holder(spec);
        return holder == null ? null : player.getAttribute(holder);
    }

    /** 从 modifer id 反查定义里的属性全名（定义只有几条属性，线性扫足够）。 */
    private static String attributeOf(ProgressSpec.Definition def, String rawId) {
        for (String attribute : def.attributes().keySet()) {
            if (idOf(def, attribute).equals(rawId)) {
                return attribute;
            }
        }
        return null;
    }

    /** 属性上的 modifier id 一律带本模组命名空间：{@code redtale_project:level/generic.max_health}。 */
    private static ResourceLocation modifierId(String rawId) {
        return ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, rawId);
    }

    // ---- 生命周期 ----

    /** 登入：确保存档有这一行 + 按当前等级重下发（等级可能因改曲线而变，§7）。 */
    public static void onLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int n = applyAttributes(player);
        LOG.info("进度重算：{} · 主等级 {} · modifier {} 条", player.getGameProfile().getName(),
                mainLevel(player.server, player.getUUID()), n);
    }

    /** {@code /redtale reload} 后：曲线/上限/属性表立即生效，在线玩家全部重算（{@code total_exp} 不动）。 */
    public static int applyAll(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int players = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyAttributes(p);
            players++;
        }
        return players;
    }
}
