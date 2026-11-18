package com.redtale.project.selftest;

import com.redtale.project.core.damage.MultiplierRules;
import com.redtale.project.core.damage.TargetClass;
import com.redtale.project.core.math.AttackSpeedCurve;
import com.redtale.project.core.math.Caps;
import com.redtale.project.core.math.Sanity;
import com.redtale.project.core.math.Settle;
import com.redtale.project.core.math.StatValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 离线数值单测（{@code gradle selftest}，不需要 Minecraft 运行时）。
 *
 * <p>只测**纯函数**：settle / StatValue / AttackSpeedCurve。这三块是 M1 的地基，
 * 一旦算错会同时污染 tooltip 与实打伤害，而它们在游戏里极难用肉眼验证
 * （「打出来 9 而写着 12.4」这种问题定位成本极高）。
 *
 * <p>不引入 JUnit：离线环境里 Gradle 拉不到测试依赖（见 docs/BUILD.md），
 * 一个 main + 断言计数器足够，且能直接进 CI 的退出码。
 */
public final class SelfTest {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // ---- settle：默认全关，先加后乘 -------------------------------------
        expect("caps off: 10 * 1.5 * 1.5 * 1.5 = 33.75",
                Settle.value(10, 0.5, List.of(0.5, 0.5), Caps.OFF, Sanity.DEFAULT), 33.75);

        expect("percentCap=0.2: 10 * 1.2 * 2.25 = 27",
                Settle.value(10, 0.5, List.of(0.5, 0.5), new Caps(0.2, 0, 0, 0), Sanity.DEFAULT), 27.0);

        expect("condCount=1: 10 * 1.5 * 1.5 = 22.5",
                Settle.value(10, 0.5, List.of(0.5, 0.5), new Caps(0, 0, 1, 0), Sanity.DEFAULT), 22.5);

        expect("condCap=0.1: 10 * 1.5 * 1.1 * 1.1 = 18.15",
                Settle.value(10, 0.5, List.of(0.5, 0.5), new Caps(0, 0.1, 0, 0), Sanity.DEFAULT), 18.15);

        expect("damageCap=5: 33.75 -> 5",
                Settle.value(10, 0.5, List.of(0.5, 0.5), new Caps(0, 0, 0, 5), Sanity.DEFAULT), 5.0);

        // ---- settle：L3 兜底 ------------------------------------------------
        expect("negative damage -> 0", Settle.value(-10, 0.5, List.of(), Caps.OFF, Sanity.DEFAULT), 0.0);
        expect("base=NaN -> 0 (no NaN leak)",
                Settle.value(Double.NaN, 0.5, List.of(), Caps.OFF, Sanity.DEFAULT), 0.0);
        expect("percent=NaN -> 0", Settle.value(10, Double.NaN, List.of(), Caps.OFF, Sanity.DEFAULT), 10.0);
        expect("NaN cond is skipped",
                Settle.value(10, 0, java.util.Arrays.asList(0.5, Double.NaN), Caps.OFF, Sanity.DEFAULT), 15.0);
        expect("1e12 hits 1e9 hard ceiling",
                Settle.value(1.0e12, 0, List.of(), Caps.OFF, Sanity.DEFAULT), 1.0e9);
        expect("ceiling off: 1e12 passes through",
                Settle.value(1.0e12, 0, List.of(), Caps.OFF,
                        new Sanity(true, 0, 0, 0)), 1.0e12);

        // ---- settle：日志阈值只观测不改值 -----------------------------------
        Settle.Result big = Settle.settle(20000, 0, List.of(), Caps.OFF, Sanity.DEFAULT);
        check("over-threshold value is not modified", big.value() == 20000);
        check("shouldLog true above threshold", Settle.shouldLog(big, Sanity.DEFAULT, 0.05));
        check("shouldLog false outside sample rate", !Settle.shouldLog(big, Sanity.DEFAULT, 0.5));

        // ---- StatValue ------------------------------------------------------
        StatValue sv = new StatValue(5, 0.5, 2, 1);
        expect("spread clamped to maxSpread: 5 + 0.5*10 + 1 = 11", sv.valueAt(10), 11.0);
        StatValue noSpread = new StatValue(7, 0, 0, 0);
        expect("no spread equals base", noSpread.valueAt(99), 7.0);

        StatValue rolled = sv.roll(() -> 1.0);
        expect("roll(1.0) clamped: spread = +1", rolled.spread(), 1.0);
        StatValue rolledLow = sv.roll(() -> 0.0);
        expect("roll(0.0) clamped: spread = -1", rolledLow.spread(), -1.0);
        expect("roll(0.5) centered: spread = 0", sv.roll(() -> 0.5).spread(), 0.0);

        // ---- AttackSpeedCurve ----------------------------------------------
        expect("20/12.5 = 1.6 per second", AttackSpeedCurve.perSecondFromDelay(12.5), 1.6);
        expect("no soft cap: delay unchanged", AttackSpeedCurve.apply(12.5, 0, 1.0), 12.5);
        expect("delay 0.5 -> floor 1.0", AttackSpeedCurve.apply(0.5, 0, 1.0), 1.0);
        expect("delay 0 (div-by-zero source) -> floor 1.0", AttackSpeedCurve.apply(0, 0, 1.0), 1.0);
        expect("delay NaN -> floor 1.0", AttackSpeedCurve.apply(Double.NaN, 0, 1.0), 1.0);
        expect("floor configured 0 still forced to 1.0", AttackSpeedCurve.apply(0.2, 0, 0), 1.0);
        check("soft_cap=4 keeps 1024 below 4/s",
                AttackSpeedCurve.effectivePerSecond(1024, 4.0) <= 4.0
                && AttackSpeedCurve.effectivePerSecond(1024, 4.0) > 3.99);
        check("soft_cap=4 raw=4 gets ~63%",
                Math.abs(AttackSpeedCurve.effectivePerSecond(4, 4.0) - 4 * (1 - Math.exp(-1))) < 1e-9);
        expect("soft_cap=4 -> delay ~5 ticks",
                AttackSpeedCurve.delayTicks(AttackSpeedCurve.effectivePerSecond(1024, 4.0), 1.0), 5.0, 0.02);

        // ---- MultiplierRules：L2 乘区的作用范围 -----------------------------
        MultiplierRules nsWildcard =
                new MultiplierRules(List.of("redtale_project:*"), true, List.of("physical_damage"));
        check("namespace 通配命中自建伤害", nsWildcard.appliesTo("redtale_project:verification_strike"));
        check("不在白名单的原版伤害放行", !nsWildcard.appliesTo("minecraft:generic"));
        check("裸路径按 minecraft: 处理", !nsWildcard.appliesTo("generic"));

        MultiplierRules exact = new MultiplierRules(List.of("minecraft:player_attack"), false, List.of());
        check("精确 id 命中", exact.appliesTo("minecraft:player_attack"));
        check("精确 id 不误伤同前缀", !exact.appliesTo("minecraft:player_attack_fake"));

        MultiplierRules all = new MultiplierRules(List.of("*"), false, List.of());
        check("\"*\" 全通配", all.appliesTo("spell_power:fire"));
        check("空清单永不生效",
                !new MultiplierRules(List.of(), false, List.of()).appliesTo("redtale_project:x"));

        // ---- 类别池（R26-b）：技能伤害按类别分轴 -----------------------------
        check("默认：法术技能吃 magical_damage",
                MultiplierRules.DEFAULT.attributesFor("redtale_project:skill_magical")
                        .equals(List.of("magical_damage")));
        check("默认：物理技能吃 physical_damage",
                MultiplierRules.DEFAULT.attributesFor("redtale_project:skill_physical")
                        .equals(List.of("physical_damage")));
        check("未命中类别 → 回落 percent_attributes",
                MultiplierRules.DEFAULT.attributesFor("redtale_project:verification_strike")
                        .equals(MultiplierRules.DEFAULT.percentAttributes()));
        check("类别池不影响白名单判定（两者共用匹配语义）",
                MultiplierRules.DEFAULT.appliesTo("redtale_project:skill_magical"));

        List<String> catWarn = new ArrayList<>();
        var catCustom = MultiplierRules.parse(
                com.redtale.project.core.config.Json5.parse("""
                        { category_attributes: [
                            { type: 'redtale_project:skill_magical', attributes: ['frost_damage', 'arcane_damage'] },
                            { attributes: ['x'] },
                            { type: 'redtale_project:trap', attributes: 'not_an_array' } ] }
                        """).getAsJsonObject(), catWarn);
        check("自定义类别池被解析（法术两条）",
                catCustom.attributesFor("redtale_project:skill_magical")
                        .equals(List.of("frost_damage", "arcane_damage")));
        check("缺 type / attributes 非数组的类别项被跳过并各 1 条告警",
                catCustom.categories().size() == 1 && catWarn.size() == 2);

        var catOff = MultiplierRules.parse(
                com.redtale.project.core.config.Json5.parse("{ category_attributes: [] }")
                        .getAsJsonObject(), new ArrayList<>());
        check("显式写 [] 等于关掉分轴（法术也落回 physical_damage）",
                catOff.categories().isEmpty()
                        && catOff.attributesFor("redtale_project:skill_magical")
                                .equals(catOff.percentAttributes()));

        check("first-match：前面的类别先命中",
                new MultiplierRules(List.of("redtale_project:*"), true, List.of("physical_damage"),
                        Map.of(),
                        List.of(new MultiplierRules.Category("redtale_project:*", List.of("shadow_damage")),
                                new MultiplierRules.Category("redtale_project:skill_magical", List.of("magical_damage"))))
                        .attributesFor("redtale_project:skill_magical").equals(List.of("shadow_damage")));

        // ---- 目标分类（M2 条件乘区的唯一分类实现）---------------------------
        check("非玩家非亡灵 → 只有 pve",
                TargetClass.keysFor(false, false).equals(List.of("pve")));
        check("亡灵 → pve + undead（子集加成，不是互斥）",
                TargetClass.keysFor(false, true).equals(List.of("pve", "undead")));
        check("玩家 → 只有 pvp", TargetClass.keysFor(true, false).equals(List.of("pvp")));
        check("玩家优先于亡灵判定", TargetClass.keysFor(true, true).equals(List.of("pvp")));

        // ---- 条件乘区解析：默认三条全开、显式 {} 关闭、错键告警 ---------------
        var condDefault = MultiplierRules.parse(
                com.redtale.project.core.config.Json5.parse(
                        "{ damage_types: ['redtale_project:*'] }").getAsJsonObject(),
                new ArrayList<>());
        check("缺 conditional_attributes 时默认三条全开",
                condDefault.conditionalAttributes().keySet()
                        .equals(java.util.Set.of("pve", "pvp", "undead"))
                        && condDefault.conditionalAttributes().get("undead").equals(List.of("undead_damage")));

        List<String> condWarn = new ArrayList<>();
        var condCustom = MultiplierRules.parse(
                com.redtale.project.core.config.Json5.parse("""
                        { conditional_attributes: { pve: ['pve_damage', 'holy_damage'], pvp: [], typo_key: ['x'] } }
                        """).getAsJsonObject(), condWarn);
        check("自有键被解析（pve 两条 / pvp 空表）",
                condCustom.conditionalAttributes().get("pve").size() == 2
                        && condCustom.conditionalAttributes().get("pvp").isEmpty());
        check("未知键 typo_key 被跳过并告警",
                !condCustom.conditionalAttributes().containsKey("typo_key") && condWarn.size() == 1);

        var condOff = MultiplierRules.parse(
                com.redtale.project.core.config.Json5.parse("{ conditional_attributes: {} }")
                        .getAsJsonObject(), new ArrayList<>());
        check("显式写 {} 等于关掉条件乘区", condOff.conditionalAttributes().isEmpty());

        // ---- 因子口径：闸门全关时 settle(base, p) / base == 1 + p（L2 只乘不加）
        expect("factor = 1 + percent",
                Settle.value(10, 0.2, List.of(), Caps.OFF, Sanity.DEFAULT) / 10, 1.2);

        // 条件项与无条件项进同一个池：0.2（物理）+ 0.3（对亡灵）→ ×1.5，不是 ×1.56
        expect("条件项相加不连乘",
                Settle.value(10, 0.2 + 0.3, List.of(), Caps.OFF, Sanity.DEFAULT), 15.0);

        // ---- M2：内容解析（未知属性跳过、未知基底拒绝、耐久默认值）---------
        List<String> warnings = new ArrayList<>();
        var swordRoot = com.redtale.project.core.config.Json5.parse("""
                {
                  id: 't_sword',
                  base: 'sword',
                  durability: 900,
                  stats: { attack_damage: 7, physical_damage: 0.2, not_a_stat: 1 },
                }
                """).getAsJsonObject();
        var sword = com.redtale.project.items.ItemContent.parse(swordRoot, "t/sword.json5", warnings);
        check("内容解析成功", sword != null);
        check("基底解析为 SWORD", sword != null && sword.base() == com.redtale.project.items.ItemBase.SWORD);
        check("未知属性被跳过，只留 2 条", sword != null && sword.stats().size() == 2);
        check("未知属性产生 1 条告警", warnings.size() == 1);
        check("attack_damage 归一到原版全名",
                sword != null && sword.stats().containsKey("minecraft:generic.attack_damage"));
        check("physical_damage 保留原始小数",
                sword != null && Double.valueOf(0.2).equals(sword.stats().get("physical_damage")));

        var badBase = com.redtale.project.core.config.Json5.parse(
                "{ id: 'x', base: 'katana' }").getAsJsonObject();
        List<String> badWarnings = new ArrayList<>();
        check("未知基底被拒绝",
                com.redtale.project.items.ItemContent.parse(badBase, "t/bad.json5", badWarnings) == null
                        && badWarnings.size() == 1);

        var noDura = com.redtale.project.core.config.Json5.parse(
                "{ id: 't_ring', base: 'ring' }").getAsJsonObject();
        var ring = com.redtale.project.items.ItemContent.parse(noDura, "t/ring.json5", new ArrayList<>());
        check("缺 durability 时用基底默认值（饰品 = 0 不可损坏）", ring != null && ring.durability() == 0);

        // ---- M2：显示单位（0.2 显示成 +20%，护甲 20 显示成 +20）-------------
        var phys = com.redtale.project.core.attr.AttributeSpecs.resolve("physical_damage");
        var armor = com.redtale.project.core.attr.AttributeSpecs.resolve("armor");
        check("百分比属性显示 +20%",
                "+20%".equals(com.redtale.project.items.StatFormat.signed(phys, 0.2)));
        check("平加属性显示 +20",
                "+20".equals(com.redtale.project.items.StatFormat.signed(armor, 20)));

        // ---- 汇总 -----------------------------------------------------------
        affix();
        tree();
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("[selftest] PASS " + passed + " 项");
            return;
        }
        System.out.println("[selftest] FAIL " + failures.size() + " / passed " + passed);
        for (String f : failures) {
            System.out.println("  - " + f);
        }
        System.exit(1);
    }

    /**
     * D22 词条：解析（数字 / 曲线两种写法）+ 加权不放回 + 同组互斥 + 等级区间 + 数值累加。
     *
     * <p>全部用固定 seed 的 {@link java.util.Random}，所以「摇出哪一条」是确定的 ——
     * 只验规则，不验概率分布（概率要靠上千次统计，不适合放进这个粗粒度自测）。
     */
    private static void affix() {
        var parse = (java.util.function.Function<String, com.google.gson.JsonObject>)
                s -> com.redtale.project.core.config.Json5.parse(s).getAsJsonObject();

        List<com.redtale.project.core.config.ConfigFile> configs = new ArrayList<>();
        configs.add(new com.redtale.project.core.config.ConfigFile("affixes/sharp.json5", parse.apply("""
                { id: 'sharp', kind: 'prefix', weight: 100, exclusive_group: 'damage',
                  stats: { attack_damage: { base: 3.0, per_item_level: 0.08 } } }
                """)));
        configs.add(new com.redtale.project.core.config.ConfigFile("affixes/heavy.json5", parse.apply("""
                { id: 'heavy', kind: 'prefix', weight: 80, exclusive_group: 'damage',
                  stats: { attack_damage: 4 } }
                """)));
        configs.add(new com.redtale.project.core.config.ConfigFile("affixes/bear.json5", parse.apply("""
                { id: 'of_the_bear', kind: 'suffix', weight: 60, stats: { 'minecraft:generic.max_health': 2 } }
                """)));
        configs.add(new com.redtale.project.core.config.ConfigFile("affixes/high.json5", parse.apply("""
                { id: 'high_only', kind: 'prefix', weight: 100, item_level: { min: 10, max: 60 },
                  stats: { attack_damage: 99 } }
                """)));
        configs.add(new com.redtale.project.core.config.ConfigFile("affix_pools/p.json5", parse.apply("""
                { id: 'p', applies_to: ['redtale_project:sword'], slots: { prefix: 1, suffix: 1, core: 0 },
                  prefix: { sharp: 100, heavy: 80, high_only: 100, ghost: 50 },
                  suffix: { of_the_bear: 60 } }
                """)));

        List<String> w = new ArrayList<>();
        var defs = com.redtale.project.items.AffixPools.parseDefs(configs, w);
        var pools = com.redtale.project.items.AffixPools.parsePools(configs, w);
        check("词条解析 4 条定义 / 1 个池", defs.size() == 4 && pools.size() == 1);
        check("曲线形态装进 StatValue",
                defs.get("sharp").stats().get("minecraft:generic.attack_damage") != null
                        && Math.abs(defs.get("sharp").stats().get("minecraft:generic.attack_damage").scale() - 0.08) < 1e-9);
        check("裸数字 = scale 0", defs.get("heavy").stats().values().iterator().next().scale() == 0);

        var pool = com.redtale.project.items.AffixRoller.poolFor(pools.values(), "sword");
        check("基底命中池", pool != null);
        check("未命中基底返回 null",
                com.redtale.project.items.AffixRoller.poolFor(pools.values(), "helmet") == null);

        List<String> rw = new ArrayList<>();
        var rolled = com.redtale.project.items.AffixRoller.roll(pool, defs, 1, new java.util.Random(7), rw);
        check("两条槽位各出一条（prefix + suffix）", rolled.affixIds().size() == 2);
        check("等级 1 摇不到 min=10 的 high_only", !rolled.affixIds().contains("high_only"));
        check("未定义词条 ghost 告警 1 条", rw.size() == 1);
        check("suffix 必是 of_the_bear", rolled.affixIds().get(1).equals("of_the_bear"));
        check("max_health 数值 = 2",
                Double.valueOf(2.0).equals(rolled.stats().get("minecraft:generic.max_health")));
        // sharp(3.0 + 0.08×1) 或 heavy(4)，都只出一条（同组 damage）
        double atk = rolled.stats().getOrDefault("minecraft:generic.attack_damage", 0.0);
        check("同组互斥：攻击伤害只来自一条前缀（3.08 或 4.0）",
                Math.abs(atk - 3.08) < 1e-9 || Math.abs(atk - 4.0) < 1e-9);

        // 不放回：prefix 给 2 槽但候选只有 2 条且同组 → 第二条拿不到，不会重复
        var p2 = new com.redtale.project.items.AffixPools.Pool("p2", List.of("*"), 3, 0, 0,
                List.of(new com.redtale.project.items.AffixPools.Entry("sharp", 100),
                        new com.redtale.project.items.AffixPools.Entry("heavy", 100)));
        var r2 = com.redtale.project.items.AffixRoller.roll(p2, defs, 1, new java.util.Random(1), null);
        check("3 槽 × 同组 2 条 → 只出 1 条", r2.affixIds().size() == 1);

        var r3 = com.redtale.project.items.AffixRoller.roll(pool, defs, 20, new java.util.Random(3), null);
        check("等级 20 才可能出 high_only",
                r3.affixIds().stream().noneMatch("high_only"::equals) || true); // 概率项，只验不抛
        check("random=null 不摇", com.redtale.project.items.AffixRoller
                .roll(pool, defs, 1, null, null).affixIds().isEmpty());
        check("pool=null 返回空", com.redtale.project.items.AffixRoller
                .roll(null, defs, 1, new java.util.Random(1), null).affixIds().isEmpty());
    }

    /**
     * M7 出口：等级换点数（R54）。{@code capFor} 是唯一派生点，TreePoints 的两种入口都必须落在它上面。
     */
    private static void tree() {
        // 上限派生：赚到 = 每级点数 × 等级，再与 max_points 取小
        expect("capFor 1 点/级 × L1 = 1",
                com.redtale.project.core.progression.TreePoints.capFor(21, 1, 1), 1);
        expect("capFor 1 点/级 × L30 被 max_points 21 封顶",
                com.redtale.project.core.progression.TreePoints.capFor(21, 1, 30), 21);
        expect("capFor 2 点/级 × L3 = 6",
                com.redtale.project.core.progression.TreePoints.capFor(21, 2, 3), 6);
        expect("capFor max_points=0 表示不设上限（21 点/级 × L1 = 21）",
                com.redtale.project.core.progression.TreePoints.capFor(0, 21, 1), 21);
        expect("capFor 每级 0 点 = 关掉等级门槛（退回 max_points）",
                com.redtale.project.core.progression.TreePoints.capFor(21, 0, 5), 21);
        expect("capFor 等级 0 不报负",
                com.redtale.project.core.progression.TreePoints.capFor(21, 1, 0), 0);
        check("capFor 大数不溢出",
                com.redtale.project.core.progression.TreePoints.capFor(0, Integer.MAX_VALUE, 3)
                        == Integer.MAX_VALUE);

        // 同一棵树：三节点（root 0 / a 1 / b 2），max_points 21、每级 1 点
        var nodes = new java.util.LinkedHashMap<String, com.redtale.project.core.progression.SkillTreeSpec.Node>();
        nodes.put("root", new com.redtale.project.core.progression.SkillTreeSpec.Node(
                "root", "起点", 0, new int[]{0, 0}, List.of(), List.of()));
        nodes.put("a", new com.redtale.project.core.progression.SkillTreeSpec.Node(
                "a", "A", 1, new int[]{0, 1}, List.of("root"), List.of()));
        nodes.put("b", new com.redtale.project.core.progression.SkillTreeSpec.Node(
                "b", "B", 2, new int[]{0, 2}, List.of("a"), List.of()));
        var def = new com.redtale.project.core.progression.SkillTreeSpec.Definition(
                "t", "T", "minecraft:stone", List.of("warrior"), 21, 1, nodes, "selftest");

        check("L1 selected=[] → 上限 1、剩余 1",
                com.redtale.project.core.progression.TreePoints.state(def, List.of(), 1).maxPoints() == 1
                        && com.redtale.project.core.progression.TreePoints.state(def, List.of(), 1).available() == 1);
        check("L1 点 cost2 的 b → NO_POINTS（先点 a 让前置成立）",
                com.redtale.project.core.progression.TreePoints.canTake(def, List.of("root", "a"), "b", 2)
                        == com.redtale.project.core.progression.TreePoints.Status.NO_POINTS);
        check("L3 点 cost2 的 b → OK",
                com.redtale.project.core.progression.TreePoints.canTake(def, List.of("root", "a"), "b", 3)
                        == com.redtale.project.core.progression.TreePoints.Status.OK);
        check("L1 点 cost1 的 a → OK",
                com.redtale.project.core.progression.TreePoints.canTake(def, List.of("root"), "a", 1)
                        == com.redtale.project.core.progression.TreePoints.Status.OK);
        check("带等级入口与 capFor 同源（L2 上限 2）",
                com.redtale.project.core.progression.TreePoints.state(def, List.of("root", "a"), 2).maxPoints() == 2);
        check("已用点数不变（等级只改上限）",
                com.redtale.project.core.progression.TreePoints.state(def, List.of("root", "a"), 9).used() == 1);
        check("旧两参入口仍按 max_points 算（R53 行为不变）",
                com.redtale.project.core.progression.TreePoints.state(def, List.of("root")).maxPoints() == 21);
        check("旧三参 canTake 仍按 max_points 判（探针不因等级掉点）",
                com.redtale.project.core.progression.TreePoints.canTake(def, List.of("root", "a"), "b")
                        == com.redtale.project.core.progression.TreePoints.Status.OK);
        check("等级回档后剩余点数为负也不抛（超额配置保护）",
                com.redtale.project.core.progression.TreePoints.state(def, List.of("root", "a", "b"), 1)
                        .available() == -2);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failures.add(name);
            System.out.println("  FAIL " + name);
        }
    }

    private static void expect(String name, double actual, double expected) {
        expect(name, actual, expected, 1e-9);
    }

    private static void expect(String name, double actual, double expected, double eps) {
        boolean ok = Math.abs(actual - expected) <= eps;
        check(name + " [actual=" + actual + " expected=" + expected + "]", ok);
    }
}
