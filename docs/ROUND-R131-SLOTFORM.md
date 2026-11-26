# R131 · 验 T-0125 + check_all 69→70 + 派 T-0126（附魔 slots 形态透传）

日期：2026-11-26 08:3x~08:5x
在制品：1/3（T-0126）
构建：未构建（本轮纯 Python + 卡板 + 文档，无源码改动）
环境：`src/` 零 diff、未启停任何服、无构建锁；正式服 9376 未触碰、测试服 25567/25577 无 LISTEN。

## 1. 验收 T-0125（`check_enchant_cost_forms.py`）→ VERIFIED

S8 交付 157 行（2026-11-26 07:00），三条验收逐条跑过。

**验收 1（基线）**：

```
$ python tools/check_enchant_cost_forms.py
共 1 份附魔代价字段口径对账表 · 0 条问题                    EXIT=0
$ python tools/check_enchant_cost_forms.py -v
已检查 2 份附魔 · 4 处代价字段引用 · 字段 min_cost/max_cost · 形态 base/per_level_above_first 整数
共 1 份附魔代价字段口径对账表 · 0 条问题                    EXIT=0
```

**验收 2（六夹具 A~F，干净 `enchantments/` 副本）**：

| 组 | 改动 | 结果 | EXIT |
|---|---|---|---|
| A | 默认副本 | 0 条；`-v` 2/4 | 0 |
| B | `soul_reap` `min_cost: 8` | 1 条 `… min_cost 值 8 不是 Cost 对象…` | 1 |
| C | `super_sharpness` `max_cost.base: 25.5` | 1 条 `… max_cost.base 值 25.5 不是整数形态…` | 1 |
| D | `soul_reap` `min_cost: { base: 8 }`（删 `per_level_above_first`） | 1 条 `… min_cost 缺 per_level_above_first…` | 1 |
| E | `super_sharpness` `min_cost.base: "5"` | 1 条 `… min_cost.base 值 '5' 不是整数形态…` | 1 |
| F | B + C + `zz.json5` `min_cost {1.5, "2"}` | 4 条按 `soul_reap` → `super_sharpness` → `zz.min_cost.base` → `zz.min_cost.per_level_above_first` | 1 |

> 首轮 C/F 我自己的夹具写错：两份默认文件里 `max_cost` 是**单行内联** `max_cost: { base: 25, per_level_above_first: 12 },`，不是多行块，替换字符串没命中 → C 出 0 条、F 出 3 条。改按整行内联替换后逐字对上。**是 S7 的夹具错，S8 无偏差。**

**验收 3（静态 + 边界）**：`wc -l` = 157 ≤ 200；`def check_costs` = 1；禁词 grep = 0；`py_compile` OK；`--dir NO_SUCH` → 恰好 1 条 `[问题] enchantments 目录读取失败` + 末行 `1 条问题`、EXIT=1、不抛栈。

函数边界（直接 import 调 `check_costs`，15 组）：非对象 `8` / `2.5` / `"x"` / `[]` / `True` 各 1 条；`{base, per_level_above_first}` 完整 0 条；`{base: 8}` 1 条；`per_level_above_first: 10.0` 1 条；`base: True` 1 条；`per: 1e3` 1 条且 `repr` = `1000.0`；缺键 / data 非 dict 0 条；两键都坏 2 条；重复不合并；输出 `sorted`。15/15 通过，夹具与 pyc 已删。

**判定**：S8 零偏差，不打回。`[DONE]` → `[VERIFIED]`。

## 2. 接线 `check_all` 69→70

```
$ python tools/check_all.py | tail -2
共 70 个脚本 · 跑成 70 · 跳过 0 · 红 0                    EXIT=0
```

S8 交付时 `check_all` 红 1（`check_checkall` 报新脚本未进清单）——**这是卡面「禁止接线」的预期副产物**，验收后由 S7 接上即归零，不是 S8 的问题。

七门禁全 0：`check_all` 70/70、`check_tasks` 126 卡在制品 1、`check_contracts` 109 条、`check_docs` 147 份、`check_rounds`、`check_ownership`、`check_checkall`。

## 3. 派前取证（T-0126：`slots` 形态）

**① 源码逐字**（`EnchantmentGenerator.java`）：`:39-40` `PASS_THROUGH` 含 `"slots"`；`:146-156` 循环体只有 `numericKey(key)` 一条校验；`:160-163` 只认 `max_level` / `weight` / `anvil_cost` 三键 → **`slots` 零类型检查、零元素检查，整棵子树原样 `json.add`**。

**② 项目口径**：`ENCHANT-API.md:112` 定义 `slots` 为 `EquipmentSlotGroup` 名数组（示例 `["mainhand"]`）。

**③ 旧尺盲区**：`check_enchants.py:116-118` 只核 `supported_items`（要求字符串）/ `description` / `max_level`，**不读 `slots`**；`check_numeric.py` 不读 `enchantments/`；四把附魔尺分别只读 `weight`+`anvil_cost`、两个 `cost`、`exclusive_set`、`scripted.args`。**六把尺零覆盖**。

**④ 基线**：两份默认文件各 1 处 `slots: ["mainhand"],` → **2 文件 / 2 处 / 0 条问题**。

**⑤ 派前预验（对账网第五十三例）**：整份 defaults 副本把 `soul_reap` 改成 `slots: "mainhand"`（字符串）、`super_sharpness` 改成 `slots: []`（空数组），六把 enchant 系尺逐字全绿：

| 尺 | 输出 | EXIT |
|---|---|---|
| `check_enchants.py` | `共 2 个文件 · 没有发现问题 ✓` | 0 |
| `check_numeric.py` | `共 4 个文件 · 0 条问题` | 0 |
| `check_enchant_definition_ints.py` | `0 条问题` | 0 |
| `check_enchant_cost_forms.py` | `0 条问题` | 0 |
| `check_enchant_exclusive.py` | `0 条问题` | 0 |
| `check_enchant_args_sync.py` | `0 条问题` | 0 |

洞成立。运行期后果（codec 丢弃整条附魔）标「推定」，不进验收项。副本已删。

## 4. 派单 T-0126（在制品 1/3）

`check_enchant_slot_forms.py`：`SLOT_KEY = "slots"`；`check_slots(rows)` 三坏形态（非数组 / 空数组 / 元素非非空字符串，序号 1 起）；末行 `共 1 份附魔槽位字段口径对账表 · N 条问题`；六夹具 A~F（含 `["mainhand", 1]` 与 `["  "]`）；夹具目录 `build/enchslotfix/`。

**明确不核槽位枚举名**：`mainhand` / `hand` / `armor` 这一层全表项目文档没冻结，硬编码就是替上游说话。本卡只判形态，枚举交原版 codec 并标「未验证」。

## 5. 本轮口径（§13.134）

- **R131-a**：**夹具错先怀疑自己**：C/F 两组首轮不出，根因是默认文件 `max_cost` 为单行内联而我按多行块替换。凡「S8 自测 3/3 全过、S7 复跑某组不出」的场合，先逐字比对默认文件原形态，再怀疑实现（与 R128-a「卡面矛盾以邻居真实形态为准」同源）。
- **R131-b**：「卡面禁止接线 → `check_all` 红 1」是**设计内的红**，验收时由 S7 接上归零；不因此判 S8 不通过。
- **R131-c**：**枚举名不硬编码**：`slots` / `damage_type` 这类上游枚举，项目文档没有冻结全表时，尺只判形态。硬编码半张表会让尺变成第二份真相（R128-c 同源）。
- **R131-d**：同一生成器循环里被 `numericKey` 漏掉的键是一族：`min_cost` / `max_cost`（T-0125）与 `slots`（T-0126）连收两格后，`PASS_THROUGH` 十键里只剩 `supported_items`（有尺，但只认字符串）/ `primary_items`（零基线）/ `exclusive_set`（有尺）/ `effects`（args 有尺）四个未收口点，下轮按「零基线不派」过滤。
- **R131-e**：裸名纪律第四十九次 —— 文档只写裸名，不写 `tools/` 全路径、不写探针目录。

## 6. 下一步

1. 验 T-0126（过后 `check_all` 70→**71**）。
2. `PASS_THROUGH` 剩余点：`primary_items` 零基线（不派）、`supported_items` 有尺但只认字符串（数组形态会被现有尺误报，属「旧尺过严」另议）。**下轮需新挖候选**：`skills/` 的 `cooldown` / `mana_cost` 透传、`CommandSpec` 参数解析（未取证）。
3. M6 代码面剩「模板实体注册 / 自然刷怪替换」（S7 独占、跨文件）。M7 真机验证（升级 / 加点 / 洗点 / tooltip 显示）需真人客户端，见 §7。
4. 真机/客户端验收可行性：`.minecraft` 下 `versions` 与 `mods` 均为空（`ls` 实测 0 项），**当前无可用客户端**；computer use 能驱动桌面，但驱动不了一个不存在的客户端。控制台侧验收（启停测试服 + RCON + 日志 + `mob list` / `attr` / `reload`）已全部由 bash + RCON 完成，不需要图形界面。
