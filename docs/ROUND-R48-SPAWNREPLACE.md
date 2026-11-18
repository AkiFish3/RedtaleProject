# R48 · M6 第三层：自然刷怪替换（`SpawnReplacer`）

- 时间：2026-11-18 15:24–15:45
- 卡板：T-0043 `[DONE]` → **VERIFIED**（3/3）；**派 T-0044**（`tools/check_enchants.py` 附魔配置起飞前对账）
- 产物：`mobs/SpawnReplacer.java`（新建）+ `core/command/SpawnCommand.java`（`replace` 四个子命令）+ `GameHooks` 2 处接线
- 数字：`build --offline` exit 0（jar **582343 B**）、`selftest` **78 PASS**、测试服起停 4 次全部干净（`Done 2.098 / 2.029 / 2.015s`），**已停服，25567/25577 无 LISTEN**

## 1. 做了什么

`MobSpawnEvent.PositionCheck` 上挂一层执行器：命中规则 → `setResult(FAIL)` 拦下原版那只 → **下一 tick** 在同一个坐标用
`MobSpawner.spawn` 生成自建怪。`action: add` 的规则只叠加（不设 `FAIL`，原版照生成 + 我们额外生成一只）。

```
NaturalSpawner.spawnCategoryForPosition
  └ EventHooks.checkSpawnPosition(mob, level, NATURAL)      ← 我们的监听点
        DEFAULT  → mob.checkSpawnRules + checkSpawnObstruction（原版口径）
        SUCCEED  → 直接放行
        FAIL     → 返回 false，这只不进世界
  └ 我们：FAIL + 入队
ServerTickEvent.Post → SpawnReplacer.tick(server) → 每 tick 最多 8 只 → MobSpawner.spawn
```

三件东西是本轮实现的核心：

| 物 | 作用 |
|---|---|
| `decide(...)` | **唯一判定点**：match → conditions → 可行性 → 每区块上限 → 掷骰。事件与命令探针共用，杜绝两份判定 |
| `explain(...)` | 决策树逐条文字化（`可达 / 条件不满足 / mob 未定义 / 每区块上限已满 / match 不符`），给 `/redtale spawn replace probe` 用 |
| `queue` + `tick()` | 事件里**不同步生成**（红线 1），改由 tick 尾排空 |

## 2. 观测面（新增命令）

```
/redtale spawn replace                       # 开关 + 已命中规则数 + 钩子名
/redtale spawn replace on|off
/redtale spawn replace probe <实体> <x> <y> <z>   # 只读决策树
/redtale spawn replace fire  <实体> <x> <y> <z>   # 造**真事件** POST 到总线
```

`fire` 是这一轮的关键工具：**无玩家在线时自然刷怪根本不跑**（R37-b / R47），
所以「监听器挂上了没有」只能靠 `NeoForge.EVENT_BUS.post(...)` 一个真事件来证明 —— 走的是同一根总线、同一批监听器、同一条决策链。

## 3. 实测（测试服，全部 RCON）

基线状态：`spawn replace` = `开 · 已命中规则 0 条 · 钩子 MobSpawnEvent.PositionCheck`。

| 组 | 输入 | 输出 | 结论 |
|---|---|---|---|
| A 探针 | `probe minecraft:zombie 106 99 106` | `t_r48 → ember_guard：可达 · 概率 1.00 · 区块现存 0`、`overworld_night_elite → 条件不满足` | 全序 + 条件分支 |
| B 替换 | `fire minecraft:zombie 106 99 106` | `结果 FAIL（拦下原版，下一 tick 补生成）` → 次 tick `@e[type=redtale_project:elite]` **count 1**、`Tags ["redtale_mob/ember_guard"]`、`Pos [106.5, 54.0, 106.5]` | 事件→队列→生成 全链通 |
| C 关闭 | `replace off` → `fire zombie` | `结果 DEFAULT（放行原版）`、无新实体 | 开关生效 |
| D 叠加 | `fire minecraft:cow`（规则 `action: add` → `ash_caster`） | `结果 DEFAULT` **且** 次 tick `@e[type=redtale_project:caster]` count 1、`Tags ["redtale_mob/ash_caster"]` | add 不拦、只加 |
| E 上限 | `probe minecraft:husk`（规则 `per_chunk_cap: 1`，区块内已有 1 只 ember_guard） | `每区块上限已满（1/1）` | §6「现存同 tag 实体数」成立 |
| F 悬空 | 规则写 `mob: ghost_mob` | `probe`：`mob 未定义`；`fire`：`结果 DEFAULT` | 红线 5：不可行就不拦 |
| G 负向 | `fire minecraft:not_a_mob` / `minecraft:arrow` | `未知实体 …（要注册表里的实体 id）` / `arrow 不是 Mob，走不了刷怪链路` | 不抛异常 |

日志：`刷怪替换首次命中：redtale_project:t_r48 → ember_guard（拦下原版 · zombie）`（每规则只一条）。

夹具全部删除后回落：`1 条刷怪规则 · 33 份配置`、`spawn check 问题 0 / 引用完整 ✓`、`validate 问题 0`、`mob check 引用完整 ✓`、
redtale **零 ERROR**（4 条 `RecipeManager` 是既有噪声）。

## 4. 本轮抓到的四个坑（都已修，都值得记）

1. **`BuiltInRegistries.ENTITY_TYPE.get(未知 id)` 返回 `pig`**，不返回 `null` —— 注册表默认值是 pig。
   第一版 `type == null` 判断让 `fire minecraft:not_a_mob` 打出了「事件已 POST：pig」。
   ⇒ 查注册表一律 `containsKey` 先判（`spawn check` 早就是这么写的，新命令忘了）。
2. `BlockPosArgument.getLoadedBlockPos` **声明** `CommandSyntaxException`，`getBlockPos` **不声明** —— 在 srg 编译面上
   try/catch 前者编不过、catch 后者「异常永不抛出」也编不过。用 `getBlockPos`。
3. `StringArgumentType.string()` 吃引号不吃冒号（R40 老坑第三次复发）→ 实体 id 参数改 `ResourceLocationArgument.id()`，
   RCON 侧裸写 `minecraft:zombie` 即可，裸 `zombie` 也会补命名空间。
4. srg 编译面 **没有 `Entity#entityTags()`** → 用 `getTags()`（scoreboard tag，与 `MobSpawner.TAG_PREFIX` 同源）。

## 5. 口径（写进 PLAN §13.51）

- **R48-a** 事件里只「决策 + 入队 + `setResult`」，生成一律走 `ServerTickEvent.Post` 排空（每 tick ≤8），**不在事件里生成**。
- **R48-b** `decide()` 是唯一判定点，命令探针与事件共用；`explain()` 只读、不改状态。判定顺序固定：match → conditions → 可行性 → 上限 → 掷骰。
- **R48-c** 可行性（`mobs.get(mob) == null`）**必须在 `setResult(FAIL)` 之前**判；不可行 → 不拦（原版照生成）。
  代价明写：`FAIL` 之后若 `MobSpawner.spawn` 仍失败（世界拒绝），这一只就**少了**，只留一条 `首次失败` WARN。
- **R48-d** `action` 语义：`replace` = 拦 + 生成；`add` = 不拦 + 额外生成。`add` 是「加量」，不是「替换成两只」。
- **R48-e** 无玩家在线 → 自然刷怪不跑 ⇒ 验收一律用 `spawn replace fire` 造真事件；**真人自然刷怪替换仍挂账**（需 Aki 上线）。
- **R48-f** 生成坐标沿用事件坐标（未做落地校正）；在高空 `fire` 会掉到地面并吃摔落伤害（实测 `Health 127/128`、`Pos y=54`），
  验「有没有生成」看 tag 与 type，**不要看血量绝对值**。
