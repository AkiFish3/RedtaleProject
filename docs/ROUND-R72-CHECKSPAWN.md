# R72 · 验收 check_spawn + 接线 16→17 + 派 ConfigDiff

轮次：R72 · 2026-11-20 00:37-01:0x · 工作目录 `E:/RedtaleProject`

## 1. 卡板

### T-0067 `tools/check_spawn.py` → VERIFIED（3/3）

| # | 验收 | 实测 |
|---|---|---|
| 1 | 基线 | `python tools/check_spawn.py` → `共 1 个文件 · 0 条问题`，EXIT=0 |
| 2 | 负向/边界夹具 | 11 组全中：`bad` 恰好 3 条（replace_enabled 非布尔 / CAP 范围 / 未知键 bogus_key）；`frac`/`bool`/`str`/`nan`/`inf`/`flag`/`root`/`broken` 各恰好 1 条；`1.0`/`4096.0` 各 0 条；`-v` 首行 `已检查 <file> · 7 项` |
| 3 | 静态 | `wc`=51 ≤200；`from check_progress import clean_json5`=1；禁用 API grep=0；`git status` 除卡上文件外无 `src/` 改动 |

加测（卡面未列）：400 位整数 `{per_tick_cap: 999…9}` → 恰好 1 条、不抛栈。原因：`1 <= v <= 4096` 先短路，未执行 `math.isfinite`，避开 `float(huge_int)` 的 `OverflowError`（§13.75 R72-b）。

夹具 `build/spawnfix/` 验收后已删。

### T-0068 `core/config/ConfigDiff`（本轮派出，OPEN）

- 派生层：`between(Map<String,Long> before, Map<String,Long> after)` + `describe(Report)` + `record Report(added, removed, changed)`。
- 快照取值与日志打点留 S7 下轮（跨文件），卡面明写「不改 `ConfigStore` / `RedtaleCore` / 任何命令」。
- 在制品：1/3。

## 2. 本轮自研

- `tools/check_all.py` TOOLS 末尾追加 `check_spawn.py` → **`共 17 个脚本 · 跑成 17 · 跳过 0 · 红 0`**（EXIT=0）。
- **无 Java 改动**（`src/` 零 diff）→ 未构建、未启停任何服务器、无构建锁；正式服 9376 / 测试服 25567 均未动。
- `check_tasks` 68 卡 / 在制品 1 / 问题 0；`check_docs` 87 份 351 引用 0 问题；`check_deps` 0 问题。

## 3. 坑与口径

- **重复标题被机器抓到**：插入 T-0068 时标题写了两遍，`check_tasks` 报 `T-0068 重复定义（先出现在第 3644 行）`、在制品 2，`check_all` 连带 `红 1`；删除重复行后全绿（§13.75 R72-a）。
- 口径 §13.75 R72-a~c，下一步见 `PLAN.md` §13.75。

## 4. 挂账

- Q-021（respec 扣费）/ Q-022（M5 原语范围）无答复，按默认「免费」「按需补」走。
- 真人窗口 A~G 全组等 Aki，不排期。
