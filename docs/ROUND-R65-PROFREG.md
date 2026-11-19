# R65 · D24 L2：`ProfessionRegistry` + 默认件（专业从「只有解析」到「有快照」）

日期：2026-11-19 11:13-11:4x · 测试服一次启停 · 构建锁取删 1 次

## 1. 本轮干了什么

1. **验收 T-0060**（`ProfessionSpec`，D24 L1）→ `[VERIFIED]`（3/3，见 §4）。
2. **派 T-0061**（`check_tasks.py`（T-0061 在建）：卡板只读自检），在制品 1/3。
3. **自研 L2**：`core/progression/ProfessionRegistry` + `RedtaleCore` 接线 + 两份默认件（`DEFAULT_FILES` 33→35）。
4. **修 `ProfessionSpec` 一个真缺陷**：显式 `mode: "geometric"` 被误报「未知模式」。

## 2. L2 做了什么（契约 §4.39）

| 职责 | 具体行为 | 为什么在这层 |
|---|---|---|
| 属性键归一 | 三种写法（裸名 / `redtale_project:` / 点号）过 `AttributeSpecs.resolve`，写成 `AttributeSpec.id()`；表外**丢弃 + 告警** | L4 下发时一律再 `resolve` 一次，但存档/日志/对账要**一种形态** |
| `professions` ↔ `jobs/` 重叠 | 按 path 比（`mining` ↔ `redtale_project:mining`），告警**不拦** | 「先有 mining 职业、再加 mining 专业」是正常运营动作（R64-d） |
| 快照 | `Map<String, Definition>` 不可变、保文件顺序 + `find` 走 path 归一 | L3/L4/L5 共用一个真相，不再各自解析 |

**没做的**（有意）：曲线正性（`ExpCurve` 已保证，写了是死分支，R65-a）、tag 存在性（datapack 数据）、掉落、事件、存档、命令。

## 3. 默认件

| 文件 | 演示什么 | 关键值 |
|---|---|---|
| `professions/mining.json5` | `mine` 的三种写法：精确 id / block tag / `default` | `poly 60/1.35` · 100 级 · `mining_efficiency +1%/级` |
| `professions/smelting.json5` | **一个专业吃两个源**（`smelt` + `craft`） | `geo 70/1.11` · 80 级 · `additional_experience +0.5%/级` |

## 4. 证据

### 4.1 验收 T-0060

| 项 | 结果 |
|---|---|
| `gradle build --offline` | RC=0，jar **649623 B**（S8 产物，本轮首构建复用） |
| jar 内类 | `com/redtale/project/core/progression/ProfessionSpec.class` = **1**（13095 B） |
| 探针 | `ProfessionSpecProbe 69/69`，零 FAIL |
| 行数 / 依赖 | `wc`=173 ≤200 · `net.minecraft`=0 · `AttributeSpecs`=0 |
| 口径复核 | 12 条逐条对照（值取整→负→1000 顺序、`resolve` 四档、双不可变、未知键告警）**零偏差** |

### 4.2 L2 实测（测试服 `Done (2.049s)`）

```
配置重载完成：2 条脚本，39 个文件，… ，1 棵技能树（10 个节点，21 点上限），2 个专业（2 条属性）。攻速：…
```
- 启动即落 `config/redtale_project/professions/{mining,smelting}.json5`，**零 `配置告警`**（假 geometric 告警已消失）。
- `/redtale reload` → `… + 2 个专业（39 份配置文件）`。
- 临时塞 `zz_overlap_probe.json5`（id 撞 `jobs/warrior` + 一条 `bogus_attr`）→ 专业数 3、**恰好 2 条告警**：
  `专业 redtale_project:warrior 的 id 与 jobs/ 里的职业 warrior 重叠（…）` / `属性 bogus_attr 不在 80 条属性表里，已丢弃`。
- 删文件再 reload → 回 2 个专业、零告警；`/redtale validate` 问题 0。
- 停服干净（`All dimensions are saved` + RCON 停止），25567/25577 无 LISTEN。

### 4.3 环境

`selftest` **102 PASS** · `ProfessionRegistryProbe` **38/38** · jar **655179 B** · 构建锁取删 1 次 · 测试服已停。

## 5. 本轮踩的坑（已进 §13.68）

1. **死分支**：我先按 PROFESSION §7 的字面写了「曲线需求经验非正 → 告警」，实测 `expForLevel(maxLevel)` 恒 0（顶级没有下一级）、`base ≤ 0` 被 `ExpCurve` 回落 100 —— 这条路永不可触发，删掉而不是留着。**永远绿的检查等于没有检查。**
2. **规范形态是裸 path**：`AttributeSpec.id()` 对自建属性返回 `mining_efficiency`，不是 `redtale_project:mining_efficiency`。探针第一版按全名断言，4 条假红；错的是期望（同 R43-a）。
3. **`expForLevel(maxLevel)` = 0** 这个事实本身值得记：任何「顶级需求经验」的判断都要用 `maxLevel - 1`。

## 6. 下一步（R66）

1. 验收 T-0061（`check_tasks.py`）。
2. D24 **L3**：`ProgressStore` v2→v3 + 三个事件接线 + `/redtale exp profession <id> <n>`（跨文件 + 存档版本，S7 独占）。
3. D24 **L4**：属性下发接进 `ProgressService` 的同一次重算（前缀 `profession/<专业 path>/`）。
4. 真人窗口（`REALPLAYER-WINDOW.md`）仍等 Aki；专业 P1~P5 待 L3 后并成 F 组。
