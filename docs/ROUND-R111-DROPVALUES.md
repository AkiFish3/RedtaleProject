# R111 · 验收 T-0105 + check_all 53→54 + 派 T-0106（掉落表 xp/min/max 边界口径）

时间：2026-11-23 05:4x（S7 · 移植工程队列）

## 1. 卡板

### 验收 T-0105 `check_mob_skill_cooldowns.py` → VERIFIED

| 卡面验收 | 实跑结果 |
|---|---|
| ①基线 | 仓库根 `python tools/check_mob_skill_cooldowns.py` → `共 1 份怪物技能冷却口径对账表 · 0 条问题` EXIT=0；`-v` → `已检查 2 只怪物 · 3 处 cooldown_ticks 引用 · 边界 [1,1000000]` |
| ②六夹具 | S7 独立探针 `r111_cooldown_probe 探针`（独立目录，不复用 S8 断言）：A 基线 0 / B `0` 1 条 / C `1000001` 1 条 / D `1`+`1000000` 合法边界 0 条 / E 两条按 a/b / F `-v` 2/2 → EXIT=0/1/1/0/1/0，**9/9 断言 PASS** |
| ③静态 + 边界 | `wc=144` ≤200；`grep -c "def check_cooldowns"`=1；禁用 grep=0；`py_compile` OK；`--dir NO_SUCH` → 1 条读失败 EXIT=1；直接 import：`1.5/0.5/0/inf/nan` 报、`True/"200"/缺键`跳过、重复 `0` 两条不合并、下标 0/1/5/6/7/8 正确 |

- S8 交付零偏差，不打回。仓库根与 `tools/` 工作目录差异：默认 `--dir` 是仓库相对路径，在 `tools/` 下直接跑会误判目录不可读（S7 首跑踩过），按卡面从仓库根重跑即过，不算 S8 偏差。
- 接线：`tools/check_all.py` TOOLS 53 → **54**（`check_mob_skill_cooldowns.py` 加入，`check_all` 实跑 54/54 红 0）。

### 派单 T-0106 `check_drop_values.py`（OPEN）

- 独占 `check_drop_values.py`（新建 ≤200 行）+ `build/dropvaluesfix/` 夹具；零接线。
- 接口固定：四常量 `XP_MIN=0` / `XP_MAX=1_000_000` / `COUNT_MIN=0` / `COUNT_MAX=64`，`_loads` / `read_drops` / `check_values` / `main`；基线 1 张掉落表 / 1 xp / 3 min / 3 max / 0 问题；六夹具 A~F + 静态/函数边界，详见 `docs/TASKS.md` T-0106。

## 2. S7 最小一步：C57 复评 → 派 T-0106

对账网第 **37** 例。四判据逐条取证：

1. **具名常量 + 静默出口**：`DropSpec.java:22` `MAX_XP=1_000_000, MAX_COUNT=64`；`:61` `Math.min(MAX_XP, value)`（xp 上界静默夹）、`:86` `Math.max(0, Math.min(MAX_COUNT, ...))`（min 静默夹）、`:87-88` max 同样静默夹。三处均零 WARN。
2. **WARN 反证**：`:60` xp 负值有 WARN、`:89` `max < min` 有 WARN、`:107` 非整数有 WARN → 夹取出口是故意静默，不是漏报。
3. **旧尺反事实全绿**：探针 `r111_c57_probe 探针` 写 `{xp:1000001, min:-5, max:65}` 进 drops 配置，`check_drop_rolls.py` EXIT=0、`check_mobs.py` EXIT=0、`check_all --only drop` EXIT=0（1 脚本红 0），报错集合与基线逐字相同。
4. **基线非空**：默认 `drops/ember_guard.json5` 1 表 / xp 1 处（12）/ min 3 处 / max 3 处。

口径：xp/min/max 同属一张掉落表、同一解析器、同一类静默夹取出口 → 一次收口（区别于 R110-b 同名不同对象的拆卡）。`drops[].chance` 越界静默夹 `[0,1]`（`DropSpec.java:94-101`）满足「常量 + 静默」，基线/形态未逐条取证 → 留池 C58。六只默认怪 + lang 仍属 S7 内容面，不硬凑。

## 3. 环境 / 门禁

- `src/` 零 diff；纯 Python 文档与工具改动；未构建、未启停任何服、无构建锁；正式服未动，测试服 25567/25577 无 LISTEN。
- 七门禁：`check_tasks` 106 卡在制品 1 问题 0；`check_contracts` 75 条契约 0 问题；`check_docs` 127 份 / 536 引用 0 问题；`check_checkall` 1 清单 0 问题；`check_rounds` / `check_ownership` / `check_commands` 均 0 问题。
- 裸名纪律第二十九次：派单当轮文档只写裸名 `check_drop_values.py`。

## 4. 收尾

- 探针 `r111_cooldown_probe 探针` / `r111_c57_probe 探针` / `r111_docs 文档脚本` 用完即删；`tools/__pycache__/check_mob_skill_cooldowns.*.pyc` 由验收 `py_compile` 生成，一并清掉。
- 下一步（R112）：验 T-0106（过后 `check_all` 54→55）；C58 逐出口取证；Q-021/022/023 与真人窗口 A~F 等 Aki。
