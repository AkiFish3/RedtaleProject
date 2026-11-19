# R73 · 验收 T-0068（ConfigDiff）+ reload 变更摘要接线 + 派 T-0069

轮次：R73 · 2026-11-20 01:4x–02:0x

## 卡板

- **T-0068 `core/config/ConfigDiff` `[DONE]` → VERIFIED**（3/3）：
  1. `gradle build --offline` RC=0（S7 重跑）；jar 内 `core/config/ConfigDiff` 条目 **2**（类 + Report）；`wc`=**35** ≤200。
  2. 探针 `javac`+运行 `PASS 37 / FAIL 0`（空 / 双 null / 只增 / 只删 / 只改 / 混合 / 排序 / 不可变 UOE / 值 null 当 0L / 入参不改 / 重复一致 / describe 逐字含零段与 null 分支）。
  3. grep `net.minecraft|Files.|Path.of|currentTimeMillis` = **0**；`src/` 除卡上新文件外零改动。
  - S7 另跑 6 条独立边界（自比较 / 不可变 Map 入参 / 空串+中文+英文混合排序 / 负数 mtime 同与不同）全过；首跑 `sort.mixed` FAIL 是**夹具期望写错**（把两边都有的空串算进 added），修正期望后 6/6，非代码缺陷。
- **派 T-0069 `check_display.py`**（display.json5 起飞前对账，D20 显示口径安全网，单文件零接线）。在制品 1/3。卡面钉死 6 检查项 + 3 组负向夹具 + 复用 `check_progress.clean_json5` + 不接 `check_all`。

## S7 自研：D14 reload 变更摘要（CONTRACTS §4.43）

接线 3 文件（S8 独占文件 VERIFIED 后归 S7）：

- `ConfigStore`：+ import `java.util.Map`；+ `mtimes()` 扫 `config/redtale_project/**/*.json5`（与 loadAll 同一集合：仅 .json5、跳过 `_` 开头），返回「相对路径(/分隔) → mtime(ms)」，单文件 mtime 读失败跳过不拖垮 reload。
- `RedtaleCore`：+ import `ConfigDiff`；+ 字段 `lastConfigMtimes`（上次成功 reload 快照）；`reload(boolean)` 在 `outcome.clean()` 分支取本次快照 → `ConfigDiff.between(last, snapshot)` → 仅 `allowResourceReload=true`（显式 reload）打 `配置变更：{describe}` → 推进字段。

### 关键修正：跨 reload 差分，不是单次内两次快照

台账 R72-next 初稿写「ensureDefaults 后取一次、解析完成再取一次」——两次都在同一次 reload 内、中间无外部改动则恒等，**永远全 0**，验不出任何形态。按 R71-a「实测推翻预期」改为跨 reload 存字段对比（上次成功快照 ↔ 本次快照）。

### 测试服实测（1 启停 `Done 2.089s`，已停）

- 启动期：无「配置变更」行（符合「只在显式 reload 打印」）。
- 基线 reload：`配置变更：新增 0 · 删除 0 · 变更 0`。
- 新增 `probe_add.json5` + 改 `display.json5` 的 `show_dps` + 删 `crit.json5` → `新增 1 · 删除 0 · 变更 2`。
  - **删默认件不算删除**：`ensureDefaults()` 重建 `crit.json5` → mtime 变 → 算「变更」。要验删除必须用非默认件。
- 零改动再 reload：`新增 0 · 删除 0 · 变更 0`（双 reload 归零）。
- 删非默认件 `probe_add.json5` → `新增 0 · 删除 1 · 变更 0`。
- 下划线文件 `_probe.json5`：跳过 → `新增 0 · 删除 0 · 变更 0`。
- `validate` 问题 0；redtale ERROR 行 0。
- 收尾：配置目录与备份零差异，夹具已清；25567/25577 无 LISTENING；正式服 9376 未动。

## 口径（§13.76）

- **R73-a**：快照差分必须跨 reload（存字段），单次 reload 内两次扫描恒等 = 永远绿的死检查（R65-a 同源）。
- **R73-b**：删默认件被 `ensureDefaults()` 重建 → 计入「变更」不是「删除」；验收删除形态只能用非默认件。
- **R73-c**：自造探针夹具先核期望（本次 S7 把两边都有的空串算进 added，假红一次）——夹具本身也要按被测语义独立推一遍。

## 环境

- jar 686121 B；build RC=0（2 次：T-0068 验 + 接线验，构建锁取删 2 次）。
- `check_all` 17/17 全绿；`check_tasks` 共 69 卡 · 在制品 1 · 问题 0。
- 测试服已停、端口释放；正式服未动。
- 下轮：验 T-0069（过后 `check_all` 17→18）；Q-021/022 仍无 Aki 答复；真人 A~G 等 Aki。
