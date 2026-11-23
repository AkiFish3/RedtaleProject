# R117 · 验 T-0111 + check_all 59→60 + 派 T-0112（词条 item_level 截断）

时间：2026-11-24 01:1x · 队列：S7 · 里程碑：M3/M1 对账网（第 43 例）

## 1. 卡板

- **T-0111 `check_affix_weights.py` → `[VERIFIED]`**：三条验收全跑 + S7 独立夹具/函数边界全过。
  - 基线：`共 1 份词条权重口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 3 份词条定义 · 1 份词条池 · 6 处权重引用 · 截断 (int) · 静默零 WARN`。
  - 六夹具 A/B/C/D/E1/E2/F：EXIT=0/1/1/1/0/0/1，末行计数与报错文案逐条逐字一致（E 卡面写法歧义由两个变体同时覆盖，均 0 条）。
  - 静态：wc=162 ≤200、`def check_weights`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 2 条读失败 EXIT=1。
  - 函数边界 15/15：`100.7`/`2.5`/`0.4` 各 1 条且路径逐字；`0`/`-2`/`"100.7"`/`True`/`None`/`nan`/`inf`/`2147483648`/`100`/`80`/缺键全跳过；重复两条不合并。
  - S8 零偏差不打回；S7 探针目录已删。
- **已接 `tools/check_all.py` TOOLS 59→60**：`python tools/check_all.py` → `共 60 个脚本 · 跑成 60 · 跳过 0 · 红 0` EXIT=0；`check_checkall.py` → `共 1 份清单 · 0 条问题` EXIT=0。
- **派 T-0112 `check_affix_levels.py`**（单文件零接线，六夹具，接口/文案/跳过规则由 S7 定死），在制品 1/3。

## 2. 派前取证（四判据 + 反事实）

候选 = `affixes/*.json5` 的 `item_level.min` / `item_level.max`。

| 判据 | 证据 |
|---|---|
| ① 静默出口 | `AffixPools.java:69-71` 两处 `integer(level,"min"/"max",…)`；`:162-168` 的 `integer()` 对 `isNumber(e)` 直接 `getAsInt()`，仅非数字 WARN。`0.9 → 0`、`60.9 → 60` 全程零日志。 |
| ② 旧尺全绿 | `check_items.py:48,96-99` 的 `_num()` 放行 float，只核 `lo<=hi`；`check_affix_reach.py:78-86` 用 float 直接比大小；`check_affix_weights.py` 只读 weight；`check_affix_pool_refs.py` 只读键名。S7 干净目录反事实：三份默认词条改 `min:0.9/0.5/0.25`、`max:60.9/60.5/60.75`（保持 `lo<=1<=hi` 剥掉下游症状）后四把尺末行逐字全绿 EXIT 全 0，已还原。 |
| ③ 常量可冻结 | 缺省 `1` / `60` 具名写在 `AffixPools.java:69-71`；`getAsInt()` 截断语义冻结合法集为整数形态；`MAX_LEVEL=2147483647` 沿用 int 收敛上限。 |
| ④ 基线非空 | 3 份默认词条各写 `item_level: { min: 1, max: 60 }` = 6 处显式引用，0 问题。 |

**R116 旧排除的重评**：R116 曾以「`check_affix_reach` 会红」排除本候选。按 R106-b「看夹取前的配置形态」重评：首轮探针 `min:1.7` 确实让可达性尺报 2 条红，但那是**下游症状**（`1` 不再落在 `[1.7,60]` 内），不是截断口径本身；改用 `min:0.9` 后四尺全绿，真洞成立。

排除（全类扫描留证）：`sockets.count` / `gem.tier` / `set bonuses.count`（`check_items.py` 已用 `_int`）、tree node `cost`（`check_trees.py` 已核）、profession `growth`/`exponent`（有 WARN 且 `check_professions.py:67,71` 已核边界）、`spawn_rules` chance/per_chunk_cap/priority（`check_mobs.py:140-148` 已核）、`skills.cooldown_ticks` / scripts 同名（`check_skills.py` / `check_scripts.py` 已核整数）、`repair.amount`（`check_repair_active.py:56-57` 已核）、`repair.cost.amount`（真盲但默认无基线）、C62 树 icon（显示面，第四轮留池）。

## 3. 口径

- **R117-a**：同族字段接力成卡。T-0111 覆盖权重、T-0112 覆盖同一 `integer()` 出口的 `item_level` 边界；同出口不同语义按具名路径分卡，不合并（与 R116-b 的「同语义多入口收口」互补）。
- **R117-b**：下游症状不能替代截断口径证据。断言「旧尺盲」必须剥掉下游可达性/伤害等派生症状，只留纯静默截断形态。
- **R117-c**：旧尺读了字段 ≠ 覆盖形态。`check_items.py` 用 `_num()`、`check_affix_reach.py` 用 float 比较，两把尺都读过 `item_level` 却都放过小数 —— R116-a 第二例。
- **R117-d**：C62 显示面继续留池；数值面仍有真洞，不硬凑显示面卡（R113-d/R115-d/R116-d 延续）。
- **R117-e**：裸名纪律第三十五次：派单当轮文档与台账在建脚本只写裸名，避免文档路径检查把未建文件当断链。

## 4. 环境

`src/` 零 diff；本轮只动 Python 与文档，未跑 gradle、未启停任何服、无构建锁；正式服未动，测试服 25567/25577 无 LISTEN。
