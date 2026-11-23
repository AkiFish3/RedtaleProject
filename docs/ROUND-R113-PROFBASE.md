# R113 · 2026-11-23 07:5x · 验 T-0107 + 接线 `check_all` 55→56 + 派 T-0108（专业曲线 base 静默改写/夹顶）

## 1. 卡板动作

- T-0107 `check_tree_ppl.py` `[DONE]` → **VERIFIED**。三条验收全过，S8 零偏差不打回。
- `check_all` TOOLS 增加 `check_tree_ppl.py`，接线后 `共 56 个脚本 · 跑成 56 · 跳过 0 · 红 0`。
- 派 T-0108 `check_profession_base.py`：专业曲线起点 base 的零值静默回落与超硬顶静默夹顶对账，单文件零接线；在制品 1/3。

## 2. T-0107 验收证据（S7 独立复跑）

| 项 | 实测 | 判定 |
|---|---|---|
| 基线 | `共 1 份技能树点数口径对账表 · 0 条问题` EXIT=0 | 通过 |
| 基线 `-v` | `已检查 1 棵树 · 1 处 points_per_level 引用 · 缺省 1` | 逐字 |
| 六夹具 | A~F EXIT=0/1/0/1/1/0；B `1.5`、D `'2'` 各 1 条；E 两条按 a/b 升序（a.1.5 / b.True）；F `0`/`-1` 全放行且 `-v` 2/2 | 逐字 |
| 静态 | `wc=164` ≤200；`def check_ppl`=1；禁用 grep=0；`py_compile` OK | 通过 |
| 异常目录 | `--dir NO_SUCH` → 1 条 `skill_trees目录读取失败`、末行 1 条问题、EXIT=1、不抛栈 | 通过 |
| 函数边界 | `1.5` / `'2'` / `True` / `None` / `nan` / `inf` 各 1 条；`-1` / `0` / `2.0` / `4` / 缺键跳过；重复 `1.5` 两条不合并 | 逐字 |

探针与 pyc 已清；`src/` 零 diff，未构建、未启停任何服、无构建锁。

## 3. S7 最小一步：全类排除 + 源码逐字核实 + 干净探针反事实

### 3.1 `curve.base` 真洞（对账网第 39 例）

| 判据 | 逐字证据 |
|---|---|
| ① 出口/语义可冻结 | `ProfessionSpec.java:102` `base = num(co, "base", 100.0, 0.0, path, w);`；`:109-116` `if (!num || !Double.isFinite(v) || v < min) { warn(...); return dflt; }`；`ExpCurve.java:8` `MAX_STEP_EXP = 1_000_000_000L`、`:19` `this.base = base > 0.0 ? base : 100.0;`、`:43-45` `return value > MAX_STEP_EXP ? MAX_STEP_EXP : value;` |
| ② 静默零 WARN | `v < min`（非 `<=`）让 `base: 0` 解析阶段原样返回 `0.0`；`ExpCurve.java:19` 随即静默改成 100。大值只判 `Double.isFinite`，`1e300` 一路放行到 step exp 静默夹顶。对照 `ProgressSpec.java:130` 的 `v <= min` 对 0 会 WARN——符号差异就是根因 |
| ③ 旧尺全绿 | `check_professions.py:56-72` 的 curve 段只读 `mode`/`growth`/`exponent`；`check_progress.py:137-167` 只扫 `levels/`/`jobs/`；`check_numeric.py:6` 只扫四份数值文件；`grep -rn 'curve.*base\|base.*curve' tools/*.py` 零命中。每形态独立干净探针目录写歪 `base`（`0` / `1.5` / `-5` / `"x"` / `1e300`）后 `check_professions.py` 全部 `共 1 个文件 · 0 条问题` EXIT=0，报错集合逐字相同 |
| ④ 基线非空 | `mining.json5:10` `base: 60.0`、`smelting.json5:9` `base: 70.0` → 2 份专业 / 2 处引用 / 0 问题 |

新尺语义：显式 0 与 `> MAX_STEP_EXP` 报，非数字 / bool / 负 / 非有限 / 缺键跳过（已有 WARN 或合法缺省），只认 `curve.base`，不重实现整段 curve 检查（承接 R112-d 的同族拆分，落 R113-a）。

### 3.2 排除的其它候选

- `curve.mode` 非法枚举：`ProgressSpec.java:72-73` / `ProfessionSpec.java:101` 均有 WARN，不满足「零 WARN」。
- `effect_attack_speed.minecraft:haste` 非零：`AttackSpeedRules.java:118-125` 虽静默忽略，但 `check_numeric.py:59-61` 已在役尺要求必须为 0。
- `skill_trees[].exclusive_with`：`SkillTreeSpec.java:23,25` 列为已知键、运行期无读取（零 WARN），但默认配置无基线，①不成立 → 留池。
- `skill_trees[].icon`（`SkillTreeSpec.java:73-74,145-147` 静默回落 `minecraft:stone`）与 `items[].durability`（`ItemContent.java:101,257-267` `(int)` 静默截断）四判据齐但落点次优先：前者仅显示面，后者 T-0096 曾明确留白。两条进池 C60/C61，不合并成一卡（R113-d）。

### 3.3 探针坑（R113-c）

首轮把五种 base 形态写进同一个探针目录，`check_professions.py` 的 id 重复定义误报盖过真实结果；改成每形态独立干净目录后，五形态逐字全绿。与 R65-a 同类坑，已写进口径表。

## 4. 门禁与环境

- 七门禁：接线条目前 `check_checkall` / `check_tasks` / `check_contracts` / `check_docs` / `check_rounds` / `check_ownership` 全 0；收尾再跑一次。
- 环境：`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服未动，测试服端口无 LISTEN。裸名纪律第三十一次。
