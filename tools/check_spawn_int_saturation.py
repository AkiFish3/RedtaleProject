#!/usr/bin/env python3
"""刷怪规则大整数静默饱和的离线对账（T-0118，零接线）。

只核显式写了「有限整数但超 Java int 范围」的 priority / per_chunk_cap；
运行期 SpawnRuleSpec.integer() 会对这类值静默夹到 int 边界、解析零 WARN。
非数字 / bool / 小数 / NaN / Inf / 缺键一律跳过。
默认从工程根读取 src/main/resources/redtale/defaults/spawn_rules。
"""

import argparse
import json
import math
import os
import re
import sys

INT_MIN = -2147483648
INT_MAX = 2147483647
FIELDS = ("priority", "per_chunk_cap")

DEFAULT_DIR = "src/main/resources/redtale/defaults"


def _clean(text):
    """JSON5 → JSON：去注释、单引号归一、裸键加引号、去尾逗号。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch in "\"'":
            quote = ch
            out.append('"')
            i += 1
            while i < n:
                c = text[i]
                if c == "\\" and i + 1 < n:
                    nxt = text[i + 1]
                    if quote == "'":
                        out.append('\\"' if nxt == "'" else ('"' if nxt == '"' else c + nxt))
                    else:
                        out.append(c + nxt)
                    i += 2
                    continue
                if c == quote:
                    out.append('"')
                    i += 1
                    break
                out.append('\\"' if (quote == "'" and c == '"') else c)
                i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(ch)
        i += 1
    s = "".join(out)
    s = re.sub(r"([\{,]\s*)([A-Za-z_][A-Za-z0-9_-]*)(\s*:)",
               lambda m: m.group(1) + '"' + m.group(2) + '"' + m.group(3), s)
    return re.sub(r",\s*([}\]])", r"\1", s)


def _loads(text):
    try:
        data = json.loads(_clean(text))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_rules(rules_dir):
    try:
        names = sorted(n for n in os.listdir(rules_dir)
                       if n.endswith(".json5") and os.path.isfile(os.path.join(rules_dir, n)))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(rules_dir, name), encoding="utf-8") as handle:
                rows.append(("spawn_rules/" + name, _loads(handle.read())))
        except (OSError, UnicodeError):
            rows.append(("spawn_rules/" + name, None))
    return rows, True


def _saturates(v):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return False
    try:
        f = float(v)
    except (OverflowError, ValueError):
        return False
    if not math.isfinite(f) or not f.is_integer():
        return False
    return v < INT_MIN or v > INT_MAX


def check_rules(rows):
    out = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        for field in FIELDS:
            if field not in data:
                continue
            v = data[field]
            if _saturates(v):
                out.append("%s: %s 值 %s 超 int 范围，运行期静默饱和（解析零 WARN）" % (
                    name, field, str(v)))
    return sorted(out)


def _verbose(rows, problems):
    points = 0
    for _, data in rows:
        if isinstance(data, dict):
            points += sum(1 for f in FIELDS if f in data)
    print("已检查 %d 份规则 · %d 处整数落点 · %d 个静默饱和" % (len(rows), points, len(problems)))


def main(argv=None):
    parser = argparse.ArgumentParser(description="刷怪规则整数静默饱和对账")
    parser.add_argument("--dir", default=DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args(argv)

    rows, ok = read_rules(os.path.join(args.dir, "spawn_rules"))
    problems = check_rules(rows)
    if args.v:
        _verbose(rows, problems)
    total = len(problems)
    if not ok:
        print("[问题] spawn_rules 目录读取失败")
        total += 1
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份刷怪整数饱和口径对账表 · %d 条问题" % total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
