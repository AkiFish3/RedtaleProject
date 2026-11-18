# R58 · 验收 T-0053 + `tree skills <tree>` 观测面 + 派 T-0054

日期：2026-11-19 03:06–03:3x · 里程碑：M7（出口闭环后的观测面补齐）

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0053 `tools/check_skills.py` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + 边界 6 组） |
| T-0054 `tools/check_scripts.py` | 派单 | `[OPEN]`（在制品 1/3） |

T-0053 三条验收：

1. `python tools/check_skills.py` → `EXIT=0`，末行逐字 `共 4 个文件 · 0 条问题`
2. 自编 9 文件夹具 → `EXIT=1`，`共 9 个文件 · 8 条问题`，8 类全命中；跨文件重复 id 报在后出现的 `a8.json5`
3. `wc`=164 ≤200 / `grep -c "def clean_json5"`=0 / `check_trees` 回归 `EXIT=0`

边界补测（S7）：`message`+`console_log` 接受（M0 调试原语并集）、`cooldown_ticks: true` 判错、`mana_cost: 2.5` 判对（浮点合法）、`icon: "Red_Icon"` 判错、空 `effects` 与未知键仅提示、无 `skills/` → `EXIT=1`、`docs/SKILLS.md` §4 的 **25 条原语逐条接受**。

## 2. 自研：`tree skills <tree>`

`TreeCommand` 新增：

- `skills <tree>` 子命令（未知名与 `state` 同文案）
- `skillsText(def, viewer)` public 纯函数 —— 控制台分支只报声明集 + `（控制台不读玩家存档）`；玩家分支 `声明 N 条 · 已解锁 M 条` + 每条 `· 已解锁` / `· 未解锁`
- `declaredSkills(def)` = `TreeUnlocks.unlocked(def, def.nodes().keySet())`（假装全点，不新增第二个解析器）
- `probe` 新增 R 组

实测（测试服 1 次启停 `Done (2.615s)`，已停、25567/25577 0 LISTEN）：

```
[redtale] warrior_vanguard · 声明技能 2 条（控制台不读玩家存档）
  redtale_project:gale_slash
  redtale_project:quake_slam
```

`tree probe` **18/18**（17 → 18）。
回归：`tree info` 11 行逐字、`tree list` 逐字、`validate 0`、`reload 37 份`、`exp probe 7/7`、redtale 零 ERROR。

## 3. 数字

| 项 | 值 |
|---|---|
| jar | 641392 B（640121 → +1271） |
| `selftest` | 102 PASS |
| 构建锁 | 取删 2 次 |
| 探针 | `tree probe` 18/18 |

## 4. 口径

见 `PLAN.md` §13.61（R58-a~d）：控制台只报声明集、声明集与解锁集同源、标注走 path 归一、`skillsText` 纯函数化。

## 5. 挂账

- **M5 原语缺口**（新）：`SKILLS.md` §4 的 25 条里只注册 13 条，`execute`/`ignite`/`cleanse`/`shield`/`damage_over_time`/`teleport`/`leap`/`beam`/`aura`/`summon`/`trap`/`fx` 未实现，`slow` 与 `debuff` 语义重叠。加原语 = 换 jar（D17），**需 Aki 拍板范围**。
- 真人一次性验收窗口不变（M6 刷怪 + per_tick_cap 行为 + M7 升级/take·untake + `tree info`/`tree skills` 玩家段 + tooltip + undead 乘区）。
- `respec` 扣费挂 Q-021。
