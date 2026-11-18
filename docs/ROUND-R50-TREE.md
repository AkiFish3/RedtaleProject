# R50 · M7 技能树 L2 快照层 + 验收 T-0045

- 日期：2026-11-18 17:47–18:2x
- 队列：S7（派单 / 验收 / 自研）
- 里程碑：**M7（等级 / 经验 / 职业 → 技能树 D25）**，第 2 层落地

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0045 `SkillTreeSpec` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + S7 独立探针 117/117） |
| T-0046 `TreePoints` | 派单 | 新卡，`[OPEN]`，在制品 **1/3** |

验收三条（卡面）：

1. `gradle build --offline` → `RC=0` ✅
2. jar 内 `SkillTreeSpec` 宽匹配 **4**（主类 + `$Effect`/`$Node`/`$Definition`）、`wc -l` = **167** ≤ 200、`net.minecraft` = 0 ✅
3. `python tools/probe_run.py SkillTreeSpecProbe` → `PASS 67 / FAIL 0 / TOTAL 67`（≥25 达标）✅

S7 独立探针 `build/probe/S7TreeProbe.java`：**117/117**（A~O 十五组，不复用 S8 断言）。当场抓到的一条是**我自己的期望写错**（`N1` 把顶层未知键与节点未知键塞进同一夹具，却只期望一条告警）。

### 五条口径裁定（S8 提的，全采纳，不改码）

1. 卡面「9 节点 / 21 点」→ **笔误**：`SKILL-TREE.md` §3 实为 **10 个节点**（root + 9 子节点）；`max_points: 21` 是显式给定值，cost 合计 **19**（探针 A23 断言 19）。按语义裁（R43-a / R49-c 同源）。
2. `requires` 悬空 → **保留条目 + 保留节点**，只告警。
3. 非字符串 `requires` 元素 → 静默跳过（卡面未定义）。
4. `effect` 内未知键（§3 的 `args`）→ 不告警（口径 13 只管顶层与节点内）。
5. 顶层 `name` 非字符串/缺失 → 原样 `null`，不回落（卡面只规定节点 name 回落）。

### 补测边界（卡面未写死，记录不改码）

| 输入 | 行为 |
|---|---|
| `max_points: 21.5` | 按「缺失」处理 → 取 cost 合计 + `max_points 缺失` 告警 |
| `cost: 1.5` / `"2"` | 回落 0（非整数不当整数） |
| `pos: [-1,-2]` | 合法，**不夹取** |
| `nodes: []` | 与 `nodes: {}` 同文案「nodes 为空，已跳过」 |

## 2. 自研：`SkillTreeRegistry`（L2 快照）

新建 `core/progression/SkillTreeRegistry.java`（约 210 行），与 `ProgressRegistry` / `SkillRegistry` / `SpawnRegistry` 同构：

- `load(all, progress, skills, warnings)`：解析 → 逐树交叉校验 → 属性键归一 → 不可变快照。
- 三条交叉校验（文案逐字进 `CONTRACTS.md` §4.31）：
  - `… 的职业 <job> 不在 jobs/ 里（树仍保留，职业过滤时不会命中）`
  - `… 引用的技能 <skill> 不存在（该节点点了也不给技能）`
  - `… 的属性 <attr> 不在 80 条属性表里，已丢弃（该条不会下发）`
- 属性键归一成 `AttributeSpec.id()`；**重建新的 `Definition` 对象，不就地改 L1 产物**。
- `treesForJob(job)` / `nodeCount()` / `pointTotal()` / `describe()`；`describe()` = `1 棵技能树（10 个节点，21 点上限）`。

接线：`RedtaleCore.skillTrees()` + reload 里**排在 `progress` 与 `skills` 之后**（R47-a 第三次复现）+ 日志/回显带棵数。

### 默认件三件套（必须一起存在，否则一启动就告警）

| 文件 | 作用 |
|---|---|
| `skill_trees/warrior_vanguard.json5` | §3 的战士·先锋树，10 节点 / 21 点 |
| `jobs/warrior.json5` | 树的 `classes: ["warrior"]` 要有对应职业 |
| `skills/quake_slam.json5` | 树的 `quake` 节点 `unlock_skill` 指向它 |

§3 示例里三个键**不在 80 条属性表里**（表 D17 冻结，不许增删）→ 默认件按表改写并在文件头注释记偏差：

| §3 | 实际 | 原因 |
|---|---|---|
| `redtale_project:blunt_power` | `redtale_project:physical_damage` | 表里没有 |
| `redtale_project:armor_toughness` | `minecraft:generic.armor_toughness` | 护甲韧性只有原版 |
| `minecraft:generic.attack_knockback` | `redtale_project:knockback_power` | 表里只有 `knockback_resistance` / `knockback_power` |

## 3. 本轮最值钱的一条：`classes` 与 `jobs/` 的键形态

`SKILL-TREE.md` §3 写 `classes: ["warrior"]`（裸名），而 `jobs/*.json5` 的 id 是 `redtale_project:warrior`（全名）。

第一版校验直接 `progress.jobs().containsKey(job)` → **仓库默认树上线的第一条告警就是「职业 warrior 不在 jobs/ 里」**，两边字符串各自都合法。

修法：比较前统一取 **path**（`pathOf(id)` = 冒号之后部分），`treesForJob(job)` 同样两向都认（探针 F6：传 `redtale_project:miner` 能命中 `classes: ["miner"]`）。

这与 R45-a（`ResourceLocation.parse` 把自建裸 path 补成 `minecraft:` → modifier 静默不下发）是**同一类错误**：同一概念在两处用了不同形态。两条都写进 `PLAN.md` §13.53 R50-a。

顺带确认：`AttributeSpec.id()` 对自建属性返回**裸 path**（`physical_damage`），所以归一的终点形态是「原版全名 / 自建裸 path」混排 —— L3 下发必须走 `AttributeSpecs.resolve`，不能自己拼 `redtale_project:` 前缀。

## 4. 实测（测试服 1 次启动，已停）

`E:\server-test`，jar 599247 B 直接覆盖 `mods/`。

| 检查 | 结果 |
|---|---|
| 启动 | `Done (2.010s)` |
| 启动日志 | `1 棵技能树（10 个节点，21 点上限）`、**36 份配置**、**零 `配置告警`** |
| `/redtale reload` | `…+ 1 棵技能树（36 份配置文件）`，无告警段 |
| 坏夹具 `skill_trees/t_bad.json5`（悬空职业 + 悬空技能 + 不在表的属性） | `2 棵技能树 / 37 份配置 / 3 条告警`，三条文案逐字对，且**只来自夹具** |
| 连跑第二次 reload | 计数与三条告警**逐字相同**（幂等） |
| 删夹具 → reload | 回落 `1 棵 / 36 份 / 零告警` |
| 回归 | `validate 问题 0 条`、`exp probe 7/7`、`level list` 主等级 1 + 职业 2、`selftest 78 PASS` |
| 停服 | `stop` 干净退出，25567/25577 **无 LISTEN** |

离线侧：`S7TreeRegProbe` **61/61**，其中 A 组**直接读仓库里那份 `warrior_vanguard.json5`**（走 `Json5.parse`）断言「解出来零告警 / 10 节点 / 21 点 / cost 合计 19」，I 组断言默认三件套互相自洽 —— 这样默认件写歪不用起服就能红。

## 5. 未验证 / 挂账

- `treesForJob` 的**玩家上下文**：现在没有任何调用方（命令面还只有日志里的棵数），真人路径要等 `/redtale tree` 与 L3 持久化。
- `respec` / `exclusive_with` 按契约**原样忽略**，洗点与互斥都还没实现（L4/L5）。
- `current()`（R49）、tooltip 真人客户端、pvp 真目标、玩家 `stun`、`mob spawn` 客户端体积、自然刷怪替换真人验收、真人击杀 → `MobDrops → ProgressService`：**挂账不变**。

## 6. 数字

| 项 | 值 |
|---|---|
| `build --offline` | exit 0，jar **599247 B** |
| `selftest` | **78 PASS** |
| S8 探针 | `SkillTreeSpecProbe` 67/67 |
| S7 探针 | `S7TreeProbe` 117/117、`S7TreeRegProbe` 61/61 |
| 配置份数 | 36（+3） |
| 构建锁 | 取 / 删各一次（`.queue` 空） |
