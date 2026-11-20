# R87 · 2026-11-21 03:1x · 验 T-0082 + check_all 30→31 + 派 T-0083（效果 target 枚举值域对账）

## 本轮结论

- 当前里程碑：对账网铺网期（内容里程碑 M0~M7 地基已稳，本轮属起飞前安全网）。
- 验收：T-0082 `check_script_args.py` `[DONE]` → **VERIFIED**（3/3，S8 自测与 S7 复跑逐字一致）。
- 派单：T-0083 `check_effect_target.py`（效果 `target` 字符串枚举值域对账，单文件零接线，对账网第十四例）。
- 自研：`check_all` 接线 30→31；派前逐字读五个 Effect 的 `pick()` 确认真洞与合法值集合。

## 验收 T-0082 证据

| 项 | 结果 |
|---|---|
| 验收 1 基线 | `python tools/check_script_args.py` → `共 1 份脚本条件值域表 · 0 条问题` EXIT=0；`-v` → `已检查 2 份脚本 · 2 个值域键` |
| 验收 2 六夹具 | A ratio=1.5 / B chance=-0.2 / C ratio=true / D args=3 各逐字 1 条 EXIT=1；E always 任意 args / F 未知 type 各 0 条 EXIT=0；独立干净目录、用完即删 |
| 验收 3 静态 | wc=129≤200、`def check_args`=1、禁用 grep=0、`py_compile` OK、`--scripts NO_SUCH` 恰好 1 条 `脚本目录读取失败` EXIT=1 不抛栈 |

## 派单 T-0083 依据（R84-b 覆盖面核实 + R86-b 先证无夹）

逐个查已有 check 的核对象，确认真洞：

| 已有检查 | 核到哪 | 缝隙 |
|---|---|---|
| `check_scripts.check_script` / `check_skills.check_skill` | effects[].type 已注册 + args 是否 dict | **不进 `args.target` 值域** |
| `check_script_args` / `check_skill_args`（T-0082/0081） | conditions 的 ratio/chance 值域 | 只进 conditions，不进 effects |
| 运行期五个 Effect 的 `pick()` | `buff`/`grant_exp`/`heal` 缺省 caster、`debuff`/`stun` 缺省 target；合法值只有 `caster`/`target` | 拼错字符串（`taret`/`CAST`/大写）静默改作用对象；除「非字符串 warnOnce」外无 clamp、无 WARN |

真洞成立：编译绿、四类 check 全绿、运行期效果悄悄打错对象、无人被叫醒（R83-b 判据）。数值键（amount/radius/duration）有 `clamp01`/`Math.min` 运行期夹，按 R85-d 排除；卡面只核字符串枚举键。

**形态差异**：这是同一把尺子从「数值值域」第一次扩到「字符串枚举」（R87-a），且五个原语缺省方向相反但合法值集合相同（R87-b）——对账只核「写了必须合法」，不替内容作者选「该写哪个」（取舍归 S7）。

派卡接口由 S7 定死（`read_configs` / `check_effects` / `main`），六夹具逐字、基线 2 脚本/4 技能/1 个 target 命中、零接线。

## 环境

- `src/` 零 diff；未构建、未启停任何服、无构建锁。
- 正式服 25565/25575（PID 9376）常驻未动；测试服 25567/25577 无 LISTEN。
- 全部验收为纯 Python 静态检查，无 jar / 测试服动作。

## 下一步（R88）

1. 验 T-0083，过后 `check_all` 30→32。
2. 对账网已第十四例；按 R80-b / R84-b / R85-d / R86-b / R87-a 判据核实后无真洞转内容面，不硬凑。
3. Q-021/Q-022 无答复按默认走；真人窗口 A~G 等 Aki。
