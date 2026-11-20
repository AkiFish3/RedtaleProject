"""伤害乘区白名单 damage_types 形态对账（T-0086）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

from check_progress import clean_json5
from check_damage_types import read_registered

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "redtale_project:"
DEFAULT_CFG = "src/main/resources/redtale/defaults/damage.json5"
DEFAULT_DATA = "src/main/resources/data"
_BARE = re.compile(r"[a-z0-9_.-]+")


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


def read_entries(cfg_path: str) -> list[tuple[int, object]] | None:
    """取 multiplier.damage_types 的 (下标, 元素) 保序表；读不到/形态错返 None。"""
    try:
        with open(cfg_path, "r", encoding="utf-8", errors="replace") as fh:
            data = _loads(fh.read())
    except Exception:
        return None
    if not isinstance(data, dict):
        return None
    mult = data.get("multiplier")
    if not isinstance(mult, dict):
        return None
    items = mult.get("damage_types")
    if not isinstance(items, list):
        return None
    return [(i, item) for i, item in enumerate(items)]


def check_whitelist(entries: list[tuple[int, object]], registered: set[str]) -> list[str]:
    """逐条对账白名单形态；全通配与外部精确 id 跳过。"""
    out: list[str] = []
    for i, value in entries:
        prefix = f"damage_types[{i}]"
        if not isinstance(value, str) or not value.strip():
            out.append(prefix + " 必须是非空字符串")
            continue
        if value == "*":
            continue
        if ":" in value:
            ns, _, rest = value.partition(":")
            if rest == "*" and ns not in ("redtale_project", "minecraft"):
                out.append(prefix + " 未知命名空间通配：" + value)
                continue
        if not value.startswith(NS):
            continue
        bare = value[len(NS):]
        if bare == "*":
            continue
        if bare and ":" not in bare and "*" not in bare and _BARE.fullmatch(bare):
            if bare not in registered:
                out.append(prefix + " 未注册伤害类型：" + value)
            continue
        out.append(prefix + " 非法通配形态：" + value)
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="伤害乘区白名单对账")
    ap.add_argument("--cfg", default=DEFAULT_CFG)
    ap.add_argument("--data", default=DEFAULT_DATA)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    registered = read_registered(a.data)
    entries = read_entries(a.cfg)
    if entries is None:
        print("[问题] damage.json5 读取失败")
        print("共 1 份伤害白名单 · 1 条问题")
        return 1
    problems = check_whitelist(entries, registered)
    if a.v:
        print(f"已检查 {len(entries)} 条白名单 · {len(registered)} 个注册类型")
    for msg in problems:
        print("[问题] " + msg)
    print(f"共 1 份伤害白名单 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
