# R140 · 2026-11-28 06:5x~07:1x · 验 T-0134（护甲词条池，四个防具基底各 2 条词条真机首证）+ 派 T-0135（词条池段名 ↔ `kind` 一致性尺）

## 0. 一句话

T-0134 `[DONE]` → `[VERIFIED]`（3/3，S8 零偏差）：`reload` 57 → **62 份**，四个防具基底 `tooltip` 全部摇出 **2 条**词条；顺手挖出「池段名与词条 `kind` 不一致被 `AffixRoller` 静默丢掉」这条零覆盖洞（六把尺反事实全绿），派 T-0135。

## 1. 卡板：T-0134 `[DONE]` → `[VERIFIED]`

### 1.1 静态三条（逐字复跑）

| 验收 | 命令 | 卡面期望 | 实测 |
|---|---|---|---|
| 1 | `check_affix_reach -v` | `已检查 12 份词条定义 · 2 个词条池 · 5 个有效槽位段 · 物品等级 1` / `共 1 份词条等级可达性表 · 0 条问题` | 逐字一致 |
| 1 | `check_affix_pool_refs -v` | `2 份词条池 · 12 条词条定义 · 12 条词条引用` / 0 | 逐字一致 |
| 1 | `check_affix_weights` / `check_affix_levels` | 各 `0 条问题` | 各 0 |
| 2 | `check_defaults` | `清单 58 条 · 磁盘 58 个 · 问题 0 条` | 逐字一致 |
| 2 | `check_items` / `check_numeric` / `check_all` | `28 个文件 · 0 条` / 0 / `74 跑成 74 · 跳过 0 · 红 0` | 逐字一致 |
| 3 | `wc -l` ×5 | `11/11/11/11/9`（各 ≤20） | 逐字一致 |
| 3 | `git status --porcelain src/` | 恰 5 条 `??` + 1 条 ` M` | 逐字一致 |
| 3 | `git diff --numstat ConfigStore.java` | `5	0` | 逐字一致 |
| 3 | 禁词 grep | `0` | 0 |

`ConfigStore` 的 5 行插入位置在 R139 那批（`affixes/runed.json5`）之后、`affix_pools/sword_common.json5` 之前，顺序 `plated → sturdy → vital → of_the_bulwark → armor_common`，与卡面逐字一致。

### 1.2 构建 + 真机（S7 跑的）

- `gradle build --offline`（取锁 → 构建 → 删锁）EXIT=0，jar **701476 B**（R139 基线 699235 B，+2241 B）。
- 测试服 `Done (2.371s)`；`/redtale reload` = **62 份配置文件**（57 → 62，+5 逐字对上）。
- `/redtale validate` → `校验：15 件内容 / 2 个词条池 / 12 条词条定义 / 2 个套装 · 技能 4 条 · 问题 0 条（外部 weapon_attributes 引用 3 条未校验）`。
- 停服：`stop` → `Stopping the server` → 25567 / 25577 无 LISTEN（仅 TIME_WAIT 收尾）。

### 1.3 四个防具基底真机首证（本卡真正的出口）

`/redtale tooltip demo_chestplate` 连摇 8 次，**8/8 都是 2 条词条**（prefix 1 + suffix 1），四种新词条全部出现过：

```
sturdy · vital          sturdy · of_the_bulwark     plated · vital
plated · of_the_bulwark sturdy · of_the_bulwark     plated · of_the_bulwark
```

另外三个基底各一次，逐字：

```
demo_helmet    词条 2：plated · vital          套装 灰烬誓约 0/3
demo_greaves   词条 2：plated · vital          套装 灰烬誓约 0/3
demo_boots     词条 2：sturdy · of_the_bulwark 套装 灰烬誓约 0/3
```

武器池零回归：`demo_longsword` 仍是 `词条 3：keen · of_ruin · runed` + `≈ 10.096 /次` + `攻速 12.5 tick · 1.6 次/秒`。

本轮是**「只新增文件」类内容卡**（R139-b）：5 份文件全部由 `ensureDefaults` 自动补种，不需要手工同步 live —— 与 R139 那次「改已有文件」的坑正好互为对照。

## 2. 派 T-0135（`check_affix_kind_sync.py`，词条池段名 ↔ `kind` 一致性）

**为什么派**：本卡验收后清理池内容时顺手查了 `AffixRoller` 的三段抽签实现 —— 段名是**过滤条件**而不是**标签**：

```java
// AffixRoller.java:104-106
for (int i = 0; i < kinds.length; i++)
    for (int n = 0; n < slots[i]; n++)
        AffixPools.Def chosen = pick(pool, defs, kinds[i], ...);
// :140
if (!def.kind().equals(kind) || entry.weight() <= 0) { continue; }
```

`:140` 的 `continue` **不计数、不打日志**（只有「引用了未定义词条」才进 `deadEntries`，见 `:119-121`）。后果：池里把 `vital`（suffix 类）写进 `prefix` 段 → 该段候选为空 → `:107-108` `break`，槽位**默默留空**，玩家只看到「这件装备少一条词条」，服端零 WARN。

**六把已有尺零覆盖**：`check_affix_pool_refs` 只核「段内 id 在不在定义集」、`check_affix_reach` 只核「段数 + 等级可达」、`check_items` / `check_numeric` / `check_affix_weights` / `check_affix_levels` 均不读「段名 vs `kind`」。

**派前反事实**（`build/affixkind_s7/`，整份 `defaults/` 副本，`armor_common` 的 `prefix` / `suffix` 段名互换）：

| 尺 | 反事实结果 |
|---|---|
| `check_affix_pool_refs` | `共 1 份词条池引用表 · 0 条问题` EXIT=0 |
| `check_affix_reach` | `共 1 份词条等级可达性表 · 0 条问题` EXIT=0 |
| `check_items` | `共 28 个文件 · 0 条问题` EXIT=0 |
| `check_numeric` | `共 4 个文件 · 0 条问题` EXIT=0 |
| `check_affix_weights` / `check_affix_levels` | 各 `0 条问题` EXIT=0 |

六把尺逐字全绿 → 对账网**第五十八例**。副本用后已删。

**卡面已写死**：七个函数签名（`read_def_kinds` / `read_pools` / `mismatches` / `_files` / `_load` / `main`）、唯一一条判据文案、四条「不核」边界（未定义 id / 数值 / `slots` 匹配 / `kind` 非法）、基线 `2 池 · 12 定义 · 12 引用 · 0 问题`、六夹具期望（A 0 / B 1 / C 1 / D 1 / E 2 且有序 / F 0）。**不接线** `check_all`，接线留 R141。

## 3. 口径 §13.143（R140 五条）

| 编号 | 口径 |
|---|---|
| R140-a | **池段名是过滤条件不是标签**：`prefix` / `suffix` / `core` 三个段里放错 `kind` 的条目被静默丢弃（`AffixRoller.java:140` 零 WARN），表现为「少一条词条」而不是报错。内容卡写池时必须「段名 = 词条 `kind`」。 |
| R140-b | **验收要看「每个基底」，不能只看一件**：`armor_common.applies_to` 四个 id，只摇 `demo_chestplate` 会漏掉「另外三个基底 `applies_to` 写歪」。本轮四个基底各跑一次，成本 4 条 RCON。 |
| R140-c | **「只新增文件」类内容卡不必手工同步 live**：5 份全是新文件 → `ensureDefaults` 自动补种，`reload` 62 份即证明进内存；与 R139「改已有文件」类必须手工同步形成对照，派卡时写明属于哪类。 |
| R140-d | **反事实优先选「语义错但形态对」**：本轮把两个段名互换（JSON 结构完全合法、字段名全在枚举里），比「写个非法字符串」更能证明旧尺真的没核这件事。 |
| R140-e | **裸名纪律第五十八次**：只写裸名，不写 `tools/` 全路径。 |

## 4. 环境

- 构建 1 次（取锁 → `gradle build --offline` EXIT=0 → 删锁），无残留锁。
- 测试服 **1 启 1 停**：`Done (2.371s)` → `stop` → RCON 停、chunks saved；25567 / 25577 无 LISTEN。
- **正式服未触碰**（25565 全程无监听）。
- 部署：`mods/redtale_project-0.1.0.jar` 701476 B（旧包 `.r138.bak` 留在 mods 目录，未动）。
- 夹具 `build/affixkind_s7/` 用后删除（`rm -rf` 前已确认路径）。
- 本轮 `src/` 改动为 T-0134 那 5 新 + 1 改，验收后已合流入库。

## 5. 下一步（R141）

1. **验 T-0135**（词条段名 ↔ `kind` 尺）→ 通过后接 `check_all` **74 → 75**。
2. **M7 真机进服**：`rt-test` → 多人游戏 → 直接连接 `127.0.0.1:25567`，需 Aki 在线或会话桌面可交互；`quickPlayMultiplayer` 不再尝试（R132 已证被忽略）。
3. Q-021 ~ Q-025 等 Aki；Q-025（护甲 tooltip 自身属性行）是显示面，与词条数值面不冲突，仍按「默认 A 不动」挂账。
