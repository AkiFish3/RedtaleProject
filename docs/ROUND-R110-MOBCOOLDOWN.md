# R110 · 验收 T-0104 + check_all 52→53 + 派 T-0105（怪物技能 cooldown_ticks 值域口径）

## 1. 结论

- T-0104 `check_mob_skill_chance.py`：**VERIFIED**。三条验收全过 + S7 独立六夹具 6/6 逐字一致；S8 零偏差不打回。
- `check_all`：**52→53**，53/53 全绿、红 0。
- 新派 T-0105 `check_mob_skill_cooldowns.py`：单文件零接线、六夹具、基线 2 怪 / 3 处 `cooldown_ticks` 引用 / 0 问题。对账网第 36 例。
- 本轮 `src/` **零 diff**；纯 Python 文档与接线，未构建、未启停任何服、无构建锁。

## 2. T-0104 验收证据（3/3）

| 项 | 命令 / 方法 | 结果 |
|---|---|---|
| ①基线 | `python check_mob_skill_chance.py` / `-v` | `共 1 份怪物技能概率口径对账表 · 0 条问题` EXIT=0；`已检查 2 只怪物 · 3 处 chance 引用 · 边界 [0,1]` |
| ②六夹具 | S7 独立干净目录 A~F 逐字复跑 | EXIT=0/1/1/0/1/0；B `2.5`、C `-0.5` 各 1 条逐字（含 `[问题] ` 前缀）；D `0`/`1` 合法边界；E 两条按 a/b 顺序；F `-v` 逐字 2/2 |
| ③静态 | wc / grep / py_compile / NO_SUCH | wc=139≤200；`def check_chances`=1；禁用 grep=0；`py_compile` OK；`--dir NO_SUCH` 1 条 `mobs目录读取失败` EXIT=1 不抛栈 |

S7 首轮探针 B/C 期望漏写 `[问题] ` 前缀，属探针自错（R65-a），改正后 6/6；S8 交付与卡面零偏差，不打回。探针与自建 pyc 已清。

## 3. 本轮最小一步：C56 复评 → 新真洞

### 3.1 全类排除

Explore 只读扫 53 把 check / 36 份默认 JSON5 / 运行期 Java。逐条排除：技能定义 `skills[].cooldown_ticks`（`check_skills.py:94-97` 已覆盖且 `SkillSpec.java:35,127-133` 有 WARN，常量 1e9 不可混用）、`drops[].rolls`（T-0103 已落地）、`drops[].chance`（`check_mobs.py:123-125` 已堵）、`skill_trees.nodes[].effects[].value`（无具名常量、区间随属性与 operation 变）、`spawn_rules.match[]`（开放词汇无离线真值）、`repair.targets[]`（基线 0、开放词汇）。

### 3.2 真洞：怪物技能冷却 tick 数

- 配置：`ash_caster.json5:25` `cooldown_ticks: 200`、`:26` `80`；`ember_guard.json5:25` `120`（基线 2 怪 / 3 处）。
- 旧尺：`check_mobs.py:88-95` 的 `check_mob()` 只查 `skills[].skill` 引用，`check_mob_skill_chance.py:88-103` 只查 `chance`；全仓 53 把尺无一读怪物技能 `cooldown_ticks`。
- 内存反事实：`-5 / 0 / 1000001 / 2000000 / 0.5 / "abc" / true / 缺键` 八形态传进 `check_mob()` + `check_chances()`，报错集合逐字相同。
- 运行期：`MobSpec.java:58` `DEFAULT_COOLDOWN=100` / `MAX_COOLDOWN=1_000_000`；`:212` 只对非数字 WARN；`:214-215` 对 `<=0` / 非有限 / 超上界 / 小数静默归零或 `(int)Math.min(...)` 截断、零 WARN；`SkillEngine.java:158,196` 把 `ticks<=0` 改走技能自身冷却。配置写 `0.5` 的怪技能实际按技能默认冷却释放，日志全静。
- R109-b「同父异尺」落地：`chance` 已验，`cooldown_ticks` 常量与基线独立，另派一张卡，不合并。

### 3.3 C57 留池复评

`xp` 上界 `MAX_XP=1_000_000`（`DropSpec.java:61` 静默夹）基线仅 1 处；`min` 负值与 `min/max` 上界（`DropSpec.java:86-89`）分支多：`min` 负值被旧尺 `lo>hi` 部分间接覆盖，`min=max=100` 时旧尺全绿但属内容上限而非静默改写。按 R107-a/d 不硬凑卡，留 C57。

## 4. T-0105 接口（S7 定死，S8 不读源码）

- 常量：`COOLDOWN_MIN=1` / `COOLDOWN_MAX=1_000_000`，从 `MobSpec.java:58,208-215` 冻结。
- `_loads(text)`：自带 JSON5 shim；`read_mobs(dir)` → `(rows, ok)`，目录不可读不抛栈。
- `check_cooldowns(rows)`：只看 `skills` 为 list 的 dict 条目；缺键 / 非数字 / bool 跳过；显式数字必须是 `[1,1000000]` 内整数形态，否则产 `<文件名>: skills[<i>].cooldown_ticks 值 <repr(v)> 不在 [1,1000000] 整数`；按文案升序、稳定、不去重。
- `main()`：`--dir` 默认 mobs 目录、`-v`；末行 `共 1 份怪物技能冷却口径对账表 · <p> 条问题`；`-v` 行 `已检查 <n> 只怪物 · <k> 处 cooldown_ticks 引用 · 边界 [1,1000000]`。
- 不核技能定义顶层同名字段（常量不同）、不核 skill id / chance、不核内容平衡。

## 5. 边界与台账

- 七门禁全 0：`check_all` 53/53、`check_checkall`、`check_tasks`（105 卡在制品 1）、`check_contracts`（73 条）、`check_docs`（126 份 / 530 引用）、`check_rounds`、`check_ownership`。
- 收录：`PLAN.md` §13 R110 + §13.113、`docs/TASKS.md`（T-0104 VERIFIED / T-0105 OPEN / 池 C56 结项）、`docs/CONTRACTS.md` §4.70 验收行 + §4.71/§4.72、`docs/ROUNDS.md`、`tools/check_all.py`、协作板 §3。
- 下轮：验 T-0105（过后 `check_all` 53→54）；C57 逐出口取证；六只默认怪 + lang 属 S7 内容面；Q-021/022/023 与真人 A~F 等 Aki。
