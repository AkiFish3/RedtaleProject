# R90 · 验收 T-0085 + check_all 33→34 + 派 T-0086（伤害乘区 damage_types 白名单形态对账）

时间：2026-11-21 06:3x~06:5x · S7（Kite 🪁）。纯 Python 轮次，`src/` 零 diff、未构建、未启停任何服、无构建锁。

## 1. 验收 T-0085（`check_use_script.py`）

`[DONE]` → **`[VERIFIED]`**。三条验收 + S7 独立边界 18/18：

1. 基线 `python tools/check_use_script.py` → 末行 `共 1 份消耗品脚本原语表 · 0 条问题` EXIT=0；`-v` → `已检查 7 份物品 · 2 条 use.script 调用 · 27 个合法原语`。
2. 六夹具独立干净目录逐字：A `effect:"mesage"`=1 条未注册原语、B `type:"heal"`=0（别名+已注册）、C `effect:3`=1 条缺键/非字符串、D `script:"message"`=1 条必须是数组、E `use:{}`=0、F 两条取下标 1 的 `taret`=1 条未注册；EXIT=1/0/1/1/0/1。
3. wc=123≤200、`def check_use_script`=1、禁用 grep=0、`py_compile` OK、`--items NO_SUCH` 恰好 1 条 `items目录读取失败` EXIT=1 不抛栈。夹具已清。

S7 边界 18/18：data=None / use 缺失 / None / 空 dict / 非 dict / script None / 空数组 / 字符串 / entry 非 dict / effect 空串与 None 不回落 type / 外部命名空间 / 多冒号 / type 别名 / 多条目下标定位 / bool 与空白 type / 混合多问题；另核 `_loads` 单引号与注释 shim、`read_items` 非 dict 与坏文件记 None、缺目录返空。

**夹具首跑六红是我自己的探针写错**（printf 把 `use` 键多包了一层引号，写成 `,"use:{...}}`，`clean_json5` 解析失败 → 全 `配置解析失败`）；按 R65-a 先判夹具错、改成 JSON5 裸键语法后逐字全过，不打回实现（§13.93 R90-b）。

## 2. 接线 check_all 33→34

`tools/check_all.py` TOOLS 末尾追加 `check_use_script.py`（R90 接上）。复跑 `共 34 个脚本 · 跑成 34 · 跳过 0 · 红 0`；`check_checkall` 1 清单 0 问题。

## 3. 派 T-0086（`check_damage_whitelist.py`，单文件零接线）

在制品 0 → 1/3。真洞定位（R88-c/R89-c 派前全类排除留证）：

- **唯一真洞**：`damage.json5` 的 `multiplier.damage_types[]` 白名单模式。`check_numeric.check_damage` 只核元素是「非空字符串且非 `*`」；`check_damage_types.read_refs` 只扫 skills/scripts 的 damage 原语与 `category_attributes[].type`——两者都不进 `damage_types[]`。运行期 `MultiplierRules.appliesTo()` 逐条走 `matches()`：精确 id / `ns:*` 通配 / `*`，**无 WARN、无夹、无日志**；写歪 `redtale_projce:*` 或 `redtale_project:skill_ghost` = 全服自定义伤害静默不吃属性乘区（R83-b/R90-a 对账网第十七例）。
- **逐条排除的候选**：effects 的 amount/radius/falloff → 有 WARN+`clamp01`/`Math.min`（R85-d）；weapon_attributes → `/redtale validate` 已有同款离线校验；affix/set/drops/装备 id/技能树/professions 引用 → 各有 check；`use.effects[]` 与 buff/debuff 的 `effect` → 原版 MobEffect id，离线无注册表；conditions 值域 → 已有 spawn/skill/script 三例；空数组与未用键 → 永远绿的红线。
- 基线 S7 离线核：1 条白名单 `redtale_project:*` / 4 个注册类型（`generic` + 3 datapack）/ 0 问题；注册集复用 `check_damage_types.read_registered`，不另抄。

卡面写死：独占文件（新建 `check_damage_whitelist.py` + `build/dmgwlf/` 夹具）、接口（`NS` / `read_entries` / `check_whitelist` / `main`）、六夹具 A~F、三条可机器执行验收。落 `docs/TASKS.md` §5 T-0086 + §6 池 C36、`docs/CONTRACTS.md` §4.50。

## 4. 状态与下一步

- 全套 0 问题：`check_all` 34/34、`check_tasks` 86 卡在制品 1、`check_checkall` 1 清单、`check_ownership`、`check_docs`、`check_contracts`、`check_rounds`。
- 正式服 25565/25575（PID 9376）常驻未动；测试服 25567/25577 无 LISTEN。
- 下轮：验 T-0086（过后 `check_all` 34→35）；对账网已十七例，按 R80-b/R84-b/R85-d/R86-b/R87-a/R88-a/R89-b/R90-a 判据核实后无真洞就转内容面，不硬凑。真人窗口 A~G 与 Q-021/022 仍等 Aki。
