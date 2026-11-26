#!/usr/bin/env python3
"""附魔 slots 字段形态离线对账（JSON5 裸键 + 注释 + 尾逗号）。"""
import argparse
import json
import os
import sys

SLOT_KEY = "slots"
SUBDIR = "enchantments"
DEFAULT_DIR = "src/main/resources/redtale/defaults"


def _shape(text):
    out = []
    i = 0
    n = len(text)
    quote = ""
    while i < n:
        c = text[i]
        if quote:
            if c == "\\" and i + 1 < n:
                nxt = text[i + 1]
                if quote == "'" and nxt == "'":
                    out.append("'")
                else:
                    out.append(c)
                    out.append(nxt)
                i += 2
                continue
            if c == quote:
                out.append('"')
                quote = ""
            elif quote == "'" and c == '"':
                out.append('\\"')
            else:
                out.append(c)
            i += 1
            continue
        if c == "/" and i + 1 < n:
            if text[i + 1] == "/":
                i += 2
                while i < n and text[i] not in "\r\n":
                    i += 1
                continue
            if text[i + 1] == "*":
                i += 2
                while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                    i += 1
                i = min(i + 2, n)
                continue
        if c in "'\"":
            quote = c
            out.append('"')
        else:
            out.append(c)
        i += 1
    return "".join(out)


def _keys(text):
    out = []
    i = 0
    n = len(text)
    instr = False
    while i < n:
        c = text[i]
        if instr:
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 2
                continue
            if c == '"':
                instr = False
            i += 1
            continue
        if c == '"':
            instr = True
            out.append(c)
            i += 1
            continue
        if c in "{,":
            out.append(c)
            i += 1
            j = i
            while j < n and text[j] in " \t\r\n":
                j += 1
            if j < n and (text[j].isalpha() or text[j] == "_"):
                k = j
                while k < n and (text[k].isalnum() or text[k] in "_-"):
                    k += 1
                out.append('"' + text[j:k] + '"')
                i = k
            continue
        out.append(c)
        i += 1
    s = "".join(out)
    out = []
    i = 0
    n = len(s)
    instr = False
    while i < n:
        c = s[i]
        if instr:
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(s[i + 1])
                i += 2
                continue
            if c == '"':
                instr = False
            i += 1
            continue
        if c == '"':
            instr = True
            out.append(c)
            i += 1
            continue
        if c == ",":
            j = i + 1
            while j < n and s[j] in " \t\r\n":
                j += 1
            if j < n and s[j] in "}]":
                i += 1
                continue
        out.append(c)
        i += 1
    return "".join(out)


def _loads(text):
    try:
        data = json.loads(_keys(_shape(text)))
    except Exception:
        return None
    return data if isinstance(data, dict) else None


def read_files(root):
    d = os.path.join(root, SUBDIR)
    try:
        names = sorted(n for n in os.listdir(d) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(d, name), encoding="utf-8") as f:
                data = _loads(f.read())
        except OSError:
            data = None
        rows.append((SUBDIR + "/" + name, data))
    return rows, True


def check_slots(rows):
    out = []
    for prefix, data in rows:
        if not isinstance(data, dict) or SLOT_KEY not in data:
            continue
        v = data[SLOT_KEY]
        if v is None:
            continue
        if not isinstance(v, list):
            out.append(f"{prefix}: slots 值 {v!r} 不是数组（解析零 WARN 原样透传）")
        elif not v:
            out.append(f"{prefix}: slots 是空数组（解析零 WARN 原样透传）")
        else:
            for i, x in enumerate(v, 1):
                if not (isinstance(x, str) and x.strip()):
                    out.append(f"{prefix}: slots 第 {i} 项 值 {x!r} 不是非空字符串（解析零 WARN 原样透传）")
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    rows, ok = read_files(args.dir)
    problems = []
    if not ok:
        print("[问题] enchantments 目录读取失败")
        problems.append(1)
    msgs = check_slots(rows)
    for msg in msgs:
        print("[问题] " + msg)
    if args.v:
        k = sum(1 for _, d in rows if isinstance(d, dict) and SLOT_KEY in d)
        print(f"已检查 {len(rows)} 份附魔 · {k} 处 slots 引用 · 形态 非空数组 + 元素非空字符串")
    total = len(problems) + len(msgs)
    print(f"共 1 份附魔槽位字段口径对账表 · {total} 条问题")
    return 0 if total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
