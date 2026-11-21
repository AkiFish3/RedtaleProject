# R96 · 2026-11-22 · 验 T-0091 + 接线 39→40 + 派 T-0092（套装档位装备槽可达性）

## 验收（T-0091，3/3 PASS）

| 项 | 期望 | 实测 |
|---|---|---|
| 基线 | `共 1 份词条等级可达性表 · 0 条问题` EXIT=0 | 逐字一致 EXIT=0 |
| `-v` | `已检查 3 份词条定义 · 1 个词条池 · 2 个有效槽位段 · 物品等级 1` | 逐字一致 |
| 六夹具 A~F | EXIT=1/0/1/1/0/1 | 逐字一致（A/C/D 各 1 条 `词条池 p 的 prefix 槽无等级可达的同类型词条（物品等级 1）`） |
| 静态 | wc≤200、`def unreachable`=1、禁用 grep=0、py_compile OK、`--dir NO_SUCH` 1 条读失败 | 158 / 1 / 0 / OK / 1 条 EXIT=1 |

S7 函数边界 21/21：ident 五种、covers 的 bool/NaN/Inf/字符串、unreachable 的槽 0/bool/float/缺引用段/排序、双目录读失败。夹具各自从干净目录重来，`build/affixreachfix/` 已清。S8 零偏差，不打回。

## 派单（T-0092，在制品 1/3）

`check_set_reach.py`：sets 的 `bonuses[].count` ↔ pieces 覆盖的装备槽数。接口 S7 定死：`SLOT_OF` / `ident` / `read_items` / `read_sets` / `reachable_slots` / `unreachable` / `main`。基线 1 套 / 2 件 / 2 档 / 2 槽 / 0 问题；六夹具 A~F；单文件零接线，VERIFIED 后 R97 接 `check_all`（应 `跑成 41`）。

真洞证据链：
- `check_items.check_set` 只核 `count ≤ len(pieces)`；`check_items.check_item` 只核 `base ∈ 14 基底`。
- `SetBonus.counts()` 只统计 6 个槽里带 `ContentData.setId()` 的不同内容 id；同内容两件只算一件。
- `SetSpec.activeBonuses()` 只返回 `count <= wornPieces` 的档；够不到的档零 WARN / ERROR。
- `sets/emberforged.json5` 注释已写明 demo_robe 故意不进池（同胸甲基底会造 3 件档永远到不了），但无机器尺子。

派前全类排除：`skill_trees.points_per_level`（基线单字段，卡薄）、`affix_pools.applies_to` 无实物（池空转，退化弱）、`repair.targets`（空，永远绿）、`spawn_rules.match`（离线无注册表）、`passive.id`（死代码）。

**源码更正**：Explore 初稿把 amulet 当独立槽；`ItemBase` 明写 `RING/AMULET -> MAINHAND`。冻结表按源码：14 键 6 槽。

## 本轮其他

- 接线 `check_all` 39→40；全套门禁 0 问题。
- `src/` 零 diff、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服 25567/25577 无 LISTEN。
- 裸名纪律第十四次：在建脚本只写裸名 `check_set_reach.py`。
