# R55 · 2026-11-18 23:4x-00:2x · 验收 T-0050 + `per_tick_cap` 落盘（M6 最后一个挂账收口）

## 1. 卡板

| 卡 | 结果 | 证据 |
|---|---|---|
| T-0050 `tools/check_mobs.py` | `[DONE]` → **`[VERIFIED]`** | 基线 `EXIT=0` 末行逐字 `共 4 个文件 · 0 条问题`；六类负向夹具各命中一条（7 文件 6 问题 `EXIT=1`）；`wc -l`=199、`grep -c "def clean_json5"`=0 |
| T-0051 `tools/check_items.py` | 新派（`[OPEN]`） | 物品 / 词条 / 词条池 / 套装四向离线对账；在制品 **1/3** |

**T-0050 的六类负向（S7 自编，不复用 S8 夹具）**

```
[问题] mobs/t_bad_base.json5 · base 非法：dragon
[问题] mobs/t_bad_base.json5 · 属性 spell_power:fire 不在 80 条表内
[问题] mobs/t_bad_base.json5 · skill 未命中：redtale_project:no_such_skill
[问题] mobs/t_bad_base.json5 · drops 未命中：no_such_table
[问题] mobs/t_dup.json5 · id ember_guard 与 mobs/ember_guard.json5 重复
[问题] spawn_rules/t_bad.json5 · mob 未命中：no_such_mob
共 7 个文件 · 6 条问题
```

边界四组：JSON5 截断 / 顶层数组（`顶层不是对象`）/ 缺 `spawn_rules/`（提示行，仍 `EXIT=0`）/ 三类全缺
（3 条提示 + `共 0 个文件 · 0 条问题`，`EXIT=0`）。

**裁定 S8 的提问**：`drops[].item` 带 `minecraft:` 命名空间 → **采纳实现（只验写法不验存在）**。依据 R41-b：
掉落表 `item` 两种合法形态（内容 id 裸名 / 真实物品 id 带命名空间），离线没有注册表，猜存在性会把 `minecraft:` 那批全判假红。

## 2. 自研：`per_tick_cap` 落盘（R48 硬编 8 → 配置面）

### 改了哪四处

| 文件 | 改动 |
|---|---|
| `mobs/SpawnSettings` | record 加 `perTickCap` 一字段；新增 `DEFAULT_PER_TICK_CAP = 8`；`parse` 解析 `per_tick_cap`（`1~4096` 整数，坏值回落 8 + 一条告警）；`describe()` 带上限 |
| `mobs/SpawnReplacer` | 删 `DRAIN_PER_TICK` 常量 → `volatile int perTickCap`；`applySettings` 同时设开关与上限；`tick` 用字段；`persist` **两字段一起写**；新增 `perTickCap()` / `setPerTickCap()`（夹 `[1,4096]`，变了留日志） |
| `core/command/SpawnCommand` | `replace` 状态行加 `· 每 tick 上限 N`；新子命令 `replace cap <n>`（`IntegerArgumentType.integer(1,4096)`）+ 落盘回显 |
| `defaults/spawn.json5` | 加 `per_tick_cap: 8`（默认件，`DEFAULT_FILES` 已含该文件，清单不变） |

### 实测（测试服 2 次启停 `Done (1.948s)` / `Done (2.099s)`，均已停、25567/25577 无 LISTEN）

| 组 | 命令 | 输出 / 结果 |
|---|---|---|
| A | `redtale spawn replace` | `[redtale] 刷怪替换：开 · 每 tick 上限 8 · 已命中规则 0 条 · 钩子 MobSpawnEvent.PositionCheck` |
| B | `redtale spawn replace cap 3` | `[redtale] 每 tick 上限已改为 3 · 已写入 spawn.json5`；磁盘 `per_tick_cap: 3` |
| C | `redtale spawn replace off` | 文件变 `replace_enabled: false` + **`per_tick_cap: 3` 仍在**（两字段一起写） |
| D | `redtale reload` | `37 份配置文件`；状态行仍 `每 tick 上限 3` |
| E | 手改 `per_tick_cap: "yes"` → `reload` | `1 条告警` + 状态行回落 **8**；日志 `配置告警：spawn.json5: per_tick_cap 不是数字，已改用 8` |
| F | **重启** | 状态行 `每 tick 上限 3` —— 第三条才是真断言（不落盘时 E 之外都能过，重启必红） |
| G | 回归 | `reload 37 份` / `spawn list` 1 条 / `tree list` 1 棵 10 节点 21 点 / `exp probe 7/7` / `validate 0 问题` |

收尾：`cap 8` 复原默认，`stop` 干净退出，redtale 零 ERROR。

## 3. 数字

- `gradle build --offline` `EXIT=0`，jar **635176 B**（633988 → +1188）
- `gradle selftest --offline` **102 PASS**（94 → 102，新增 `spawn()` 8 条）
- `python tools/check_mobs.py` `EXIT=0`（`共 4 个文件 · 0 条问题`）
- `python tools/check_defaults.py` `EXIT=0`（`清单 33 条 · 磁盘 33 个 · 问题 0 条`）
- 构建锁取删一次（`.queue` 空）

## 4. 口径（§13.58）

| # | 口径 | 理由 |
|---|---|---|
| R55-a | `per_tick_cap` **是防洪阀不是玩法旋钮**：合法 `1~4096`，`0` / 负数 / 小数 / 超界一律回落 8 + 告警。**`0` 不表示「关闭/不限」** | D16 的 `0 = 关闭` 套到这里会变成「每 tick 无限生成」，正是这道阀要防的事故 |
| R55-b | `persist()` **两字段一起写** | 只写一个会把另一个手改值静默抹掉（R52-a 同一条纪律：配置文件是唯一真相） |
| R55-c | 上限读写走 `SpawnReplacer` 的两个方法，**配置解析仍只在 L1 `SpawnSettings`** | 口径与 R52 一致：解析层不碰运行期状态，运行期状态只经 `applySettings` 进入 |
| R55-d | 行为上限（「一批 >N 只时只补 N 只」）**本轮未验** —— 无玩家时自然刷怪不跑，`fire` 每次一条命令落在不同 tick | 如实挂账，不拿「命令行能设」冒充「上限生效」 |

## 5. 未验 / 挂账

- `per_tick_cap` 的**行为**（同 tick 多命中时截断）—— 需真人自然刷怪或同 tick 多事件，与既有真人路径同批挂账。
- `respec` 扣费（Q-021 挂，默认免费）、技能树 / 升级真人在线路径、`#minecraft:undead` 真人验证。

## 6. 下轮

1. 验 T-0051（`tools/check_items.py`：基线 `共 12 个文件 · 0 条问题` + 六类负向 + `wc ≤ 200`）。
2. `tree info` 补「当前等级能拿多少点」一行（R54 下一步第 4 条，需玩家上下文，控制台分支保持不变）。
3. 真人一次性验收窗口（M6 刷怪替换 + M7 升级/技能树 + tooltip），需 Aki 上线。
