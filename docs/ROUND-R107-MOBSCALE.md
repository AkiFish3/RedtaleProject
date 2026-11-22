# R107 · 2026-11-23 01:1x · 验 T-0101 + 接线 check_all 49→50 + 派 T-0102（怪物 scale 下界口径）

## 1. 卡板

- T-0101 `check_mob_levels.py`：`[DONE]` → **`[VERIFIED]`**（3/3 + S7 独立六夹具 6/6）。
- 新派 T-0102：`check_mob_scales.py`（单文件、零接线、六夹具、基线 2 怪/2 引用/0 问题），在制品 **1/3**。

## 2. T-0101 验收证据（逐字）

| 项 | 结果 |
|---|---|
| 基线 | `共 1 份怪物等级口径对账表 · 0 条问题` EXIT=0；`-v` = `已检查 2 份怪物 · 2 处 level 引用 · 边界 [1,1000]` |
| S7 独立六夹具 | A=0 · B `0` · C `1001` · D `12.7` · E 两条按 a/b 顺序（`'12'` / `True`） · F 两边界值=0；逐字 EXIT=0/1/1/1/1/0，F 的 `-v` 逐字 |
| 静态 | `wc -l`=163≤200；`def check_levels`=1；禁用 grep=0；`py_compile` RC=0；`--dir NO_SUCH` 恰好 1 条 `mobs目录读取失败` EXIT=1，不抛栈 |

S8 自测数字与 S7 独立复跑逐字一致，零偏差不打回；探针 `build/moblevelfix/` 与自建 pyc 已清。

## 3. S7 本轮最小一步：反事实验证 + 全类排除

- `mobs[].scale` 是 R106-b 明确保留的下一张候选。只读核实：`MobSpec.java:57` 冻结 `MIN_SCALE=0.25D, MAX_SCALE=4.0D`；`:113` 注释逐字「合法值夹 [0.25,4.0] 不告警」；`:114-121` 非数字才 WARN，`<=0` / 非有限静默回落 1.0，其余静默夹取；`MobSpawner.java:73,124-135` 直接把该值写到 `minecraft:generic.scale` 基值。
- 反事实（内存直调旧尺，零写盘）：`check_mobs.check_mob` 对 `scale=0.1 / 0.24 / 0.25` 全绿，只有 `5` 红；即旧尺只漏 `(0,0.25)` 的静默夹取带。
- 另一候选 `skill_trees[].points_per_level` 全部形态（`1.5 / "2" / true / 0 / -1`）旧尺全绿，但合法分支多（0 关门槛 / 负数 WARN 归零 / 非整静默 1）、无具名常量可冻结、基线仅 1 处，按 R96-c / R102 / R104「卡面太薄」先例留池（C53）。
- 另两个更强候选留池：`mobs[].skills[].cooldown_ticks`（`<=0` 静默归 0、`>1e6` 静默夹，按 R85-d clamp 排除）、`skill_trees.nodes[].effects[].value`（无合法区间可冻结）。

## 4. 接线与门禁

- `tools/check_all.py` TOOLS 追加 `check_mob_levels.py`（R107）：`共 50 个脚本 · 跑成 50 · 跳过 0 · 红 0` EXIT=0。接线前 `check_checkall` 已抓出「未接进 check_all」1 条，接线后归零。
- 七门禁全 0：check_checkall 1 清单/0；check_tasks 102 卡在制品 1；check_contracts 68 条；check_docs 122 份/510 引用；check_rounds / check_ownership 全 0。

## 5. 环境与边界

- 本轮 `src/` 零 diff；纯 Python + 文档，未构建、未取构建锁、未启停任何服；正式服 9376 常驻未动，测试服 25567/25577 无 LISTEN。
- 裸名纪律第二十五次：在建脚本只写裸名，避免文档路径检查把未建文件当断链。
