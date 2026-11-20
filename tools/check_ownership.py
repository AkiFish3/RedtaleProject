"""在制品卡「独占文件」重叠对账（只读、零接线）。"""
from __future__ import annotations

import argparse
import re

HEAD_RE = re.compile(r"^### (T-\d{4}) · \[([A-Z]+)\]")
CLAIM_RE = re.compile(r"^\s*-\s*独占文件")
CHILD_RE = re.compile(r"^\s{2,}-\s")
TOKEN_RE = re.compile(r"`([^`]+)`")
WIP = {"OPEN", "DOING", "BOUNCED"}
DEFAULT_TASKS = "docs/TASKS.md"


def read_cards(path: str) -> list[tuple[str, str, int, list[str]]]:
    """取卡 id / 状态 / 标题行号 / body；模板 T-XXXX 与规则行不匹配。"""
    try:
        with open(path, "r", encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except (OSError, UnicodeError):
        return []
    cards: list[tuple[str, str, int, list[str]]] = []
    for i, line in enumerate(lines):
        m = HEAD_RE.match(line)
        if not m:
            continue
        end = i + 1
        while end < len(lines):
            if lines[end].startswith("### ") or lines[end].startswith("## "):
                break
            end += 1
        cards.append((m.group(1), m.group(2), i + 1, lines[i + 1:end]))
    return cards


def exclusive_files(body: list[str]) -> list[str]:
    """只取「独占文件」块内更深缩进子行的反引号 token，去重保序。"""
    out: list[str] = []
    seen: set[str] = set()
    base: int | None = None
    for line in body:
        if base is None:
            if CLAIM_RE.match(line):
                base = len(line) - len(line.lstrip())
            continue
        indent = len(line) - len(line.lstrip())
        if line.strip().startswith("- ") and indent <= base:
            break
        if not CHILD_RE.match(line):
            continue
        for token in TOKEN_RE.findall(line):
            path = token[2:] if token.startswith("./") else token
            path = path.rstrip("/")
            if path and path not in seen:
                seen.add(path)
                out.append(path)
    return out


def check_overlaps(claims: list[tuple[str, list[str]]]) -> list[str]:
    """同路径被 ≥2 张卡声明才报；卡 id 升序去重，路径升序。"""
    by_path: dict[str, set[str]] = {}
    for cid, files in claims:
        for path in files:
            by_path.setdefault(path, set()).add(cid)
    return [
        f"独占文件 {path} 被 {' / '.join(sorted(ids))} 同时声明"
        for path, ids in sorted(by_path.items())
        if len(ids) >= 2
    ]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tasks", default=DEFAULT_TASKS)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    cards = read_cards(args.tasks)
    if not cards:
        print("[问题] 卡板读取失败")
        print("共 1 份卡板 · 1 条问题")
        return 1

    claims: list[tuple[str, list[str]]] = []
    wip = 0
    for cid, state, _line, body in cards:
        if state not in WIP:
            continue
        wip += 1
        files = exclusive_files(body)
        if files:
            claims.append((cid, files))

    if args.v:
        print(f"已对账 {wip} 张在制品卡 · {sum(len(f) for _, f in claims)} 个独占文件声明")
    problems = check_overlaps(claims)
    for msg in problems:
        print(f"[问题] {msg}")
    print(f"共 1 份卡板 · {len(problems)} 条问题")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
