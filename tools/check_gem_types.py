"""宝石类型孔位可达性对账（T-0089）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations

import argparse
import json
import os
import sys

from check_progress import clean_json5

ANY = "any"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = "src/main/resources/redtale/defaults"


def _loads(text: str):
    """单引号字面量归一化后交 clean_json5（沿用既有对账脚本口径）。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(text[i:j])
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            i = j
            continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1] if text[j + 1] == "'" else text[j:j + 2])
                    j += 2
                    continue
                buf.append('\\"' if text[j] == '"' else text[j])
                j += 1
            out.append('"' + "".join(buf) + '"')
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return json.loads(clean_json5("".join(out)))


def read_items(root: str) -> list[tuple[str, object, str | None]] | None:
    """扫 <root>/items/*.json5（文件名升序），返回 (rel, data, err)。"""
    folder = os.path.join(root, "items")
    if not os.path.isdir(folder):
        return None
    try:
        names = sorted(n for n in os.listdir(folder) if n.endswith(".json5"))
    except Exception:
        return None
    rows: list[tuple[str, object, str | None]] = []
    for name in names:
        rel = "items/" + name
        path = os.path.join(folder, name)
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                data = _loads(fh.read())
        except Exception as exc:
            rows.append((rel, None, "配置解析失败：" + str(exc)))
            continue
        if not isinstance(data, dict):
            rows.append((rel, None, "顶层非对象"))
            continue
        rows.append((rel, data, None))
    return rows


def _nonblank(value) -> bool:
    return isinstance(value, str) and bool(value.strip())


def gem_types(rows) -> set[str]:
    """归一化宝石 type；字段缺失/空串/非字符串跳过。"""
    out: set[str] = set()
    for _rel, data, err in rows:
        if err is not None or not isinstance(data, dict):
            continue
        gem = data.get("gem")
        if not isinstance(gem, dict):
            continue
        t = gem.get("type")
        if _nonblank(t):
            out.add(t.strip().lower())
    return out


def socket_types(rows) -> set[str]:
    """归一化孔类型；开放词汇不枚举，count>0 且 types 缺失/非数组补 any。"""
    out: set[str] = set()
    for _rel, data, err in rows:
        if err is not None or not isinstance(data, dict):
            continue
        sockets = data.get("sockets")
        if not isinstance(sockets, dict):
            continue
        types = sockets.get("types")
        if isinstance(types, list):
            for v in types:
                if _nonblank(v):
                    out.add(v.strip().lower())
            continue
        count = sockets.get("count")
        if isinstance(count, int) and not isinstance(count, bool) and count > 0:
            out.add(ANY)
    return out


def unreachable(gems: set[str], sockets: set[str]) -> list[str]:
    """无孔可收的宝石类型（升序）；any 在场则全可达。"""
    if not gems or ANY in sockets:
        return []
    return sorted(t for t in gems if t != ANY and t not in sockets)


def _first_file(rows, target: str) -> str:
    """宝石类型首次出现的物品文件 rel（rows 已按文件名升序）。"""
    for rel, data, err in rows:
        if err is not None or not isinstance(data, dict):
            continue
        gem = data.get("gem")
        if not isinstance(gem, dict):
            continue
        t = gem.get("type")
        if _nonblank(t) and t.strip().lower() == target:
            return rel
    return "items/"


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="宝石类型孔位可达性对账")
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    rows = read_items(a.dir)
    if rows is None:
        print("[问题] items 目录读取失败")
        print("共 0 份宝石孔位可达性表 · 1 条问题")
        return 1
    problems: list[str] = []
    for rel, _data, err in rows:
        if err is not None:
            problems.append(f"{rel} 配置读取失败：{err}")
    gems = gem_types(rows)
    sockets = socket_types(rows)
    for t in unreachable(gems, sockets):
        problems.append(
            f"{_first_file(rows, t)} 宝石类型 {t} 没有任何孔位可接受"
            "（sockets.types 中既无 'any' 也无同值）"
        )
    if a.v:
        print(f"已检查 {len(gems)} 种宝石类型 · {len(sockets)} 种孔类型 · {len(rows)} 份物品配置")
    for msg in problems:
        print("[问题] " + msg)
    print(f"共 1 份宝石孔位可达性表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
