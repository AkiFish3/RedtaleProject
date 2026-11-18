# R53 · 验收 T-0048 + M7 L5 `TreeService`（技能树第一次真正影响面板）

> 2026-11-18 21:20-21:5x · S7 移植工程队列 · 目标：把技能树从「只读观测面」推进到「能点、能退、能洗，且属性面板真的变」。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0048 `core/progression/TreePlan` | 验收 | **VERIFIED**（3/3 + S7 独立探针 **48/48**） |
| T-0049 `tools/check_trees.py` | 派单 | `[OPEN]`，在制品 **1/3** |

### T-0048 三条验收（逐条）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `gradle build --offline`（取锁） | `RC=0` | `RC=0`（UP-TO-DATE） |
| 2 | jar 内类 / 行数 / 禁词 | `TreePlan`≥1、`wc`≤150、`net.minecraft`=0、`TreePoints`=0 | **2**（主类+`$Modifier`）、**62**、**0**、**0** |
| 3 | S8 探针 `TreePlanProbe` | `PASS n / FAIL 0`，≥25 条 | **41/41** |

S7 独立探针 `build/probe/S7TreePlanProbe.java` **48/48**（A~P 十六组，与 S8 不重叠）：
`pathOf` 双冒号 / `operationOf` 五种回落 / 三属性效果 `/0 /1 /2` + `passive` 不位移 /
`effects=null` 不炸 / 负数与 `1e9` 不钳制（D16）/ 两次调用不同对象且逐元素相同 / `declared` 与 `plan` 同 id 规则。

> 首版两条断言是我写错的（E2 把顺序期望成 `selected` 序、E3 属性名抄成 `attack_speed`）→ 期望错、代码对，
> 同 R43-a / R49-c 口径，不打回卡。

## 2. 自研：M7 L5 `TreeService`

新类 `src/main/java/com/redtale/project/core/progression/TreeService.java`：

- `take` / `untake` / `respec` → 判定只信 `TreePoints`（L3 已 VERIFIED），落 `ProgressStore`，再 `applyTree`
- `applyTree` = **前缀清场** `redtale_project:tree/<path>/` → 按已点集合 `TreePlan.plan` 重加
- `applyAll` / `removeAll`（登录 / reload / 探针清场）
- `resolve` 裸名与全名都认（先精确、再 `pathOf` 兜底）；存档键用 `def.id()`
- `Status` 九值：`OK / UNKNOWN_TREE / UNKNOWN_NODE / ALREADY_TAKEN / MISSING_PREREQ / NO_POINTS / NOT_TAKEN / HAS_DEPENDENTS / NO_PLAYER`

命令面（`TreeCommand` 从观测面扩成观测+写面，`list|info` 逐字未动）：

```
/redtale tree state <tree>            玩家：点数/已用/剩余/已点；控制台：只看上限
/redtale tree take <tree> <node>
/redtale tree untake <tree> <node>
/redtale tree respec <tree>
/redtale tree probe                   FakePlayer，13 条断言
```

接线：`ProgressService.onLogin` 与 `applyAll` 各补一行 `TreeService.applyAll` —— 等级属性与技能树属性**同一次重算**。

## 3. 实测（测试服 2 次启停，均已停）

`Done (1.987s)` / `Done (2.018s)`；25567 / 25577 均无 LISTEN。

`/redtale tree probe` **13/13**：

```
基线 攻速 4.0 / 护甲 0.0 / 物伤 0.0
A 点 root            → OK，攻速不动
B 点 atk_spd_1       → modifier 1 条，攻速 4.2（4.0 × 1.05）
C 重复点 atk_spd_1   → ALREADY_TAKEN，攻速不动
D 退 root            → HAS_DEPENDENTS · atk_spd_1
E 点 heavy_2         → MISSING_PREREQ · heavy_1
F 点 armor_1         → 护甲 2.0
G 点 heavy_1         → 物伤 0.08（自建裸 path 复测）
H 退 atk_spd_1       → 攻速回 4.0
I 退没点的 atk_spd_2 → NOT_TAKEN
J 洗点               → 退回 3 个，三件属性全回基值，前缀残留 0
K 树/节点不存在      → UNKNOWN_TREE / UNKNOWN_NODE
L 判定层口径         → root 计入 taken
M 幽灵 modifier      → tree/warrior_vanguard/ghost 被前缀清场，移速回 0.10000000149011612
```

M 组是本轮最值钱的一条：手工挂一个**配置里已经不存在**的节点 id，证明清场走的是前缀扫描而不是
「按当前声明清单逐条清」。后者在「改配置换了属性键」时会把旧 modifier 永久留在旧属性上
（`SKILL-TREE.md` §5 第 4 条要防的正是这个）。

回归：`tree list` / `tree info warrior_vanguard` 逐字同 R52 基线；`tree state` 控制台分支；
`take`/`respec` 控制台报「只能由玩家执行」；`validate 0 问题`；`exp probe 7/7`；`level list` 不变；`reload 37 份`。

## 4. 数字

| 项 | 值 |
|---|---|
| `gradle build --offline` | exit 0，jar **632690 B** |
| `gradle selftest --offline` | **78 PASS** |
| `offline_check.py` | `sources: 132 exit: 0` |
| 构建锁 | 取删 3 次，`.queue` 已空 |
| 测试服 | 起停 2 次，已停 |
| 新增/改动文件 | `TreeService.java`（新）、`TreeCommand.java`（扩）、`ProgressService.java`（2 处各 +1 行） |

## 5. 口径（详见 `PLAN.md` §13.56、`CONTRACTS.md` §4.33）

- **R53-a** 存档键用 `def.id()`，命令两种写法不产生两个桶
- **R53-b** 清场走前缀扫描，不按当前声明清单
- **R53-c** `untake` 有依赖者即拒（带名单），**不级联**；批量清走 `respec`
- **R53-d** 点数上限暂取 `def.maxPoints()`；等级换点数是 M7 剩余项（取舍题，待定）
- **R53-e** `grep TreePoints = 0` 只对 T-0047 那轮有效；合流后作废（R52-c 的顺序已遵守）

## 6. 下一轮

1. 验收 T-0049（`tools/check_trees.py`）
2. **等级 → 技能点**（`points_per_level`）—— M7 出口条件「升级加属性点」的最后一块
3. `respec` 扣费（Q-021 仍挂，默认免费）
4. 技能树真人在线路径（`src.getPlayer()` 分支至今只有 FakePlayer 走过）
