"""附魔 exclusive_set 引用形态对账（T-0088）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VALID_NS = ("minecraft", "redtale_project")
DEFAULT_DIR = "src/main/resources/redtale/defaults"
_BARE = re.compile(r"[a-z0-9_/.-]+")


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


def read_configs(root: str) -> list[tuple[str, object, str | None]] | None:
    """扫 <root>/enchantments/*.json5（文件名升序），返回 (rel, 字段值, err)。"""
    folder = os.path.join(root, "enchantments")
    if not os.path.isdir(folder):
        return None
    try:
        names = sorted(n for n in os.listdir(folder) if n.endswith(".json5"))
    except Exception:
        return None
    rows: list[tuple[str, object, str | None]] = []
    for name in names:
        rel = "enchantments/" + name
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
        rows.append((rel, data.get("exclusive_set"), None))
    return rows


def check_ref(value: object) -> str | None:
    """核一条 exclusive_set 的引用形态；字段可省略，合法返 None。"""
    if value is None:
        return None
    if not isinstance(value, str):
        return "exclusive_set 必须是字符串"
    if value.startswith("#"):
        body = value[1:]
        if ":" not in body:
            return "exclusive_set tag 引用缺少命名空间：" + value
        ns, _, path = body.partition(":")
        if ns not in VALID_NS:
            return f"exclusive_set tag 引用命名空间未知：{ns}（允许：minecraft / redtale_project）"
        if not path or ".." in path:
            return "exclusive_set tag 引用路径非法：" + value
        return None
    if any(ch.isspace() for ch in value) or "#" in value:
        return "exclusive_set 单条附魔 id 形态非法：" + value
    if ":" in value:
        ns, _, path = value.partition(":")
        if not ns or not path or not _BARE.fullmatch(ns) or not _BARE.fullmatch(path):
            return "exclusive_set 单条附魔 id 形态非法：" + value
        return None
    if not _BARE.fullmatch(value):
        return "exclusive_set 单条附魔 id 形态非法：" + value
    return None


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="附魔互斥引用形态对账")
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    configs = read_configs(a.dir)
    if configs is None:
        print("[问题] enchantments 目录读取失败")
        print("共 0 份附魔互斥引用表 · 1 条问题")
        return 1
    problems: list[str] = []
    refs = 0
    for rel, value, err in configs:
        if value is not None:
            refs += 1
        if err is not None:
            problems.append(f"{rel} 配置读取失败：{err}")
            continue
        msg = check_ref(value)
        if msg:
            problems.append(f"{rel} {msg}")
    if a.v:
        print(f"已检查 {refs} 条 exclusive_set 引用 · {len(configs)} 份附魔配置")
    for msg in problems:
        print("[问题] " + msg)
    print(f"共 {len(configs)} 份附魔互斥引用表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
