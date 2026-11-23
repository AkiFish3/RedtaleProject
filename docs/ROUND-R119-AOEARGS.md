# R119 · 验 T-0113 + `check_all` 61→62 + 派 T-0114（范围伤害 args 静默改写）

- 时间：2026-11-24 03:3x（S7 轮次）
- 里程碑：M5 / M7 的对账网增量；本轮不动 `src/`、不构建、不启停任何服。

## 1. 验收 T-0113（`check_tree_icons.py`）

| 项 | 结果 |
|---|---|
| 基线 | `python tools/check_tree_icons.py` → `共 1 份技能树图标口径对账表 · 0 条问题` EXIT=0；`-v` → `已检查 1 份技能树定义 · 1 处 icon 引用 · 静默回落 minecraft:stone · 解析零 WARN` |
| S7 独立六夹具 | A~F 逐字 6/6：EXIT=0/1/1/1/0/1；末行问题数 0/1/1/2/0/2；B 数值 `123`、C 空白串 `'   '`、D `True`/`[]` 按 a/b 升序、E `-v` 4 份·3 处、F `None`/`{}` 两条。每次从干净目录重来 |
| 静态 + 边界 | wc=153≤200；`def check_icons`=1；禁用 grep=0；`py_compile` OK；`--dir NO_SUCH` 恰好 1 条 `[问题] 技能树目录读取失败` EXIT=1 不抛栈；import 直调 8 坏值各 1 条、4 种跳过形态、重复值不合并 |

S8 零偏差，不打回。卡面 `T-0113` 改 `[VERIFIED]`，接进 `tools/check_all.py`：`共 62 个脚本 · 跑成 62 · 跳过 0 · 红 0`。S7 探针与 pyc 已清。

## 2. 本轮最小一步：全类排除 + 反事实 + 数值洞再开

R118 的转派前提是「数值洞已收口」。本轮 Explore 只读扫全仓 `tools/check_*.py` / Java / 默认 JSON5，推翻该前提：`damage_aoe` 原语三个 args 的静默改写无人核。

### 2.1 真洞（逐字取证）

| 字段 | Java 出口 | 静默行为 |
|---|---|---|
| `radius` | `DamageAoeEffect.java:55` `MAX_RADIUS = 32.0D`；`:93` `Math.min(DamageEffect.num(args,"radius",4.0D), MAX_RADIUS)` | `>32` 静默夹到 32；无 WARN |
| `falloff` | `:94` `clamp01(DamageEffect.num(args,"falloff",0.5D))`；`:140-144` `Math.min(1, Math.max(0,v))`；`DamageEffect.java:168-170` 非正数返缺省 | `0` 被回落 `0.5`（与 `:36,:67` 文档「0 = 不衰减」直接冲突）、`>1` 夹到 1；零 WARN |
| `max_targets` | `:95` `(int) Math.max(0.0D, DamageEffect.num(args,"max_targets",0.0D))` | 正数小数静默截断、负数静默回落 0（不限）；零 WARN |

### 2.2 旧尺反事实（两组，五把尺逐字全绿）

干净目录复制默认 4 份技能 + 2 份脚本 + 4 份数值配置：

- 组一 `radius:40` / `falloff:1.5` / `max_targets:6.9`：`check_skill_args`、`check_arg_keys`、`check_skills`、`check_script_args`、`check_numeric` 五把全 `0 条问题` EXIT=0。
- 组二 `falloff:0` / `max_targets:-2`：同上五把全绿（`check_arg_keys` 需两目录都存在，先建空 `scripts/` 后复跑才对齐）。

排除项：`amount` / `damage_type`（有 WARN）、`radius` 非正（D16 异常保护）、`stun` / `buff` / `debuff` / `knockback` / `pull` 的同名 args（默认基线 0）、`heal` 非正（有 WARN）、`grant_exp`（有 WARN）、`sockets.types` 补 any 与 `gem.type` 空白回落 any（真盲但属 ANY 同族，留池 C68）、`skill_trees.respec`（L1/L2 明确不实现）、`per_item_level`（无离线真值）。

### 2.3 派单

**T-0114** `check_damage_aoe_args.py`（OPEN，在制品 1/3）：两目录（skills / scripts）五个具名形态，常量 `MAX_RADIUS=32.0` / `DEFAULT_FALLOFF=0.5` / `FALLOFF_MAX=1.0` / `DEFAULT_MAX_TARGETS=0` 由 S7 从 Java 冻结；单文件 ≤200 行、零接线、六夹具、函数边界、CLI 读失败 p=2，接口与文案卡面逐字定死。C66 三处 `name` 显示面继续排队——按 R118-a，新发现的数值洞优先。

## 3. 门禁与环境

- 七门禁全 0：`check_all` 62/62 红 0；`check_checkall` 1 清单 0 问题；`check_tasks`、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership` 全 0。
- `src/` 零 diff；纯 Python；未取构建锁、未 `gradle build`；正式服未启停、测试服未启停；未发任何游戏内指令。
- 裸名纪律第三十七次：派单当轮文档只写裸名 `check_damage_aoe_args.py`。

## 4. 口径（R119-a~e）

| 编号 | 口径 |
|---|---|
| R119-a | **「数值洞收口」是可推翻的前提**：R118 因全类扫描未见数值洞而转显示面；R119 扫描范围扩到原语 `args` 的 effect 出口后立即发现新洞，按 R118-a 数值优先重派，不视为反复。 |
| R119-b | **文档语义与实现冲突也算真洞**：`falloff: 0` 文档明写「不衰减」、实现回落 0.5；不是「异常保护」，尺子必须报。 |
| R119-c | **同名 args 在不同原语出口不同**：`damage` 的 `radius` 用 `warnOnce`、`damage_aoe` 的 `radius` 静默夹；不因键名相同复用旧尺。 |
| R119-d | **反事实 CLI 要对准夹具目录**：`check_arg_keys` 支持 `--skills/--scripts`；首轮只给 skills 目录导致「scripts 目录读取失败」盖过真实结果，按 R65-a 先修探针再判旧尺。 |
| R119-e | **裸名纪律第三十七次**：派单当轮文档与台账在建脚本只写裸名，避免路径检查把未建文件当断链。 |

下一步（R120）：验 T-0114（过后 `check_all` 62→63）；C66 显示面单卡；C68 ANY 同族留池取证；Q-021/022/023 无人答、真人 A~F 等 Aki。
