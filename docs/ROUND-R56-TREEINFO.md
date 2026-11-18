# R56 · 验收 T-0051（`check_items`）+ `tree info` 补「当前等级能拿多少点」+ 派 T-0052

2026-11-19 00:50-01:1x · S7

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0051 `tools/check_items.py` | 派单 → 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + 边界 20 条） |
| T-0052 `core/progression/TreeUnlocks` | **新派** | `[OPEN]`，在制品 **1/3** |

### T-0051 验收明细（3/3）

1. `python tools/check_items.py` → **EXIT=0**，末行逐字 `共 12 个文件 · 0 条问题`。
2. 六类负向夹具（`build/tmp_items` 副本）各命中一条，**18 文件 6 问题 EXIT=1**：

```
[问题] items/zz_bad_base.json5 · base 非法：dragon
[问题] items/zz_bad_set.json5 · set_id 未命中：redtale_project:no_such_set
[问题] items/zz_bad_stat.json5 · 属性 spell_power:fire 不在 80 条表内
[问题] items/zz_dup_longsword.json5 · id redtale_project:demo_longsword 与 items/demo_longsword.json5 重复
[问题] affix_pools/zz_bad_pool.json5 · prefix 未命中词条：no_such_affix
[问题] sets/zz_bad_set.json5 · pieces 未命中：no_such_item
```

3. `wc -l` = **178** ≤ 200；`grep -c "def clean_json5"` = **0**。

**S7 独立边界补测（20 条，`build/tmp_items2`）**：JSON5 截断 / 顶层数组 / 缺 `sets/` 目录（打 `提示 没有 sets/ 目录，跳过` 并跳过该类）/ `rarity: legendary` / `gem.tier: 0` / `sockets.count: 7` / `types` 含数字 / `weapon_attributes` 裸名 / `rev: 0` / `abilities` 悬空 / `kind: aura` / `weight: 0` / `item_level` min>max / `applies_to` 错命名空间（`minecraft:sword`）与未知基底（`redtale_project:dragon`）/ `slots` 负值与未知键 / 词条池权重 0 —— 全部按卡面语义报出，无假阳性、无漏报。夹具已删。

**S8 提问裁定**：`ATTR_IDS` 模块级注入 **采纳**。卡面把 `check_item/check_affix/check_pool/check_set` 的签名写成不带 `ids`，却又要求 `stats` 键归一到 80 表 —— 是**卡面自相矛盾**（同 R43-a 口径），S8 用模块级注入绕开是对的，不打回。

一条口径观察（不判缺陷）：删除 `sets/` 目录后，三件带 `set_id` 的物品各报一条「未命中」。这是**正确**行为——集合真的不存在，`提示…跳过` 只是不校验「套装自身字段」，不该顺手把引用校验也关掉。

## 2. 自研：`tree info` 的「当前等级能拿多少点」

`TreeCommand.info` 在「职业… · 来源 …」之后追加一段，**仅玩家上下文**：

```
/redtale tree info warrior_vanguard        （控制台，11 行，逐字不变）
[redtale] warrior_vanguard · 战士 · 先锋 · 上限 21 点 · 每级 1 点 · 节点 10
  职业：warrior · 来源 skill_trees/warrior_vanguard.json5
  节点 root · 起点 · cost 0 · pos 1,0 · 需 0 · 效果 0
  …（共 10 行节点）
```

新增纯函数：

```java
public static String TreeCommand.levelLine(SkillTreeSpec.Definition def, int level)
// → "\n  当前等级 " + level + " · 可拿 " + TreePoints.capFor(maxPoints, pointsPerLevel, level) + " 点"
```

上限一律走 `TreePoints.capFor`（R54-a 的唯一派生点），命令层不重算 `points_per_level × level` —— 否则又长出一个「第二个真相」。

## 3. 实测（测试服 1 次启停，`Done (2.068s)`，已停、25567/25577 无 LISTEN）

| 项 | 结果 |
|---|---|
| `redtale tree info warrior_vanguard` | 11 行逐字同 R52 基线，**无**「当前等级」行（控制台分支零回归） |
| `redtale tree list` | `1 棵 · 节点 10 个 · 点数上限 21`，逐字同 R54 |
| `redtale tree probe` | **14/14**（A~N 全 ✓，含 M 组幽灵 modifier 清场、N 组等级 1 → 上限 1） |
| `redtale validate` | `问题 0 条（外部 weapon_attributes 引用 1 条未校验）` |
| `redtale reload` | `37 份配置文件`，`2 条脚本 + 10 件装备 + 4 条技能 + 2 只怪物 + 1 张掉落表 + 1 条刷怪规则 + 3 条等级定义 + 1 棵技能树` |
| redtale ERROR | **0**（日志里 4 条 `RecipeManager` 噪声来自 simplyswords / brewinandchewin，与本 mod 无关） |

离线：`gradle build --offline` exit 0（jar **635391 B**）、`selftest` **102 PASS**（不变，`TreeCommand` 不在 selftest 源码集）、探针 `S7InfoLineProbe` **8/8**。

`S7InfoLineProbe` 覆盖：L5 未触顶 / L30 触顶 21 / L21 恰等于上限 / `points_per_level=0` 关门槛（L1 与 L99 都是 21）/ `max_points=0` 不限（L7 → 7）/ L0 与负数等级 → 0 点。

## 4. 未验（挂账，不冒充）

- `levelLine` 的**玩家分支胶水**（`src.getPlayer()` 非 null 时进 `info`）—— 与本项目其它真人路径同批（M6 刷怪替换、`per_tick_cap` 行为、`tree take/untake`、tooltip、undead 乘区），等 Aki 上线一次性收。
- `tree info` 在玩家上下文里的**真实排版**（RCON 无玩家）——同上。

## 5. 下一步（R57）

1. 验收 T-0052（`TreeUnlocks`：五个签名、定义序、去重保首次、`describe` 文案、零 MC 依赖）。
2. **`unlock_skill` 接进服务层**：`TreeService.applyTree` 之后按 `TreeUnlocks.unlocked` 下发/回收玩家技能解锁集（M7 出口「点满一条分支并解锁技能」）。
3. `tree skills <tree>` 观测面（列出已解锁技能）—— 依赖第 2 条。
4. 真人一次性验收窗口（同上挂账）。
5. `respec` 扣费仍挂 Q-021（默认免费），等 D28 经济桥接。
