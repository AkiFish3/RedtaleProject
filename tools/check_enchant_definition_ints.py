"""Offline checker for non-integer definition field shapes."""
import argparse
import json
import os
import sys

INT_KEYS = ("weight", "anvil_cost")
SUBDIR = "enchantments"


def _shape(text):
    out, mask, i, n = [], [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                out.append("\n" if text[i] == "\n" else " ")
                mask.append(False)
                i += 1
            i = min(i + 2, n)
            continue
        if c in ('"', "'"):
            q, i = c, i + 1
            out.append('"')
            mask.append(True)
            while i < n:
                ch = text[i]
                if ch == "\\" and i + 1 < n:
                    nxt = text[i + 1]
                    if q == "'" and nxt == "'":
                        out.append("'")
                        mask.append(True)
                    else:
                        out.extend(("\\", nxt))
                        mask.extend((True, True))
                    i += 2
                    continue
                if ch == q:
                    out.append('"')
                    mask.append(True)
                    i += 1
                    break
                if ch == '"' and q == "'":
                    out.append("\\")
                    mask.append(True)
                out.append(ch)
                mask.append(True)
                i += 1
            continue
        out.append(c)
        mask.append(False)
        i += 1
    return "".join(out), mask


def _smask(s):
    m, ins, esc = [False] * len(s), False, False
    for i, ch in enumerate(s):
        if ins:
            m[i] = True
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                ins = False
        elif ch == '"':
            ins = True
            m[i] = True
    return m


def _keys(s, mask):
    out, i, n = [], 0, len(s)
    while i < n:
        if not mask[i] and s[i] in "{,":
            j = i + 1
            while j < n and s[j] in " \t\r\n":
                j += 1
            k = j
            while k < n and (s[k].isalnum() or s[k] in "_$"):
                k += 1
            if k > j and (s[j].isalpha() or s[j] in "_$"):
                m = k
                while m < n and s[m] in " \t\r\n":
                    m += 1
                if m < n and s[m] == ":":
                    out.append(s[i:j])
                    out.append('"' + s[j:k] + '"')
                    i = k
                    continue
        out.append(s[i])
        i += 1
    return "".join(out)


def _strip(s):
    mask, out, i, n = _smask(s), [], 0, len(s)
    while i < n:
        if s[i] == "," and not mask[i]:
            j = i + 1
            while j < n and s[j] in " \t\r\n":
                j += 1
            if j < n and s[j] in "}]":
                i += 1
                continue
        out.append(s[i])
        i += 1
    return "".join(out)


def _loads(text):
    try:
        shaped, mask = _shape(text)
        data = json.loads(_strip(_keys(shaped, mask)))
    except Exception:
        return None
    return data if isinstance(data, dict) else None


def read_files(root):
    try:
        names = sorted(n for n in os.listdir(os.path.join(root, SUBDIR))
                       if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(root, SUBDIR, name), encoding="utf-8") as f:
                data = _loads(f.read())
        except (OSError, UnicodeError):
            data = None
        rows.append((SUBDIR + "/" + name, data))
    return rows, True


def check_ints(rows):
    out = []
    for prefix, data in rows:
        if not isinstance(data, dict):
            continue
        for key in INT_KEYS:
            if key in data and isinstance(data[key], float):
                out.append(
                    prefix + ": " + key + " 值 " + repr(data[key])
                    + " 不是整数形态（解析零 WARN，原样透传进 datapack）"
                )
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("-v", action="store_true")
    ns = ap.parse_args()
    rows, ok = read_files(ns.dir)
    bad = [] if ok else ["enchantments 目录读取失败"]
    msgs = check_ints(rows)
    if ns.v:
        k = sum(1 for _, d in rows if isinstance(d, dict)
                for key in INT_KEYS if key in d)
        print(f"已检查 {len(rows)} 份附魔 · {k} 处整数字段引用 · 字段 weight/anvil_cost · 非整数形态 float 计")
    for msg in bad + msgs:
        print("[问题] " + msg)
    total = len(bad) + len(msgs)
    print(f"共 1 份附魔定义整数字段口径对账表 · {total} 条问题")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
