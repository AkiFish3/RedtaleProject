package com.redtale.project.core.progression;

import com.redtale.project.RedtaleProject;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 技能树的**服务层**（M7 L5）：点 / 退点 / 洗点 → 存档 → 按 {@link TreePlan} 下发或清场属性。
 *
 * <p>分层：L1 {@link SkillTreeSpec} 解析、L2 {@link SkillTreeRegistry} 快照、L3 {@link TreePoints} 纯判定、
 * L4 {@link ProgressStore} 存档、L5 本类只做「串起来 + 写 {@code AttributeMap}」。
 *
 * <p>四条口径：
 * <ul>
 *   <li><b>属性残留是这类系统最常见的 bug</b>（{@code SKILL-TREE.md} §5 第 4 条）：每次下发都先按
 *       {@code redtale_project:tree/<树>/} 前缀把 {@code AttributeMap} 上的旧 modifier 全清，再按当前
 *       已点集合重加；洗点走同一路径（清空集合 → 下发 = 只剩清场）；</li>
 *   <li><b>存档键用 {@code def.id()}（全名）</b>，不用命令里传进来的写法：{@code warrior_vanguard} 与
 *       {@code redtale_project:warrior_vanguard} 是同一棵树的两种写法，混用会在存档里长出两个桶（R51-a 同源）；</li>
 *   <li><b>判定只信 {@link TreePoints}</b>，本类不重算点数/前置（第二份真相必然漂）；</li>
 *   <li><b>属性实例一律走 {@link AttributeSpecs#resolve} + {@link RedtaleAttributes#holder}</b>，
 *       不自己拼 {@code redtale_project:} 前缀（自建属性在表里是裸 path，R45 实测）。</li>
 * </ul>
 *
 * <p>线程：只在服务端主线程调用（命令 / 登录 / reload 同线程）。
 */
public final class TreeService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("RedtaleProject");

    /** 一次点/退/洗的结果。{@code detail} 是给人看的补充（缺哪几个前置、还差几点、谁依赖它）。 */
    public enum Status {
        OK, UNKNOWN_TREE, UNKNOWN_NODE, ALREADY_TAKEN, MISSING_PREREQ, NO_POINTS, NOT_TAKEN, HAS_DEPENDENTS, NO_PLAYER
    }

    public record Result(Status status, String treeId, String nodeId, List<String> detail,
                         int removed, int added) {

        public boolean ok() {
            return status == Status.OK;
        }

        public String detailText() {
            return detail == null || detail.isEmpty() ? "" : String.join(",", detail);
        }
    }

    private TreeService() {
    }

    // ---- 读 ----

    /** 命令里既可写 {@code warrior_vanguard} 也可写全名；找不到回 null。 */
    public static SkillTreeSpec.Definition resolve(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty()) {
            return null;
        }
        SkillTreeRegistry reg = RedtaleCore.get().skillTrees();
        SkillTreeSpec.Definition direct = reg.get(id);
        if (direct != null) {
            return direct;
        }
        String want = TreePlan.pathOf(id);
        for (String key : reg.sortedIds()) {
            if (TreePlan.pathOf(key).equals(want)) {
                return reg.get(key);
            }
        }
        return null;
    }

    /** 这棵树当前已点节点（存档顺序 = 点单顺序）。 */
    public static List<String> selected(ServerPlayer player, SkillTreeSpec.Definition def) {
        if (player == null || def == null) {
            return List.of();
        }
        Set<String> nodes = ProgressStore.get(player.server).nodes(player.getUUID(), def.id());
        return List.copyOf(new ArrayList<>(nodes));
    }

    /** 点数状态：上限按**主等级**派生（R54-a，等级换点数），控制台 / 探针走同一支。 */
    public static TreePoints.State state(ServerPlayer player, SkillTreeSpec.Definition def) {
        return TreePoints.state(def, selected(player, def), levelOf(player));
    }

    /** 玩家主等级；{@code player == null} → 0（只影响展示，不影响存档）。 */
    public static int levelOf(ServerPlayer player) {
        if (player == null || player.server == null) return 0;
        ProgressSpec.Definition main = ProgressService.mainDef();
        if (main == null) return 0;
        return ProgressService.levelOf(main, ProgressStore.get(player.server).mainExp(player.getUUID()));
    }

    // ---- 写：点 / 退 / 洗 ----

    /** 点一个节点：判定 → 落存档 → 重新下发这棵树。 */
    public static Result take(ServerPlayer player, String rawTree, String rawNode) {
        if (player == null) {
            return new Result(Status.NO_PLAYER, rawTree, rawNode, List.of(), 0, 0);
        }
        SkillTreeSpec.Definition def = resolve(rawTree);
        if (def == null) {
            return new Result(Status.UNKNOWN_TREE, rawTree, rawNode, List.of(), 0, 0);
        }
        String nodeId = rawNode == null ? "" : rawNode.trim();
        List<String> sel = selected(player, def);
        int level = levelOf(player);
        TreePoints.Status st = TreePoints.canTake(def, sel, nodeId, level);
        switch (st) {
            case UNKNOWN_NODE -> {
                return new Result(Status.UNKNOWN_NODE, def.id(), nodeId, List.of(), 0, 0);
            }
            case ALREADY_TAKEN -> {
                return new Result(Status.ALREADY_TAKEN, def.id(), nodeId, sel, 0, 0);
            }
            case MISSING_PREREQ -> {
                return new Result(Status.MISSING_PREREQ, def.id(), nodeId,
                        TreePoints.missingPrereqs(def, sel, nodeId), 0, 0);
            }
            case NO_POINTS -> {
                TreePoints.State s = TreePoints.state(def, sel, level);
                return new Result(Status.NO_POINTS, def.id(), nodeId,
                        List.of(String.valueOf(s.available())), 0, 0);
            }
            case OK -> {
                // 落到下一段
            }
        }
        ProgressStore store = ProgressStore.get(player.server);
        if (!store.addNode(player.getUUID(), def.id(), nodeId)) {
            // 存档层说「本来就有」—— 判定层与存档层不一致，按已有处理而不是硬加
            return new Result(Status.ALREADY_TAKEN, def.id(), nodeId, sel, 0, 0);
        }
        int added = applyTree(player, def);
        LOG.info("技能树：{} 点 {} / {}（已点 {} 个，modifier {} 条）", name(player), def.id(), nodeId,
                selected(player, def).size(), added);
        return new Result(Status.OK, def.id(), nodeId, List.of(), 0, added);
    }

    /** 退一个节点：**有别的已点节点依赖它就不许退**（否则前置悬空，洗点永远洗不干净）。 */
    public static Result untake(ServerPlayer player, String rawTree, String rawNode) {
        if (player == null) {
            return new Result(Status.NO_PLAYER, rawTree, rawNode, List.of(), 0, 0);
        }
        SkillTreeSpec.Definition def = resolve(rawTree);
        if (def == null) {
            return new Result(Status.UNKNOWN_TREE, rawTree, rawNode, List.of(), 0, 0);
        }
        String nodeId = rawNode == null ? "" : rawNode.trim();
        if (def.nodes() == null || !def.nodes().containsKey(nodeId)) {
            return new Result(Status.UNKNOWN_NODE, def.id(), nodeId, List.of(), 0, 0);
        }
        List<String> sel = selected(player, def);
        if (!sel.contains(nodeId)) {
            return new Result(Status.NOT_TAKEN, def.id(), nodeId, List.of(), 0, 0);
        }
        List<String> dependents = new ArrayList<>();
        for (String other : sel) {
            if (other.equals(nodeId)) {
                continue;
            }
            SkillTreeSpec.Node n = def.nodes().get(other);
            if (n != null && n.requires() != null && n.requires().contains(nodeId)) {
                dependents.add(other);
            }
        }
        if (!dependents.isEmpty()) {
            return new Result(Status.HAS_DEPENDENTS, def.id(), nodeId, List.copyOf(dependents), 0, 0);
        }
        ProgressStore store = ProgressStore.get(player.server);
        if (!store.removeNode(player.getUUID(), def.id(), nodeId)) {
            return new Result(Status.NOT_TAKEN, def.id(), nodeId, sel, 0, 0);
        }
        int added = applyTree(player, def);
        LOG.info("技能树：{} 退 {} / {}（已点 {} 个，modifier {} 条）", name(player), def.id(), nodeId,
                selected(player, def).size(), added);
        return new Result(Status.OK, def.id(), nodeId, List.of(), 0, added);
    }

    /** 洗点：清空这棵树的已点集合 + 前缀批清。扣费仍挂 Q-021（默认免费）。 */
    public static Result respec(ServerPlayer player, String rawTree) {
        if (player == null) {
            return new Result(Status.NO_PLAYER, rawTree, null, List.of(), 0, 0);
        }
        SkillTreeSpec.Definition def = resolve(rawTree);
        if (def == null) {
            return new Result(Status.UNKNOWN_TREE, rawTree, null, List.of(), 0, 0);
        }
        ProgressStore store = ProgressStore.get(player.server);
        int before = selected(player, def).size();
        store.clearTree(player.getUUID(), def.id());
        applyTree(player, def);
        LOG.info("技能树：{} 洗点 {} / {}（退回 {} 个节点）", name(player), def.id(), "-", before);
        return new Result(Status.OK, def.id(), null, List.of(), before, 0);
    }

    // ---- 写：属性下发 ----

    /** 前缀：{@code redtale_project:tree/<path>/}（{@link TreePlan#modifierPrefix} 的唯一拼法）。 */
    public static String prefixOf(String treeId) {
        return RedtaleProject.MOD_ID + ":" + TreePlan.modifierPrefix(treeId);
    }

    /**
     * 重新下发一棵树：**先按前缀把旧 modifier 全清，再按已点集合加**。
     *
     * <p>「全清」而不是「按声明 id 逐条清」：配置里改了节点的属性键时，旧 modifier 挂在**旧属性**上，
     * 按当前声明清不到它 —— 前缀扫描才是洗点能洗干净的保证（{@code SKILL-TREE.md} §5 第 4 条）。
     *
     * @return 本次真正挂上的 modifier 条数
     */
    public static int applyTree(ServerPlayer player, SkillTreeSpec.Definition def) {
        if (player == null || def == null) {
            return 0;
        }
        removeByPrefix(player, prefixOf(def.id()));
        int added = 0;
        for (TreePlan.Modifier m : TreePlan.plan(def, selected(player, def))) {
            if (addOne(player, m)) {
                added++;
            }
        }
        return added;
    }

    /** 玩家身上所有树按当前存档重下发（登录 / reload 用）。 */
    public static int applyAll(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int added = 0;
        for (SkillTreeSpec.Definition def : RedtaleCore.get().skillTrees().all().values()) {
            added += applyTree(player, def);
        }
        return added;
    }

    /** 清掉玩家身上**所有**技能树 modifier（按 {@code redtale_project:tree/} 总前缀，跨树）。 */
    public static int removeAll(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return removeByPrefix(player, RedtaleProject.MOD_ID + ":tree/");
    }

    // ---- 读：技能解锁派生（R57：`unlock_skill` 的第一个消费方）----

    /**
     * 所有技能树里被 {@code unlock_skill} 声明过的技能（**归一到 path**，保定义序）。
     *
     * <p>拿法是把每棵树的**全部节点**当「已点」喂给 {@link TreeUnlocks#unlocked} —— 它只做纯派生、
     * 不读存档，所以「声明全集」= 「假装全点」的结果。
     */
    public static Set<String> gatedSkillPaths() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (SkillTreeSpec.Definition def : RedtaleCore.get().skillTrees().all().values()) {
            for (String skill : TreeUnlocks.unlocked(def, def.nodes().keySet())) {
                String p = pathOf(skill);
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return java.util.Collections.unmodifiableSet(out);
    }

    /** 该技能是否被任一棵树管着。**没被任何树声明的技能不设门槛**（历史内容 / 调试保留，R57-a）。 */
    public static boolean gated(String rawSkillId) {
        String p = pathOf(rawSkillId);
        return !p.isEmpty() && gatedSkillPaths().contains(p);
    }

    /**
     * 玩家已解锁的技能（**全名，按配置写法**；跨树合并、按 path 去重保首次）。
     *
     * <p>不落存档：解锁集是「已点节点 + 树配置」的派生值，第二次存就等于第二份真相（同 R50-c）。
     */
    public static List<String> unlockedSkills(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (SkillTreeSpec.Definition def : RedtaleCore.get().skillTrees().all().values()) {
            for (String skill : TreeUnlocks.unlocked(def, selected(player, def))) {
                String p = pathOf(skill);
                if (p.isEmpty() || !seen.add(p)) {
                    continue;
                }
                out.add(skill);
            }
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /** 全名 / 裸名都认（比对走 path，R50-b 同源）。 */
    public static boolean isUnlocked(ServerPlayer player, String rawSkillId) {
        String p = pathOf(rawSkillId);
        if (p.isEmpty()) {
            return false;
        }
        for (String s : unlockedSkills(player)) {
            if (pathOf(s).equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** 给人看的解锁清单：每行 {@code "  " + 技能全名}；空 → {@code "  （无）"}。 */
    public static String unlockedText(ServerPlayer player) {
        List<String> list = unlockedSkills(player);
        if (list.isEmpty()) {
            return "  （无）";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("  ").append(s);
        }
        return sb.toString();
    }

    private static String pathOf(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    private static boolean addOne(ServerPlayer player, TreePlan.Modifier m) {
        if (m == null || m.attribute() == null) {
            return false;
        }
        AttributeSpec spec = AttributeSpecs.resolve(m.attribute());
        if (spec == null) {
            return false;
        }
        Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            return false;
        }
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) {
            return false;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(RedtaleProject.MOD_ID, m.id()),
                m.amount(), operationOf(m.operation())));
        return true;
    }

    private static int removeByPrefix(ServerPlayer player, String prefix) {
        int removed = 0;
        Collection<AttributeInstance> all = player.getAttributes().getSyncableAttributes();
        for (AttributeInstance instance : all) {
            if (instance == null) {
                continue;
            }
            List<ResourceLocation> hit = new ArrayList<>();
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.id() != null && m.id().toString().startsWith(prefix)) {
                    hit.add(m.id());
                }
            }
            for (ResourceLocation id : hit) {
                instance.removeModifier(id);
                removed++;
            }
        }
        return removed;
    }

    /** {@link TreePlan} 的 operation 字符串 → 原版枚举；未知回落 {@code ADD_VALUE}（同 L1 口径）。 */
    public static AttributeModifier.Operation operationOf(String raw) {
        String s = TreePlan.operationOf(raw);
        return switch (s) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    /** 观测用：某棵树当前挂在玩家身上的 modifier id（按属性分组由调用方决定）。 */
    public static Map<String, Double> snapshot(ServerPlayer player, SkillTreeSpec.Definition def) {
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        if (player == null || def == null) {
            return out;
        }
        String prefix = prefixOf(def.id());
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            if (instance == null) {
                continue;
            }
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.id() != null && m.id().toString().startsWith(prefix)) {
                    out.put(m.id().toString(), m.amount());
                }
            }
        }
        return out;
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile() == null ? "?" : player.getGameProfile().getName();
    }

    /** UUID 便捷（命令与日志共用；避免每条命令各自建一遍）。 */
    public static UUID idOf(ServerPlayer player) {
        return player == null ? new UUID(0L, 0L) : player.getUUID();
    }
}
