#!/usr/bin/env python3
"""掉落表 xp/min/max 绝对边界的离线对账（T-0106，零接线）。

只核显式整数形态的三个数值字段；非整数 / bool / 缺键跳过。
默认从工程根读取 src/main/resources/redtale/defaults/drops。
"""

import argparse
import json
import os
import re
import sys

XP_MIN = 0
XP_MAX = 1_000_000
COUNT_MIN = 0
COUNT_MAX = 64

DEFAULT_DIR = "src/main/resources/redtale/defaults/drops"


def _clean(text):
    """JSON5 → JSON：去 // 注释、单引号归一、裸键加引号、去尾逗号。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == '"' or ch == "'":
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


def read_drops(dir_path):
    try:
        names = sorted(n for n in os.listdir(dir_path)
                       if n.endswith(".json5") and os.path.isfile(os.path.join(dir_path, n)))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), encoding="utf-8") as handle:
                rows.append((name, _loads(handle.read())))
        except (OSError, UnicodeError):
            rows.append((name, None))
    return rows, True


def _is_int_number(v):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return False
    try:
        return float(v).is_integer()
    except (OverflowError, ValueError):
        return False


def check_values(rows):
    problems = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        if "xp" in data:
            v = data["xp"]
            if _is_int_number(v) and not (XP_MIN <= v <= XP_MAX):
                problems.append("%s: xp 值 %r 不在 [%d,%d]" % (name, v, XP_MIN, XP_MAX))
        drops = data.get("drops")
        if not isinstance(drops, list):
            continue
        for i, item in enumerate(drops):
            if not isinstance(item, dict):
                continue
            for field in ("min", "max"):
                if field not in item:
                    continue
                v = item[field]
                if _is_int_number(v) and not (COUNT_MIN <= v <= COUNT_MAX):
                    problems.append("%s: drops[%d].%s 值 %r 不在 [%d,%d]" % (
                        name, i, field, v, COUNT_MIN, COUNT_MAX))
    return sorted(problems)


def _verbose(rows):
    xp = sum(1 for _, data in rows if isinstance(data, dict) and "xp" in data)
    lo = hi = 0
    for _, data in rows:
        if not isinstance(data, dict) or not isinstance(data.get("drops"), list):
            continue
        for item in data["drops"]:
            if isinstance(item, dict):
                lo += "min" in item
                hi += "max" in item
    print("已检查 %d 张掉落表 · %d 处 xp 引用 · %d 处 min 引用 · %d 处 max 引用 · 边界 xp[%d,%d] count[%d,%d]" % (
        len(rows), xp, lo, hi, XP_MIN, XP_MAX, COUNT_MIN, COUNT_MAX))


def main(argv=None):
    parser = argparse.ArgumentParser(description="掉落表 xp/min/max 边界对账")
    parser.add_argument("--dir", default=DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args(argv)

    rows, ok = read_drops(args.dir)
    if args.v:
        _verbose(rows)
    if not ok:
        print("[问题] drops目录读取失败")
        print("共 1 份掉落数值边界口径对账表 · 1 条问题")
        return 1

    problems = check_values(rows)
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份掉落数值边界口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
