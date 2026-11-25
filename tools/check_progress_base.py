"""离线对账 levels/ + jobs/ 的 curve.base 超硬顶静默夹顶（T-0123）。"""

import argparse
import json
import math
import os
import sys

BASE_DEFAULT = 100.0
MAX_STEP_EXP = 1000000000
SUBDIRS = ("levels", "jobs")


def _shape(text):
    """去注释、单引号归一为双引号（带转义处理）。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c in ('"', "'"):
            q = c
            out.append('"')
            i += 1
            while i < n:
                d = text[i]
                if d == '\\' and i + 1 < n:
                    e = text[i + 1]
                    out.append('\\"' if e == '"' else (e if e == "'" else '\\' + e))
                    i += 2
                elif d == q:
                    out.append('"')
                    i += 1
                    break
                elif d == '"':
                    out.append('\\"')
                    i += 1
                else:
                    out.append(d)
                    i += 1
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            while i < n and text[i] not in '\r\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '*':
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def _bare(s, j):
    """在 j 之后找 `标识符 :`，返回 (起, 止)，否则 None。"""
    n = len(s)
    while j < n and s[j].isspace():
        j += 1
    k = j
    while j < n and (s[j].isalnum() or s[j] in '_-'):
        j += 1
    if j == k:
        return None
    m = j
    while m < n and s[m].isspace():
        m += 1
    return (k, j) if m < n and s[m] == ':' else None


def _keys(s):
    """裸键加引号、去尾逗号，字符串内部不动。"""
    out = []
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == '"':
            out.append(c)
            i += 1
            while i < n:
                if s[i] == '\\' and i + 1 < n:
                    out.append(s[i:i + 2])
                    i += 2
                elif s[i] == '"':
                    out.append(s[i])
                    i += 1
                    break
                else:
                    out.append(s[i])
                    i += 1
            continue
        if c in ('{', ','):
            b = _bare(s, i + 1)
            if b:
                out.append(c)
                out.append('"' + s[b[0]:b[1]] + '"')
                i = b[1]
                continue
            j = i + 1
            while j < n and s[j].isspace():
                j += 1
            if j < n and s[j] in '}]':
                i += 1
                continue
        out.append(c)
        i += 1
    return ''.join(out)


def _loads(text):
    try:
        data = json.loads(_keys(_shape(text)))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_files(root, subdir):
    d = os.path.join(root, subdir)
    try:
        names = sorted(x for x in os.listdir(d) if x.endswith('.json5'))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(d, name), encoding='utf-8') as fh:
                data = _loads(fh.read())
        except (OSError, UnicodeError, ValueError):
            data = None
        rows.append((subdir + '/' + name, data))
    return rows, True


def check_base(rows):
    out = []
    for prefix, data in rows:
        if not isinstance(data, dict):
            continue
        curve = data.get('curve')
        if not isinstance(curve, dict) or 'base' not in curve:
            continue
        v = curve['base']
        if isinstance(v, bool) or not isinstance(v, (int, float)):
            continue
        try:
            if not math.isfinite(v):
                continue
        except OverflowError:
            pass
        if v <= 0 or v <= MAX_STEP_EXP:
            continue
        out.append('%s: curve.base 值 %s 超过 MAX_STEP_EXP=1000000000 静默夹顶（解析零 WARN）'
                   % (prefix, repr(v)))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default='src/main/resources/redtale/defaults')
    ap.add_argument('-v', action='store_true')
    a = ap.parse_args()
    rows = []
    problems = 0
    for sub in SUBDIRS:
        got, ok = read_files(a.dir, sub)
        if not ok:
            problems += 1
            print('[问题] %s 目录读取失败' % sub)
            continue
        rows.extend(got)
    msgs = check_base(rows)
    for m in msgs:
        problems += 1
        print('[问题] ' + m)
    if a.v:
        k = 0
        for _, data in rows:
            curve = data.get('curve') if isinstance(data, dict) else None
            if isinstance(curve, dict) and 'base' in curve:
                k += 1
        print('已检查 %d 份进度曲线 · %d 处 base 引用 · 缺省 100.0 · 硬顶 1000000000'
              % (len(rows), k))
    print('共 1 份进度曲线起点经验口径对账表 · %d 条问题' % problems)
    return 0 if problems == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
