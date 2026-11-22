#!/usr/bin/env python3
"""掉落表 rolls 上界对账：显式整数必须在 [1,16]；缺键/非整数/小于下界跳过。"""
import argparse
import json
import os
import sys

ROLLS_MIN = 1
ROLLS_MAX = 16


def _clean(text):
    """去 // 注释、单引号归一、裸键加引号、去尾逗号。"""
    out, prev = [], ""
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
        if c in "{[,:":
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


def read_drops(dir_path):
    """扫 *.json5（文件名升序），返 (rows, ok)；目录不可读返 ([], False)。"""
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


def check_rolls(rows):
    """显式整数超上界产文案；按文案升序、稳定、不去重。"""
    out = []
    for name, data in rows:
        if not isinstance(data, dict) or not isinstance(data.get("drops"), list):
            continue
        for i, item in enumerate(data["drops"]):
            if not isinstance(item, dict) or "rolls" not in item:
                continue
            v = item["rolls"]
            if not isinstance(v, int) or isinstance(v, bool):
                continue
            if ROLLS_MIN <= v <= ROLLS_MAX or v < ROLLS_MIN:
                continue
            out.append("%s: drops[%d].rolls 值 %s 不在 [%d,%d]"
                       % (name, i, repr(v), ROLLS_MIN, ROLLS_MAX))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description="掉落表 rolls 上界口径对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults/drops")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    rows, ok = read_drops(args.dir)
    if not ok:
        print("[问题] drops目录读取失败")
        print("共 1 份掉落掷骰次数口径对账表 · 1 条问题")
        return 1

    refs = 0
    for _n, d in rows:
        if isinstance(d, dict) and isinstance(d.get("drops"), list):
            refs += sum(1 for x in d["drops"]
                        if isinstance(x, dict) and "rolls" in x)
    if args.verbose:
        print("已检查 %d 张掉落表 · %d 处 rolls 引用 · 边界 [%d,%d]"
              % (len(rows), refs, ROLLS_MIN, ROLLS_MAX))

    problems = check_rolls(rows)
    for msg in problems:
        print("[问题] %s" % msg)
    print("共 1 份掉落掷骰次数口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
