# R42 · 验收 T-0037 + M7 契约定稿 + D26 钩子核验

2026-11-18 08:00–08:40 · 里程碑：M6 收口在望 / **M7 开工（L1 已派单）**

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0037 `/redtale drop list` | 验收 | **VERIFIED（3/3）** |
| T-0038 `core/progression/ExpCurve` | **派单** | OPEN（在制品 1/3） |

**T-0037 证据**

- `gradle build --offline` → EXIT=0，jar 502715 B；`unzip -l` 内 `core/command/DropListCommand` = 1。
- `wc -l` = 69（≤160）；`grep -c DropRoll` = 0；`node()` / `list()` 签名各 1。
- `git diff RedtaleCommand.java`：只 +1 行 `.then(DropListCommand.node())`，零删改。
- 测试服 RCON 原文：
  ```
  [redtale] 掉落表 1 张 · 条目合计 3 条 · 被引用 1 张
    ember_guard · xp 12 · 条目 3 · 引用 1 只：炽焰守卫
  ```
- 回归：`validate` → 10 件内容 / 1 池 / 3 词条 / 1 套装 / 技能 3 条 / 问题 0；`mob check` → 2 只 / 技能引用 3 条 / 掉落表 1 张 · 引用完整 ✓。
- 测试服已停（25567/25577 无 LISTEN），构建锁已删。

## 2. M7 契约定稿（本轮主体）

新 `docs/PROGRESSION.md`（10 节），要点：

1. **双累加器**：主等级 + 每职业各存 `total_exp`，同一份经验按配置分别加；不做互换算。
2. **曲线数学写死**：`geometric: round(base × growth^(L-1))`、`polynomial: round(base × L^exponent)`；单步硬顶 `1e9`；`L >= maxLevel` → 0。
3. **等级是派生值**：只存 `total_exp`，等级 = `levelFor(total)` 现算 → 改曲线后重算即自洽（K4 可机器验）。
4. **经验源 v1 三条**：`kill`（吃 M6 掉落表的 `xp`）/ `command` / `primitive`（脚本原语 `grant_exp`）。
5. **属性下发**：`level/<path>`、`job/<job>/<path>`，`ADD_VALUE`，先移除再加（自建属性基值 0，R5 教训）。
6. **存档走 `SavedData`**（`world/data/redtale_project_progress.dat`），不落 `playerdata`。
7. **L1~L5 交付顺序** + K1~K6 出口条件。

## 3. D26 钩子核验（推翻 D26 原文的候选 A）

`javap` 读 `neoforge-21.1.257-universal.jar` / `neoforge-21.1.257-server.jar`：

| 结论 | 证据 |
|---|---|
| `FinalizeSpawnEvent` 存在且 `ICancellableEvent`，带 `getSpawnType()` / `getSpawner()` | `javap -p` 类签名 |
| 但**只有 `BaseSpawner` / `TrialSpawner` 引用它**（全 jar grep 仅 2 个类） | `grep -rl FinalizeSpawnEvent` 全解包目录 |
| 补丁后的 `Mob.finalizeSpawn` 只 `putfield spawnType`，**不调** `EventHooks.finalizeMobSpawn` | `javap -c` 方法体 |
| `NaturalSpawner` 的 `EventHooks` 调用只有 `checkSpawnPosition`×2 / `getMaxSpawnClusterSize` / `getPotentialSpawns` | `javap -c NaturalSpawner \| grep EventHooks\.` |

⇒ 自然刷怪替换只能走 `MobSpawnEvent.PositionCheck`（`SUCCEED`/`DEFAULT`/`FAIL`）+ 同坐标 `MobSpawner.spawn`；`FinalizeSpawnEvent` 只覆盖刷怪笼/试炼笼。

## 4. 数字

- `build --offline` exit 0（jar 502715 B，本轮零代码改动）
- `selftest --offline` **78 PASS**
- 测试服起停 1 次（`Done` 后 RCON 三轮），已停
- redtale 零 ERROR；4 条 `RecipeManager` 是 R7 起的既有噪声

## 5. 下一步（R43）

1. 验 T-0038（`ExpCurve` 探针 ≥24 条，含 9 条定点值与全量对拍）。
2. M7 L2：`ProgressSpec` + `ProgressRegistry` + `RedtaleCore.levels()/jobs()` + `progression.json5` 进 `DEFAULT_FILES`。
3. D26 技术验证：`PositionCheck` 只读探针（真人挂账）。
