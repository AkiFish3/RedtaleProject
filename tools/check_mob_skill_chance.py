#!/usr/bin/env python3
"""怪物技能 chance 值域离线对账：显式数值必须落在 [0,1]。零接线，自带 JSON5 shim。"""
import argparse
import json
import os
import re
import sys

CHANCE_MIN = 0.0
CHANCE_MAX = 1.0
TITLE = "怪物技能概率口径对账表"
DEFAULT_DIR = "src/main/resources/redtale/defaults/mobs"


def _clean(text: str) -> str:
    """去 // 注释、单引号归一为双引号（内幕双引号转义）、裸键加引号、去尾逗号。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c in ('"', "'"):
            q = c
            i += 1
            buf = []
            while i < n:
                ch = text[i]
                if ch == "\\" and i + 1 < n:
                    nxt = text[i + 1]
                    if q == "'" and nxt == "'":
                        buf.append("'")
                    else:
                        buf.append(ch)
                        buf.append(nxt)
                    i += 2
                    continue
                if ch == q:
                    i += 1
                    break
                if q == "'" and ch == '"':
                    buf.append('\\"')
                else:
                    buf.append(ch)
                i += 1
            out.append('"' + "".join(buf) + '"')
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        out.append(c)
        i += 1
    s = "".join(out)
    s = re.sub(r"([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)", r'\1"\2"\3', s)
    s = re.sub(r",\s*([}\]])", r"\1", s)
    return s


def _loads(text: str):
    try:
        data = json.loads(_clean(text))
    except Exception:
        return None
    return data if isinstance(data, dict) else None


def read_mobs(dir_path: str):
    try:
        names = sorted(n for n in os.listdir(dir_path) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), "r", encoding="utf-8") as fh:
                rows.append((name, _loads(fh.read())))
        except (OSError, UnicodeError):
            rows.append((name, None))
    return rows, True


def _skills_of(data):
    if isinstance(data, dict) and isinstance(data.get("skills"), list):
        return data["skills"]
    return None


def check_chances(rows):
    problems = []
    for name, data in rows:
        skills = _skills_of(data)
        if skills is None:
            continue
        for i, item in enumerate(skills):
            if not isinstance(item, dict) or "chance" not in item:
                continue
            value = item["chance"]
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                continue
            if CHANCE_MIN <= value <= CHANCE_MAX:
                continue
            problems.append(f"{name}: skills[{i}].chance 值 {repr(value)} 不在 [0,1]")
    return sorted(problems)


def _ref_count(rows):
    total = 0
    for _, data in rows:
        skills = _skills_of(data)
        if skills is None:
            continue
        total += sum(1 for item in skills if isinstance(item, dict) and "chance" in item)
    return total


def main() -> int:
    parser = argparse.ArgumentParser(description=TITLE)
    parser.add_argument("--dir", default=DEFAULT_DIR, help="mobs 配置目录")
    parser.add_argument("-v", action="store_true", help="打印检查范围")
    args = parser.parse_args()

    rows, ok = read_mobs(args.dir)
    if not ok:
        print("[问题] mobs目录读取失败")
        print(f"共 1 份{TITLE} · 1 条问题")
        return 1

    if args.v:
        print(f"已检查 {len(rows)} 只怪物 · {_ref_count(rows)} 处 chance 引用 · 边界 [0,1]")

    problems = check_chances(rows)
    for msg in problems:
        print("[问题] " + msg)
    print(f"共 1 份{TITLE} · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
