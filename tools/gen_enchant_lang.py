#!/usr/bin/env python3
"""从 enchantments/*.json5 生成附魔 lang 键（**合并**写入，只拥有 enchantment.redtale_project.*）。

用法：
    python tools/gen_enchant_lang.py

为什么是合并而不是覆写：`gen_attr_lang.py` 表即真相源、整体覆写；本脚本只拥有
`enchantment.redtale_project.*`，`attribute.*` 66 条 / `item.*` 14 条 / 死亡消息
必须原样带出，否则重跑一次就把别人的键抹掉。

键写法与 `EnchantmentGenerator` 逐字一致：
    "enchantment." + ns + "." + path.replace('/', '.')
id 缺命名空间 → 补 `redtale_project:`；description 缺失 → 用 path 兜底 + WARN；
description_en 缺失 → 回落 description。文件解析不了 → WARN 跳过，不崩。

依赖：只用标准库。JSON5 的注释/裸键/尾逗号由本文件的轻量清洗处理，不引 pip 包。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENCH_DIR = ROOT / "src/main/resources/redtale/defaults/enchantments"
LANG_DIR = ROOT / "src/main/resources/assets/redtale_project/lang"
KEY_PREFIX = "enchantment.redtale_project."
DEFAULT_NS = "redtale_project"


def _rel(path: Path) -> str:
    """相对仓库根的展示路径；不在仓内（探针临时目录）就原样给出。"""
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def strip_comments(text: str) -> str:
    """去掉 // 与 /* */ 注释，字符串内的不动。"""
    out: list[str] = []
    i, n, quote = 0, len(text), ""
    while i < n:
        ch = text[i]
        if quote:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = ""
            i += 1
            continue
        if ch in "\"'":
            quote = ch
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(text[i])
        i += 1
    if quote:
        raise ValueError("字符串未闭合")
    return "".join(out)


def json5_to_json(text: str) -> str:
    """裸键加引号 + 单引号串转双引号 + 去尾逗号（按容器栈判断 key 位置）。"""
    text = strip_comments(text)
    out: list[str] = []
    stack: list[str] = []
    i, n = 0, len(text)
    expect_key = False
    while i < n:
        ch = text[i]
        if ch in "\"'":
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == ch:
                    break
                j += 1
            body = text[i + 1:j]
            out.append(text[i:j + 1] if ch == '"' else json.dumps(body.replace("\\'", "'"), ensure_ascii=False))
            i, expect_key = j + 1, False
            continue
        if ch == "{":
            stack.append("obj")
            expect_key = True
        elif ch == "[":
            stack.append("arr")
            expect_key = False
        elif ch in "}]":
            if stack:
                stack.pop()
            expect_key = False
        elif ch == ",":
            expect_key = bool(stack) and stack[-1] == "obj"
        elif ch == ":":
            expect_key = False
        elif ch.isspace():
            pass
        elif expect_key and (ch.isalpha() or ch in "_$"):
            j = i
            while j < n and (text[j].isalnum() or text[j] in "_$"):
                j += 1
            out.append('"' + text[i:j] + '"')
            i = j
            continue
        else:
            expect_key = False
        out.append(ch)
        i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


def load_entry(path: Path) -> tuple[str, str, str] | None:
    """返回 (lang 键, 中文名, 英文名)；任何问题都 WARN + None（跳过该文件）。"""
    try:
        obj = json.loads(json5_to_json(path.read_text(encoding="utf-8")))
    except (OSError, ValueError, UnicodeDecodeError) as exc:
        print(f"WARN: {path.name} 解析失败（{type(exc).__name__}: {exc}），已跳过")
        return None
    if not isinstance(obj, dict):
        print(f"WARN: {path.name} 顶层不是对象，已跳过")
        return None

    rid = obj.get("id")
    if not isinstance(rid, str) or not rid.strip():
        print(f"WARN: {path.name} 缺 id，已跳过")
        return None
    rid = rid.strip()
    if ":" not in rid:
        rid = DEFAULT_NS + ":" + rid
    ns, _, rest = rid.partition(":")
    if ns != DEFAULT_NS:
        print(f"WARN: {path.name} 的 id 命名空间是 {ns}，不在 {KEY_PREFIX} 名下，已跳过")
        return None

    zh = obj.get("description")
    if not isinstance(zh, str) or not zh.strip():
        print(f"WARN: {path.name} 缺 description，用 id 兜底")
        zh = rest
    zh = zh.strip()
    en = obj.get("description_en")
    if not isinstance(en, str) or not en.strip():
        en = zh
    return KEY_PREFIX + rest.replace("/", "."), zh, en.strip()


def merge_into(path: Path, entries: list[tuple[str, str]]) -> int:
    """保留非本前缀的键（原顺序），再按文件名升序追加新键。返回总键数。"""
    data: dict[str, str] = {}
    if path.exists():
        raw = path.read_text(encoding="utf-8")
        if raw.strip():
            try:
                loaded = json.loads(raw)
            except json.JSONDecodeError as exc:
                print(f"!! {path.name} 解析失败（{exc}），已跳过，未写入", file=sys.stderr)
                return -1
            if not isinstance(loaded, dict):
                print(f"!! {path.name} 顶层不是对象，已跳过，未写入", file=sys.stderr)
                return -1
            data = loaded

    before = sum(1 for k in data if k.startswith(KEY_PREFIX))
    kept = {k: v for k, v in data.items() if not k.startswith(KEY_PREFIX)}
    for key, value in entries:
        kept[key] = value

    text = json.dumps(kept, ensure_ascii=False, indent=2) + "\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    now = sum(1 for k in kept if k.startswith(KEY_PREFIX))
    print(f"  写入 {_rel(path)}（{len(kept)} 键，enchantment 键 {before} → {now}）")
    return len(kept)


def main() -> int:
    if not ENCH_DIR.is_dir():
        print(f"!! 找不到 {_rel(ENCH_DIR)}", file=sys.stderr)
        return 2

    files = sorted(ENCH_DIR.glob("*.json5"), key=lambda p: p.name)
    found: list[tuple[str, str, str]] = []
    for path in files:
        entry = load_entry(path)
        if entry is not None:
            found.append(entry)
    if not found:
        print("!! 没解析到任何附魔定义，未写入", file=sys.stderr)
        return 2

    print(f"解析到 {len(found)} 份附魔定义：" + ", ".join(k[len(KEY_PREFIX):] for k, _, _ in found))
    for path, idx in ((LANG_DIR / "zh_cn.json", 1), (LANG_DIR / "en_us.json", 2)):
        if merge_into(path, [(e[0], e[idx]) for e in found]) < 0:
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
