# R118 · 验 T-0112 + check_all 60→61 + 派 T-0113（技能树 icon 静默回落）

时间：2026-11-24 02:2x · 队列：S7 · 里程碑：M7/M3 对账网（第 44 例）

## 1. 卡板

- **T-0112 `check_affix_levels.py` → `[VERIFIED]`**：三条验收全跑 + S7 独立夹具/函数边界全过。
  - 基线：`共 1 份词条等级口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 3 份词条定义 · 6 处 item_level 引用 · 截断 (int) · 静默零 WARN`。
  - 六夹具 A/B/C/D/E/F：EXIT=0/1/1/1/0/1，末行计数与 `max` 先于 `min` 的排序逐条逐字一致。
  - 静态：wc=161 ≤200、`def check_levels`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条读失败 EXIT=1。
  - 函数边界 27/27：截断 4 形态路径逐字；数学整数 / 域外 / 非数字 / bool / null / nan / inf 全跳过；`lo>hi` 整段跳过；重复两条不合并。
  - S7 探针首轮自错：`1.7` 用例的 `max` 也写成 `60.2` → 得 2 条而非预期 1 条；按 R65-a/R114-a 先修探针，S8 零偏差不打回。夹具已清。
- **已接 `tools/check_all.py` TOOLS 60→61**：接线前 `check_checkall.py` 报漏接 1 条，接线后 `共 1 份清单 · 0 条问题`。
- **派 T-0113 `check_tree_icons.py`**（单文件零接线，六夹具，接口/文案/跳过规则由 S7 定死），在制品 1/3。

## 2. 派前取证（四判据 + 全类排除）

候选 = `skill_trees/*.json5` 顶层 `icon`。

| 判据 | 证据 |
|---|---|
| ① 静默出口 | `SkillTreeSpec.java:73` `String icon = stringOf(root.get("icon"));`；`:74` `if (icon == null || icon.trim().isEmpty()) icon = "minecraft:stone";`；`:145-147` `stringOf()` 仅接受字符串 primitive，其余返 `null`，全路径零 WARN。 |
| ② 旧尺全绿 | `check_trees.py:45-125` 逐节点只核 `requires`/`cost`/`effects`/`classes`/`max_points`；`check_tree_reach.py` 只读 `requires`；`check_tree_ppl.py` 只读 `points_per_level`；全 60 把尺零读 `icon`。 |
| ③ 常量可冻结 | 缺省 `"minecraft:stone"` 具名写在 `SkillTreeSpec.java:74`，`stringOf()` 的「非字符串即 null」语义确定。 |
| ④ 基线非空 | 1 份默认技能树写 `icon: "minecraft:iron_axe"` = 1 处显式引用，0 问题。 |

**全类排除（R118 全量扫描 120 Java / 36 json5 / 60 尺）**：

- 三处显示面真洞（`items[].name` — `ItemContent.java:162,240-246` 数字/布尔 primitive 经 `getAsString()` 成串；`skill_trees[].name` — `SkillTreeSpec.java:76` 非字符串变 `null`；`skill_trees[].nodes[].name` — `:54-55` 空白/非字符串静默回落 `tree.node.<id>`）：四判据齐但均显示面，入池 C66，不与 icon 合并。
- `affix_pools[].slots.*`：`check_items.py:111-114` 已用 `_int` 逐键核，反事实 `prefix: 1.7` 会红 → 排除。
- `items[].rev`：`ItemContent.java:136` 确有 `(int)`，但 `check_items.py:86` 已核整数形态 → 排除。
- `items[].strip_vanilla_effects`：`bool()` 静默回落 `false` 且无尺，但 0 份默认声明 → 判据③失败，留池。

## 3. 口径

- **R118-a**：数值洞收口才转显示面。T-0111/T-0112 已把同一 `integer()` 出口的数值字段收口；继续留池会变成「为留而留」，按 R117 下一步转派。
- **R118-b**：显示面仍须单独卡。C62 icon 一张；C66 三处 `name` 另排队；显示面内部也按对象分卡。
- **R118-c**：夹具期望先疑自己。S7 自错按 R65-a/R114-a 处理，不打回 S8。
- **R118-d**：空基线不派。`strip_vanilla_effects` 与 C58 同为四判据否决，但否决理由不同（前者无基线，后者旧尺已覆盖）。
- **R118-e**：裸名纪律第三十六次：派单当轮文档与台账在建脚本只写裸名，避免文档路径检查把未建文件当断链。

## 4. 环境

`src/` 零 diff；本轮只动 Python 与文档，未跑 gradle、未启停任何服、无构建锁；正式服未动，测试服 25567/25577 无 LISTEN。
