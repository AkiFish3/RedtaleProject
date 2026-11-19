# 专业（D24）：schema、经验源、接合点

> 建立 2026-11-19 10:2x（R64）。**先有这份 spec 才派卡**（PLAN §13 R63 下一步第 4 条：别让 S8 现发明 schema）。
> 上游对照：`docs/GAP-AUDIT.md` A5（MMOCore `default/professions/{mining,smelting,woodcutting}.yml`：`exp-curve` / `on-mine.{block}.drop-table` / `options.vanilla-drops` / `regen.time`）。
> 相邻文档：`docs/PROGRESSION.md`（主等级 / 职业 / 曲线 / 存档）、`docs/ATTRIBUTE-REGISTRY.md`（80 条表）、`docs/CONTRACTS.md`（冻结接口）。

## 1. 边界：专业是什么，不是什么

- **是**：第三个**经验累加器族**。玩家干采集 / 加工类动作 → 该专业累计经验 → 按曲线出等级 → 按等级下发属性。与主等级、职业**互不干扰、各自独立存档桶**。
- **是**：内容形态与 `jobs/*.json5` **同构**（同一套 `id` / `max_level` / `curve` / `attributes`），只多一张 `grants`（哪个动作给多少经验）。**不为专业发明第二套 schema**。
- **不是**：不是「职业的别名」。职业（`jobs/`）吃战斗经验、决定技能树归属；专业吃采集加工、不下发技能、不参与技能树 `classes`。
- **不是**：不做方块再生（D27 已否「自定义矿脉」）、不做专业专属 GUI、不做专业之间的互斥或转职。
- **不是**：v1 不做 `drop-table`（额外掉落）。上游 `on-mine.drop-table` 属于「改掉落」，我们的掉落已经走 `drops/` + `MobDrops`；专业 v1 只发经验，不改掉落。真要接，另开 spec 段 + 新卡，不在本轮偷做。

## 2. 三族累加器的一张表

| 族 | 目录 | 经验源 | 存档键 | 属性 id 前缀 | 谁决定等级 |
|---|---|---|---|---|---|
| 主等级 | `levels/main.json5` | kill / command / primitive | `main` | `level/` | 全服共用一条曲线 |
| 职业 | `jobs/*.json5` | kill / command / primitive | `jobs.<jobId>` | `job/<职业>/` | 每职业自己一条曲线 |
| **专业** | `professions/*.json5` | **mine / craft / smelt** + command | `professions.<profId>` | `profession/<专业>/` | 每专业自己一条曲线 |

- 等级**一律不存**，由 `ExpCurve.levelOf(total)` 派生（同 PROGRESSION §2）。
- 三族可同时持有：一个玩家可以既是 `warrior`、又是 `mining 42 级`，互不影响。
- `professions/` 与 `jobs/` 的 **id 空间要求不重叠**（L2 交叉校验，见 §7）；重叠不拦，但一定告警一条 —— 同一串 id 在两个桶里各存一份经验，运营排查时无法解释。

## 3. `professions/*.json5` schema（v1，一个文件一个专业）

```json5
{
  id: "redtale_project:mining",
  name: "采矿",
  max_level: 100,
  curve: { mode: "polynomial", base: 60.0, exponent: 1.35 },
  attributes: {
    "redtale_project:mining_efficiency": 0.01,
  },
  grants: {
    mine: { "minecraft:stone": 3, "minecraft:deepslate": 4, "#minecraft:logs": 2, default: 1 },
  },
}
```

字段表（`default` 一列 = 键缺失时的行为）：

| 键 | 类型 | 默认 | 边界 / 判定 |
|---|---|---|---|
| `id` | string | **必填** | 必须含 `:`；缺失或非字符串 → 整份跳过 + 告警；重复 → **保留先出现**的（与 `jobs/` 同一条规则）+ 告警 |
| `name` | string | `id` 冒号后的 path | 空串 / 非字符串 → 用 path + 告警 |
| `max_level` | int | `100` | 必须整数且 `2 ≤ v ≤ 1000`；否则回落 100 + 告警（与 `jobs/` 不同：专业默认 100，职业默认 50） |
| `curve` | object | `geometric / base 100.0 / growth 1.12` | 同 `jobs/`：`mode` 只认 `geometric` / `polynomial` |
| `attributes` | object | 空 | 键必须是 **80 条表**里的属性（裸名 / `redtale_project:` / `minecraft:` 三形态，归一走 `AttributeSpecs`）；值必须有限数；不在表内 → 丢弃该条 + 告警 |
| `grants` | object | 空（该专业不给经验） | 见 §4 |

- 顶层未知键 → 告警「未知键 <k>（已忽略）」，不拦。
- **同一份文件只出一种专业**（一个 `id`）；一专业多文件 = 后者按重复处理。

## 4. `grants`：动作 → 经验

### 4.1 结构

```
grants: {
  <源>: {                        // 源 ∈ { mine, craft, smelt }
    "<ns:path>": <整数>,          // 具体方块 / 物品 id
    "#<ns:path>": <整数>,         // tag（mine → block tag；craft / smelt → item tag）
    default: <整数>,              // 未命中任何条目时的兜底，缺省 = 0
  },
}
```

- **值只收整数**：`0 ≤ v ≤ 1000`。非整数 → `Math.rint` 取整 + 告警；负数 → 归零 + 告警；`> 1000` → 夹到 1000 + 告警（防洪阀，D16 的 `1e9` 硬顶在这里不需要，单次经验不值得给它留位置）。
- **源只认三个**：`mine` / `craft` / `smelt`。写别的（含 `kill`）→ 整段忽略 + 告警。理由：专业不吃战斗经验，`kill` 属于主等级与职业；放开会让「采矿专业靠刷怪升级」这种配置静默成立。
- `default` 是**保留字**（字面键名），不能与物品 id 冲突（物品 id 必带 `:`，天然不冲突）。

### 4.2 匹配优先级（**顺序无关**，不依赖 JSON5 键序）

1. `"<ns:path>"` 精确命中 → 用它；
2. 否则，命中的**全部 tag** 里取**数值最大**的那条（两条 tag 同时命中时取大，避免「谁写在前面」变成语义）；
3. 否则 `default`；
4. 都没有 → 0（**不发经验，也不告警**：采集一个没配过的方块是正常运营，不是错误）。

### 4.3 tag 由谁展开

`mine` 查 **block tag**（`#minecraft:logs` 是方块 tag），`craft` / `smelt` 查 **item tag**。tag 是 datapack 数据，**解析期不校验存在性**（离线无注册表；写错的 tag 永远匹配不上 = 静默 0，这条由 L2 的交叉校验尽力提示，最终以运行期行为为准）。

### 4.4 一次动作发多少

| 源 | 触发点（运行期） | 计数口径 |
|---|---|---|
| `mine` | 玩家破坏方块完成（`BlockEvent.BreakEvent`，未被取消、玩家非创造 / 非旁观） | 按**被破坏的方块 id** 查一次，发一次 |
| `craft` | `PlayerEvent.ItemCraftedEvent` | 按**结果物品 id** 查一次，**不乘产出件数**（否则「原木→4 木板」立刻变成刷经验入口） |
| `smelt` | `PlayerEvent.ItemSmeltedEvent` | 按**产出物品 id** 查一次（每件触发一次，天然按件） |

- 三条都不吃 `mob_exp_multiplier`（那是 `kill` 专用，同 PROGRESSION §5）。
- 一次动作**只发一条**经验，不叠加：同一方块既命中精确 id 又命中三个 tag，仍然只取 §4.2 的那一个数。

## 5. 属性下发

- modifier id：`profession/<专业 path>/<属性 path>`（例 `profession/mining/mining_efficiency`）。
- 值 = `per_level × (level - 1)`，`ADD_VALUE`；`level < 2` 只移除不加；到顶不再涨但 modifier 保留。
- **先按 id 前缀移除，再按新值加**（PROGRESSION §6 同一条，R20/R53 的坑）。
- 与主等级、职业、技能树**在同一次重算里做完**（`ProgressService.applyAttributes` 的调用点扩一处），不做第二次遍历 —— 否则同一 tick 内两次 `AttributeMap` 变更会让面板读到中间态。
- 专业**不下发技能、不参与技能树**（`skill_trees/*.json5` 的 `classes` 只认 `jobs/`）。

## 6. 存档与热重载

- `ProgressStore` **v2 → v3**：`professions: { "<profId>": <long> }`。v2 档读起零告警（缺键 = 空桶），v3 档被 v2 代码读会丢专业经验 —— 不可逆，故**先升版本再加接线**，顺序见 §8。**R66 已落地**（`ProgressStore.VERSION = 3`）。
- `/redtale reload`：曲线 / 上限 / `grants` **立即生效**，`total_exp` 不动，在线玩家按新曲线重算等级并重下发属性。
- 上限：单专业 `total_exp ≤ 1e15`（与 `ProgressStore.MAX_TOTAL_EXP` 同一条硬顶）。

## 7. 校验分层（谁能拦什么）

| 层 | 拦什么 | 为什么在这层 |
|---|---|---|
| L1 解析（纯，零 MC） | 类型、必填、范围、未知键、未知源、重复 id | 离线可测，坏配置不炸注册表 |
| L2 注册表（快照） | `attributes` 键归一后是否在 80 条表内；`professions` 与 `jobs` 的 id 是否重叠 | 需要 `AttributeSpecs` 与 `ProgressRegistry` 的现成事实。**曲线能否产出等级不在本层判**：`ExpCurve` 构造函数已把 `base ≤ 0 / growth ≤ 1 / exponent ≤ 0` 回落默认（R65-a，原表里的这一条是永不可测的死分支） |
| 运行期 | tag 是否真存在（不拦，只影响命中） | tag 是 datapack 数据，只在世界里才有答案 |
| 起飞前工具 | 配置层静态对账（`check_professions.py`，L6 新建） | 给运营在改完配置、起服之前用 |
| `/redtale validate` | 不新增检查项（专业无跨文件引用） | 避免把「零引用」的东西塞进引用检查 |

## 8. 交付顺序（层与层之间必须按顺序）

| 层 | 产物 | 谁做 | 为什么这个顺序 |
|---|---|---|---|
| L1 | `ProfessionSpec`（解析 + `resolve` 纯函数，零 MC import） | S8 | 纯解析先落地，schema 一旦写错只改一份文件 |
| L2 | `ProfessionRegistry`（快照 + 两交叉校验 + 默认件） | S7（**R65 已落地**） | 校验需要 80 条表与 jobs 快照 |
| L3 | `ProgressStore` v3 + 三个事件接线 + `/redtale exp profession <id> <n>` | S7（**R66 已落地**） | 存档升版必须早于任何写入路径 |
| L4 | 属性下发接进 `ProgressService` 的同一次重算 | S7（**R66 已落地**） | 跨文件，且与等级/技能树共用一个重算点 |
| L5 | `/redtale profession list|info` 观测面 | S8 | 命令面在服务落地之后（R36-a 同一条）；`exp profession` 已覆盖「临时看数」，L5 只做正式清单 |
| L6 | `check_professions.py` 起飞前对账（tools/ 下新建） | S8（**R66 提前派**） | 原排「内容铺开后」；提前的理由：L6 不依赖 L3，纯配置面，S8 空转时先上安全网 |

## 9. 验收矩阵

| # | 场景 | 期望 | 判据来源 |
|---|---|---|---|
| P1 | 挖一个配过的方块 | 该专业 `total_exp +N`，其余族不变 | `/redtale profession info mining` |
| P2 | 挖一个只命中 tag 的方块 | 取 tag 值；两条 tag 同时命中取大 | 配置 + 输出比对 |
| P3 | 挖一个没配过的方块 | 经验不变、**无告警** | 日志零 WARN |
| P4 | 合成「原木 → 4 木板」 | 只发 1 次经验（不乘 4） | `total_exp` 差值 = 1 份 |
| P5 | 熔炼 5 个铁矿 | 发 5 次（每件一次） | 差值 = 5 × 单件值 |
| P6 | 升到 2 级 | 属性出现 `profession/<id>/<attr>`，值 = `per_level × 1` | `AttributeMap` 按 id 分组 |
| P7 | 降级（`/redtale exp profession <id> set`） | 高档 modifier 被移除，不留残值 | 同上，前缀扫描 = 0 |
| P8 | 存档 v2 → v3 | 旧档读起零告警；存回后带 `professions` 键，`main` / `jobs` / `trees` 逐字保留 | `redtale_project_progress.dat` |

P1~P5 需要真人在线（`REALPLAYER-WINDOW.md` 追加一组 F），P6~P8 可用控制台 + 夹具验。

- **R66 实测**：P6 / P7 由 `/redtale exp profession probe` 的 F 组覆盖（升级 → 属性出现且值 = `per_level × 1`；归零 → 前缀清场残留 0 条）；P8 用手工 v2 夹具单独验过（`version: 3` + `professions: {}` + `main/jobs/trees` 逐字保留）。
- **夹具形态**：`.dat` 是 `{ data: {...}, DataVersion: 3955 }` 双层包装，手工造档必须两层都写（缺 `DataVersion` 会被 DataFixer 打坏 → 静默读成空档，R66-b）。

## 10. 明确不做（v1）

1. 方块再生 / 矿脉刷新（D27 已否）。
2. `drop-table` 额外掉落、`options.vanilla-drops` 开关。
3. 专业间互斥、转专业、专业重置收费（经济仍等 D28）。
4. 专业专属 GUI / HUD 进度条（D30 owo 排期在后面）。
5. 专业影响技能伤害 —— 专业只发属性，伤害仍走 `Settle` + `DamageLayer`（D12）。
6. 把 `mine` / `craft` / `smelt` 开放给 `jobs/`：职业的 `exp_sources` 仍只认 `kill` / `command` / `primitive`。
