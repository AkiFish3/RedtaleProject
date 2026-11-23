# R116 · 验 T-0110 + check_all 58→59 + 派 T-0111（词条权重截断）

时间：2026-11-23 23:5x · 队列：S7 · 里程碑：M3/M1 对账网（第 42 例）

## 1. 卡板

- **T-0110 `check_use_ints.py` → `[VERIFIED]`**：三条验收全跑 + S7 独立探针（六夹具 + 基线 + 函数边界）全过。
  - 基线：`已检查 7 份物品 · 6 处 use 整数引用 · 截断 (int) · 静默零 WARN` / `共 1 份消耗品整数口径对账表 · 0 条问题` EXIT=0。
  - 六夹具 A~F EXIT=0/1/1/0/1/0，报错文案与路径 `use.effects[0].duration` / `use.cooldown_ticks` / `use.restore_food` / `use.effects[i].amplifier` 逐字一致；C 组按文案升序先 cooldown 后 restore。
  - 静态：wc=171 ≤200、`def check_ints`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 1 条读失败 EXIT=1。
  - 函数边界 22/22：四处路径各报 1 条；域外（21.5 / 0.5）/ 非数字 / bool / None / nan / inf / 超 int / 整数全跳过；重复两条不合并。
  - S8 自报与 S7 复跑逐字一致，零偏差不打回；探针与夹具已清。
- **已接 `tools/check_all.py` TOOLS 58→59**：`python tools/check_all.py` → `共 59 个脚本 · 跑成 59 · 跳过 0 · 红 0` EXIT=0；接线前 `check_checkall.py` 报漏接 1 条，接线后 `共 1 份清单 · 0 条问题` EXIT=0。
- **派 T-0111 `check_affix_weights.py`**（单文件零接线，六夹具，接口/文案/跳过规则由 S7 定死），在制品 1/3。

## 2. 派前取证（四判据 + 反事实）

候选 = `affixes/*.json5` 顶层 `weight` 与 `affix_pools/*.json5` 三段权重。

| 判据 | 证据 |
|---|---|
| ① 静默出口 | `AffixPools.java:72` 顶层权重走 `integer(root,"weight",100,...)`；`:162-168` 的 `integer()` 对 `isNumber(e)` 直接 `getAsInt()`，仅非数字 WARN；`:106-110` 池段权重同样直接 `getAsInt()`。`AffixRoller.java:140` 的 `entry.weight() <= 0` 让 `0.4` 截断归零后整条词条静默跳过。 |
| ② 旧尺全绿 | `check_items.py:94-95/:118-120` 用 `_num` + `>0`（不是整数形态）；`check_affix_reach.py` 只读 item_level；`check_affix_pool_refs.py` 只读键名。S7 干净目录反事实：默认 `sharp` 顶层 `100→100.7`、池段 `sharp:100→2.5` 后三把尺 EXIT/末行逐字不变（全绿），夹具已清。 |
| ③ 常量可冻结 | 具名缺省 `100`（顶层）+ `getAsInt()` 截断语义 + `weight<=0` 跳过 → 合法集为正整数形态；`KINDS = prefix/suffix/core` 来自 `AffixPools.java:44`。非原版注册表开放词汇。 |
| ④ 基线非空 | 3 份词条定义（100/80/60）+ 1 份词条池（prefix 100/80、suffix 60）= 6 处显式权重，0 问题。 |

排除（本轮全类扫描留证）：`SkillSpec` 冷却/法力（`check_skills.py:94-102` 已核整数）、`ProfessionSpec` 其它 curve 字段（有 WARN 或已覆盖）、`AffixPools.item_level`（`check_affix_reach` 会红）、`SocketSpec`/`SetSpec`/`rev`（`check_items` 已核 `_int`）、附魔 `weight`（原版注册表透传，无离线真值）、`RepairSpec.cost.amount`（默认空基线）。C62 树 icon 四判据齐但仍是显示面，连续留池不与数值洞合卡。

## 3. 口径

- **R116-a**：「尺读了字段」不等于「尺覆盖形态」。`check_items.py` 读 weight 但只核 `_num>0`，小数合法且静默改写 —— 反事实必须改值实跑，不能只 grep 键名（R112-a 深化）。
- **R116-b**：同一解析 helper 的多入口一次收口。`integer()` 顶层与池段手写 `getAsInt()` 两个出口，仍属同一「权重整数形态」语义，用一把尺按具名路径区分，不拆两卡（R115-a 同族）。
- **R116-c**：截断归零也要报。`0.4 > 0` 让在役尺全绿，但 `int(0.4)=0` 被 `AffixRoller` 静默丢弃，比普通截断更静；卡面把 `iv <= 0` 单列为红线，同时把 `v <= 0`（在役尺已报）跳过，避免两尺同报（R108-d）。
- **R116-d**：C62 显示面继续留池；本轮出现数值面新洞，不为凑卡把显示面与数值面合并（R113-d/R115-d 延续）。
- **R116-e**：裸名纪律第三十四次：派单当轮文档与台账在建脚本只写裸名，避免文档路径检查把未建文件当断链。

## 4. 环境

`src/` 零 diff；本轮只动 Python 与文档，未跑 gradle、未启停任何服、无构建锁；正式服未动，测试服 25567/25577 无 LISTEN。
