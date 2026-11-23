"""词条与词条池权重 (int) 静默截断对账：离线、单文件、零接线。"""

import argparse
import json
import math
import os
import re

KINDS = ("prefix", "suffix", "core")
MAX_WEIGHT = 2147483647
_DEF_SUB = "affixes"
_POOL_SUB = "affix_pools"
_DEFAULT_DIR = "src/main/resources/redtale/defaults"
_BARE_KEY = re.compile(r"([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)")
_TRAILING = re.compile(r",\s*([}\]])")


def _loads(text):
    """自带 JSON5 shim：// 注释、单引号、裸键、尾逗号。"""
    out = []
    i = 0
    n = len(text)
    quote = None
    while i < n:
        ch = text[i]
        if quote:
            if ch == "\\" and i + 1 < n:
                nxt = text[i + 1]
                if quote == "'" and nxt == "'":
                    out.append("'")
                else:
                    out.append(ch)
                    out.append(nxt)
                i += 2
                continue
            if ch == quote:
                out.append('"')
                quote = None
                i += 1
                continue
            if quote == "'" and ch == '"':
                out.append('\\"')
                i += 1
                continue
            out.append(ch)
            i += 1
            continue
        if ch in ('"', "'"):
            quote = ch
            out.append('"')
            i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            i += 2
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        out.append(ch)
        i += 1
    cleaned = _TRAILING.sub(r"\1", _BARE_KEY.sub(r'\1"\2"\3', "".join(out)))
    try:
        data = json.loads(cleaned)
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_rows(dir_path):
    """扫目录 *.json5，文件名升序；不可读返 ([], False)，单文件坏值记 None。"""
    try:
        names = sorted(
            name for name in os.listdir(dir_path)
            if name.endswith(".json5") and os.path.isfile(os.path.join(dir_path, name))
        )
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), "r", encoding="utf-8") as fh:
                data = _loads(fh.read())
        except (OSError, ValueError, TypeError):
            data = None
        rows.append((name, data))
    return rows, True


def _report(name, path, value, problems):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return
    if not math.isfinite(value) or value > MAX_WEIGHT or value <= 0:
        return
    iv = int(value)
    if value != iv or iv <= 0:
        problems.append(
            "%s: %s 值 %r 被 (int) 静默截断（解析零 WARN）" % (name, path, value)
        )


def check_weights(def_rows, pool_rows):
    """只报显式正数的静默截断；结果升序、保稳定、不去重。"""
    problems = []
    for name, data in def_rows:
        if isinstance(data, dict) and "weight" in data:
            _report(name, "weight", data["weight"], problems)
    for name, data in pool_rows:
        if not isinstance(data, dict):
            continue
        for kind in KINDS:
            segment = data.get(kind)
            if not isinstance(segment, dict):
                continue
            for key, value in segment.items():
                _report(name, "%s.%s" % (kind, key), value, problems)
    return sorted(problems)


def _ref_count(def_rows, pool_rows):
    total = 0
    for _name, data in def_rows:
        if isinstance(data, dict) and "weight" in data:
            total += 1
    for _name, data in pool_rows:
        if isinstance(data, dict):
            for kind in KINDS:
                segment = data.get(kind)
                if isinstance(segment, dict):
                    total += len(segment)
    return total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=_DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    def_rows, def_ok = read_rows(os.path.join(args.dir, _DEF_SUB))
    pool_rows, pool_ok = read_rows(os.path.join(args.dir, _POOL_SUB))
    if args.v:
        print(
            "已检查 %d 份词条定义 · %d 份词条池 · %d 处权重引用 · 截断 (int) · 静默零 WARN"
            % (len(def_rows), len(pool_rows), _ref_count(def_rows, pool_rows))
        )

    failed = 0
    if not def_ok:
        print("[问题] 词条目录读取失败")
        failed += 1
    if not pool_ok:
        print("[问题] 词条池目录读取失败")
        failed += 1
    problems = check_weights(def_rows, pool_rows)
    for message in problems:
        print("[问题] %s" % message)
    total = failed + len(problems)
    print("共 1 份词条权重口径对账表 · %d 条问题" % total)
    return 1 if total else 0


if __name__ == "__main__":
    raise SystemExit(main())
