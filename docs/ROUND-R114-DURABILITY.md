# R114 · 2026-11-23 09:1x · 验 T-0108 + 接 check_all 56→57 + 派 T-0109（物品耐久静默截断/夹顶）

## 1. 验收 T-0108：`check_profession_base.py` → VERIFIED

| 项 | 期望 | 实测 | 结论 |
|---|---|---|---|
| 基线 | 末行 `共 1 份专业起点经验口径对账表 · 0 条问题` EXIT=0 | 逐字一致 EXIT=0 | 过 |
| `-v` | `已检查 2 份专业 · 2 处 base 引用 · 缺省 100.0 · 硬顶 1000000000` | 逐字一致 | 过 |
| 六夹具 A~F | EXIT=0/1/1/0/1/0；B/C/E 文案逐字 | 逐字一致（S7 首轮夹具漏外层花括号，补齐后自复跑） | 过 |
| 静态 | wc=127 ≤200；`def check_base`=1；禁用 grep=0；`py_compile` OK | 全过 | 过 |
| 读失败 | `--dir NO_SUCH` 1 条 `[问题] professions目录读取失败` EXIT=1 | 逐字一致 | 过 |
| 函数边界 | `0`/超顶各 1 条；`-5`/`"x"`/`True`/`None`/`nan`/`inf`/缺键/`1.5`/`1e9` 全跳过；重复不合并 | 13 形态逐字一致 | 过 |

S8 零偏差不打回。S7 探针目录 `durfix/` 无关、本轮探针已清；`check_all` TOOLS 追加一行，接线前 `check_checkall` 报漏接 1 条，接线后 57/57 红 0。

## 2. 派 T-0109：`check_item_durability.py`（对账网第四十例）

**四判据取证**（只读，不改源码）：

1. **零 WARN 出口**：`ItemContent.java:101` 为 `(int) num(root, "durability", base.defaultDurability(), warnings, source)`；`:257-266` 的 `num()` 对任意数字直接 `return el.getAsDouble()`，只有非数字才 WARN；`:102-105` 只对负值告警。`1200.9 → 1200`、`2147483648 → Integer.MAX_VALUE` 全程零 WARN。
2. **旧尺反事实全绿**：`check_items.py:57-88` 的 `check_item` 不读 `durability`；tools 目录对该键 grep=0。S7 内存反事实九形态（缺键 / `1200.9` / `200.5` / `2147483648` / `0.0` / `1200` / `-5` / `"1200.9"` / `true`）报错集合逐字相同。
3. **基线非空**：7 份物品 / 5 处显式 durability（600 / 1200 / 200 / 0 / 0）/ 0 问题。
4. **单文件可机器验**：新建 `check_item_durability.py` ≤200 行，`_loads`/`read_items`/`check_durability`/`main` 接口由 S7 定死；六夹具 A~F + 三条验收命令。

**同轮排除**：C62 `skill_trees[].icon` 四判据齐（`SkillTreeSpec.java:73-74,145-147` 零 WARN，`SkillSpec.java:111-113` 同族有 WARN 反证，基线 1 处），但只落显示面，按 R113-d 留池；`spawn_rules.chance` / `sockets.count` / `gem.tier` / `rev` 已有在役尺全红，排除；`SkillSpec` 冷却与法力有 WARN，排除。

**冻结语义**：`MAX_DURABILITY = 2147483647`（Java `(int)` 收敛语义，非源码具名常量）；缺键/负值/非数字/bool/非有限数跳过；小数报截断，超硬顶报夹顶；文案、CLI、六夹具期望全部写进卡面，S8 不读源码、不推断。

## 3. 本轮口径

- R114-a：探针夹具结构错误不算 S8 偏差，先疑自己的夹具。
- R114-b：语言语义可作冻结常量，不要求 S8 读 Java。
- R114-c：显示面候选继续留池，不因「可派」就凑卡。
- R114-d：同一 `(int)` 出口的截断与夹顶一次收口。
- R114-e：裸名纪律第三十二次。

## 4. 环境

`src/` 零 diff；纯 Python 改动，未构建、未启停任何服、无构建锁；正式服未动、测试服端口无 LISTEN。`check_all` 57/57 红 0。
