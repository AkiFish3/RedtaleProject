# R109 · 验收 T-0103 + check_all 51→52 + 派 T-0104（怪物技能 chance 值域口径）

## 1. 结论

- T-0103 `check_drop_rolls.py`：**VERIFIED**。三条验收全过 + S7 独立六夹具 6/6 逐字一致；S8 零偏差不打回。
- `check_all`：**51→52**，52/52 全绿、红 0。
- 新派 T-0104 `check_mob_skill_chance.py`：单文件零接线、六夹具、基线 2 怪 / 3 处 chance 引用 / 0 问题。对账网第 35 例。
- 本轮 `src/` **零 diff**；纯 Python 文档与接线，未构建、未启停任何服、无构建锁。

## 2. T-0103 验收证据（3/3）

| 项 | 命令 / 方法 | 结果 |
|---|---|---|
| ①基线 | `python check_drop_rolls.py` / `-v` | `共 1 份掉落掷骰次数口径对账表 · 0 条问题` EXIT=0；`已检查 1 张掉落表 · 3 处 rolls 引用 · 边界 [1,16]` |
| ②六夹具 | S7 独立干净目录 A~F 逐字复跑 | EXIT=0/1/1/0/1/0；B `17`、C `100` 各 1 条逐字；D `16` 放行；E 两条按 a/b 顺序；F `-v` 逐字 2·2 |
| ③静态 | wc / grep / py_compile / NO_SUCH | wc=165≤200；`def check_rolls`=1；禁用 grep=0；`py_compile` OK；`--dir NO_SUCH` 1 条 `drops目录读取失败` EXIT=1 不抛栈 |

S7 首轮探针把 E 的期望问题数写成 1（实际 2 条），按 R65-a 属探针自错，改正后 6/6；S8 交付与卡面零偏差，不打回。探针与自建 pyc 已清。

## 3. 本轮最小一步：clamp 重评 → 新真洞

### 3.1 排除复评

Explore 只读扫 52 把 check / 默认配置 / Java。四老留池项（spawn_rules.match / repair.targets / set_id / drops 引用）、C53 points_per_level、C56 cooldown_ticks 初筛、C57 xp/min/max 同族字段逐条复评。

### 3.2 真洞：怪物技能释放概率

- 配置：`ember_guard.json5:25` `chance: 0.35`；`ash_caster.json5:25` `chance: 0.5`；`:26` `chance: 0.25`（基线 2 怪 / 3 处）。
- 旧尺：`check_mobs.py:88-95` 的 `check_mob()` 只查 `skills[].skill` 引用命中，全仓 52 把尺无一读该字段。
- 内存反事实（S7 离线实跑）：把 `chance` 依次换成 `0.35 / 2.5 / -0.5 / inf / True / '2.5' / 0`，技能 id 命中时 `check_mob()` 返回 `[]` **逐字相同**。
- 运行期：`MobSpec.java:217-225`，`:224` `Math.max(0.0D, Math.min(1.0D, value))` 静默夹进 `[0,1]` 零 WARN；`:221` 非数字才 WARN（反证夹取出口有意静默）。配置写 `chance: 2.5` 实际 100% 释放、零日志。
- R103 曾以「有 clamp」排除本字段；本轮按 R106-b「夹之前的配置值」重评，确认 clamp 零 WARN 恰是洞而非排除理由（§13.112 R109-a）。

### 3.3 离线核基线

用同一 shim 解析两份默认怪配置：ash_caster 2 处（0.5 / 0.25）、ember_guard 1 处（0.35），n=2 / k=3 / 0 问题，与卡面冻结值逐字一致。

## 4. T-0104 接口（S7 定死，S8 不读源码）

- 常量：`CHANCE_MIN=0.0` / `CHANCE_MAX=1.0`，从 `MobSpec.java:220,224` 冻结。
- `_loads(text)`：自带 JSON5 shim；`read_mobs(dir)` → `(rows, ok)`，目录不可读不抛栈。
- `check_chances(rows)`：只看 `skills` 为 list 的 dict 条目；缺键 / 非数字 / bool 跳过；显式数字不在 `[0,1]`（含 `nan`/`inf`）产 `<文件名>: skills[<i>].chance 值 <repr(v)> 不在 [0,1]`；按文案升序、稳定、不去重。
- `main()`：`--dir` 默认 mobs 目录、`-v`；末行 `共 1 份怪物技能概率口径对账表 · <p> 条问题`；`-v` 行 `已检查 <n> 只怪物 · <k> 处 chance 引用 · 边界 [0,1]`。
- 不核 `cooldown_ticks`（C56 留池，具名常量 `DEFAULT_COOLDOWN=100` / `MAX_COOLDOWN=1_000_000`）、不核 skill id（归 `check_mob_skills.py`）、不核内容平衡。

## 5. 边界与台账

- 七门禁全 0：`check_all` 52/52、`check_checkall`、`check_tasks`（104 卡在制品 1）、`check_contracts`（71 条）、`check_docs`、`check_rounds`、`check_ownership`。
- 收录：`PLAN.md` §13 R109 + §13.112、`docs/TASKS.md`（T-0103 VERIFIED / T-0104 OPEN / 池 C55~C57）、`docs/CONTRACTS.md` §4.69 验收行 + §4.70、`docs/ROUNDS.md`、`tools/check_all.py`、协作板 §3。
- 下轮：验 T-0104（过后 `check_all` 52→53）；按 C56/C57 复评；六只默认怪 + lang 属 S7 内容面；Q-021/022/023 与真人 A~F 等 Aki。
