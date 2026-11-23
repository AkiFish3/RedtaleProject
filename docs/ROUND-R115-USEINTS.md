# R115 · 2026-11-23 22:5x · 验 T-0109 + 接 check_all 57→58 + 派 T-0110（消耗品 use 整数静默截断）

## 1. 验收 T-0109：`check_item_durability.py` → VERIFIED

| 项 | 期望 | 实测 | 结论 |
|---|---|---|---|
| 基线 | `共 1 份物品耐久口径对账表 · 0 条问题` EXIT=0 | 逐字一致 EXIT=0 | 过 |
| `-v` | `已检查 7 份物品 · 5 处 durability 引用 · 截断 (int) · 硬顶 2147483647` | 逐字一致 | 过 |
| 六夹具 A~F | EXIT=0/1/1/0/1/0；B/C/E 文案逐字 | 逐字一致（S7 独立夹具目录） | 过 |
| 静态 | wc=128 ≤200；`def check_durability`=1；禁用 grep=0；`py_compile` OK | 全过 | 过 |
| 读失败 | `--dir NO_SUCH` 1 条 `[问题] items目录读取失败` EXIT=1 | 逐字一致 | 过 |
| 函数边界 | 13 形态 + 重复不合并 | 逐字一致 | 过 |

S8 零偏差不打回；探针已清。`check_all` TOOLS 追加一行，接线前 `check_checkall` 报漏接 1 条，接线后 58/58 红 0。

## 2. 派 T-0110：`check_use_ints.py`（对账网第四十一例）

**四判据取证**（只读，不改源码）：

1. **零 WARN 出口**：`ConsumableSpec.java:184-190` 的 `integer()` 对 `isNumber(e)` 直接 `return e.getAsInt()`，仅非数字才 WARN；四个调用点 `:76` duration / `:81` amplifier / `:96` restore_food / `:115` cooldown_ticks。`duration: 200.9 → 200`、`restore_food: 6.7 → 6` 落在合法域内，日志全静；越界/负值分支（`:78-81`/`:83-85`/`:93-95`/`:105-107`）才有 WARN，尺子跳过这些形态。
2. **旧尺反事实全绿**：S7 在干净目录复制默认配置并把 `demo_stew.json5` 六处整数全改小数，`check_items.py`（`共 12 个文件 · 0 条问题`）、`check_use_args.py`、`check_use_script.py`、`check_item_durability.py` 四条退出码与末行逐字不变。
3. **基线非空**：7 份物品 / 1 份带 `use` / 6 处整数引用（200 / 1 / 1 / 0 / 6 / 400）/ 0 问题。
4. **单文件可机器验**：新建 `check_use_ints.py` ≤200 行，`_loads`/`read_items`/`check_ints`/`main` 接口由 S7 定死；六夹具 A~F + 三条验收命令。

**同轮排除**：Explore 只读扫 57 尺 / 36 json5 / Java——`SpawnRuleSpec.java:48,110` chance/per_chunk_cap 与 `check_mobs.py:140-145` 重合；`SkillSpec.java:135` `(int)value` 已被 `check_skills.py:94-97` 要求 int；`MobSpec` level/scale/cooldown/chance 已被四把专尺读；`AffixPools.java:165,109` 已被 `check_items.py` 读；professions curve/grant 有 WARN；D16 有意夹取注释不是洞。C62 `skill_trees[].icon` 四判据齐但连续两轮落显示面，按 R113-d / R114-c 留池。

**冻结语义**：`MAX_RESTORE_FOOD = 20`；四处具名路径逐字写进卡面；截断后落 WARN 域的形态跳过；文案、CLI、六夹具期望全写死，S8 不读源码、不推断。

## 3. 本轮口径

- R115-a：同一解析 helper 的多调用点一次收口。
- R115-b：截断后落 WARN 域的形态跳过，不与运行期告警重复。
- R115-c：反事实跑四把在役尺，grep 只作辅助。
- R115-d：显示面候选连续留池，不硬凑卡。
- R115-e：裸名纪律第三十三次。

## 4. 环境

`src/` 零 diff；纯 Python 改动，未构建、未启停任何服、无构建锁；正式服未动、测试服端口无 LISTEN。`check_all` 58/58 红 0。
