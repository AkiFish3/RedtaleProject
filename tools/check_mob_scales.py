#!/usr/bin/env python3
"""怪物 scale 体积口径对账：显式 scale 必须是 [0.25,4.0] 内有限数字（bool 不算）。"""
import argparse
import json
import math
import os
import sys

SCALE_MIN = 0.25
SCALE_MAX = 4.0


def _clean(text):
    """// 去注释 + 单引号归一 + 裸键加引号 + 去尾逗号。"""
    out, stack, prev = [], [], ""
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            prev = '"'
            i = j
            continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    nxt = text[j + 1]
                    buf.append(nxt if nxt == "'" else "\\" + nxt)
                    j += 2
                else:
                    buf.append('\\"' if text[j] == '"' else text[j])
                    j += 1
            out.append('"' + "".join(buf) + '"')
            prev = '"'
            i = min(j + 1, n)
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c.isspace():
            out.append(c)
            i += 1
            continue
        if c in "{[":
            stack.append(c)
            out.append(c)
            prev = c
            i += 1
            continue
        if c in "}]":
            k = len(out) - 1
            while k >= 0 and out[k].isspace():
                k -= 1
            if k >= 0 and out[k] == ",":
                out[k] = ""
            if stack:
                stack.pop()
            out.append(c)
            prev = c
            i += 1
            continue
        if c in ",:":
            out.append(c)
            prev = c
            i += 1
            continue
        j = i
        while j < n and not text[j].isspace() and text[j] not in ',:{}[]"\'':
            if text[j] == "/" and j + 1 < n and text[j + 1] == "/":
                break
            j += 1
        if j == i:
            out.append(c)
            i += 1
            continue
        word = text[i:j]
        k = j
        while k < n and text[k].isspace():
            k += 1
        if prev in ("{", ",") and k < n and text[k] == ":":
            out.append('"' + word + '"')
            prev = '"'
        else:
            out.append(word)
            prev = word[-1]
        i = j
    return "".join(out)


def _loads(text):
    try:
        data = json.loads(_clean(text))
        return data if isinstance(data, dict) else None
    except Exception:
        return None


def read_mobs(dir_path):
    """扫目录下 *.json5（文件名升序），返 (rows, ok)；目录不可读返 ([], False)。"""
    try:
        names = sorted(os.listdir(dir_path))
    except OSError:
        return [], False
    rows = []
    for name in names:
        if not name.endswith(".json5"):
            continue
        path = os.path.join(dir_path, name)
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                data = _loads(fh.read())
        except Exception:
            data = None
        rows.append((name, data))
    return rows, True


def check_scales(rows):
    """scale 非有限数字或不在 [0.25,4.0] 产文案；文案升序、稳定、不去重。"""
    out = []
    for name, data in rows:
        if not isinstance(data, dict) or "scale" not in data:
            continue
        v = data["scale"]
        valid = (isinstance(v, (int, float)) and not isinstance(v, bool)
                 and math.isfinite(v) and SCALE_MIN <= v <= SCALE_MAX)
        if valid:
            continue
        out.append("%s: scale 值 %s 不在 [0.25,4.0]" % (name, repr(v)))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description="怪物 scale 体积口径对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults/mobs")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    rows, ok = read_mobs(args.dir)
    if not ok:
        print("[问题] mobs目录读取失败")
        print("共 1 份怪物体积口径对账表 · 1 条问题")
        return 1

    refs = sum(1 for _n, d in rows if isinstance(d, dict) and "scale" in d)
    if args.verbose:
        print("已检查 %d 份怪物 · %d 处 scale 引用 · 边界 [0.25,4.0]"
              % (len(rows), refs))

    problems = check_scales(rows)
    for msg in problems:
        print("[问题] %s" % msg)
    print("共 1 份怪物体积口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
