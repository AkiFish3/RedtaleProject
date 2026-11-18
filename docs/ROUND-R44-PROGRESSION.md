# R44 · 验收 T-0039 + M7 第二层快照（ProgressRegistry / ProgressRules）

2026-11-18 10:33–10:55 · 测试服 1 次启动 `Done (2.001s)`，验完即停（25567/25577 无 LISTEN）。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0039 `ProgressSpec` | 验收 → `[VERIFIED]` | 3/3 + S7 独立探针 83/83 |
| T-0040 `/redtale level list\|info` | **派单**（在制品 1/3） | `[OPEN]` |

### T-0039 验收证据

| 验收项 | 期望 | 实测 |
|---|---|---|
| build | exit 0 | `BUILD SUCCESSFUL`，jar 526008 B |
| jar 条目 | `core/progression/ProgressSpec` = 2 | 2（主类 + `$Definition`） |
| 行数 / 禁用词 | ≤200 / `net.minecraft`=0 / `Logger`=0 | 141 / 0 / 0 |
| 独立探针 | ≥24 条，覆盖语义 1~11 | `S7ProgressSpecProbe` **PASS 83 / FAIL 0**（A~K 十一组） |

探针分组：A 单条正常（id 原样 / name 缺省=path 段 / max 缺省 / `expForLevel(1)`=base）；B jobs 缺省（`["kill"]`、属性键原样）；
C 跨目录不互吃（含 `configs=null`、非 `.json5`、子目录）；D id 四态 + 根非对象；E 重复 id 保先；
F `max_level` 五态（2.5 / 0 / 1 / 5000 / 37 / 字符串 / 布尔）；G 曲线（未知 mode、base 0、growth 1.0、polynomial、缺 curve、curve 非对象）；
H 属性坏值丢弃且裸键原样；I `exp_sources` 去重与显式 `[]`、levels 忽略该键；J 三处不可变 + 保序；K 保文件顺序 / `warnings=null` / 中文 name / 曲线与 `ExpCurve` 逐点相等。

**S7 的一处改动（已记原因）**：`Definition` 的 `attributes` / `expSources` 从 `Map.copyOf` / `List.copyOf` 改为 `unmodifiableMap(new LinkedHashMap<>(...))` / `unmodifiableList(new ArrayList<>(...))`。
卡面写的是 `Map.copyOf`，但它与 `PLAN §13.46 R43-b`（对外不可变集合必须保序）冲突 —— `Map.copyOf` 走 `ImmutableCollections` 的随机 SALT，跨 JVM 迭代序不保证，属性行的输出顺序会跟着抖。语义不变（仍不可变），探针 J2/J3 复验仍抛 `UnsupportedOperationException`。

## 2. 本轮自研：M7 L2 快照层

### 新类 `core/progression/ProgressRegistry.java`

不可变快照，API：`EMPTY` / `load(all, warnings)` / `levels()` / `jobs()` / `get(id)` / `has(id)` / `size()` / `levelCount()` / `jobCount()` / `attributeCount()` / `sortedIds(job)` / `warnings()` / `describe()`。

三件解析层不做的事（R44-a / R44-b / R44-d）：

1. **80 条属性表校验**：键经 `AttributeSpecs.resolve` 解析不到 → 丢弃该条 + 一条 WARN（`PROGRESSION.md` §8 红线 3 的落点）。
2. **键归一成全名**：`attack_damage` / `generic.attack_damage` / `minecraft:attack_damage` → `minecraft:generic.attack_damage`；两种写法同时出现 → 后者覆盖 + 一条 WARN（R11-b 的坑：不同写法 = 两个 modifier id = 面板翻倍）。
3. **跨级同 id 去重**：同一 id 同时出现在 `levels/` 与 `jobs/` → 留主等级、丢掉职业那份 + 一条 WARN（否则 L4 会多下发一套 `job/…` modifier，而命令面看不到）。

### 新类 `core/progression/ProgressRules.java`

`progression.json5` 的解析结果，v1 只有 `mob_exp_multiplier`：缺省 1.0、负数 → 0 + WARN、非数字 → 1.0 + WARN、>1e9 截断 + WARN（D16 的物理硬顶，不是平衡钳制）。

### 接线与默认内容

- `RedtaleCore`：新增 `progress` / `progressRules` 两个 volatile 字段 + 两个访问器，reload 里 2 行解析，reload 日志与回显带「N 条等级定义」。
- `ConfigStore.DEFAULT_FILES` +3：`levels/main.json5`、`jobs/miner.json5`、`progression.json5`。
- 默认 `levels/main.json5`：geometric base 100 growth 1.12 max 100，属性 `max_health +0.5/级`、`attack_damage +0.1/级`。
- 默认 `jobs/miner.json5`：polynomial base 80 exponent 1.4 max 50，属性 `mining_efficiency +0.02/级`，`exp_sources ["kill"]`。

> 注意：初稿默认件写的是 `redtale_project:mining_speed` —— **80 条表里没有这条**（真实键是 `mining_efficiency`），
> 上线即被校验层丢弃并告警。这正是「校验必须在快照层」的活例子：写错一个字母，reload 时立刻一行 WARN，而不是等到玩家升到 10 级发现面板没动。

## 3. 实测（测试服）

| 组 | 操作 | 实测 |
|---|---|---|
| A 启动 | 直接开服 | `Done (2.001s)`；日志 `… 1 张掉落表，2 只怪物（3 条技能引用，原型 2 种），主等级 1 条 / 职业 1 条（3 条按级属性）`；32 份配置（29 + 3） |
| B reload | `/redtale reload` | `已载入 2 条脚本 + 10 件装备内容 + 3 条技能 + 2 只怪物 + 1 张掉落表 + 2 条等级定义（32 份配置文件）` |
| C 热加坏属性 | 写 `levels/t_probe.json5`（含 `spell_power`）→ reload | `3 条等级定义（33 份配置文件），1 条告警`；WARN 文案 `levels/t_probe.json5: redtale_project:probe_level 的属性 spell_power 不在 80 条属性表里，已丢弃（该条不会下发）` |
| D 回落 | 删夹具 → reload | 回到 `2 条等级定义（32 份）`、零告警 |
| E 负向 id | `levels/t_bad.json5`（`id: "no_namespace"`）+ `jobs/t_dup.json5`（冒充 `redtale_project:main`）→ reload | `2 条等级定义（34 份），3 条告警`：`id 缺失或不是字符串（必填），已跳过这条` / `curve 缺失或不是对象，已用 geometric/100.0/1.12` / `redtale_project:main 与主等级定义 levels/main.json5 同 id，已按主等级处理（职业那份不下发）`；等级数仍 2（坏 id 被跳过、dup 职业被丢） |
| F 回归 | `/redtale validate` | `校验：10 件内容 / 1 个词条池 / 3 条词条定义 / 1 个套装 · 技能 3 条 · 问题 0 条` |

红线都在：零 redtale ERROR（4 条 RecipeManager 既有噪声）、夹具全部删除、forceload/实体无残留、测试服已停。

## 4. 数字

- `gradle build --offline`：`BUILD SUCCESSFUL in 4s`（jar **526041 B**）
- `gradle selftest --offline`：**78 PASS**（无新增离线单测，本轮的可测面在两个独立探针）
- `S7ProgressSpecProbe`：**83 / 83**
- `S7ProgressRegProbe`：**47 / 47**（A 归一 / B 坏键 / C 跨级同 id / D 视图与计数 / E `ProgressRules`）
- 新文件：`ProgressRegistry.java`、`ProgressRules.java`、3 份默认配置、2 个探针（`build/` 不入库）

## 5. 下一轮（R45）

1. 验收 T-0040（`level list|info` 三行文案逐字对 + 负向文案 + `grep -c ProgressSpec`=0）。
2. M7 L4 `ProgressService`：击杀经验入双累加器、`level/` 与 `job/<职业>/<path>` 属性下发（先移除再加）、登录与 reload 后重算。
3. 经验来源第二条：`/redtale exp add`（`command`）+ 脚本原语 `grant_exp`（`primitive`，加原语要换 jar）。
