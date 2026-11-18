# R52 · 验收 T-0047 + `spawn.json5` 开关持久化（M6 收口）+ 派 T-0048

2026-11-18 20:10–20:3x。当前里程碑：**M7（技能树 L5 起步）**；M6 收口完成一项遗留。

## 1. 卡板动作

| 动作 | 卡 | 结果 |
|---|---|---|
| 验收 | T-0047 `/redtale tree list\|info` | `[DONE]` → **`[VERIFIED]`**（3/3 + 无树分支补测） |
| 派单 | T-0048 `core/progression/TreePlan` | `[OPEN]`，独占 `TreePlan.java`（新建 ≤150 行）+ `build/probe/TreePlanProbe.java` |
| 在制品 | — | **1/3**（T-0048） |

### T-0047 三条验收（逐条）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradle build --offline`（带 `.queue/build.lock`） | `BUILD SUCCESSFUL`，`RC=0` |
| 2 | jar 内 `TreeCommand` / `wc -l` / `grep -c TreePoints` | `1` / **80** ≤200 / **0** |
| 3 | 测试服 RCON（`Done (1.974s)` 起停一次） | 三组文案逐字对，见下 |

```
> redtale tree list
[redtale] 技能树 1 棵 · 节点 10 个 · 点数上限 21
  warrior_vanguard · 战士 · 先锋 · 职业 warrior · 节点 10 · 点数 21
> redtale tree info warrior_vanguard
[redtale] warrior_vanguard · 战士 · 先锋 · 上限 21 点 · 每级 1 点 · 节点 10
  职业：warrior · 来源 skill_trees/warrior_vanguard.json5
  节点 root · 起点 · cost 0 · pos 1,0 · 需 0 · 效果 0
  …（共 10 行，序 = 配置序：root / atk_spd_1 / atk_spd_2 / gale / heavy_1 / heavy_2 / quake / armor_1 / armor_2 / unyielding）
> redtale tree info nope
[redtale] 没有技能树 nope（现有 1 棵：warrior_vanguard）
```

### S8 请裁的那条（无树分支）

卡面「无树时第 1 行 `[redtale] 技能树 0 棵`」与通用首行模板「… · 节点 <n> 个 · 点数上限 <m>」在 `size=0` 时互斥。
补测：把 `skill_trees/warrior_vanguard.json5` 内容改成 `{}` → reload → `tree list` 输出严格两行
`[redtale] 技能树 0 棵` + `  （没有技能树定义：读 config/redtale_project/skill_trees）`；还原 → 回落 1 棵。
**裁定：采纳 S8 实现**（专门规则优先，有树才走通用模板），同 R43-a 口径 —— 语义判、不改代码。

## 2. 自研：`spawn.json5` 开关持久化（R48/R49 遗留）

| 文件 | 改动 |
|---|---|
| `mobs/SpawnSettings.java` | **新建**（纯解析 record，零 MC import）：`replace_enabled` 缺省 true，非布尔 → 回落 + WARN |
| `mobs/SpawnReplacer.java` | `+applySettings(SpawnSettings)`（走 `setEnabled`，变了才留日志）、`+persist()`（写配置根 `spawn.json5`） |
| `core/RedtaleCore.java` | reload 在 `spawnRules` 之后解析并 `applySettings`，附一行日志 |
| `core/command/SpawnCommand.java` | `toggle` 落盘，文案尾部 `· 已写入 spawn.json5` / `落盘失败（内存已生效，重启会回缺省）` |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` 加 `spawn.json5`（36 → **37 份**） |
| `resources/redtale/defaults/spawn.json5` | **新建**默认件 |

## 3. 实测（测试服 3 次启停，均已停、25567/25577 无 LISTEN）

| 组 | 步骤 | 观测 |
|---|---|---|
| A 默认件 | 首启 | `config/redtale_project/spawn.json5` 落地，`replace_enabled: true`；`spawn replace` → 「开」 |
| B 关闭落盘 | `spawn replace off` | 文案 `… · 已写入 spawn.json5`；磁盘 `replace_enabled: false`；状态「关」 |
| C reload 读回 | `redtale reload` | 仍「关」（不是被重置成缺省「开」）—— 37 份配置 |
| D **重启保持** | `stop` → 再起（`Done (1.956s)`） | 仍「关」——**本条才是真正要证的**（不落盘时 A/B/C 都过、D 必红） |
| E 坏值 | 手写 `replace_enabled: 'yes'` → reload | `1 条告警`，日志逐字 `spawn.json5: replace_enabled 不是布尔值，已改用 true`，状态回「开」 |
| F 还原 | `spawn replace on` | 磁盘 `true`；`tree list` / `level list` / `validate 0 问题` 零回归 |

环境：`build --offline` exit 0（jar **611877 B**）、redtale 零 ERROR（4 条 `RecipeManager` 既有噪声）。

## 4. 为什么这轮没做 `TreeService`

PLAN §13.54 下一步第 2 项是 `TreeService`，实际先做了第 3 项（开关持久化）。原因：`TreeService` 的属性下发
要以 `TreePlan` 的 **modifier id 规则**为唯一真相，而 `TreePlan` 刚派给 S8；先写 `TreeService` 会把 id 拼法抄成
第二份（洗点前缀移除依赖它，抄歪了症状是「属性残留」）。先它后我，等 T-0048 合流一次接线即可。

## 5. 下一步（R53）

1. 验收 T-0048（`TreePlan`，探针 ≥25 条覆盖 A~O 十五组；重点盯 id 规则：单效果裸 id / 双效果 `/0` `/1` / `passive` 混排不打乱下标）。
2. M7 L5 `TreeService`：`take`/`untake` → `TreePoints.canTake` → `ProgressStore.addNode` → `TreePlan.plan/declared` 下发与清场；验收读 `AttributeMap` 差值。
3. 洗点 `respec`（`clearTree` + 前缀批清，扣费挂 Q-021）。
4. 挂账：`per_tick_cap` 未落盘；技能树真人在线路径。
