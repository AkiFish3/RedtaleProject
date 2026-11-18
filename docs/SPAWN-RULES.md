# 原版刷怪替换（D26）：`spawn_rules/*.json5` schema v1

M6 最后一块（R46 定稿）。目标：**在自然刷怪这一步**把原版怪按概率换成自建怪，不改群系候选表、不接管刷怪笼。

> 钩子不是「可选项」，是 R42 用字节码核过的**唯一解**。照 D26 原文（`FinalizeSpawnEvent`）开工的结果是「自然刷怪完全没被替换 + 零报错」，见 §2。

## 1. 范围

做：

- 自然刷怪（`NaturalSpawner`）按 `match` 命中 → 按 `chance` 掷 → `action: replace` 换成自建怪
- `conditions` 六个维度（world / biome / light / time / y / outside / difficulty）
- `priority` 多规则命中优先级、`per_chunk_cap` 每区块上限

不做（明确，不是漏项）：

- 不接管刷怪笼（`BaseSpawner`）与试炼笼（`TrialSpawner`）—— 它们的 `finalizeSpawn` 路径与本钩子无关
- 不改结构生成 / 袭击 / 刷怪蛋 / `/summon`
- 不做「区块重生」或「清场」类行为；本层只决定「这一次生成是什么」

## 2. 钩子事实（R42 字节码核验，别推翻）

| 事实 | 证据 | 结论 |
|---|---|---|
| `MobSpawnEvent.FinalizeSpawn` 在 21.1.257 **只由** `BaseSpawner` / `TrialSpawner` 触发 | 补丁后 `Mob.finalizeSpawn` 只 `putfield spawnType`，不调 `EventHooks.finalizeMobSpawn`；`grep -rl FinalizeSpawnEvent` 全解包目录 | 自然刷怪**不会**走到它 |
| `NaturalSpawner` 的 `EventHooks` 调用只有 `checkSpawnPosition`×2 / `getMaxSpawnClusterSize` / `getPotentialSpawns` | `javap -c` 反编译 | 能用的只有 `MobSpawnEvent.PositionCheck` |
| `PositionCheck` 拿得到 `getEntity()` / `getLevel()` / `getX/Y/Z` / `getSpawnType()` | 事件 API | `FAIL` 即拦下这一次生成 |

因此 v1 的唯一路径：

```
NaturalSpawner → MobSpawnEvent.PositionCheck
   ├─ 命中规则 + 掷中 ─→ event.setResult(FAIL)  → 同坐标 MobSpawner.spawn(自建怪)  → action: replace
   │                    └─ 掷不中 / 无规则 ────────────────────────────────────→ DEFAULT（原版照生成）
   └─ action: add ─→ 不 FAIL，原版照生成，另在同坐标 spawn 一只自建怪
```

`getPotentialSpawns` **不进 v1**：它只能改群系候选表，表达不了 `match/conditions` 这一整套；两套语义同时开会让「为什么这只没被换」无法归因（R42-d）。

## 3. 文件与最小例

一个文件一条规则，放 `config/redtale_project/spawn_rules/*.json5`（`DEFAULT_FILES` 白名单要加目录）。文件名随意，`id` 才是身份。

```json5
{
  id: "redtale_project:overworld_night_elite",
  name: "夜间精英替换",
  match: ["minecraft:zombie", "minecraft:husk"],   // 被替换的原版实体 id；["*"] = 全部
  action: "replace",                                // replace | add
  mob: "ember_guard",                               // 自建怪 id（MobSpec 的 id，不补命名空间）
  chance: 0.25,
  priority: 100,
  per_chunk_cap: 1,                                 // 0 = 不限
  conditions: {
    world: ["minecraft:overworld"],
    biome: ["#minecraft:is_forest", "minecraft:plains"],
    light: { max: 7 },
    time: "night",
    y: { min: 0, max: 128 },
    outside: true,
    difficulty: ["normal", "hard"],
  },
}
```

## 4. 字段表

| 键 | 类型 | 缺省 | 语义 / 夹取 |
|---|---|---|---|
| `id` | string | **必填** | 必须含 `:`，缺失/非字符串 → 整条跳过 + 1 条 WARN |
| `name` | string | = `id` | 只给 `/redtale spawn list` 显示 |
| `match` | string[] | **必填** | 原版实体 id；`["*"]` = 全部；空数组 → 整条跳过 + WARN |
| `action` | string | `replace` | 只认 `replace` / `add`；其它值 → WARN + 按 `replace` |
| `mob` | string | **必填** | 自建怪 id；解析层不校验存在性（交叉校验归 `/redtale spawn check`） |
| `chance` | number | 1.0 | 夹 `[0,1]`，**不告警**（夹取不是错误，R36 的教训）；`0` = 永不生效 |
| `priority` | int | 0 | 越大越先；同 priority 按 `id` 升序（**排序必须稳定**，否则同配置两跑结果不同） |
| `per_chunk_cap` | int | 0 | `0` = 不限；`>0` 时看「该区块现存同 `mob` 的实体数」是否已达上限（§6） |
| `conditions` | object | `{}` | 空 = 全通过；见 §5 |

## 5. `conditions` 六个维度

| 键 | 形态 | 语义 | 缺省 |
|---|---|---|---|
| `world` | string[] | 维度 id 白名单 | 空 = 不限 |
| `biome` | string[] | 群系 id 或 `#tag` | 空 = 不限 |
| `light` | `{min?, max?}` | 亮度区间（0~15，含端点） | 不限 |
| `time` | string | `day` / `night` / `any` | `any` |
| `y` | `{min?, max?}` | 生成点 y 区间（含端点） | 不限 |
| `outside` | bool | 是否要求露天 | 不限 |
| `difficulty` | string[] | `peaceful`/`easy`/`normal`/`hard` | 空 = 不限 |

判定口径：

- **全部条件 AND**，未写的维度跳过（不是「等于缺省值」而是「不参与判定」）
- 单个维度内部是 OR（`world: [a, b]` = a 或 b）
- 条件求值失败（维度取不到 / 群系未注册）→ 该维度视为**不通过**（宁可少替换，不可错替换），并**不告警刷屏**（同一条规则每 tick 都可能被问一次）

## 6. `per_chunk_cap` 不维护计数器

用「**该区块现存同 `mob` 的实体数**」近似，`MobSpawner.TAG_PREFIX` 的 scoreboard tag 就是现成的身份（`redtale_mob/<mob>`）：

```
count = chunk.getEntities(area, e -> e.entityTags().contains("redtale_mob/" + mob)).size()
if (cap > 0 && count >= cap) → 跳过这条规则
```

理由：维护「每区块已替换次数」需要持久化（重启清零会让刷怪在重启瞬间暴涨），而现存实体数天然自愈、无需存档字段、且与玩法直觉一致（「这块地方已经有 1 只精英了」）。代价：玩家手动 `/summon` 的同 tag 实体也计入 —— 可接受。

## 7. 三层分层（照 M5/M6/M7 的既有做法）

| 层 | 类 | 职责 | 能否离线验 |
|---|---|---|---|
| L1 解析 | `mobs/SpawnRuleSpec` | JSON5 → `Definition` 记录，夹取 + 告警，**零 `net.minecraft` import** | ✅ 探针 |
| L2 快照 | `mobs/SpawnRegistry` | 不可变快照 + 稳定排序 + 告警随快照换代 | ✅ 探针 |
| L3 执行 | `mobs/SpawnReplacer` | 挂 `MobSpawnEvent.PositionCheck`，查快照 → 掷 → `MobSpawner.spawn` | 测试服 |

**先 L1 落地才许派 L2**（R36-a 同源：没有快照层，命令自己再解析一遍 = 第二份真相）；L3 由 S7 独占（事件挂钩 + 跨模块）。

## 8. 红线

1. **不许在 `PositionCheck` 里同步生成实体**：事件在 `NaturalSpawner` 的循环里，生成要放到事件返回之后（`ServerTickEvent` 或 `event.getLevel().getServer().execute(...)`），否则会撞 `ConcurrentModificationException`（同 tick 加实体进世界）。
2. **`FAIL` 之后必须自己生成**，否则「替换」变成「减少刷怪」——静默改变刷怪量是最难发现的一类回归。
3. **不碰 `Spawner` 方块**：刷怪笼/试炼笼走 `FinalizeSpawn`，与本层无交集，别顺手挂上去。
4. **不改群系候选表**：`getPotentialSpawns` v1 不做（§2）。
5. 替换失败（`MobSpawner.spawn` 返回 `ok=false`）→ 必须回落成「原版照生成」（再 `setResult(DEFAULT)` 已经晚了 ⇒ 因此**先判定可行性再 FAIL**：`mob` 查不到 / 原型 `base` 不合法 → 直接不拦）。

## 9. 告警口径

- 解析层：`id`/`match`/`mob` 缺失 → 整条跳过；`action`/`chance`/`priority` 坏值 → 夹取或回落，**只在「语义会变」时告警**（`action` 坏值告警，`chance: 3` 夹成 1 不告警）
- 快照层：`mob` 在 `MobRegistry` 里查不到 → 1 条 WARN + 该规则**保留**（内容后补即可，不要求重启）
- 执行层：同一条规则每 tick 被问一次，**不得逐次打日志**；只在「首次命中 / 首次失败」各一次

## 10. 交付顺序

| 步 | 内容 | 归谁 |
|---|---|---|
| 1 | 本文件（schema v1 定稿） | S7 ✅ R46 |
| 2 | `SpawnRuleSpec` L1 解析器（牌见 `TASKS.md` T-0042） | S8 |
| 3 | `SpawnRegistry` L2 快照 + `RedtaleCore.spawnRules()` + `DEFAULT_FILES` + 默认一份 `spawn_rules/*.json5` | S7 |
| 4 | `/redtale spawn list|check` 只读观测面 | S8 |
| 5 | `SpawnReplacer` L3 挂 `PositionCheck` + 同 tick 延迟生成 | S7 |
| 6 | 真人验收（需玩家在线才会自然刷怪，R37-b） | S7 + Aki |

## 11. v1 可机器验的部分

- `grep -c "^| " docs/SPAWN-RULES.md`：字段表与条件表都在（文档自检）
- 解析器探针：坏 `id` / 空 `match` / `chance: 3` 夹成 1 且不告警 / `action: "x"` 告警并回落 `replace` / `priority` 同值稳定排序
- 快照：`mob` 查不到 → 1 条 WARN 且规则仍在
- 执行层只有测试服 —— 且**必须真人在线**才刷怪，「事件注册成功 + 开关日志」不等于验收通过（R37-b）
