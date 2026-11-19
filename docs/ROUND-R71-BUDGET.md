# R71 · SpawnBudget 收口验收 + 两处接线 + 两路径实测 + 派 check_spawn

轮次：R71（2026-11-19 23:3x，补完被中断的 R71）
当前里程碑：M6 刷怪替换（per_tick_cap 数值规则双份收口完成）+ M2/M3/M4/M7/D24 均已落地待真人

## 本轮做了什么

### 1. 开场：识别中断轮残留（R71-c）
- `git status` 开场即有未提交改动：T-0066 已标 `[VERIFIED]`、`SpawnReplacer.java`/`SpawnSettings.java` 已改（两处委托）、`SpawnBudget.java` 未跟踪；但 PLAN §13 / memory / commit 全停在 R70。
- 判定为上一轮执行到一半被切走。**先复核残留完整性再补台账**，不推倒重做。

### 2. 验收 T-0066 → VERIFIED（3/3）
- ①取锁 `gradle build --offline` RC=0（UP-TO-DATE，jar 682637 B）；javap 确认接线已编译进 jar：`SpawnReplacer.setPerTickCap` → `SpawnBudget.clamp`、`SpawnSettings.perTickCap` → `SpawnBudget.fromConfig`；jar 内 `SpawnBudget.class`=1、`wc`=50≤200。
- ②`python tools/probe_run.py SpawnBudgetProbe` 末行 `PASS 53 / FAIL 0`（S8 探针覆盖 clamp 8 形态含 Integer.MIN/MAX、fromConfig 11、takePerTick 12、ticksToDrain 10、describe 逐字 4、重复调用 5）。
- ③`net.minecraft`=0、禁用 IO/时钟 API=0、`git status` 仅卡上文件。

### 3. 接线（R70 计划，S7 独占 2 文件）
- `SpawnSettings.java`：`DEFAULT_PER_TICK_CAP` → `SpawnBudget.DEFAULT_CAP`；`perTickCap` 数值口径委托 `fromConfig`，告警文案与判罚条件不变，合法 8 不告警。
- `SpawnReplacer.java`：`setPerTickCap` 委托 `clamp`。

### 4. 测试服实测两路径语义（1 次启停 `Done (2.011s)`）
- **命令入口**：`/redtale spawn replace cap 0` → Brigadier `integer(1,4096)` 拒绝；`cap 9999` → 同样拒绝；`cap 5`/`cap 1` 正常写入 spawn.json5。
- **配置面 reload**：写 `per_tick_cap: 0`/`2.5`/`9999` → 全部回落 8 + 恰好 1 条告警；合法 `1`/`4096` 生效零告警；还原基线回 8 零告警。
- `validate 0`、redtale 零 ERROR；测试服已停（25567/25577 无 LISTEN），正式服 9376 未动，spawn.json5 基线已还原、临时备份已删。
- **R70 计划「cap 0 应得 1」按实测推翻**（R71-a）。

### 5. 派 T-0067（`check_spawn.py`，R72 验收）
- 单文件新建 ≤200 行，对账 `spawn.json5` 主配置 7 检查项（replace_enabled 布尔 / per_tick_cap 数字与 1~4096 整数 / 未知键 / 根类型 / 解析失败 / -v / 末行计数与退出码），复用 `check_progress.clean_json5`、零接线、`spawn_rules/` 仍归 `check_mobs.py`。
- 补的是「改配置不用起测试服就能看分歧」的离线空白。在制品 1/3。

## 证据
- jar：`build/libs/redtale_project-0.1.0.jar` 682637 B，`SpawnBudget.class`×1
- javap：`SpawnReplacer.setPerTickCap` 调 `SpawnBudget.clamp:(I)I`、`SpawnSettings.perTickCap` 调 `SpawnBudget.fromConfig:(D)I`
- 探针：`SpawnBudgetProbe` PASS 53 / FAIL 0
- 检查套件：`check_all` 16/16、`check_tasks` 67 卡在制品 1 问题 0、`check_docs` 86 份 349 引用 0 问题
- 测试服日志：`E:/server-test/r71_server.log`（Done 2.011s，已停）

## 口径
- 见 PLAN §13.74 R71-a~c（实测推翻预期 / 命令入口与配置 reload 分两条验 / 中断轮先核状态）。
