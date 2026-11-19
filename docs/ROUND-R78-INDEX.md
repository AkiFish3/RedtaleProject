# R78 · 验 T-0073（生成器前缀）+ 接线 check_all 21→22 + 派 T-0074（轮次索引对账）

时间：2026-11-20 07:2x。工作目录 `E:\RedtaleProject`。

## 1. 验收 T-0073 → VERIFIED

`check_gen_prefix.py`（S8 T-0073 交付，129 行）三条验收全过：

1. 基线 `共 1 份生成器清单 · 0 条问题` EXIT=0；`-v` 含 `已检查 4 个生成器`。
2. 三组负向夹具**各自从干净副本重来**（首跑把 A/B 叠在一起得 2 条，已纠正为独立夹具）：A=1 条前缀写歪、B=1 条未声明、C=1 条行注释掉声明，EXIT=1 逐字对上。
3. `wc`=129 ≤200、`def check_prefixes`=1、禁用 API grep=0；`--tools build/genfix/NO_SUCH` 恰 1 条 `生成器目录读取失败`，不抛栈。

S7 另跑 9 组独立边界 9/9：行注释/块注释假声明不计、单引号不算、第一个匹配胜出、赋值两侧空白容忍、`MY_KEY_PREFIX` 前缀不符不算、空文本返回 None、`found` 多余键忽略、按 `expected` 键序输出、`None` 不追加第二类问题。夹具 `build/genfix/` 已清。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾追加 `check_gen_prefix.py` → `共 22 个脚本 · 跑成 22 · 红 0`。接线后 `check_checkall -v` 为 `清单 22 项 · 磁盘 22 项`，仍 0 问题（R75-b 的自排除按接线时刻失效，二次验证正确）。

## 3. 派单 T-0074（轮次索引 ↔ 轮次文档）

卡片：`check_rounds.py`（L6 新建）≤200 行，零接线、单文件纯 Python。`docs/ROUNDS.md` 现有 76 行表格 + 磁盘 76 份 `ROUND-*.md`（本轮新增本文件后），S7 写死接口 `read_index` / `read_files` / `check_index` / `main`；三类问题 = 未进索引 / 幽灵路径 / 重复指向。禁用：`check_docs` 导入、硬编码轮次名或基线数字、改 `check_all.py` / `ROUNDS.md`、解析 MarkdownAST。

为什么派它：`check_docs.py` 对「未被引用轮次文档」只给 `[提示]`、不影响退出码，索引少一行/多一行幽灵全绿无人知。这是元层洞第五例——清单（T-0070）→命令挂载（T-0071）→原语注册（T-0072）→生成器前缀（T-0073）→轮次索引（T-0074），同一张「名单 ↔ 磁盘实体」的网换对象再套一次。

## 4. 环境与痕迹

本轮 `src/` 零 diff → 未构建、未启停任何服、无构建锁。`check_all` 22/22、`check_tasks` 75 卡在制品 1、`check_docs` 与 `check_contracts` 见收尾实测。口径记 `PLAN.md` §13.81。
