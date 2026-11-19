# R75 · 2026-11-20 04:0x · 验收 T-0070（清单安全网）+ check_all 18→19 + 派 T-0071（命令树挂载对账）

本轮无 Java 改动、未构建、未启停任何服务器（25567 / 25577 / 9376 全程无 LISTEN）、无构建锁。全部验收为纯 Python 静态检查。

## 1. 卡板

### T-0070 `check_checkall.py` `[DONE]` → **`[VERIFIED]`**（3/3）

1. 基线：`python tools/check_checkall.py` → `共 1 份清单 · 0 条问题` EXIT=0；`-v` → `已检查 tools/check_all.py · 清单 18 项 · 磁盘 18 项`（接线前基线 18/18）。
2. S7 自建三组负向夹具（写到 `build/chkfix/`，复制清单 + 磁盘各自改）：
   - A 删清单 `check_display.py` / `check_spawn.py` → 恰好 2 条 `脚本 … 未接进 check_all` EXIT=1；
   - B 清单塞 `check_ghost.py`（磁盘无）→ 恰好 1 条 `清单项 … 磁盘上不存在` EXIT=1；
   - C 清单 `check_tasks.py` 写两遍 → 恰好 1 条 `清单项 … 重复` EXIT=1。三类逐字对上，夹具已清。
3. 静态：`wc -l`=105≤200、`grep -c "def check_listing"`=1、禁用模式 grep=0（`clean_json5` / `check_progress` / `net.minecraft` / `check_all.*append` 全无）。

S7 另跑 9 条独立边界全过：TOOLS 块缩进闭合与顶格闭合两种都认 / 无块与空块返回 `[]` / 接线后自身纳入 / `check_listing` 三类问题顺序（重复→磁盘不存在→未接进）/ 空集 / 清单读取失败 1 条且不抛栈。

**裁定 S8 报的两处漂移**：均采纳实现、不打回（见 §13.78 R75-a/b）。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾追加 `"check_checkall.py",`（R75 接上）→ **`共 19 个脚本 · 跑成 19 · 跳过 0 · 红 0`**。接线后 `check_checkall.py -v` → `清单 19 项 · 磁盘 19 项`（自身排除逻辑自动失效、纳入双向核对），仍 0 问题 EXIT=0。

## 3. 派 T-0071（在制品 1/3）

`check_commands.py`（新建 ≤200，单文件零接线）：命令树挂载安全网。动机与 T-0070 同源——`core/command/` 加一个 `*Command.java` 却漏在 `RedtaleCommand` 挂一行 → 命令永远不存在，`build` / `check_all` 仍全绿。双向对账「磁盘命令类」↔「根命令引用集」。

接口 `read_classes` / `read_refs`（先剥注释）/ `check_mounts`（两类问题：未挂进 → `无对应命令类`）/ `main`（`--cmd` / `--root` / `-v`）全部由 S7 给定。当前基线：20 个命令类、20 个外部引用（`RedtaleCommand` 自身排除）。验收 3 条全可机器判。**接线归 S7 下轮**（不在本卡）。

## 4. 环境

- `check_all` **19/19**、`check_tasks` **71 卡 · 在制品 1 · 问题 0**、`check_docs` 91 份 365 引用 0 问题、`check_contracts` 44 条 0 问题。
- 补推 R74 挂账：`1dc43f3` 本轮 `git push` RC=0 成功（`HEAD...origin/main` = 0/0），未 force / 未 rebase。
- Q-021 / Q-022 仍无 Aki 答复，默认「免费」「按需补」继续走；Q-023 已收口。真人窗口 A~G 全组等 Aki 上线，不排期。
