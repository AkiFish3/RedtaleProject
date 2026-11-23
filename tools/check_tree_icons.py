"""离线对账：skill_trees/*.json5 顶层 icon 非字符串/空白静默回落。

只依赖标准库；自带 JSON5 shim，不 import 任何其它 check 模块。
"""

import json
import os
import re
import sys

DEFAULT_ICON = "minecraft:stone"

_BARE_KEY = re.compile(r'([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)')
_TRAILING_COMMA = re.compile(r',\s*([}\]])')


def _clean(text: str) -> str:
    """去 // 注释、单引号归一、裸键加引号、去尾逗号。"""
    out = []
    i = 0
    n = len(text)
    quote = None
    while i < n:
        c = text[i]
        if quote == "'":
            if c == "\\" and i + 1 < n:
                out.append(c)
                out.append(text[i + 1])
                i += 2
                continue
            if c == "'":
                quote = None
                out.append('"')
                i += 1
                continue
            if c == '"':
                out.append('\\"')
                i += 1
                continue
            out.append(c)
            i += 1
            continue
        if quote == '"':
            if c == "\\" and i + 1 < n:
                out.append(c)
                out.append(text[i + 1])
                i += 2
                continue
            if c == '"':
                quote = None
                out.append(c)
                i += 1
                continue
            out.append(c)
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            i += 2
            while i < n and text[i] not in "\r\n":
                i += 1
            continue
        if c == "'" or c == '"':
            quote = c
            out.append('"')
            i += 1
            continue
        out.append(c)
        i += 1
    s = "".join(out)
    s = _BARE_KEY.sub(r'\1"\2"\3', s)
    s = _TRAILING_COMMA.sub(r'\1', s)
    return s


def _loads(text: str) -> dict | None:
    try:
        data = json.loads(_clean(text))
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_rows(dir_path: str) -> tuple[list[tuple[str, object | None]], bool]:
    try:
        names = sorted(n for n in os.listdir(dir_path) if n.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), encoding="utf-8") as fh:
                data = _loads(fh.read())
        except (OSError, ValueError):
            data = None
        rows.append((name, data))
    return rows, True


def check_icons(rows: list[tuple[str, object | None]]) -> list[str]:
    problems = []
    for name, data in rows:
        if not isinstance(data, dict) or "icon" not in data:
            continue
        v = data["icon"]
        if isinstance(v, str) and v.strip():
            continue
        problems.append(
            f"{name}: icon 值 {repr(v)} 静默回落 {DEFAULT_ICON}（解析零 WARN）"
        )
    return sorted(problems)


def _verbose(rows: list[tuple[str, object | None]]) -> str:
    n = len(rows)
    k = sum(1 for _, data in rows if isinstance(data, dict) and "icon" in data)
    return (
        f"已检查 {n} 份技能树定义 · {k} 处 icon 引用 · "
        f"静默回落 {DEFAULT_ICON} · 解析零 WARN"
    )


def main() -> int:
    args = sys.argv[1:]
    dir_path = "src/main/resources/redtale/defaults"
    verbose = False
    i = 0
    while i < len(args):
        if args[i] == "--dir" and i + 1 < len(args):
            dir_path = args[i + 1]
            i += 2
        elif args[i] == "-v":
            verbose = True
            i += 1
        else:
            i += 1

    rows, ok = read_rows(os.path.join(dir_path, "skill_trees"))
    if not ok:
        print("[问题] 技能树目录读取失败")
        print("共 1 份技能树图标口径对账表 · 1 条问题")
        return 1

    problems = check_icons(rows)
    for msg in problems:
        print("[问题] " + msg)
    if verbose:
        print(_verbose(rows))
    print(f"共 1 份技能树图标口径对账表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
