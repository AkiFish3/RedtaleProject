# R59 · 验收 T-0054 + 修 `gen_attr_lang.py` 的破坏性覆写 + 对账总入口

日期：2026-11-19 04:14–05:0x · 里程碑：M7（工程面收口：显示层与对账入口）

本轮无 Java 改动（`src/` 零 diff）→ **未构建、未启测试服**。改动全在 `tools/`。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0054 `tools/check_scripts.py` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + 边界 13 文件） |
| T-0055 `tools/check_lang.py` | 派单 | `[OPEN]`（在制品 1/3） |

T-0054 三条验收：

1. `python tools/check_scripts.py` → `EXIT=0`，末行逐字 `共 2 个文件 · 0 条问题`
2. 自编 10 文件夹具 → `EXIT=1`，`共 10 个文件 · 10 条问题`，8 类全命中：`on_kill` 未知 trigger / `effects` 是对象 / `effects: []` / `cooldown_ticks:"20"` / `args:[1,2]` / `conditions[0].type 非法：moon_phase` / 跨文件重复 id 报在后出现的 `a8_second.json5` / JSON5 截断；文案差异命中：`ignite` 带 `（M5 未实现）`、`drain` 不带后缀
3. `wc`=192 ≤200 / `grep -c "def clean_json5"`=0 / `check_enchants` 回归 `EXIT=0`

边界补测（S7，13 文件 11 条问题）：单引号 + `//` 注释的单条形态可解析、`scripts` 包外层未知键只提示、`cooldown_ticks` 的 `true` 与 `1000000001` 判错而 `1000000000` 通过、`" b_trim "` 与 `b_trim` trim 后判重、`effects[0] 不是对象` / `effects[1] 缺少 type` / `conditions[0].args 不是对象` / `顶层不是对象` / `既没有 scripts 数组…` / `scripts 必须是数组`、无 `scripts/` 目录 `EXIT=1`「共 0 个文件 · 1 条问题」。

## 2. 自研 1：`gen_attr_lang.py` 从「整体覆写」改成「合并写入」

**发现的坑（有证据）**：

```
$ python tools/gen_attr_lang.py --check
  .../lang/zh_cn.json：过期（67 键）        ← 实际文件是 93 键
EXIT=1
```

`--check` 说「过期」是因为它拿「只含 66 条 `attribute.*` + 1 条 `death.*`」的期望文本比对全文；**若有人照提示直接跑一次去「修」，lang 会从 93 键削到 67 键**——`item.*` 14 条、`entity.*` 8 条、`enchantment.*` 2 条、他人写的 `death.*` 2 条全没。这正是 R8 栽过的「显示裸键」的入口。

与另外三个生成器（`gen_item_lang` / `gen_enchant_lang` / `gen_entity_lang`）口径对齐：

- 新常量 `OWNED_PREFIX = "attribute.redtale_project."`，本脚本**只拥有** 66 条属性键 + `EXTRA` 里列出的键
- 新函数 `preserved(path, owned)`：读现有 lang，剔除自有键后原样返回其余键；**文件坏 JSON 或顶层非对象 → `SystemExit(2)` 拒绝覆盖**（不拿「静默覆盖」当容错）
- 写盘加 `newline="\n"`（此前 Windows 下写成 CRLF，工作区与 HEAD 反复显示 M 却无内容差异）
- `--check` 改比「合并后的期望文本」，输出带上自有/总数：`OK（93 键，自有 67）`

实测四组：

| 场景 | 结果 |
|---|---|
| 修前 `--check` | `过期（67 键）` `EXIT=1` |
| 修后 `--check` | `zh_cn OK（93 键，自有 67）` / `en_us OK（93 键，自有 67）` `EXIT=0` |
| 修后直接跑（写盘） | 内容与 HEAD **零 diff**（`git status` 干净、`md5` 与 HEAD 字节一致），打印 `写入 …（93 键，保留他人 26）` |
| 人工破坏：删 1 条属性键 + 塞 1 条 `zz.probe.key` | 跑一次 → `94 键，保留他人 27`；**属性键补回**、**外壳键仍在**。清掉外壳键后 `--check` 回到 `OK（93 键）` |

## 3. 自研 2：`tools/check_all.py` 对账总入口

九个 `check_*.py` 各认一段配置，上线前要记九条命令、逐条看退出码。新增一个**纯编排**入口：

- 固定顺序：`defaults → progress → items → mobs → enchants → skills → trees → scripts → lang`
- 子进程跑，**只转述末行** + 判 `OK` / `红 <code>`；`-v` 时附完整输出（含 stderr）
- 单脚本异常/超时（180s）算一条红，**不中断整轮**
- 脚本不存在 → `（未实现，跳过）`（`check_lang.py` 落地后自动接上，不用改本文件）
- `--only <子串>` 过滤；末行 `共 N 个脚本 · 跑成 R · 跳过 S · 红 F`；全绿 0 / 有红 1 / 一个没跑成 2

实测（仓库根）：

```
check_defaults.py  OK   清单 33 条 · 磁盘 33 个 · 问题 0 条
check_progress.py  OK   共 3 个文件 · 没有发现问题 ✓
check_items.py     OK   共 12 个文件 · 0 条问题
check_mobs.py      OK   共 4 个文件 · 0 条问题
check_enchants.py  OK   共 2 个文件 · 没有发现问题 ✓
check_skills.py    OK   共 4 个文件 · 0 条问题
check_trees.py     OK   共 1 个文件 · 0 条问题
check_scripts.py   OK   共 2 个文件 · 0 条问题
check_lang.py      -    （未实现，跳过）
共 9 个脚本 · 跑成 8 · 跳过 1 · 红 0        EXIT=0
```

红路径用桩脚本验证过（`check_defaults.py` 改成 `exit(1)` + stderr）：`红 1`、末行 `跑成 2 · 跳过 7 · 红 1`、`EXIT=1`、`-v` 打出 stderr。`--only nosuch` → `EXIT=2`。

## 4. 派单 T-0055（`tools/check_lang.py`）

理由：四个 lang 生成器各管一段，**没有任何脚本检查四段合起来是否齐**。本轮刚证明这条路能被一个「照提示修一下」的动作毁掉。卡里写死四类键的**现读来源与正则**（属性走 `AttributeSpecs.java` 的 `v|c|cb` 行、物品走 `ItemBase.java` 枚举首字面量、实体走 `RedtaleEntities.java` 的 `new Proto(`、附魔走 `enchantments/*.json5` 的 `id`），六条检查项（缺键 / 两侧不对称 / 空值 / 占位符不一致 / 孤儿键只提示 / 裸 `death.*` 不管），三条机器验收。

## 5. 挂账（未变）

1. **真人一次性验收窗口**仍是 M6/M7 的唯一出口阻塞：M6 自然刷怪替换 + `per_tick_cap` 行为 + M7 升级/`take`·`untake` + `tree info`/`tree skills` 玩家段 + 武器 tooltip（D20）+ `#minecraft:undead` 条件乘区。全部需要 Aki 上线，本轮无法推进。
2. **Q-022（M5 原语范围）**未答复，默认「按需补」已写在协作板，不排期。
3. `respec` 扣费挂 Q-021（默认免费）。

## 6. 口径（写进 `PLAN.md` §13.62）

- **R59-a** 生成器只能拥有自己的前缀，其余键原样保留；坏 JSON 一律拒绝覆盖，不静默重建。
- **R59-b** `--check` 的「过期」必须是**可安全执行**的信号：提示过期 ⇒ 跑一次就能修好，且不删别人的键。修不了就别报过期。
- **R59-c** 对账入口只编排不判断：末行原样转述，判红只看退出码。它不能成为第二个真相。
- **R59-d** 写盘固定 `newline="\n"`：CRLF 工作区会让 `git status` 永远脏、真改动被噪声淹没。
