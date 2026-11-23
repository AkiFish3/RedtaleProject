"""Offline ruler for explicit consumable use integer fields lost to (int) conversion."""

import argparse
import json
import math
import os
import re

MAX_RESTORE_FOOD = 20
MAX_INT = 2147483647


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


def _candidate(v, path):
    """Return (iv, message-ready path) for an explicit numeric value, else None."""
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return None
    if not math.isfinite(v) or v > MAX_INT:
        return None
    iv = int(v)
    if path == "use.duration" or path.endswith(".duration"):
        ok = iv >= 1
    elif path == "use.amplifier" or path.endswith(".amplifier"):
        ok = iv >= 0
    elif path == "use.restore_food":
        ok = 0 <= iv <= MAX_RESTORE_FOOD
    elif path == "use.cooldown_ticks":
        ok = iv >= 0
    else:
        return None
    return (iv, path) if ok else None


def check_ints(rows):
    problems = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        use = data.get("use")
        if not isinstance(use, dict):
            continue
        fields = []
        for key in ("restore_food", "cooldown_ticks"):
            if key in use:
                fields.append((use[key], "use." + key))
        effects = use.get("effects")
        if isinstance(effects, list):
            for index, item in enumerate(effects):
                if not isinstance(item, dict):
                    continue
                for key in ("duration", "amplifier"):
                    if key in item:
                        fields.append(
                            (item[key], "use.effects[%d].%s" % (index, key))
                        )
        for v, path in fields:
            candidate = _candidate(v, path)
            if candidate is None:
                continue
            iv, path = candidate
            if v != iv:
                problems.append(
                    "%s: %s 值 %s 被 (int) 静默截断（解析零 WARN）"
                    % (name, path, repr(v))
                )
    return sorted(problems)


def _ref_count(rows):
    total = 0
    for _, data in rows:
        if not isinstance(data, dict):
            continue
        use = data.get("use")
        if not isinstance(use, dict):
            continue
        total += sum(1 for key in ("restore_food", "cooldown_ticks") if key in use)
        effects = use.get("effects")
        if isinstance(effects, list):
            for item in effects:
                if isinstance(item, dict):
                    total += sum(1 for key in ("duration", "amplifier") if key in item)
    return total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults/items")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    rows, ok = read_items(args.dir)
    if args.v:
        print(
            "已检查 %d 份物品 · %d 处 use 整数引用 · 截断 (int) · 静默零 WARN"
            % (len(rows), _ref_count(rows))
        )
    if not ok:
        print("[问题] items目录读取失败")
        print("共 1 份消耗品整数口径对账表 · 1 条问题")
        return 1
    problems = check_ints(rows)
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份消耗品整数口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
