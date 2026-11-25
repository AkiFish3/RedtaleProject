# R130 · 验 T-0124 + check_all 68→69 + 派 T-0125（附魔代价字段形态透传）

日期：2026-11-26 05:3x~05:5x
在制品：1/3（T-0125）
构建：未构建（本轮纯 Python + 卡板 + 文档，无源码改动）
环境：`src/` 零 diff、未启停任何服、无构建锁；正式服 9376 未触碰、测试服 25567/25577 无 LISTEN。

## 1. 验收 T-0124（`check_enchant_definition_ints.py`）→ VERIFIED

S8 交付 `tools/check_enchant_definition_ints.py` 177 行（2026-11-26 02:54），三条验收逐条跑过。

**验收 1（基线）**：

```
$ python tools/check_enchant_definition_ints.py
共 1 份附魔定义整数字段口径对账表 · 0 条问题          EXIT=0
$ python tools/check_enchant_definition_ints.py -v
已检查 2 份附魔 · 4 处整数字段引用 · 字段 weight/anvil_cost · 非整数形态 float 计
共 1 份附魔定义整数字段口径对账表 · 0 条问题          EXIT=0
```

**验收 2（六夹具 A~F，干净 `enchantments/` 副本）**：

| 组 | 改动 | 结果 | EXIT |
|---|---|---|---|
| A | 默认副本 | 0 条；`-v` 2/4 | 0 |
| B | `soul_reap` `weight: 2` → `2.5` | 1 条 `[问题] enchantments/soul_reap.json5: weight 值 2.5 不是整数形态…` | 1 |
| C | `super_sharpness` `anvil_cost: 2` → `3.0` | 1 条 `… anvil_cost 值 3.0 不是整数形态…` | 1 |
| D | `soul_reap` `weight: "2"` | 0 条（字符串走生成器已有 WARN 省略路径） | 0 |
| E | 新增 `zz.json5` `weight: true` | 0 条（bool 不是 number） | 0 |
| F | B + C + `zz.json5` `anvil_cost: 1.5` | 3 条按 `soul_reap` → `super_sharpness` → `zz` 排序 | 1 |

**验收 3（静态 + 边界）**：`wc -l` = 177 ≤ 200；`def check_ints` = 1；禁词 grep = 0；`py_compile` OK；`--dir NO_SUCH` → 恰好 1 条 `[问题] enchantments 目录读取失败` + 末行 `1 条问题`、EXIT=1、不抛栈。

函数边界（直接 import 调 `check_ints`）：`2.5` / `3.0` / `1e3` 各 1 条（`repr(1e3)` = `1000.0`，文案内为 `1000.0`）；`2` / `True` / `"2"` / `None` / `[]` / 缺键 / data 非 dict 全 0 条；双键同时坏 → 2 条且 `sorted`（`anvil_cost` 在 `weight` 前）；重复两条不合并（2 条）；`_loads` 三探针（裸键 / 注释 / 非法）分别返回 dict / dict / `None`。夹具已删。

**判定**：S8 零偏差，不打回。`[DONE]` → `[VERIFIED]`。

## 2. 接线 `check_all` 68→69

`tools/check_all.py` 的 `SCRIPTS` 末尾追加一行 `check_enchant_definition_ints.py`（注释 `R130 接上`），位置在 `check_progress_base.py` 之后：

```
$ python tools/check_all.py | tail -2
共 69 个脚本 · 跑成 69 · 跳过 0 · 红 0                    EXIT=0
```

七门禁全 0：`check_all` 69/69、`check_tasks` 125 卡在制品 1、`check_contracts` 108 条、`check_docs` 145 份 / 602 引用、`check_rounds`、`check_ownership`、`check_checkall`。

## 3. 派前取证（T-0125：`min_cost` / `max_cost` 形态）

T-0124 的卡面与 `§4.107` 都明写「`min_cost` / `max_cost`（`Cost` 对象）形态另议，不进本卡」——本轮收口这一格。

**① 源码逐字**（`EnchantmentGenerator.java`）：

- `:39-40` `PASS_THROUGH` 含 `"min_cost", "max_cost"`（与 `max_level` / `weight` / `anvil_cost` 同一张表）。
- `:146-156` 循环体内只有 `numericKey(key)` 一条校验；`min_cost` / `max_cost` 不在其中。
- `:160-163` `numericKey` 只认 `"max_level"` / `"weight"` / `"anvil_cost"` 三键。
- 全文件 `grep min_cost` 仅命中 `:40` 一处 —— **没有任何递归/结构检查，整棵子树原样 `json.add(key, value)`**。

**② 项目口径**：`ENCHANT-API.md:114` 明确 `min_cost` / `max_cost` 形状为 `{base, per_level_above_first}`。所以「写成裸数字」是**违反本模组自己声明的形状**，不是值域问题。

**③ 旧尺盲区逐把核**：`grep -rn "min_cost\|max_cost" tools/*.py` → **零命中**（69 把尺全盲）；`check_enchants.py` 只核 `max_level` 整数与 `exclusive_set` / 脚本引用；`check_numeric.py` 不读 `enchantments/`。

**④ 基线计数**：`grep -rn "min_cost\|max_cost" src/main/resources/redtale/defaults/` → 4 行 = 2 文件 × 2 键 → **2 文件 / 4 处 / 0 条问题**。

**⑤ 派前预验（对账网第五十二例）**：整份 defaults 拷到 `build/enchcost_s7/`，把 `soul_reap` `min_cost` 改成裸数字 `8`、`super_sharpness` `max_cost.base` 改成 `25.5`，五把最可能抓到它的旧尺逐字全绿：

| 尺 | 输出 | EXIT |
|---|---|---|
| `check_enchants.py` | `共 2 个文件 · 没有发现问题 ✓` | 0 |
| `check_numeric.py` | `共 4 个文件 · 0 条问题` | 0 |
| `check_enchant_definition_ints.py` | `共 1 份附魔定义整数字段口径对账表 · 0 条问题` | 0 |
| `check_enchant_exclusive.py` | `共 2 份附魔互斥引用表 · 0 条问题` | 0 |
| `check_enchant_args_sync.py` | `共 1 份附魔脚本参数对账表 · 0 条问题` | 0 |

洞成立：改错形态后整族零提示。副本已删。

**运行期后果标「推定」**：裸数字/小数会被原版 `EnchantmentDefinition` 的 `Cost` codec 拒绝 → 整条附魔不进注册表。本卡不把它当验收项（R129-c 同源），真机复现归 S7。

## 4. 派单 T-0125（在制品 1/3）

`check_enchant_cost_forms.py`：`COST_KEYS = ("min_cost", "max_cost")`、`FIELDS = ("base", "per_level_above_first")`、`SUBDIR = "enchantments"`；`check_costs(rows) -> list[str]` 三形态文案（非对象 / 缺字段 / 字段非整数），`<v>` 用 `repr`；末行 `共 1 份附魔代价字段口径对账表 · N 条问题`；六夹具 A~F 含「缺 `per_level_above_first`」（D）与「字符串 `base`」（E，`repr` 带引号）；夹具目录 `build/enchcostfix/`。

卡面把「缺字段判红」标明是**项目口径**（`ENCHANT-API.md:114` 定义形状），原版是否放宽标「未验证」——避免 S8 把它当原版能力断言。

## 5. 本轮口径（§13.133）

- **R130-a**：上一轮卡面显式写「另议」的格子，下一轮优先收口 —— 那是 S7 自己留的债，不是新发现，验收人对它已有完整上下文，成本最低。
- **R130-b**：「违反项目自己声明的形状」可以单独成尺，不必等原版真值：`ENCHANT-API.md:114` 就是项目内的契约，与 `Cost` codec 是否严格无关。
- **R130-c**：缺字段这类「可能被上游宽容」的判据，卡面必须标注「项目口径 / 原版未验证」，否则 S8 会把内部约定当外部事实，后续真机翻车时无法归因。
- **R130-d**：派前预验要覆盖「整族」而非「最像的那一把」：本轮跑五把 enchant 系尺，才能说「整族零提示」（R129-d 的加强版）。
- **R130-e**：裸名纪律第四十八次 —— 文档只写裸名（`check_enchant_cost_forms.py` / `EnchantmentGenerator.java` / `soul_reap.json5`），不写 `tools/` 全路径、不写探针目录。

## 6. 下一步

1. 验 T-0125（过后 `check_all` 69→**70**）。
2. M6 内容面 8/8 已收口；代码面剩「模板实体注册 / 自然刷怪替换」（S7 独占、跨文件，不进 S8 卡）。M7 代码面已实现，缺真机验证（升级 / 加点 / 洗点），需 Aki 在线。
3. 池内可派候选已空（C72 按 R128-c 留池、C14 为观察项）。下一轮继续按 R129-a「静默放行」扫：`slots` / `supported_items` 形态、`CommandSpec` 参数解析是候选（未取证，不下结论）。
4. Q-021 / Q-022 / Q-023 无答复继续按默认走；真人窗口 A~F 等 Aki。
