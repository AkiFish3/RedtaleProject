# R18 · 2026-11-16 13:39-14:0x · 验收 T-0012 + M3 第 3 步（`ContentData` 容器扩容 / schema 1→2）

## 1. 卡板

| 卡 | 结果 | 依据 |
|---|---|---|
| T-0012 `/redtale content check` | **VERIFIED** | 3/3 机器验收 + 功能四跑（见 §3） |
| T-0014 `SetSpec` 套装解析器 | **OPEN（新派）** | M3 第 5 步，`ITEM-MODEL.md` §5.4，在制品 T-0013+T-0014 = 2/3 |

在制品：T-0013（OPEN，上轮派）+ T-0014（OPEN，本轮派）= **2/3**。无 `[DONE]` 待验、无三振超期卡。

## 2. T-0012 验收

### 2.1 机器验收

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `gradle build --offline` | exit 0 | **0**（jar 262440 B） |
| 2 | `grep -c "ContentCheckCommand::check" RedtaleCommand.java` | = 1 | **1** |
| 3 | `unzip -l jar \| grep -c core/command/ContentCheckCommand` | = 1 | **1**（无内部类） |

### 2.2 功能四跑（测试服，RCON）

| 跑 | 操作 | 输出 | 判定 |
|---|---|---|---|
| ① | 首次执行 | `内容检查：17 份 json5（items 8 份）· rev 漏加 0 份` / `已建立基线 17 份（下次起参与比对）` / `基线已更新：E:\server-test\config\redtale_project\.rev-baseline.json` | 建基线返回 0 ✓ |
| ② | `demo_longsword` 的 `physical_damage 0.2→0.25`，**不加 rev** | `rev 漏加 1 份` + `  items/demo_longsword.json5: 内容已改但 rev 没加（基线 0 → 当前 0）` | 漏加检出 ✓ |
| ③ | 同文件加 `rev: 1` | `rev 漏加 0 份` + `  （没有 rev 漏加）` | 加 rev 后清零 ✓ |
| ④ | 还原文件（`cmp` 与备份逐字相同） | `  items/demo_longsword.json5: rev 倒退（基线 1 → 当前 0）`，且汇总行 `rev 漏加 0 份` | 倒退单列、不计入 K ✓ |

- 删基线后再跑回到「已建立基线」态，验收现场已清理（箱子/forceload 已撤，测试服已停）。
- **运行期确认**：`content list` 与 `content check` 是两条同名 `content` 兄弟节点，Brigadier 1.3.10 `addChild` 同名合并成立 —— 两条命令同时可用（`content list` 输出 8 行）。这是 S8 在卡上挂的「只能实测确认」项，已闭环。
- 零 redtale ERROR/WARN。

## 3. M3 第 3 步：`ContentData` 容器扩容

### 3.1 改动

| 文件 | 改动 |
|---|---|
| `items/ContentData.java` | 字段 10 → **12**：`+sockets`（`List<String>`，`""` = 空孔）、`+gemStats`（`Map<String,Double>`）；`CURRENT_SCHEMA` **1 → 2**；codec 两个 `optionalFieldOf`（`sockets` / `gem_stats`）；四个 `with*` 方法补齐；新增 `withSockets(sockets, gemStats)` / `hasSockets()` |
| `items/ItemUpgrader.java` | `SCHEMA = ContentData.CURRENT_SCHEMA`（**引用**，不写字面量）；`ensure` 重建时 `data.sockets()` / `data.gemStats()` 原样带出 |
| `items/ItemFactory.java` | 新建物品写 `List.of()` / `Map.of()`（孔位布局等第 4 步） |

**为什么这两个字段不并进 `rolled_stats`**：同一属性若被词条与宝石各加过、并进一个键，拆宝石会把词条那部分一起扣掉（扣不准）。两表分开 → 拆宝石 = 重算 `gemStats`，词条一个数都不动（`ITEM-MODEL.md` §5.1）。

### 3.2 实测（测试服 2 次启动，均已停服）

手写两件 `t_upg_sword`（定义 `rev: 3`）进箱子 `0,100,0`：

- **A 件**：`schema:1` + `sockets:["",""]` + `gem_stats:{"minecraft:physical_damage":0.3}` + `rolled_stats:{"minecraft:generic.attack_damage":5.0}` + `rolled_keys:[attack_damage]`
- **B 件**：同数据但 `schema:2`

`/redtale reload` → 日志 `全量刷新已排程：43 个区块 / 5 个实体` → `全量刷新完成：43 个区块（跳过 1728）/ 5 实体 / 升级 1 件 / 耗时 260 ms`。

| 组 | 迁移后组件 | 判定 |
|---|---|---|
| A（schema 1） | 补出 `attribute_modifiers`（`redtale_project:content/generic.attack_damage` = **5.0**，`rolled_keys` 保旧值）、`custom_name "更新测试剑"`、`rarity epic`、`max_damage 500`；`sockets:["",""]` 与 `gem_stats:{"minecraft:physical_damage":0.3}` **逐字保留**；`schema` 键**消失**（= 当前版本 2） | 结构迁移 + 资产保留 ✓ |
| B（schema 2） | 组件**逐字未变**（无 `attribute_modifiers` / 无名称） | 快路径 ✓ |

- `physical_damage` 没出现在面板是**正确**行为：手写件的 `rolled_stats` 里没有这个键，按 U3「不白送」，面板只体现「实例真有的键」（R18-d）。

## 4. 两条新事实

1. **`optionalFieldOf` 值等于默认值时不写字段**。`Codec.INT.optionalFieldOf("schema", CURRENT_SCHEMA)` → 升到 2 之后组件里**看不到** `schema` 键。验迁移的判据是「字段消失」；显式写着 `schema:1` 的才是旧版。这条会误导后来人（「怎么升完还是 schema:1？」），已写进 `CONTRACTS.md` §4 与 `PLAN.md` §13.21。
2. **`ItemUpgrader.SCHEMA` 与 `ContentData.CURRENT_SCHEMA` 必须是同一个值**。上一版是两处字面量 `1`，本轮改成引用 —— 两处各写数字早晚漂一次，而漂一次的后果是「schema 判据恒假 → 全服物品永不迁移」，静默且极难查。

## 5. 口径（写进 `PLAN.md` §13.21）

| # | 口径 |
|---|---|
| R18-a | `sockets` / `gemStats` 是玩家资产，`ensure` 重建时原样带出；孔数对齐（截断/补空孔/退回宝石）属第 4 步镶嵌结算 |
| R18-b | `ItemUpgrader.SCHEMA` 引用 `ContentData.CURRENT_SCHEMA`，不写字面量 |
| R18-c | 新字段一律 `optionalFieldOf` + 默认值（老 NBT 照常解码），靠 schema 版本触发一次重建 |
| R18-d | 面板只体现「实例真有的键」，缺的不白送（U3） |

## 6. 未做 / 挂账

- **`ItemContent.sockets` 段未接**（M3 第 4 步）：定义写不出孔数，`ItemFactory.build` 只能给空孔序。
- 挂账第 17 轮（需带客户端的窗口）：schema 1→2 全量迁移后真人客户端面板是否刷新；其余六项沿上轮。
- `build/probe/*.java` 里若有用旧 10 参构造 `ContentData` 的探针会编不过（探针不在 `src/`，不影响 `gradle build`），下次跑探针时按 12 参改。
