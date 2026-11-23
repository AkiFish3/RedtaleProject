"""Offline ruler for silent socket/gem fallbacks in items/*.json5."""
import json
import os
import sys

ANY = "any"
MAX_COUNT = 6


def _shape(text):
    out, mask = [], []
    q, i, n = None, 0, len(text)
    while i < n:
        c = text[i]
        if q:
            if c == "\\":
                if q == "'" and i + 1 < n and text[i + 1] == "'":
                    out.append("'"); mask.append(False); i += 2; continue
                out.append(c); mask.append(False); i += 1
                if i < n:
                    out.append(text[i]); mask.append(False); i += 1
                continue
            if c == q:
                out.append('"'); mask.append(False); q = None; i += 1; continue
            if q == "'" and c == '"':
                out.extend(('\\', '"')); mask.extend((False, False)); i += 1; continue
            out.append(c); mask.append(False); i += 1; continue
        if c in "\"'":
            q = c; out.append('"'); mask.append(False); i += 1; continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            i += 2
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i = min(i + 2, n); continue
        out.append(c); mask.append(True); i += 1
    return "".join(out), mask


def _loads(text):
    s, mask = _shape(text)
    out, i, n = [], 0, len(s)
    while i < n:
        c = s[i]
        if not mask[i]:
            out.append(c); i += 1; continue
        if c == ",":
            j = i + 1
            while j < n and s[j] in " \t\r\n" and mask[j]:
                j += 1
            if j < n and s[j] in "}]" and mask[j]:
                i += 1; continue
        if c in "{,":
            j = i + 1
            while j < n and s[j] in " \t\r\n" and mask[j]:
                j += 1
            if j < n and mask[j] and (s[j].isalpha() or s[j] == "_"):
                k = j + 1
                while k < n and mask[k] and (s[k].isalnum() or s[k] == "_"):
                    k += 1
                m = k
                while m < n and s[m] in " \t\r\n" and mask[m]:
                    m += 1
                if m < n and s[m] == ":" and mask[m]:
                    out.append(c); out.append('"' + s[j:k] + '"'); i = k; continue
        out.append(c); i += 1
    try:
        data = json.loads("".join(out))
    except Exception:
        return None
    return data if isinstance(data, dict) else None


def read_items(items_dir):
    try:
        names = sorted(f for f in os.listdir(items_dir) if f.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        prefix = "items/" + name
        try:
            with open(os.path.join(items_dir, name), encoding="utf-8") as fh:
                text = fh.read()
        except (OSError, UnicodeError):
            rows.append((prefix, None)); continue
        rows.append((prefix, _loads(text)))
    return rows, True


def check_fallbacks(rows):
    out = []
    for prefix, data in rows:
        if not isinstance(data, dict):
            continue
        sockets = data.get("sockets")
        if isinstance(sockets, dict):
            types, count = sockets.get("types"), sockets.get("count")
            if (isinstance(types, list) and isinstance(count, int)
                    and not isinstance(count, bool) and 0 < count <= MAX_COUNT
                    and len(types) < count):
                out.append("%s: sockets.types 长度 %d 小于 count %d，末位静默补 %s（解析零 WARN）"
                           % (prefix, len(types), count, ANY))
        gem = data.get("gem")
        if isinstance(gem, dict):
            gt = gem.get("type")
            if isinstance(gt, str) and gt.strip() == "":
                out.append("%s: gem.type 空白串静默回落 %s（解析零 WARN）" % (prefix, ANY))
            eg = gem.get("exclusive_group")
            if isinstance(eg, str) and eg.strip() == "":
                out.append("%s: gem.exclusive_group 空白串静默回落空串，互斥失效（解析零 WARN）" % prefix)
    return sorted(out)


def main():
    args = sys.argv[1:]
    root, verbose, i = "src/main/resources/redtale/defaults", False, 0
    while i < len(args):
        if args[i] == "--dir" and i + 1 < len(args):
            root, i = args[i + 1], i + 2; continue
        if args[i] == "-v":
            verbose = True
        i += 1
    rows, ok = read_items(os.path.join(root, "items"))
    n = s = g = 0
    if ok:
        n = len(rows)
        s = sum(1 for _, d in rows if isinstance(d, dict) and isinstance(d.get("sockets"), dict))
        g = sum(1 for _, d in rows if isinstance(d, dict) and isinstance(d.get("gem"), dict))
    problems = check_fallbacks(rows) if ok else []
    if verbose:
        print("已检查 %d 份物品 · %d 处 sockets 布局 · %d 处 gem 定义 · 3 个静默回落落点" % (n, s, g))
    if not ok:
        print("[问题] items 目录读取失败")
    else:
        for msg in problems:
            print("[问题] " + msg)
    print("共 1 份孔位回落口径对账表 · %d 条问题" % (len(problems) if ok else 1))
    return 0 if ok and not problems else 1


if __name__ == "__main__":
    sys.exit(main())
