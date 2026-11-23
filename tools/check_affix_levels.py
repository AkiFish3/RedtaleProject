"""词条 item_level.min/max 小数 (int) 静默截断对账：离线、单文件、零接线。"""

import argparse
import json
import math
import os
import re

MIN_DEFAULT = 1
MAX_DEFAULT = 60
MAX_LEVEL = 2147483647
_DEF_SUB = "affixes"
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


def _is_number(value):
    return (
        not isinstance(value, bool)
        and isinstance(value, (int, float))
        and math.isfinite(value)
    )


def check_levels(def_rows):
    """只报显式小数的静默截断；结果升序、保稳定、不去重。"""
    problems = []
    for name, data in def_rows:
        if not isinstance(data, dict):
            continue
        level = data.get("item_level")
        if not isinstance(level, dict):
            continue
        lo = level.get("min")
        hi = level.get("max")
        inverted = (
            "min" in level and "max" in level
            and _is_number(lo) and _is_number(hi) and lo > hi
        )
        if inverted:
            continue
        for key in ("min", "max"):
            if key not in level:
                continue
            value = level[key]
            if not _is_number(value) or value > MAX_LEVEL or value == int(value):
                continue
            problems.append(
                "%s: item_level.%s 值 %r 被 (int) 静默截断（解析零 WARN）"
                % (name, key, value)
            )
    return sorted(problems)


def _ref_count(def_rows):
    total = 0
    for _name, data in def_rows:
        if isinstance(data, dict) and isinstance(data.get("item_level"), dict):
            level = data["item_level"]
            total += sum(key in level for key in ("min", "max"))
    return total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=_DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    def_rows, def_ok = read_rows(os.path.join(args.dir, _DEF_SUB))
    if args.v:
        print(
            "已检查 %d 份词条定义 · %d 处 item_level 引用 · 截断 (int) · 静默零 WARN"
            % (len(def_rows), _ref_count(def_rows))
        )

    failed = 0
    if not def_ok:
        print("[问题] 词条目录读取失败")
        failed += 1
    problems = check_levels(def_rows)
    for message in problems:
        print("[问题] %s" % message)
    total = failed + len(problems)
    print("共 1 份词条等级口径对账表 · %d 条问题" % total)
    return 1 if total else 0


if __name__ == "__main__":
    raise SystemExit(main())
