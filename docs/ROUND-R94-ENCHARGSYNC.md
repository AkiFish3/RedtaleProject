# R94 · 2026-11-21 11:2x~11:4x · 验 T-0089 + 接线 37→38 + 派 T-0090（附魔脚本参数同步）

## 1. 验收 T-0089 `check_gem_types.py` → VERIFIED

- 验收 1：`python tools/check_gem_types.py` → `共 1 份宝石孔位可达性表 · 0 条问题` EXIT=0；`-v` → `已检查 2 种宝石类型 · 2 种孔类型 · 7 份物品配置`。
- 验收 2：`build/gemfix/` 六组独立干净夹具 A=1（`offensiv`）· B=0 · C=0（`any` 通配）· D=1（`defensive` 无 sockets）· E=0（大小写归一）· F=1（`arcane`，`-v` 2/1/2）逐字 EXIT=1/0/0/1/0/1。
- 验收 3：wc=174≤200、`def unreachable`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条 `items 目录读取失败` EXIT=1 不抛栈。
- S7 独立边界 9/9：归一化 / 空值 / 非字符串 / `count>0` 补 `any` / 通配 / 排序 / 读失败 / 坏 JSON5。
- 首轮夹具探针两处 FAIL 均为 S7 探针期望写错（int 混进字符串比对、`count>0` 补位误算），按 R65-a 改探针后 6/6、9/9，不打回。

## 2. 接线

`tools/check_all.py` TOOLS 增加裸名 `check_gem_types.py`（R94 接）：`共 38 个脚本 · 跑成 38 · 跳过 0 · 红 0`。接线后 `check_checkall` 0 问题、`check_docs` 109 份/461 引用 0 问题、`check_tasks` 89 卡在制品 0、`check_contracts` 54 条、`check_rounds`/`check_ownership` 全 0。

## 3. 派 T-0090 —— 附魔 `scripted.args` ↔ 脚本数值同步对账

- 真洞（对账网第二十一例，首次核「跨文件同源数值脱钩」）：`ScriptedEnchantmentEffect.java:90` 把附魔 `args` 灌进 `ctx.vars()`，`ScriptContext.expand()` 只在**字符串模板**展开 `%var.*%`；`HealEffect.run()` 的 `ratio`/`per_level` 由 `num(args,key)` 读字面值（非数字才 WARN）。因此把 `soul_reap` 的 `heal_ratio` 从 0.08 改成 0.12：`console_log` 打 0.12，实际回血仍按脚本里的 0.08。无红、无 ERROR、全 check 绿。
- 两份已有检查都绿：`check_enchants._script_refs` 只收集 script id 是否存在，不读 args 数值；`check_script_args.check_args` 只核 conditions args 值域，不核 effects args；`check_effect_target` 只核 target 字符串。
- 派前全类排除留证（R88-c/R89-c/R90-c/R91-a 延续）：Explore 全量扫 40 份 json5 + Java 消费端 + 38 个 check，逐条排除 `damage.json5` 属性逻辑 id（check_numeric 已核）、`jobs.exp_sources`（有 WARN）、附魔 `slots`（原版 id）、技能数值/刷怪条件（有 clamp 或已覆盖）、`use.script[].args.target`（基线 0 引用、已覆盖对象）、`spawn_rules.match[]` / `repair.targets[]` / `passive.id`（留池/死代码）后才落本真洞；基线 1 调用/2 参数/1 脚本/0 问题。
- 默认契约（S7 定）：新参数两处必须同名；历史 `heal_ratio → ratio` 是冻结别名（`ALIASES` 写死，不推断）；脚本多出来的自有常量不报。尺子只判「两处写的值相等」，不引入 `%var%` 运行时解析、不改 Java。

## 4. 环境

`src/` 零 diff、纯 Python 轮次；未构建、未启停任何服、无构建锁。正式服 25565/25575（PID 9376）常驻未动、测试服 25567/25577 无 LISTEN。
