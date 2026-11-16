# R8 · 数据包自动启用（R7 遗留闭合）+ 验收 T-0002

日期：2026-11-16 01:43-02:05　里程碑：M2（进行中）　队列：S7（派单 / 验收 / 自研）

## 1. 验收 T-0002（`/redtale content list`）→ VERIFIED

| 验收条 | 命令 | 结果 |
|---|---|---|
| ① build | `gradle build --offline` | exit **0**，jar `157500 B` |
| ② grep | `grep -c "ContentListCommand" RedtaleCommand.java` | **2**（S8 自报 =1 不成立，实测注释行也计数；S7 接受，不改卡） |
| ③ 测试服 | `redtale content list`（4 份 `items/*.json5`） | 汇总 1 行 + **4 行明细**，条数与文件数一致 |

实测输出（`E:\server-test`，`Done (2.421s)`）：

```
[redtale] 内容 4 件 · 数值共 9 条 · 带 weapon_attributes 2 件
  demo_chestplate · base chestplate · 数值 3 条 · 词条 -（词条层未接入） · weapon_attributes -
  demo_longsword · base sword · 数值 4 条 · 词条 -（词条层未接入） · weapon_attributes bettercombat:claymore
  demo_robe · base chestplate · 数值 0 条 · 词条 -（词条层未接入） · weapon_attributes -
  test_sword · base sword · 数值 2 条 · 词条 -（词条层未接入） · weapon_attributes redtale_project:test_sword
```

「词条」列显示 `-（词条层未接入）` 是 S8 的主动选择（不写 0 以免被读成「解析过、零个」），接受；T-0003 落地后由 S7 换成真实计数。

## 2. R8-1 数据包自动启用

### 症状（R7 记录）

reload 时生成器往 `<world>/datapacks/redtale_project/` 写文件，但 `PackRepository` 的可用列表是**启动时**扫的：

- 写完 → 目录存在，但 `repo.getAvailableIds()` 里没有 → `/datapack list` 看不到；
- 且即使看到也不会自动选中 → 生成物（`weapon_attributes` / 将来的 `enchantment`）**不进注册表**，要等重启。

### 做法（`core/datapack/PackEnabler.java`，新类 103 行）

```
ensureEnabled(server, "redtale_project", warnings) → Outcome
  ① repo.reload()               // 重扫磁盘，新目录进 available
  ② 已在 selected → ALREADY_SELECTED（重启后的常态，什么都不做）
  ③ repo.addPack("file/redtale_project")   // 选中，失败 → FAILED + 告警
  ④ server.isRunning() == false → ENABLED_PENDING_RELOAD（启动期不重载）
     2s 内已重载过 → ENABLED_PENDING_RELOAD（时间窗去重）
     否则 server.reloadResources(repo.getSelectedIds()) → ENABLED_AND_RELOADED
```

接线：`RedtaleCore.reload()` 在 `weapon_attributes` 生成之后追加 1 行（+ 1 行注释），把结果写进 `LOG.info`。

### 证据

| 场景 | 操作 | 结果 |
|---|---|---|
| **R7 的失败场景**（包不存在 → 运行期新生成） | 停服 → `rm -rf world/datapacks/redtale_project` → 启动 | `Done (2.449s)`；日志 `生成数据包状态：ENABLED_AND_RELOADED`（1 次）；`/datapack list` → `[file/redtale_project (world)]` **在 enabled 列表里，未执行任何 `/datapack enable`** |
| 运行期删包 + reload | 运行中 `rm -rf` 该目录 → `redtale reload` | 输出 `已载入 1 条脚本 + 4 件装备内容（8 份配置文件）`；文件重生成（`pack.mcmeta` + `data/redtale_project/weapon_attributes/test_sword.json`）；日志 `ALREADY_SELECTED`；`/datapack list` 仍 3 个启用 |
| 无循环 / 无栈溢出 | `grep -c "StackOverflow"` = 0（`PackEnabler` 的 4 次出现全在 SimplySwords 坏配方的嵌套栈里） | redtale 自身 **0 ERROR** |

产物：`unzip -l` 含 `com/redtale/project/core/datapack/PackEnabler.class` + `PackEnabler$Outcome.class`；`gradle selftest --offline` **55 PASS**（未退步）。

### 三条口径（已写 `PLAN.md` §13.12）

1. **R8-a**：三步缺一不可（`repo.reload` → `addPack` → `reloadResources`），这正是原版 `/reload` 的内部动作。
2. **R8-b**：启动期只选中不重载（`isRunning()` 为假）。
3. **R8-c**：2s 时间窗去重，避免启动事件 + 手动 reload 各触发一次。

## 3. 派单

在制品：T-0003（`OPEN`）、T-0004（`OPEN`）、**T-0005**（新派，`OPEN`）= **3/3 封顶**。

**T-0005 `tools/gen_item_lang.py`**：14 个基底物品**一条 lang 键都没有**（`grep -c "item.redtale_project"` = **0**），`/give` 显示裸键 `item.redtale_project.sword`。卡里写死：id 从 `ItemBase.java` 正则读（不重抄）、中英名映射表、**合并语义**（`attribute.*` 66 条必须原样保留）、`sort_keys` + 无 BOM + 幂等三条验收。

## 4. 卡点

- 无阻塞项。测试服两轮起停干净，已停服（端口 25567 无监听）。
- 观测到但**不归我们**的日志噪声：`RecipeManager` 解析失败 4 条（`simplyswords:mythicmetals_compat/*`、`brewinandchewin:fermenting/kombucha`、`minecraft:red_dye`），是测试服装的玩法 mod 缺前置/坏配方，与本 mod 无关（R7 起就有）。
- **挂账第 8 轮**：玩家上下文攻速 / 真目标 pvp / BetterCombat 双持重复计，都要一次带客户端的验收窗口。

## 5. 下一轮（R9）

1. `CriticalHitEvent` 验暴击入口（顺延 4 轮）。
2. T-0003 落地后接词条 roll（D22），tooltip「词条」列换真实计数。
3. 验收 T-0003 / T-0004 / T-0005。
