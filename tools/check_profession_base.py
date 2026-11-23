"""Offline ruler for professions/*.json5 curve.base silent rewrite / clamp.

Read-only. No imports from other check modules. No Java / jar / server access.
"""

import argparse
import json
import math
import os
import re

BASE_DEFAULT = 100.0
MAX_STEP_EXP = 1_000_000_000


def _clean(text):
    """Minimal JSON5 cleanup: comments, single quotes, bare keys, trailing commas."""
    text = re.sub(r"//[^\n]*", "", text)
    out, i, in_double = [], 0, False
    while i < len(text):
        c = text[i]
        if c == '"':
            esc, j = 0, i - 1
            while j >= 0 and text[j] == "\\":
                esc += 1
                j -= 1
            if esc % 2 == 0:
                in_double = not in_double
            out.append(c)
            i += 1
        elif c == "'" and not in_double:
            j, buf = i + 1, []
            while j < len(text) and text[j] != "'":
                buf.append('\\"' if text[j] == '"' else text[j])
                j += 1
            out.append('"' + "".join(buf) + '"')
            i = j + 1
        else:
            out.append(c)
            i += 1
    text = "".join(out)
    text = re.sub(r"([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:", r'\1"\2":', text)
    return re.sub(r",\s*([}\]])", r"\1", text)


def _loads(text):
    try:
        data = json.loads(_clean(text))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_professions(dir_path):
    try:
        names = sorted(n for n in os.listdir(dir_path) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), encoding="utf-8") as fh:
                data = _loads(fh.read())
        except (OSError, UnicodeError):
            data = None
        rows.append((name, data))
    return rows, True


def check_base(rows):
    problems = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        curve = data.get("curve")
        if not isinstance(curve, dict) or "base" not in curve:
            continue
        v = curve["base"]
        if isinstance(v, bool) or not isinstance(v, (int, float)):
            continue
        if isinstance(v, float) and not math.isfinite(v):
            continue
        if v < 0:
            continue
        if v == 0:
            problems.append(
                f"{name}: curve.base 值 0 静默回落 {BASE_DEFAULT}（解析零 WARN）"
            )
        elif v > MAX_STEP_EXP:
            problems.append(
                f"{name}: curve.base 值 {repr(v)} 超过 "
                f"MAX_STEP_EXP={MAX_STEP_EXP} 静默夹顶（解析零 WARN）"
            )
    return sorted(problems)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults/professions")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    rows, ok = read_professions(args.dir)
    if not ok:
        print("[问题] professions目录读取失败")
        print("共 1 份专业起点经验口径对账表 · 1 条问题")
        return 1
    if args.v:
        k = sum(
            1
            for _, data in rows
            if isinstance(data, dict)
            and isinstance(data.get("curve"), dict)
            and "base" in data["curve"]
        )
        print(
            f"已检查 {len(rows)} 份专业 · {k} 处 base 引用 · "
            f"缺省 {BASE_DEFAULT} · 硬顶 {MAX_STEP_EXP}"
        )
    problems = check_base(rows)
    for msg in problems:
        print(f"[问题] {msg}")
    print(f"共 1 份专业起点经验口径对账表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
