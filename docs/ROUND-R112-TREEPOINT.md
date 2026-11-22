# R112 · 2026-11-23 06:4x · 验 T-0106 + 接线 `check_all` 54→55 + 派 T-0107（技能树 points_per_level 整数形态）

## 1. 卡板动作

- T-0106 `check_drop_values.py` `[DONE]` → **VERIFIED**。三条验收全过，S8 零偏差不打回。
- `check_all` TOOLS 增加 `check_drop_values.py`，接线后 `共 55 个脚本 · 跑成 55 · 跳过 0 · 红 0`。
- 派 T-0107 `check_tree_ppl.py`：技能树顶层 `points_per_level` 整数形态对账，单文件零接线；在制品 1/3。

## 2. T-0106 验收证据（S7 独立复跑）

| 项 | 实测 | 判定 |
|---|---|---|
| 基线 | `共 1 份掉落数值边界口径对账表 · 0 条问题` EXIT=0 | 通过 |
| 基线 `-v` | `已检查 1 张掉落表 · 1 处 xp 引用 · 3 处 min 引用 · 3 处 max 引用 · 边界 xp[0,1000000] count[0,64]` | 逐字 |
| 六夹具 | A~F EXIT=0/1/1/1/1/0；B `xp 值 1000001`、C `drops[0].min 值 -1`、D `drops[0].max 值 65` 各 1 条；E 三条按 a.min / a.xp / b.max 升序；F 2 表 2/2/2/2 全绿 | 逐字 |
| 静态 | `wc=156` ≤200；`def check_values`=1；禁用 grep=0；`py_compile` OK | 通过 |
| 异常目录 | `--dir NO_SUCH` → 1 条 `drops目录读取失败`、末行 1 条问题、EXIT=1、不抛栈 | 通过 |
| 函数边界 | `1000001.0` / `65.0` / `-1.0` 三条 repr 带 `.0`；`True` / `"1"` / 缺键跳过；重复 `65` 两条不合并；下标 4/5 正确 | 6/6 |

探针与 pyc 已清；`src/` 零 diff，未构建、未启停任何服、无构建锁。

## 3. S7 最小一步：C58 反证排除 + C53 四判据升级

### 3.1 C58 `drops[].chance` **不派**（反事实不成立）

卡面原假设是「旧尺全不读 chance」。逐字复核推翻：

- `check_mobs.py:123-125`：`ch = row.get("chance"); if not _num(ch) or not 0 <= ch <= 1: probs.append("chance 必须是 [0,1] 的数")`，且该尺已接在 `check_all.py` TOOLS 中并实际扫 `drops/*.json5`。
- 内存实跑 `check_mobs.check_drop`：`1.5` / `-0.2` / `2` / `nan` / `"1.5"` / `true` / 缺键全部报同一句；`0` 合法放行。`check_drop_rolls.check_rolls` 与 `check_drop_values.check_values` 对八形态确实全绿，但**只要有一把在役尺报了，就不是静默洞**。
- 源码仍有静默出口（`DropSpec.java:93-102`，`:101` 夹 `[0,1]` 零 WARN），但与 T-0106 的 xp/min/max 不同：那三个字段 `check_mobs` 完全不读，本字段读。按 R111-d 第③判据不成立，C58 留池并标注反证。

### 3.2 C53 `points_per_level` 四判据齐，升级派单

| 判据 | 逐字证据 |
|---|---|
| ① 出口/语义可冻结 | `SkillTreeSpec.java:70` `intOf(root.get("points_per_level"), 1)`；`:160-162` `isInt(el) ? el.getAsInt() : fallback`；`:154-158` `isInt` 要求有限数字且 `d == rint(d)`；`:71-72` 仅**负数且是整数**才 WARN 归零 |
| ② 静默零 WARN | 非整数 `1.5` / 字符串 `"2"` / `true` / `nan` 走 `intOf` 回落 1，`:71-72` 的 `isInt(pp)` 为假直接跳过警告分支 |
| ③ 旧尺全绿 | `grep -rn points_per_level tools/*.py` 零命中；`check_trees.check_tree:45-125` 只读 id/nodes/requires/cost/effects/classes/max_points。内存反事实九形态（缺键 / `1.5` / `-0.5` / `2.0` / `0` / `1` / `"2"` / `true` / `nan`）报错集合逐字相同（均为同一组 9 条无关问题） |
| ④ 基线非空 | 默认 `warrior_vanguard.json5:17` 显式 `points_per_level: 1` → 1 棵树 / 1 处引用 / 0 问题 |

后果链：`TreePoints.java:28-29` 把 `ppl>0` 用于 `ppl × level` 点数上限；配 `1.5` 实际按 `1` 发点，日志全静。对账网第 38 例，首次核「技能树顶层点数形态」。

排除的其它候选：`items use.effects[].chance` / `restore_food`（`ConsumableSpec.java:88-100` 有 WARN）；`consume` 字符串（`:159-167` 有 WARN）；mobs attributes spread（`AffixPools.java:264-270` 有 WARN）；skill tree `nodes[].effects[].value`（`SkillTreeSpec.java:116-122` 非数字有 WARN 且丢弃，不静默改写）；附魔 `scripted.args`（`ScriptedEnchantmentEffect.java:60-65` 无夹取，另有数值同步尺）；damage caps / crit max_chance / Profession max_level（旧尺已读或有 WARN）。

## 4. 门禁与环境

- 七门禁：接线条目前 `check_checkall` / `check_tasks` / `check_contracts` / `check_docs` / `check_rounds` / `check_ownership` 全 0；收尾再跑一次。
- 环境：`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 9376 常驻未动，测试服端口无 LISTEN。裸名纪律第三十次。
