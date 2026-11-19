# R74 · 2026-11-20 02:5x · 验 T-0069 + check_all 17→18 + 派 T-0070（清单安全网）

## 本轮结论

- **里程碑**：M0~M7 主体已落地，当前处于「运营安全网补齐 + 真人验收窗口挂账」阶段。本轮 `src/` 零 diff。
- **验收 T-0069**（`tools/check_display.py`）→ `VERIFIED`。
- **接线**：`tools/check_all.py` `TOOLS` 末尾追加 `check_display.py` → 18/18 全绿。
- **派单 T-0070**（`check_checkall.py`）→ `OPEN`，在制品 1/3。

## 验收 T-0069 证据

| 验收项 | 命令 / 期望 | 实测 |
|---|---|---|
| 1 基线 | `python tools/check_display.py` → `共 1 个文件 · 0 条问题` EXIT=0 | `共 1 个文件 · 0 条问题` EXIT=0；`-v` 含 `已检查 … · 6 项` |
| 2 负向夹具 | A=4 / B=2 / C=1 条，均 EXIT=1 | A=4（show_dps / show_attribute_breakdown / assume_target / 未知键 bogus_key 逐字对上）；B=2（空白 id + show_dps 非 bool）；C=1（仅「解析失败」不抛栈） |
| 3 静态 | wc≤200 / clean_json5=1 / 禁用 API=0 | 113 / 1 / 0 |

S7 独立边界 12/12：空对象缺键 / 三键全对 / 空串与纯空白 assume_target 回落 / 带点合法 id / 根节点 list 与 None / 只有 ns 无 path / 只有冒号 / 带大写 / False 合法 / 0 非 bool。夹具 `build/dispfix/` 已清。

## 派单 T-0070 接口（S7 定死）

`check_checkall.py`（新建，≤200 行，单文件只读 `.py` 文本，不解析 JSON5，零接线）：

- `read_listed(text: str) -> list[str]`：`TOOLS = (` 块内 `"*.py"` 字面量，找不到返回 `[]`。
- `read_disk(tools_dir: str) -> list[str]`：`check_*.py` 且排除 `check_all.py`，字典序。
- `check_listing(listed, disk) -> list[str]`：重复 → `清单项 <n> 重复`；清单有磁盘无 → `清单项 <n> 磁盘上不存在`；磁盘有清单无 → `脚本 <n> 未接进 check_all`。
- `main() -> int`：`--all` / `--dir` / `-v`；末行 `共 1 份清单 · %d 条问题`。

验收三条：基线 `清单 18 项 · 磁盘 18 项` 0 问题；三组负向夹具（漏接 2 / 幽灵 1 / 重复 1）；wc≤200 + 无清洗器 + git status 卡外零改动。

## 环境

- 无 Java 改动，未构建，未启停测试服/正式服；25567/25577/9376 均无 LISTEN。
- `check_all` 18/18；`check_docs` 89 份 358 引用 0 问题；`check_tasks` 70 卡在制品 1 问题 0。

## 口径

见 `PLAN.md` §13.77（R74-a 清单也要被对账 / R74-b 语义段定缺省 / R74-c 派单补最便宜的洞）。
