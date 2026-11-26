#!/usr/bin/env python3
"""离线核附魔 min_cost / max_cost 的形态：Cost 对象 + 两整数字段。"""
import argparse
import json
import os
import sys

COST_KEYS = ("min_cost", "max_cost")
FIELDS = ("base", "per_level_above_first")
SUBDIR = "enchantments"


def _shape(text):
    out, mask = [], []

    def put(s, q=False):
        for ch in s:
            out.append(ch)
            mask.append(q)

    i, n = 0, len(text)
    quote = None
    while i < n:
        c = text[i]
        if quote:
            if c == "\\" and i + 1 < n:
                put("'" if text[i + 1] == "'" else c + text[i + 1], True)
                i += 2
                continue
            if c == quote:
                put('"', True)
                quote = None
            elif c == '"':
                put('\\"', True)
            else:
                put(c, True)
            i += 1
            continue
        if c in ("'", '"'):
            quote = c
            put('"', True)
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                if text[i] == "\n":
                    put("\n")
                i += 1
            i += 2
            continue
        put(c)
        i += 1
    return "".join(out), mask


def _keys(text, mask):
    out = []
    i, n = 0, len(text)
    while i < n:
        if not mask[i] and text[i] == ",":
            j = i + 1
            while j < n and text[j].isspace():
                j += 1
            if j < n and text[j] in "}]":
                i += 1
                continue
        if not mask[i] and text[i] in "{,":
            j = i + 1
            while j < n and text[j].isspace():
                j += 1
            k = j
            while k < n and (text[k].isalnum() or text[k] == "_"):
                k += 1
            m = k
            while m < n and text[m].isspace():
                m += 1
            if k > j and m < n and text[m] == ":" and not mask[m]:
                out.append(text[i])
                out.append('"' + text[j:k] + '"')
                i = k
                continue
        out.append(text[i])
        i += 1
    return "".join(out)


def _loads(text):
    try:
        norm, mask = _shape(text)
        data = json.loads(_keys(norm, mask))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_files(root):
    folder = os.path.join(root, SUBDIR)
    try:
        names = sorted(n for n in os.listdir(folder) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(folder, name), encoding="utf-8") as fh:
                data = _loads(fh.read())
        except (OSError, UnicodeError):
            data = None
        rows.append((SUBDIR + "/" + name, data))
    return rows, True


def check_costs(rows):
    out = []
    for prefix, data in rows:
        if not isinstance(data, dict):
            continue
        for key in COST_KEYS:
            if key not in data or data[key] is None:
                continue
            v = data[key]
            if not isinstance(v, dict):
                out.append("%s: %s 值 %r 不是 Cost 对象（口径 {base, per_level_above_first}，解析零 WARN 原样透传）" % (prefix, key, v))
                continue
            for fld in FIELDS:
                x = v.get(fld)
                if fld not in v:
                    out.append("%s: %s 缺 %s（口径 {base, per_level_above_first}，解析零 WARN 原样透传）" % (prefix, key, fld))
                elif not (isinstance(x, int) and not isinstance(x, bool)):
                    out.append("%s: %s.%s 值 %r 不是整数形态（解析零 WARN，原样透传进 datapack）" % (prefix, key, fld, x))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    rows, ok = read_files(args.dir)
    problems = [] if ok else ["enchantments 目录读取失败"]
    problems += check_costs(rows)
    if args.v:
        k = sum(1 for _, d in rows if isinstance(d, dict) for key in COST_KEYS if key in d)
        print("已检查 %d 份附魔 · %d 处代价字段引用 · 字段 min_cost/max_cost · 形态 base/per_level_above_first 整数" % (len(rows), k))
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份附魔代价字段口径对账表 · %d 条问题" % len(problems))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
