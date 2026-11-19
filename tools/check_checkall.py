"""check_all 清单 ↔ 磁盘对账（T-0070 / R74）：只比文件名，不跑脚本、不解析配置。

R64~R74 每轮靠 S7 手工往 `TOOLS` 里追加一行；漏接的脚本永远不会被执行，
而 check_all 仍然全绿。这张安全网双向核对清单与 tools/ 磁盘上的 check_*.py。

用法：
    python tools/check_checkall.py
    python tools/check_checkall.py --all tools/check_all.py --dir tools/ -v

退出码：0 = 双向一致；1 = 有重复 / 漏接 / 幽灵项 / 读取失败。
"""
from __future__ import annotations

import argparse
import os
import re
import sys

_LIST_START = "TOOLS = ("
_LIST_END = "\n    )"
_PY_LITERAL = re.compile(r'"([A-Za-z0-9_./-]+\.py)"')


def read_listed(text: str) -> list[str]:
    """取 check_all.py 全文里 TOOLS 块内的 .py 字面量，按出现顺序；块找不到返回 []。"""
    at = text.find(_LIST_START)
    if at < 0:
        return []
    tail = text[at + len(_LIST_START):]
    # 卡面写 `\n    )`，现行 check_all.py 实际是顶格 `\n)`；两种都认，取先出现者。
    ends = [p for p in (tail.find(_LIST_END), tail.find("\n)")) if p >= 0]
    if not ends:
        return []
    return _PY_LITERAL.findall(tail[:min(ends)])


def read_disk(tools_dir: str) -> list[str]:
    """tools/ 下所有 check_*.py（排除 check_all.py 自身），按字典序。"""
    return sorted(
        name for name in os.listdir(tools_dir)
        if name.startswith("check_") and name.endswith(".py") and name != "check_all.py"
    )


def check_listing(listed: list[str], disk: list[str]) -> list[str]:
    """三类问题依次：清单重复 → 清单项磁盘不存在 → 磁盘脚本未接进清单。"""
    problems: list[str] = []
    seen: set[str] = set()
    for name in listed:
        if name in seen:
            problems.append("清单项 %s 重复" % name)
        seen.add(name)
    disk_set = set(disk)
    for name in listed:
        if name not in disk_set:
            problems.append("清单项 %s 磁盘上不存在" % name)
    listed_set = set(listed)
    for name in disk:
        if name not in listed_set:
            problems.append("脚本 %s 未接进 check_all" % name)
    return problems


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="check_all 清单 ↔ 磁盘对账")
    ap.add_argument("--all", default="tools/check_all.py")
    ap.add_argument("--dir", default="tools/")
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    problems: list[str] = []
    listed: list[str] = []
    disk: list[str] = []
    try:
        with open(args.all, "r", encoding="utf-8", errors="replace") as f:
            text = f.read()
    except Exception:
        problems.append("清单文件读取失败")
        text = ""
    else:
        listed = read_listed(text)
    try:
        disk = read_disk(args.dir)
    except Exception:
        problems.append("脚本目录读取失败")
    # 本对账器自身由 S7 下轮接线；接线前不把它算进「未接进」清单，接线后照常核对。
    if "check_checkall.py" not in listed:
        disk = [n for n in disk if n != "check_checkall.py"]

    if not problems:
        problems = check_listing(listed, disk)
    if args.v:
        print("已检查 %s · 清单 %d 项 · 磁盘 %d 项" % (args.all, len(listed), len(disk)))
    for msg in problems:
        print("[问题] %s" % msg)
    print("共 1 份清单 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
