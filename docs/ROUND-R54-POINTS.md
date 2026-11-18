# R54 · 等级 → 技能点（M7 出口）+ 验收 T-0049

日期：2026-11-18 22:32–22:5x · 里程碑：**M7（进度 / 技能树）出口条件达成**

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0049 `tools/check_trees.py` | `[DONE]` → **`[VERIFIED]`** | 3/3 全绿（见 §2） |
| T-0050 `tools/check_mobs.py` | **新派** | 在制品 1/3 |

## 2. T-0049 验收（三条 + 补测）

| # | 验收 | 结果 |
|---|---|---|
| 1 | `python tools/check_trees.py` | EXIT=0，末行逐字 `共 1 个文件 · 0 条问题` |
| 2 | 五类负向夹具 | S7 自编（与 S8 不同件）→ 6 条问题 / EXIT=1，五类全命中：`requires` 悬空、`spell_power:fire` 不在 80 表、`classes:["nobody"]`、`max_points 1 < Σcost 4`、两文件同 id（报在**后出现**文件 + 含先出现路径，与卡面规则 9 一致） |
| 3 | `wc -l`=200 ≤200；`grep -c "def clean_json5"`=0 | 通过（确为 `from check_progress import clean_json5, read_attrs, normalize`） |

补测四类边界（S8 未覆盖）：`cost: true`（非整数）、`unlock_skill` 未命中、`passive` 缺 id、`type: weird` —— 逐条命中；`max_points` 缺失只「提示」不算问题；删掉 `jobs/`+`skills/` 只打两行提示、退出码仍 0。

**判语**：离线对账与运行期 L2 校验同源（属性键走同一份 80 条表），无口径分叉。

## 3. 自研：等级换点数

- `TreePoints.capFor(maxPoints, pointsPerLevel, level)` = `min(max_points, 每级点数 × 等级)`；`points_per_level <= 0` = 关门槛（D16 风格）。
- 新重载 `state(def, selected, level)` / `canTake(def, selected, nodeId, level)`；**旧两参 / 三参入口语义一行未改**（仍按 `max_points`，探针与离线断言靠它）。
- `TreeService.levelOf(ServerPlayer)` + `state`/`take` 走等级派生。
- `TreeCommand.state` 玩家行尾追加 `· 等级 L（P 点/级，上限 M）`；控制台分支逐字未动。
- `tree probe`：开跑前把 FakePlayer 主等级顶到 `max_points` 之上（`1e9` 经验），新增 **N 组**（等级 1 → 上限 1 → cost1 `OK` / cost2 `NO_POINTS`）；收尾 `store.clear` 零残留。

## 4. 实测

- 测试服 2 次启停：`Done (2.022s)` / `Done (2.261s)`，均已停，25567 / 25577 无 LISTEN。
- `tree probe` **14/14**，连跑两次结果逐字一致（幂等 + 清场有效）。
- 回归：`tree list` 逐字同 R52、`tree state` 控制台分支、`exp probe 7/7`、`validate 0 问题`、`reload 37 份`（10 件内容 / 4 技能 / 2 怪 / 1 掉落 / 1 刷怪 / 3 等级 / 1 树）。
- redtale 零 ERROR、零 WARN；4 条 RecipeManager 解析失败是 R7 起的既有噪声。
- `build --offline` exit 0（jar 633988 B）、`selftest` **94 PASS**（78 → 94，新增 `tree()` 16 条）。

## 5. 口径

见 `PLAN.md` §13.57（R54-a~e）与 `docs/CONTRACTS.md` §4.34。最关键一条：**等级回档后 `available` 会变负，但不自动退已点节点** —— 静默扣点比负剩余更糟。

## 6. 未验证 / 挂账

- 真人玩家的 `tree state` / `take` / 升级路径（探针全走 FakePlayer），与 R45 / R48 / R49 同批挂账。
- `respec` 扣费（Q-021 默认免费，等 D28 经济桥接）。
- `tree info` 缺「当前等级能拿多少点」一行（观测面缺口，R55 候选）。
