# R83 · 验 T-0078 + check_all 26→27 + 派 T-0079（伤害类型引用 ↔ 注册对账）

## 1. 验收 T-0078（`check_event_registration.py`）

- `[DONE]` → **`[VERIFIED]`**。验收 1 基线 `共 1 份事件总线 · 0 条问题` EXIT=0、`-v` 含 `已检查 8 个监听器 · 8 个已注册`。
- 三夹具各自干净目录：A（A.java 带 @SubscribeEvent + Wiring 注册）0 条；B（加未注册的 B.java）恰好 1 条 `事件监听器 B 有 @SubscribeEvent 但未注册到事件总线`；C（加注释掉的 register 行）仍恰好同一条。EXIT=0/1/1，逐字对上。
- 静态：wc=139 ≤ 200、`def check_registration`=1、禁用 grep=0、`py_compile` OK、`--java NO_SUCH` 恰好 1 条 `事件总线读取失败` EXIT=1 不抛栈。
- S7 独立边界 9/9：行注释 / 块注释保留换行 / 字符串内 `//` 按注释 / 未注册按名升序 / 部分注册 / 空集 / register 参数 token 提取 / 小写字段别名 / 自注册形态。首轮 2 FAIL 是 S7 夹具期望写错（块注释保留 `\n` 是接口「不删空行」的正确行为），按 R65-a 改期望后 9/9，非代码缺陷。`build/eventfix/` 已清。

## 2. 接线 + 文档修正

- `check_all.py` 的 `TOOLS` 末尾加 `check_event_registration.py` → **`共 27 个脚本 · 跑成 27 · 跳过 0 · 红 0`**（EXIT=0）。接线后 `check_checkall -v` 27/27。
- 修 R82 ROUND 文档里一处已清空具路径引用（`check_docs` 1 红 → 0）：`docs/ROUND-R82-EVENTREG.md` 第 5 行引用已删的夹具快照路径，改为占位符。起飞前对账必须零红才提交。

## 3. 派 T-0079（伤害类型引用 ↔ datapack 注册对账）

元层洞第十例。真实静默失败链：脚本 `damage` 原语 `args.damage_type` 写歪 → `DamageEffect.source()` 只 `warnOnce` 后回退缺省 / `generic` → `DamageLayer` 白名单（`redtale_project:*`）不认 → 整套属性乘区静默不生效。编译绿、`check_scripts` 绿（只验 type 是已注册原语）、`check_numeric` 绿（只验属性 id），第三份检查才把引用字符串对到 `data/redtale_project/damage_type/` 注册表。

- 接口 `read_registered` / `read_refs` / `check_refs` / `main` 由 S7 定死；三类引用（skills 的 damage / damage_aoe 原语、scripts、damage.json5 的 category_attributes）统一取裸 id，`minecraft:` 命名空间跳过。
- 基线 S7 先离线跑判定逻辑核过：4 处引用（skills 2 + damage.json5 category 2）/ 4 个注册项（generic + 3 datapack）/ 0 问题。派卡前核基线数字，防 S8 按错误期望交付（R82-c）。
- 单文件 ≤ 200 行、零接线、在制品 1/3。

## 4. 环境

本轮 `src/` 零 diff、未构建、未启停任何服、无构建锁；25567/25577/9376 无 LISTEN。`check_all` 27/27、`check_tasks` 79 卡在制品 1 问题 0。

## 5. 口径

见 `PLAN.md` §13.86 R83-a~c。
