# R19 · M3 第 4 步：镶嵌 / 取出结算 + 孔位接线

> 2026-11-16 · S7 队列 · 验收 T-0013、派 T-0015、自研镶嵌链路
> 前置：R18 把容器（`ContentData.sockets` / `gem_stats`，schema 2）铺好，但**没有任何写入路径**，
> `ItemContent` 也没有 `sockets` 段 —— 那时镶不进去，也镶不出来。本轮把这条链打通。

## 1. 卡板

### 1.1 验收 T-0013 → `VERIFIED`（3/3 + 独立探针 49/49）

| 验收项 | 结果 |
|---|---|
| `gradle build --offline` | exit **0**（jar 267999 B） |
| `grep -c "public static Layout parseLayout"` / `parseGem` | **1 / 1** |
| `unzip -l \| grep -c items/SocketSpec` | **3**（主类 + `$Layout` + `$Gem`） |

**S7 独立探针** `build/probe/S7SockProbe.java`（不复用 S8 的 `SockProbe` 断言）**49/49 PASS**，覆盖：

- `root=null` / `sockets` 缺失 → `Layout.EMPTY` 且**零告警**；`sockets:"x"`、`sockets:[]` → EMPTY + 1 告警；
  `sockets:null` → EMPTY（显式 null 按卡面「存在但不是对象」计 1 条告警，与 T-0010 `repair:null` 同口径）
- `count`：`-3 → 0`、`9 → 6`（6 个 any）、`"2"`（字符串）→ EMPTY + 告警、只给 count → 全 `any` **零告警**、`count:0` 带 types → EMPTY 且**不碰 types**
- `types`：`["Offensive","",5,null]` count 4 → `[offensive, any, any, any]`（**孔序不错位**，3 条告警）；5 项 count 3 → 截断 `[a,b,c]` + 1 条带数字的告警；**count 0..6 全档 `types.size()==count`**；不可变
- `gem` 侧：缺段 → `null` 零告警；`gem:{}` → `any`/`1`/`""`/空表零告警；`type:" Offensive "` → `offensive`；`tier:0 → 1`；`exclusive_group:" Ruby "` → `Ruby`（**保大小写**）；曲线 `{base:2,per_item_level:0.1}` 进 `StatValue` 且键归一为 `minecraft:generic.attack_damage`；`stats` 键集与告警条数与直调 `AffixPools.parseStats` **全等**；不可变

> 探针自己踩了两个坑（记下来，以后写探针别重犯）：
> ①`sockets:null` 我按「零告警」断言 → 实际 1 条，**卡面口径是对的，是断言写宽了**；
> ②`gem.stats` 里 `attack_damage` 归一成 `minecraft:generic.attack_damage`，按裸名查 `null` → **假失败**。

### 1.2 派 T-0015（`/redtale validate` 引用完整性检查）

在制品：**T-0014（`SetSpec`）+ T-0015 = 2/3**。

选它的理由：M3 之后剩下的活（套装属性下发）要跨 4 个文件、还要定 modifier id 纪律，不适合作业给 S8；
而「配置互相引用但目标不存在」这一类**静默失效**（武器指向没写的 `weapon_attributes` 定义、词条池引用删掉的词条）
现在既不报错也不生效，正好是一张纯只读、单类、机器可验的卡。卡里写死了四类检查与逐字文案。

## 2. 自研：镶嵌 / 取出结算

### 2.1 改了什么

| 文件 | 改动 |
|---|---|
| `items/ItemContent.java` | 12 → **14 字段**：`+sockets`（`SocketSpec.Layout`）/ `+gem`（`SocketSpec.Gem`）；`isGem()` / `hasSockets()`；`describe()` 带孔位与宝石 |
| `items/ItemFactory.java` | `build` 按布局**填空孔**（`blankSockets(count)`）；`modifiers(content, stats, gemStats)` 把宝石数值并进同一个 `ATTRIBUTE_MODIFIERS`，**id 前缀分开**（`content/` vs `gem/`）；`applySockets(stack, content, data)` = 宝石唯一写回点 |
| `items/Socketer.java`（新） | `insert` / `extract` / `solve` / `aligned` / `accepts` + 8 个 `Status`。**唯一结算点** |
| `items/ContentTooltip.java` | 加「孔位 N：[type] gem · …」与「宝石 type · T2 · 组 x」两行 |
| `items/ItemUpgrader.java` | `ensure` 重建时按新定义**补空孔**、**重算 `gemStats`**；改小孔数**不截断**，只逐条记账 |
| `items/ItemContentRegistry.java` | 加 `allById()` 只读视图（镶嵌要按 id 批量查宝石定义） |
| `core/command/RedtaleCommand.java` | `/redtale socket [list\|insert <i> <gem>\|extract <i>\|probe]`；`socketNode()` 独立成方法（省掉五层括号） |
| 默认配置 | 新增 `items/gem_ruby.json5`（offensive / 组 ruby）与 `items/gem_sapphire.json5`（defensive / 组 sapphire），进 `DEFAULT_FILES`；`demo_longsword` 加 2 孔（`["offensive","any"]`）并 `rev 0 → 1` |

### 2.2 三条设计取舍

1. **id 前缀必须分开**：宝石要走同一个 `ATTRIBUTE_MODIFIERS` 组件才会生效，但 `AttributeMap` 对**同 id** 是
   「后加覆盖先加」。所以内容用 `redtale_project:content/<属性>`、宝石用 `redtale_project:gem/<属性>`。
   实测：同一属性既有内容 7 点又有宝石 2.1 点时，面板合计 **9.1**（不是 7 也不是 2.1）。
2. **取宝石 = 重算整张表**：`solve(sockets)` 遍历孔序重算，不做减法。相减会累积误差，
   且「孔序与数值不一致」这种状态一旦出现就再也对不上账。
3. **孔数只补不截**：`ensure` 遇到「定义孔数 < 物品孔位」时**不截断**（截断 = 无声销毁玩家资产），
   只把超出的宝石逐条写进 `warnings` 要求管理员先 `socket extract`。这与 R18-a「原样带出」同源，代价明示在文档里。

## 3. 实测（测试服 `E:\server-test`，起停 2 次，均已停）

- `Done (2.256s)` / `Done (2.211s)`；`/redtale reload` = **19 份配置 / 10 件内容**（+2 件宝石）。
- `redtale items` 实测：`demo_longsword · sword · 4 条属性 · 耐久 1200 · bettercombat:claymore · 孔位 2 · rev 1`、
  `gem_ruby · amulet · 宝石 offensive T1`、`gem_sapphire · amulet · 宝石 defensive T2`。

### 3.1 `/redtale socket probe` **11/11 全过**

| # | 检查 | 实测 |
|---|---|---|
| 1 | 新发放孔序 = 2 个空孔 | `[, ]` / `gemStats={}` |
| 2 | 镶 `gem_ruby` → `physical_damage 0.05` + `attack_damage 2.1`（2 + 0.1×1） | `APPLIED {…attack_damage=2.1, physical_damage=0.05}` |
| 3 | 面板 `gem/` 两条 + `content/generic.attack_damage` 一条**并存** | 6 条 id 全部列出，两类各就位 |
| 4 | 面板攻击伤害 = 7 + 2.1 = **9.1** | `9.1` |
| 5 | 同组第二颗 ruby | `EXCLUSIVE_CONFLICT 互斥组 ruby 已在孔 0 里有一颗` |
| 6 | offensive 孔 + defensive 宝石 | `TYPE_MISMATCH 孔 0 是 offensive 型，宝石是 defensive 型` |
| 7 | any 孔收 sapphire → 3 条 | `{physical_damage=0.05, attack_damage=2.1, critical_strike_chance=0.03}` |
| 8 | tooltip 孔位行同时列两颗 | `孔位 2：[offensive] gem_ruby · [any] gem_sapphire` |
| 9 | 取出孔 0 → 只剩 1 条（**重算**） | `{critical_strike_chance=0.03}` |
| 10 | 空孔再取 | `EMPTY_SLOT 孔 0 是空的` |
| 11 | 下标 5 | `INDEX_OUT_OF_RANGE 孔位下标 5 越界（0~1）` |

### 3.2 D33 迁移（孔位补齐）

手写一件 `content_rev 0` 且**没有 `sockets`** 的 `demo_longsword` 放进箱子（`item replace block … with redtale_project:sword[redtale_project:content={…}]`）
→ `/redtale reload`（排程 50 区块）→ `data get block … Items[0].components`：

```
content_id: "demo_longsword"
rolled_stats: {"minecraft:generic.attack_damage": 5.0d}   ← 玩家值保留
rolled_keys: ["minecraft:generic.attack_damage"]
sockets: ["", ""]                                          ← 按新定义补齐
content_rev: 1
```

redtale 零 ERROR；`ERROR` 仍为 4 条 RecipeManager 既有噪声（simplyswords ×2 / brewinandchewin / red_dye）。

## 4. 命令

```
/redtale socket                     # 主手物品的孔位 / 宝石 / 宝石数值
/redtale socket insert <0-5> <gem>  # 镶（旧宝石退回背包，满了丢脚下）
/redtale socket extract <0-5>       # 取出（宝石退回背包）
/redtale socket probe               # 端到端探测，不需要玩家/客户端
```

## 5. 下一步（R20）

1. 验收 T-0014（套装解析）/ T-0015（`/redtale validate`）；T-0014 的解析正确性用离线探针跑。
2. **M3 第 6 步**：`ItemContent.setId` → `ContentData.set_id` → 件数统计（护甲 4 + 主副手）→ 属性下发
   （固定 id `redtale_project:set/<setId>/<count>`，先移除再加）→ tooltip 套装行 → `/redtale set`；
   `sets/*.json5` 默认配置必须进 `DEFAULT_FILES`。
3. 挂账（需带客户端的窗口）：真人镶嵌后 tooltip 是否立刻变（服务端已 `broadcastChanges`，未在真人客户端验过）。
