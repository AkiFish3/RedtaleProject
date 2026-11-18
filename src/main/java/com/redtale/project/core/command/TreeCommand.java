package com.redtale.project.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.redtale.project.core.RedtaleCore;
import com.redtale.project.core.attr.AttributeSpec;
import com.redtale.project.core.attr.AttributeSpecs;
import com.redtale.project.core.attr.RedtaleAttributes;
import com.redtale.project.core.progression.ProgressStore;
import com.redtale.project.core.progression.SkillTreeSpec;
import com.redtale.project.core.progression.TreePoints;
import com.redtale.project.core.progression.TreeService;
import com.redtale.project.core.progression.TreeUnlocks;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * /redtale tree —— 技能树：只读观测面（{@code list|info}，T-0047）+ 写面（{@code take|untake|respec}，
 * M7 L5）+ 控制台可跑的 {@code state|probe}（FakePlayer）。
 *
 * <p>{@code list|info} 的输出是 T-0047 的验收基线，**逐字不许变**；新增子命令只加分支，不改旧分支。
 */
public final class TreeCommand {

    private TreeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("tree")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("state")
                        .then(Commands.argument("tree", StringArgumentType.string())
                                .executes(ctx -> state(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tree")))))
                .then(Commands.literal("take")
                        .then(Commands.argument("tree", StringArgumentType.string())
                                .then(Commands.argument("node", StringArgumentType.string())
                                        .executes(ctx -> change(ctx.getSource(), true,
                                                StringArgumentType.getString(ctx, "tree"),
                                                StringArgumentType.getString(ctx, "node"))))))
                .then(Commands.literal("untake")
                        .then(Commands.argument("tree", StringArgumentType.string())
                                .then(Commands.argument("node", StringArgumentType.string())
                                        .executes(ctx -> change(ctx.getSource(), false,
                                                StringArgumentType.getString(ctx, "tree"),
                                                StringArgumentType.getString(ctx, "node"))))))
                .then(Commands.literal("respec")
                        .then(Commands.argument("tree", StringArgumentType.string())
                                .executes(ctx -> respec(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tree")))))
                .then(Commands.literal("skills")
                        .then(Commands.argument("tree", StringArgumentType.string())
                                .executes(ctx -> skills(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tree")))))
                .then(Commands.literal("probe").executes(TreeCommand::probe));
    }

    // ---- 只读观测面（T-0047 基线，逐字不变） ----

    private static int list(CommandSourceStack src) {
        var trees = RedtaleCore.get().skillTrees();
        StringBuilder sb = new StringBuilder("[redtale] 技能树 ")
                .append(trees.size()).append(" 棵");
        if (trees.size() == 0) {
            sb.append("\n  （没有技能树定义：读 config/redtale_project/skill_trees）");
        } else {
            sb.append(" · 节点 ").append(trees.nodeCount())
                    .append(" 个 · 点数上限 ").append(trees.pointTotal());
            for (String id : trees.sortedIds()) {
                var def = trees.get(id);
                sb.append("\n  ").append(id).append(" · ").append(def.name())
                        .append(" · 职业 ").append(classes(def.classes()))
                        .append(" · 节点 ").append(def.nodes().size())
                        .append(" · 点数 ").append(def.maxPoints());
            }
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return trees.size();
    }

    private static int info(CommandSourceStack src, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        var trees = RedtaleCore.get().skillTrees();
        var def = trees.get(id);
        if (def == null) {
            String miss = "[redtale] 没有技能树 " + id + "（现有 " + trees.size()
                    + " 棵：" + String.join(",", trees.sortedIds()) + "）";
            src.sendSuccess(() -> Component.literal(miss), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[redtale] ").append(id)
                .append(" · ").append(def.name())
                .append(" · 上限 ").append(def.maxPoints()).append(" 点")
                .append(" · 每级 ").append(def.pointsPerLevel()).append(" 点")
                .append(" · 节点 ").append(def.nodes().size())
                .append("\n  职业：").append(classes(def.classes()))
                .append(" · 来源 ").append(def.source());
        // R56：「当前等级能拿多少点」只在该定义有玩家上下文时追加 —— 控制台输出逐字不变（T-0047 基线）
        ServerPlayer viewer = src.getPlayer();
        if (viewer != null) {
            sb.append(levelLine(def, TreeService.levelOf(viewer)));
        }
        for (var node : def.nodes().values()) {
            int[] pos = node.pos();
            sb.append("\n  节点 ").append(node.id()).append(" · ").append(node.name())
                    .append(" · cost ").append(node.cost())
                    .append(" · pos ").append(pos[0]).append(",").append(pos[1])
                    .append(" · 需 ").append(node.requires().size())
                    .append(" · 效果 ").append(node.effects().size());
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return def.nodes().size();
    }

    /**
     * 「当前等级能拿多少点」段（R56，PLAN §13.58 下一步第 2 条）：纯函数、零 MC 依赖，
     * 上限一律走 {@link TreePoints#capFor}（R54-a 的唯一真相），不在这里重算 `points_per_level × level`。
     * 控制台（无玩家）**不追加**这一行 —— T-0047 的 `info` 文案是验收基线，逐字不许变。
     */
    public static String levelLine(SkillTreeSpec.Definition def, int level) {
        int cap = TreePoints.capFor(def.maxPoints(), def.pointsPerLevel(), level);
        return "\n  当前等级 " + level + " · 可拿 " + cap + " 点";
    }

    // ---- 解锁技能观测面（R58） ----

    private static int skills(CommandSourceStack src, String rawTree) {
        SkillTreeSpec.Definition def = TreeService.resolve(rawTree);
        if (def == null) {
            var trees = RedtaleCore.get().skillTrees();
            src.sendFailure(Component.literal("[redtale] 没有技能树 " + trim(rawTree) + "（现有 "
                    + trees.size() + " 棵：" + String.join(",", trees.sortedIds()) + "）"));
            return 0;
        }
        String text = skillsText(def, src.getPlayer());
        src.sendSuccess(() -> Component.literal(text), false);
        return declaredSkills(def).size();
    }

    /**
     * `tree skills` 的全文（R58，纯函数：只读 def / 玩家已点节点，不写任何状态）。
     *
     * <p>两个分支口径写死：
     * <ul>
     *   <li>{@code viewer == null}（控制台）→ 只报**声明集**，一行 {@code （控制台不读玩家存档）}，
     *       不猜「大概解锁了什么」</li>
     *   <li>有玩家 → 报声明集 + 已解锁条数，每条带 {@code · 已解锁} / {@code · 未解锁}；
     *       标注走 {@link TreeService#isUnlocked}（全名/裸名同一条，R57-d）</li>
     * </ul>
     * 声明集的顺序 = {@link TreeUnlocks#unlocked} 的定义序（= {@code def.nodes()} 迭代序）。
     */
    public static String skillsText(SkillTreeSpec.Definition def, ServerPlayer viewer) {
        List<String> declared = declaredSkills(def);
        StringBuilder sb = new StringBuilder("[redtale] ").append(def.id())
                .append(" · 声明技能 ").append(declared.size()).append(" 条");
        if (viewer == null) {
            sb.append("（控制台不读玩家存档）");
            if (declared.isEmpty()) {
                sb.append("\n  （该树没有 unlock_skill 声明）");
            } else {
                for (String s : declared) {
                    sb.append("\n  ").append(s);
                }
            }
            return sb.toString();
        }
        List<String> unlocked = TreeUnlocks.unlocked(def, TreeService.selected(viewer, def));
        sb.append(" · 已解锁 ").append(unlocked.size()).append(" 条");
        if (declared.isEmpty()) {
            sb.append("\n  （该树没有 unlock_skill 声明）");
        } else {
            for (String s : declared) {
                sb.append("\n  ").append(s)
                        .append(TreeService.isUnlocked(viewer, s) ? " · 已解锁" : " · 未解锁");
            }
        }
        return sb.toString();
    }

    /** 该树**声明**了哪些技能（= 假装全点之后的解锁集，不读存档）。 */
    public static List<String> declaredSkills(SkillTreeSpec.Definition def) {
        if (def == null) {
            return List.of();
        }
        return TreeUnlocks.unlocked(def, def.nodes().keySet());
    }

    // ---- 写面（M7 L5） ----

    private static int state(CommandSourceStack src, String rawTree) {
        ServerPlayer player = src.getPlayer();
        SkillTreeSpec.Definition def = TreeService.resolve(rawTree);
        if (def == null) {
            var trees = RedtaleCore.get().skillTrees();
            src.sendFailure(Component.literal("[redtale] 没有技能树 " + trim(rawTree) + "（现有 "
                    + trees.size() + " 棵：" + String.join(",", trees.sortedIds()) + "）"));
            return 0;
        }
        if (player == null) {
            src.sendSuccess(() -> Component.literal("[redtale] " + def.id() + " · 上限 " + def.maxPoints()
                    + " 点 · 节点 " + def.nodes().size() + " 个（控制台不读玩家存档）"), false);
            return 1;
        }
        TreePoints.State s = TreeService.state(player, def);
        String text = "[redtale] " + def.id() + " · 点数 " + s.maxPoints() + " · 已用 " + s.used()
                + " · 剩余 " + s.available() + " · 已点 " + s.taken() + " 个"
                + " · 等级 " + TreeService.levelOf(player)
                + "（" + def.pointsPerLevel() + " 点/级，上限 " + def.maxPoints() + "）";
        src.sendSuccess(() -> Component.literal(text), false);
        return s.taken();
    }

    private static int change(CommandSourceStack src, boolean take, String rawTree, String node) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        TreeService.Result r = take ? TreeService.take(player, rawTree, node)
                : TreeService.untake(player, rawTree, node);
        String text = describe(player, r, take);
        if (r.ok()) {
            src.sendSuccess(() -> Component.literal(text), false);
            return r.added();
        }
        src.sendFailure(Component.literal(text));
        return 0;
    }

    private static int respec(CommandSourceStack src, String rawTree) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[redtale] 这条命令只能由玩家执行（控制台没有 UUID）"));
            return 0;
        }
        TreeService.Result r = TreeService.respec(player, rawTree);
        if (!r.ok()) {
            src.sendFailure(Component.literal(describe(player, r, false)));
            return 0;
        }
        String text = "[redtale] 已洗点 " + r.treeId() + " · 退回 " + r.removed() + " 个节点";
        src.sendSuccess(() -> Component.literal(text), false);
        return r.removed();
    }

    /** 结果 → 一行话（成功/失败共用；失败文案写清「哪条不满足」）。 */
    private static String describe(ServerPlayer player, TreeService.Result r, boolean take) {
        String head = "[redtale] ";
        switch (r.status()) {
            case OK -> {
                SkillTreeSpec.Definition def = TreeService.resolve(r.treeId());
                TreePoints.State s = def == null ? null : TreeService.state(player, def);
                String tail = s == null ? "" : " · 已点 " + s.taken() + " 个 · 点数 " + s.used() + "/" + s.maxPoints();
                return head + (take ? "已点 " : "已退 ") + r.treeId() + "/" + r.nodeId()
                        + tail + " · modifier " + r.added() + " 条";
            }
            case NO_PLAYER -> {
                return head + "这条命令只能由玩家执行（控制台没有 UUID）";
            }
            case UNKNOWN_TREE -> {
                var trees = RedtaleCore.get().skillTrees();
                return head + "没有技能树 " + trim(r.treeId()) + "（现有 " + trees.size()
                        + " 棵：" + String.join(",", trees.sortedIds()) + "）";
            }
            case UNKNOWN_NODE -> {
                return head + r.treeId() + " 没有节点 " + r.nodeId();
            }
            case ALREADY_TAKEN -> {
                return head + r.nodeId() + " 已经点过";
            }
            case MISSING_PREREQ -> {
                return head + r.nodeId() + " 缺前置：" + r.detailText();
            }
            case NO_POINTS -> {
                return head + "点数不够（剩 " + r.detailText() + "）";
            }
            case NOT_TAKEN -> {
                return head + r.nodeId() + " 还没点";
            }
            case HAS_DEPENDENTS -> {
                return head + "不能退 " + r.nodeId() + "：" + r.detailText() + " 还依赖它";
            }
            default -> {
                return head + r.status();
            }
        }
    }

    // ---- 控制台可跑的端到端探针（FakePlayer） ----

    /**
     * {@code /redtale tree probe}：FakePlayer 走一遍「点 → 属性涨 → 重复点被拦 → 退根被拦 → 属性回落 → 洗点」。
     * 断言逐条打印 {@code ✓/✗}，返回通过条数。**验收读 {@code AttributeMap} 差值，不看返回值。**
     */
    public static int probe(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var level = src.getServer().overworld();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        ProgressStore store = ProgressStore.get(src.getServer());
        SkillTreeSpec.Definition def = TreeService.resolve("warrior_vanguard");
        if (def == null) {
            src.sendFailure(Component.literal("[redtale] 没有 warrior_vanguard 技能树，探针跳过"));
            return 0;
        }
        int pass = 0;
        int total = 0;
        String tree = def.id();

        // 清场：FakePlayer 的 UUID 固定，上一轮的已点节点会串进这一轮
        store.clear(player.getUUID());
        TreeService.removeAll(player);
        // R54：点数上限现在按主等级派生 → 先把等级顶到 max_points 之上，A~M 组才不会被 NO_POINTS 拦
        store.setMainExp(player.getUUID(), 1_000_000_000L);
        send(src, "[redtale] tree probe · 主等级 " + TreeService.levelOf(player)
                + "（点数上限 " + TreeService.state(player, def).maxPoints() + "）");

        double atkBase = value(player, "minecraft:generic.attack_speed");
        double armorBase = value(player, "minecraft:generic.armor");
        double heavyBase = value(player, "redtale_project:physical_damage");
        send(src, "[redtale] tree probe（FakePlayer）· 基线 攻速 " + atkBase + " / 护甲 " + armorBase
                + " / 物伤 " + heavyBase);

        // A：点 root（0 费 0 效果）→ 存档有、属性不动
        TreeService.Result a = TreeService.take(player, tree, "root");
        total++;
        boolean okA = a.ok() && store.hasNode(player.getUUID(), tree, "root")
                && Math.abs(value(player, "minecraft:generic.attack_speed") - atkBase) < 1e-9;
        pass += okA ? 1 : 0;
        send(src, "  A 点 root → " + a.status() + " · 攻速 " + value(player, "minecraft:generic.attack_speed")
                + (okA ? " ✓" : " ✗"));

        // B：点 atk_spd_1（attack_speed ADD_MULTIPLIED_TOTAL 0.05）→ 攻速 = 基值 × 1.05
        TreeService.Result b = TreeService.take(player, tree, "atk_spd_1");
        double atkB = value(player, "minecraft:generic.attack_speed");
        total++;
        boolean okB = b.ok() && Math.abs(atkB - atkBase * 1.05) < 1e-6;
        pass += okB ? 1 : 0;
        send(src, "  B 点 atk_spd_1 → modifier " + b.added() + " 条 · 攻速 " + atkB
                + "（应 " + (atkBase * 1.05) + "）" + (okB ? " ✓" : " ✗"));

        // C：重复点 → ALREADY_TAKEN，且属性不变（不允许「每点一次多加一份」）
        double beforeC = value(player, "minecraft:generic.attack_speed");
        TreeService.Result c = TreeService.take(player, tree, "atk_spd_1");
        total++;
        boolean okC = c.status() == TreeService.Status.ALREADY_TAKEN
                && Math.abs(value(player, "minecraft:generic.attack_speed") - beforeC) < 1e-9;
        pass += okC ? 1 : 0;
        send(src, "  C 重复点 atk_spd_1 → " + c.status() + " · 攻速 " + beforeC
                + (okC ? " ✓" : " ✗"));

        // D：有已点节点依赖它时退根 → HAS_DEPENDENTS
        TreeService.Result d = TreeService.untake(player, tree, "root");
        total++;
        boolean okD = d.status() == TreeService.Status.HAS_DEPENDENTS;
        pass += okD ? 1 : 0;
        send(src, "  D 退 root（atk_spd_1 依赖）→ " + d.status() + " · " + d.detailText()
                + (okD ? " ✓" : " ✗"));

        // E：缺前置 —— heavy_2 需 heavy_1
        TreeService.Result e = TreeService.take(player, tree, "heavy_2");
        total++;
        boolean okE = e.status() == TreeService.Status.MISSING_PREREQ && e.detailText().contains("heavy_1");
        pass += okE ? 1 : 0;
        send(src, "  E 点 heavy_2（缺 heavy_1）→ " + e.status() + " · " + e.detailText()
                + (okE ? " ✓" : " ✗"));

        // F：点 armor_1 → 护甲 +2（ADD_VALUE 绝对值可直接读）
        TreeService.Result f = TreeService.take(player, tree, "armor_1");
        double armorF = value(player, "minecraft:generic.armor");
        total++;
        boolean okF = f.ok() && Math.abs(armorF - (armorBase + 2.0)) < 1e-9;
        pass += okF ? 1 : 0;
        send(src, "  F 点 armor_1 → 护甲 " + armorF + "（应 " + (armorBase + 2.0) + "）"
                + (okF ? " ✓" : " ✗"));

        // G：点 heavy_1 → 自建属性 physical_damage +0.08（R45 的裸 path 坑在这里复测）
        TreeService.Result g = TreeService.take(player, tree, "heavy_1");
        double heavyG = value(player, "redtale_project:physical_damage");
        total++;
        boolean okG = g.ok() && Math.abs(heavyG - (heavyBase + 0.08)) < 1e-6;
        pass += okG ? 1 : 0;
        send(src, "  G 点 heavy_1 → 物伤 " + heavyG + "（应 " + (heavyBase + 0.08) + "）"
                + (okG ? " ✓" : " ✗"));

        // H：退 atk_spd_1（atk_spd_2 没点，无人依赖）→ 攻速回到基值
        TreeService.Result h = TreeService.untake(player, tree, "atk_spd_1");
        double atkH = value(player, "minecraft:generic.attack_speed");
        total++;
        boolean okH = h.ok() && Math.abs(atkH - atkBase) < 1e-9;
        pass += okH ? 1 : 0;
        send(src, "  H 退 atk_spd_1 → 攻速 " + atkH + "（应 " + atkBase + "）" + (okH ? " ✓" : " ✗"));

        // I：未点的节点再退 → NOT_TAKEN
        TreeService.Result i = TreeService.untake(player, tree, "atk_spd_2");
        total++;
        boolean okI = i.status() == TreeService.Status.NOT_TAKEN;
        pass += okI ? 1 : 0;
        send(src, "  I 退没点的 atk_spd_2 → " + i.status() + (okI ? " ✓" : " ✗"));

        // J：前缀清场 —— 洗点后三件属性全回基值、存档无已点节点、前缀扫描零残留
        TreeService.Result j = TreeService.respec(player, tree);
        double atkJ = value(player, "minecraft:generic.attack_speed");
        double armorJ = value(player, "minecraft:generic.armor");
        double heavyJ = value(player, "redtale_project:physical_damage");
        total++;
        boolean okJ = j.ok() && j.removed() == 3
                && Math.abs(atkJ - atkBase) < 1e-9 && Math.abs(armorJ - armorBase) < 1e-9
                && Math.abs(heavyJ - heavyBase) < 1e-6
                && store.nodes(player.getUUID(), tree).isEmpty()
                && TreeService.snapshot(player, def).isEmpty();
        pass += okJ ? 1 : 0;
        send(src, "  J 洗点 → 退回 " + j.removed() + " 个 · 攻速 " + atkJ + " / 护甲 " + armorJ
                + " / 物伤 " + heavyJ + " · 残留 " + TreeService.snapshot(player, def).size()
                + (okJ ? " ✓" : " ✗"));

        // K：树 / 节点不存在
        TreeService.Result k1 = TreeService.take(player, "no_such_tree", "root");
        TreeService.Result k2 = TreeService.take(player, tree, "no_such_node");
        total++;
        boolean okK = k1.status() == TreeService.Status.UNKNOWN_TREE
                && k2.status() == TreeService.Status.UNKNOWN_NODE;
        pass += okK ? 1 : 0;
        send(src, "  K 树/节点不存在 → " + k1.status() + " / " + k2.status() + (okK ? " ✓" : " ✗"));

        // L：判定层口径没被服务层改写（root 计入 taken）
        total++;
        boolean okL = TreePoints.state(def, List.of("root")).taken() == 1;
        pass += okL ? 1 : 0;
        send(src, "  L 判定层口径（root 计入 taken）→ " + okL + (okL ? " ✓" : " ✗"));

        // M：前缀扫描 ≠ 按声明清单清场 —— 手工挂一个「配置里已经不存在的节点 id」，
        //    下发时必须一起被清掉（这是「改配置后属性残留」的真假分界，SKILL-TREE §5 第 4 条）
        AttributeSpec move = AttributeSpecs.resolve("minecraft:generic.movement_speed");
        net.minecraft.core.Holder<Attribute> moveHolder = move == null ? null : RedtaleAttributes.holder(move);
        AttributeInstance moveInst = moveHolder == null ? null : player.getAttribute(moveHolder);
        double moveBase = moveInst == null ? Double.NaN : moveInst.getValue();
        boolean okM = false;
        if (moveInst != null) {
            moveInst.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            com.redtale.project.RedtaleProject.MOD_ID, "tree/warrior_vanguard/ghost"),
                    0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            double moveDirty = moveInst.getValue();
            TreeService.applyTree(player, def); // 一次下发 = 一次前缀清场
            double moveAfter = moveInst.getValue();
            okM = Math.abs(moveDirty - (moveBase + 0.5)) < 1e-9 && Math.abs(moveAfter - moveBase) < 1e-9;
        }
        total++;
        pass += okM ? 1 : 0;
        send(src, "  M 幽灵 modifier（tree/warrior_vanguard/ghost）被前缀清场 → "
                + (okM ? "✓" : "✗") + " · 移速回 " + value(player, "minecraft:generic.movement_speed"));

        // N：等级 → 点数上限（R54-a）—— 等级掉回 1，上限只剩 1 点，cost 2 的节点必须 NO_POINTS
        store.setMainExp(player.getUUID(), 0L);
        int lowLevel = TreeService.levelOf(player);
        TreePoints.State lowState = TreeService.state(player, def);
        TreeService.take(player, tree, "root"); // 0 费，只为了让 atk_spd_1 的前置成立
        TreeService.Result n1 = TreeService.take(player, tree, "atk_spd_1");
        TreeService.Result n2 = TreeService.take(player, tree, "atk_spd_2");
        total++;
        boolean okN = lowLevel == 1 && lowState.maxPoints() == Math.min(def.maxPoints(),
                def.pointsPerLevel() * lowLevel) && n1.ok() && n2.status() == TreeService.Status.NO_POINTS;
        pass += okN ? 1 : 0;
        send(src, "  N 等级 " + lowLevel + " → 上限 " + lowState.maxPoints() + " · cost1 " + n1.status()
                + " / cost2 " + n2.status() + (okN ? " ✓" : " ✗"));

        // O：`unlock_skill` 的第一个消费方（R57）—— 点通 atk_spd 链 + gale → 解锁 gale_slash
        store.setMainExp(player.getUUID(), 1_000_000_000L);
        TreeService.take(player, tree, "root");
        TreeService.take(player, tree, "atk_spd_1");
        TreeService.take(player, tree, "atk_spd_2");
        TreeService.Result o1 = TreeService.take(player, tree, "gale");
        boolean oFull = TreeService.isUnlocked(player, "redtale_project:gale_slash");
        boolean oBare = TreeService.isUnlocked(player, "gale_slash");
        boolean oGate = TreeService.gated("redtale_project:gale_slash")
                && TreeService.gatedSkillPaths().contains("gale_slash")
                && !TreeService.gated("redtale_project:arc_bolt");
        total++;
        boolean okO = o1.ok() && oFull && oBare && oGate;
        pass += okO ? 1 : 0;
        send(src, "  O 点 gale → " + o1.status() + " · 解锁 gale_slash 全名 " + oFull + " / 裸名 " + oBare
                + " / 门槛（arc_bolt 免门槛）" + oGate + (okO ? " ✓" : " ✗"));

        // P：退点后解锁集立刻回收（派生值、不落存档）；`unlockedText` 空态是固定文案
        TreeService.untake(player, tree, "gale");
        boolean pEmpty = TreeService.unlockedSkills(player).isEmpty();
        boolean pText = "  （无）".equals(TreeService.unlockedText(player));
        total++;
        boolean okP = pEmpty && pText;
        pass += okP ? 1 : 0;
        send(src, "  P 退 gale → 解锁集 " + TreeService.unlockedSkills(player).size()
                + " 条 / 文案空态 " + pText + (okP ? " ✓" : " ✗"));

        // Q：重新点回 → `unlockedText` 带两空格前缀、含全名
        TreeService.take(player, tree, "gale");
        String qText = TreeService.unlockedText(player);
        total++;
        boolean okQ = qText.startsWith("  ") && qText.contains("gale_slash") && !qText.endsWith("\n");
        pass += okQ ? 1 : 0;
        send(src, "  Q 解锁文案 → " + qText.replace("\n", " | ") + (okQ ? " ✓" : " ✗"));

        // R：`tree skills <tree>` 观测面（R58）—— 声明集 2 条按定义序、玩家段带解锁标注、控制台只报声明
        List<String> declaredR = declaredSkills(def);
        String rTaken = skillsText(def, player);
        TreeService.untake(player, tree, "gale");
        String rFree = skillsText(def, player);
        TreeService.take(player, tree, "gale");
        String rConsole = skillsText(def, null);
        total++;
        boolean okR = declaredR.size() == 2
                && declaredR.get(0).endsWith("gale_slash") && declaredR.get(1).endsWith("quake_slam")
                && rTaken.contains("gale_slash · 已解锁") && rTaken.contains("quake_slam · 未解锁")
                && rFree.contains("gale_slash · 未解锁")
                && rConsole.contains("（控制台不读玩家存档）") && rConsole.split("\n").length == 3
                && rConsole.endsWith("quake_slam");
        pass += okR ? 1 : 0;
        send(src, "  R tree skills → 声明 " + declaredR.size() + " 条 · 已解锁段 "
                + (rTaken.contains("gale_slash · 已解锁") ? "命中" : "缺失") + " · 控制台 "
                + rConsole.split("\n").length + " 行" + (okR ? " ✓" : " ✗"));

        // 收尾清场：FakePlayer 的等级与已点节点都不留（否则下一轮 / 其它探针读到上一轮的 1e9 经验）
        store.clearTree(player.getUUID(), tree);
        store.clear(player.getUUID());
        TreeService.removeAll(player);

        int finalPass = pass;
        int finalTotal = total;
        send(src, "[redtale] tree probe " + finalPass + "/" + finalTotal + " 通过");
        return finalPass;
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    /** 读一个属性的当前值（走 80 条表的解析，自建属性是裸 path）。 */
    private static double value(ServerPlayer player, String attribute) {
        AttributeSpec spec = AttributeSpecs.resolve(attribute);
        if (spec == null) {
            return Double.NaN;
        }
        net.minecraft.core.Holder<Attribute> holder = RedtaleAttributes.holder(spec);
        if (holder == null) {
            return Double.NaN;
        }
        AttributeInstance instance = player.getAttribute(holder);
        return instance == null ? Double.NaN : instance.getValue();
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String classes(List<String> list) {
        return list == null || list.isEmpty() ? "全职业" : String.join(",", list);
    }
}
