# 技能树（D25）：结构、schema 与三个例子

2026-11-15。回答「技能树具体是什么样」。上游参照 `MMOCore default/skill-trees/warrior-paladin.yml`。

## 1. 它到底是什么

一句话：**一张有向图，每个节点是一次永久解锁，前置点满了才能点下一层。**

它和另外两个东西必须分清，否则会做成三套重复系统：

| 系统 | 给什么 | 谁决定 | 举例 |
|---|---|---|---|
| **属性点**（已有，M7） | 数值微调，自由分配 | 玩家自己加减 | 力量 +1 → 攻击 +2% |
| **技能树**（本条） | 解锁**能力**与**专精**，不可自由加减 | 树的形状决定 | 点出「碎地」这个技能；或「攻速专精 +15%」 |
| **装备/词条**（M2） | 换装才有 | 掉落与打造 | 一把带「锋利」的剑 |

上游 MMOCore 也是这么分的：`attributes/default_attributes.yml` 管自由加点，`skill-trees/*.yml` 管解锁。我们照这个分工，**不做两套属性**。

## 2. 与上游的差异（有意的）

| 项 | MMOCore | 我们 |
|---|---|---|
| 节点效果 | `experience-table.triggers[]`，套一层「经验表」抽象 | 直接写 `effects[]`，复用脚本层（effect/condition），与附魔/技能同一套 |
| 坐标 | `coordinates{x,y}` 必填，手写像素位置 | `pos` 可选，缺省自动布局；给了才手排 |
| 点数 | `max-point-spent` 全局上限 | `max_points` + 每个节点自己的 `cost` |
| 洗点 | 命令 | 走经济桥（D28），带冷却 |

好处：技能树节点能直接用上 M4/M5 已经做好的原语，不用再发明一套「触发器」。

## 3. Schema

```json5
// config/redtale_project/skill_trees/warrior_vanguard.json5
{
  id: "warrior_vanguard",
  name: "tree.redtale.warrior_vanguard",
  icon: "minecraft:iron_axe",
  classes: ["warrior"],              // 空数组 = 所有职业可用
  max_points: 21,
  points_per_level: 1,               // 从 1 级开始，每级 1 点
  respec: { cost: { currency: "coin", amount: 500 }, cooldown_hours: 24 },

  nodes: {
    root: { name: "tree.node.warrior.root", cost: 0, pos: [1, 0], requires: [],
            effects: [] },

    atk_spd_1: { name: "tree.node.warrior.atk_spd_1", cost: 1, pos: [0, 1],
                 requires: ["root"],
                 effects: [{ type: "attribute", attribute: "minecraft:generic.attack_speed",
                             operation: "add_multiplied_total", value: 0.05 }] },

    atk_spd_2: { name: "tree.node.warrior.atk_spd_2", cost: 2, pos: [0, 2],
                 requires: ["atk_spd_1"],
                 effects: [{ type: "attribute", attribute: "minecraft:generic.attack_speed",
                             operation: "add_multiplied_total", value: 0.05 }] },

    gale:      { name: "tree.node.warrior.gale", cost: 3, pos: [0, 3],
                 requires: ["atk_spd_2"],
                 effects: [{ type: "unlock_skill", skill: "redtale_project:gale_slash" }] },

    heavy_1:   { name: "tree.node.warrior.heavy_1", cost: 1, pos: [1, 1],
                 requires: ["root"],
                 effects: [{ type: "attribute", attribute: "redtale_project:blunt_power",
                             operation: "add_value", value: 8 }] },

    heavy_2:   { name: "tree.node.warrior.heavy_2", cost: 2, pos: [1, 2],
                 requires: ["heavy_1"],
                 effects: [{ type: "attribute", attribute: "minecraft:generic.attack_knockback",
                             operation: "add_value", value: 0.5 }] },

    quake:     { name: "tree.node.warrior.quake", cost: 4, pos: [1, 3],
                 requires: ["heavy_2"],
                 effects: [{ type: "unlock_skill", skill: "redtale_project:quake_slam" }] },

    armor_1:   { name: "tree.node.warrior.armor_1", cost: 1, pos: [2, 1],
                 requires: ["root"],
                 effects: [{ type: "attribute", attribute: "minecraft:generic.armor",
                             operation: "add_value", value: 2 }] },

    armor_2:   { name: "tree.node.warrior.armor_2", cost: 2, pos: [2, 2],
                 requires: ["armor_1"],
                 effects: [{ type: "attribute", attribute: "redtale_project:armor_toughness",
                             operation: "add_multiplied_total", value: 0.10 }] },

    unyielding:{ name: "tree.node.warrior.unyielding", cost: 3, pos: [2, 3],
                 requires: ["armor_2"],
                 effects: [{ type: "passive", id: "damage_reduction_when_low_hp",
                             args: { threshold: 0.3, reduction: 0.15 } }] },
  },
}
```

节点 `effects` 只有三类，够用且不膨胀：

- `attribute` —— 永久属性修饰符（**固定 modifier id = 树 id + 节点 id**，洗点按这个 id 逐个移除，绝不自己乘）
- `unlock_skill` —— 允许这个角色使用某个技能（技能本体在 M5 的技能表里）
- `passive` —— 挂一个脚本层被动（`trigger` + `effect` 组合）

## 4. 三个例子（说明手感差异）

| 树 | 形状 | 特点 |
|---|---|---|
| **战士 · 先锋**（上图） | 三条平行线，末端各一个技能 | 最朴素，第一次就能懂；21 点点满需要 21 级 |
| **法师 · 元素** | Y 字分叉后互斥（火/冰只能走一条到顶） | 用 `exclusive_with` 表达互斥，洗点动机最强 |
| **游侠 · 疾风** | 中间一条主干 + 两侧小节点 | 主干免费（`cost: 0`）保证人人都有一条路，两侧是专精 |

## 5. 实现要点（M7）

1. **节点 id 不可重命名**。存档里存的是 `tree_id + node_id`。要改名只能加 `migrate_from` 字段做映射，否则玩家的点会丢。
2. **点数是派生值，不是独立货币**：`已用点数 = Σ(已点节点 cost)`，`可用 = max_points - 已用`。不做「先发点数再消耗」，避免与等级回档脱节。
3. **条件不满足时整棵树灰显**（职业不符/前置未点），不是隐藏——玩家要看见目标。
4. **洗点必须能完整回滚**：所有 `attribute` 效果的 modifier id 写成 `redtale:tree/<tree>/<node>`，洗点时按前缀批量移除；属性残留是这类系统最常见的 bug。
5. **GUI 用 owo-lib**（D30）。树的绘制是纯客户端的事，服务端只发「已点节点集合 + 可用点数」，一次 payload 就够。

## 6. 不做

- 不做「技能树内嵌属性点分配」——两套分开，避免玩家搞不清哪个能洗。
- 不做跨树共享点数：一个角色可以有多棵树（按职业解锁），各自的点独立，`max_points` 各算各的。
- 不做节点动画/连线特效（owo 能做，但会拖首屏时间，M7 之后再说）。
