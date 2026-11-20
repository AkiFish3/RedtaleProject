# R86 · 2026-11-21 02:1x · 验 T-0081 + check_all 29→30 + 派 T-0082（脚本 conditions args 值域对账）

## 本轮结论

- 当前里程碑：对账网铺网期（内容里程碑 M0~M7 地基已稳，本轮属起飞前安全网）。
- 验收：T-0081 `check_skill_args.py` `[DONE]` → **VERIFIED**（3/3 + S7 独立边界 20/20）。
- 派单：T-0082 `check_script_args.py`（脚本 conditions args 值域对账，单文件零接线）。
- 自研：`check_all` 接线 29→30（30 脚本全绿）；派前覆盖面核实确认脚本层值是真洞。

## 验收 T-0081 证据

| 项 | 结果 |
|---|---|
| 验收 1 基线 | `python tools/check_skill_args.py` → `共 1 份技能条件值域表 · 0 条问题` EXIT=0；`-v` → `已检查 4 份技能 · 2 个值域键` |
| 验收 2 六夹具 | A ratio=1.5 / B chance=-0.2 / C ratio=true / D args=3 各逐字 1 条 EXIT=1；E always 任意 args / F 未知 type 各 0 条 EXIT=0；独立干净目录、用完即删 |
| 验收 3 静态 | wc=129≤200、`def check_args`=1、禁用 grep=0、`py_compile` OK、`--skills NO_SUCH` 恰好 1 条 `技能目录读取失败` EXIT=1 不抛栈 |
| S7 独立边界 | 20/20（None / conds 非数组 / 空 / 非 dict 元素 / ratio 0,1 边界合法 / 负值 / 字符串 / None / 缺失合法 / args 非 dict / chance 1 边界 / 超 1 / bool / always 任意 / 未知 type 跳过 / 多条累积 / 非 str type） |

## 派单 T-0082 依据（R84-b 覆盖面核实）

逐个查已有 check 的核对象，确认真洞：

| 已有检查 | 核到哪 | 缝隙 |
|---|---|---|
| `check_scripts.check_script` | conditions[].type 合法性 + args 是否 dict | **不进 args 内部值域** |
| 运行期 `HealthBelowCondition` / `ChanceCondition` | `getAsDouble()` 直接用，无 clamp | `ratio:1.5` 恒真、`chance:-0.2` 恒假、非数字抛异常 |
| `check_skill_args`（T-0081，技能层） | skills/ 的 ratio/chance 值域 | 与 scripts/ 是两套独立配置 + 两个独立 check，改一个不带动另一个 |

排除项（按 R85-d 运行期有夹不算静默洞）：`MobSpec.chance()`/`cooldown()` 有 `Math.max/min` clamp、脚本 effects 的 amount/radius 有 `clamp01` / `Math.min`。

派卡接口由 S7 定死（`read_scripts` / `check_args` / `main`），六夹具逐字、基线 2 脚本/0 问题、零接线。

## 环境

- `src/` 零 diff；未构建、未启停任何服、无构建锁。
- 正式服 25565/25575（PID 9376）常驻未动；测试服 25567/25577 无 LISTEN。

## 下一步（R87）

1. 验 T-0082，过后 `check_all` 30→31。
2. 对账网已第十三例；按 R86-b 先证运行期无夹再派，核实后无真洞转内容面。
3. Q-021/Q-022 无答复按默认走；真人窗口 A~G 等 Aki。
