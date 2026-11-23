"""Offline ruler for explicit item durability values lost to (int) conversion."""

import argparse
import json
import math
import os
import re

MAX_DURABILITY = 2147483647


def _clean(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        if c in ("'", '"'):
            quote = c
            i += 1
            buf = []
            while i < n:
                ch = text[i]
                if ch == "\\" and i + 1 < n:
                    buf.append(ch)
                    buf.append(text[i + 1])
                    i += 2
                    continue
                if ch == quote:
                    i += 1
                    break
                if quote == "'" and ch == '"':
                    buf.append('\\"')
                else:
                    buf.append(ch)
                i += 1
            out.append('"' + "".join(buf) + '"')
            continue
        out.append(c)
        i += 1
    s = "".join(out)
    s = re.sub(r"([{,]\s*)([A-Za-z_][A-Za-z0-9_-]*)\s*:", r'\1"\2":', s)
    s = re.sub(r",\s*([}\]])", r"\1", s)
    return s


def _loads(text):
    try:
        data = json.loads(_clean(text))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_items(dir_path):
    try:
        names = sorted(n for n in os.listdir(dir_path) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), encoding="utf-8") as handle:
                data = _loads(handle.read())
        except (OSError, UnicodeError, ValueError):
            data = None
        rows.append((name, data))
    return rows, True


def check_durability(rows):
    problems = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        if "durability" not in data:
            continue
        v = data["durability"]
        if isinstance(v, bool) or not isinstance(v, (int, float)):
            continue
        if not math.isfinite(v) or v < 0:
            continue
        if v > MAX_DURABILITY:
            problems.append(
                "%s: durability 值 %s 超过 Integer.MAX_VALUE=2147483647 静默夹顶（解析零 WARN）"
                % (name, repr(v))
            )
        elif v != int(v):
            problems.append(
                "%s: durability 值 %s 被 (int) 静默截断（解析零 WARN）"
                % (name, repr(v))
            )
    return sorted(problems)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults/items")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    rows, ok = read_items(args.dir)
    if args.v:
        refs = sum(
            1
            for _, data in rows
            if isinstance(data, dict) and "durability" in data
        )
        print(
            "已检查 %d 份物品 · %d 处 durability 引用 · 截断 (int) · 硬顶 2147483647"
            % (len(rows), refs)
        )
    if not ok:
        print("[问题] items目录读取失败")
        print("共 1 份物品耐久口径对账表 · 1 条问题")
        return 1
    problems = check_durability(rows)
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份物品耐久口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
