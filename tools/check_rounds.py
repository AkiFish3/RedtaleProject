#!/usr/bin/env python3
"""轮次索引 ↔ 轮次文档双向对账（离线单文件，零接线）。"""
from __future__ import annotations

import argparse
import os
import re

ROW_RE = re.compile(r"^\|\s*R\d+\s*\|")
DOC_RE = re.compile(r"`docs/(ROUND-[^`]+\.md)`")


def read_index(text: str) -> list[str]:
    """只取 `| R<n> |` 表格行里的 `docs/ROUND-*.md`，返回去后缀的名。"""
    out = []
    for line in text.splitlines():
        if not ROW_RE.match(line):
            continue
        match = DOC_RE.search(line)
        if match:
            out.append(match.group(1)[:-3])
    return out


def read_files(docs_dir: str) -> list[str]:
    """磁盘上的 ROUND-*.md 裸名，字典序；读不到返回空列表。"""
    try:
        names = os.listdir(docs_dir)
    except OSError:
        return []
    return sorted(n[:-3] for n in names if n.startswith("ROUND-") and n.endswith(".md"))


def check_index(index: list[str], files: list[str]) -> list[str]:
    """三类问题按序：未进索引 / 幽灵条目 / 重复指向。"""
    problems = []
    for name in sorted(set(files) - set(index)):
        problems.append(f"轮次文档 {name} 未进 ROUNDS 索引")
    for name in sorted(set(index) - set(files)):
        problems.append(f"索引条目 {name} 指向的文档不存在")
    for name in sorted({n for n in index if index.count(n) > 1}):
        problems.append(f"索引重复指向 {name}")
    return problems


def _read_text(path: str) -> str | None:
    try:
        with open(path, encoding="utf-8") as stream:
            return stream.read()
    except (OSError, UnicodeError):
        return None


def _dir_ok(path: str) -> bool:
    try:
        os.listdir(path)
        return True
    except OSError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description="轮次索引 ↔ 轮次文档双向对账")
    parser.add_argument("--docs", default="docs")
    parser.add_argument("--index", default=os.path.join("docs", "ROUNDS.md"))
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    index_path = args.index

    text = _read_text(index_path)
    index = read_index(text) if text is not None else []
    dir_ok = _dir_ok(args.docs)
    files = read_files(args.docs)

    if args.v:
        print(f"已对账 {len(index)} 条索引 · {len(files)} 份轮次文档")

    problems = []
    if text is None:
        problems.append("索引读取失败")
    if not dir_ok:
        problems.append("轮次目录读取失败")
    if text is not None and dir_ok:
        problems.extend(check_index(index, files))
    for message in problems:
        print(f"[问题] {message}")
    print(f"共 1 份轮次索引 · {len(problems)} 条问题")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
