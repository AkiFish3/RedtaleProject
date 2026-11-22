# R103 · 原语 args 键名签名对账（对账网第 30 例）

时间：2026-11-22 08:4x~08:5x · 队列：S7·移植工程（兼 S8 派单/验收）

## 验收 T-0098（check_equipment_slots.py）

`[DONE]` → `[VERIFIED]`，3/3 + S7 独立六夹具 6/6：

| 项 | 命令 | 结果 |
|---|---|---|
| 基线 | `python check_equipment_slots.py` | `共 1 份怪物装备槽位对账表 · 0 条问题` EXIT=0 |
| -v | 同上 `-v` | `已检查 2 份怪物 · 4 处自家基底装备引用 · 14 个基底` |
| 夹具 A~F | 独立干净目录 `build/equipslotfix/` | EXIT=1/0/1/1/0/1，逐字对上；F 两怪恰好 1 条 + -v 逐字 |
| 静态 | wc≤200 / `def mismatches`=1 / 禁用 grep=0 / py_compile OK / `--mobs NO_SUCH` 1 条读失败 | 全过 |

S8 自测零偏差不打回，探针已清。接线 `check_all` 46→47（跑成 47 红 0）。

## 派 T-0099（原语 args 键名签名对账）

在制品 1/3。独占 `check_arg_keys.py`（单文件 ≤200 行零接线）+ `build/argkeysfix/` 夹具。

### 真洞判据（R83-b + R88-b）

Java 原语全部用具名 `args.has("K")` 读参，无位置参数。写歪键名时两把已有尺全绿：

- `check_skill_args.py:81-99` / `check_script_args.py:81-99` 只核 `conditions` 里 `health_below.ratio` / `chance.chance` 的 [0,1] 值域；键名写歪（`raito`）时 `key in args` 为假直接跳过。
- `check_skills.py` / `check_scripts.py` 只核 type ∈ 原语表、args 是对象。
- `check_effect_target.py` 只在 `target` 键存在时核枚举。

运行期静默（全零 WARN）：

- `MessageEffect.java:30`：`if(!args.has("text")) return;` → 欢迎消息永不出现。
- `HealthBelowCondition.java:25`：缺省 `0.5D` → 阈值从 0.9 悄悄变 0.5。
- `HealEffect.java:64`：`per_level` 缺省 0 → 每级加成静默消失。
- `DamageAoeEffect.java:94`：`falloff` 缺省 0.5 → 边缘伤害静默翻倍。

### 冻结表（16 原语，S7 从 `builtin/*.java` 逐字提取，S8 不读源码）

| 原语 | 合法 args 键 |
|---|---|
| message / console_log | text |
| heal | target, ratio, per_level, flat |
| damage | amount, damage_type, radius |
| damage_aoe | amount, radius, falloff, max_targets, damage_type |
| buff / debuff | target, effect, amplifier, duration_ticks |
| knockback / pull | strength, radius |
| stun | target, radius, duration_ticks |
| dash | power, up, direction |
| projectile | entity, speed, on_hit |
| grant_exp | amount, target |
| always | （空集） |
| health_below | ratio |
| chance | chance |

基线：13 处带 args 调用 / 6 文件 / 6 原语 / 0 问题（S7 离线核逐字一致）。

## 派前全类排除留证（两轮 Explore 只读）

第一轮候选全部排除：

- `%var.<key>%` 占位符：`ScriptContext.java:92-94` 注释明确「未定义键原样保留——写错应出现在日志里」，是有意可观测设计（非静默）；合法集由调用方动态注入，无法静态冻结。
- mobs skills chance / cooldown_ticks：clamp 类，弱于引用完整性。
- enchant slots：原版 `EquipmentSlot`，无离线冻结常量。

第二轮落 args 键名真洞。二次核实两把旧尺源码 + 4 处 Java 静默分支 + 16 类键集逐字提取。

## 口径（§13.106 R103-a~e）

- a：对账网第 30 例 = 具名参数键 ↔ 原语签名。
- b：源码注释写明「有意保留」的不是洞（读注释区分忘了处理 vs 有意如此）。
- c：合法集由调用方动态注入时不可静态冻结（宁可不派也不造误报尺）。
- d：冻结表从源码逐字抄。
- e：裸名纪律第二十一次。

## 环境

`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁。正式服 9376 常驻未动、测试服 25567/25577 无 LISTEN。
