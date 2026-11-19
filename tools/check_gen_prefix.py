#!/usr/bin/env python3
"""lang 生成器前缀对账：文件名 ↔ 声明前缀（单文件零接线）。

只读四个 tools/gen_*_lang.py 的文本，剥注释取 OWNED_PREFIX/KEY_PREFIX
常量的第一个字符串值，与 S7 写死的期望表比对。不执行生成器、不读 lang JSON。
"""
import argparse
import os
import re
import sys

GEN_FILES: dict[str, str] = {
    "gen_attr_lang.py": "attribute.redtale_project.",
    "gen_enchant_lang.py": "enchantment.redtale_project.",
    "gen_entity_lang.py": "entity.redtale_project.",
    "gen_item_lang.py": "item.redtale_project.",
}

_PREFIX_RE = re.compile(r'\b(?:OWNED_PREFIX|KEY_PREFIX)\s*=\s*"([^"]+)"')


def _strip_comments(text: str) -> str:
    """剥代码区的行尾 // 与块 /*…*/；字符串/三引号 docstring 内原文保留。"""
    out: list[str] = []
    i = 0
    n = len(text)
    in_block = False
    quote = ""
    while i < n:
        c = text[i]
        if in_block:
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                in_block = False
                i += 2
            else:
                if c == "\n":
                    out.append("\n")
                i += 1
            continue
        if quote:
            if len(quote) == 3 and text.startswith(quote, i):
                out.append(quote)
                i += 3
                quote = ""
            elif c == quote:
                out.append(c)
                i += 1
                quote = ""
            elif c == "\\" and len(quote) == 1 and i + 1 < n:
                out.append(text[i:i + 2])
                i += 2
            else:
                out.append(c)
                i += 1
            continue
        if text.startswith('"""', i) or text.startswith("'''", i):
            quote = text[i:i + 3]
            out.append(quote)
            i += 3
            continue
        if c == '"' or c == "'":
            quote = c
            out.append(c)
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            in_block = True
            i += 2
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def read_prefix(text: str) -> str | None:
    """剥注释后取第一个 OWNED_PREFIX/KEY_PREFIX 字符串值；无匹配返回 None。"""
    m = _PREFIX_RE.search(_strip_comments(text))
    return m.group(1) if m else None


def read_all(tools_dir: str) -> dict[str, str | None]:
    """按 GEN_FILES 键序读每个脚本取前缀；单文件读失败该项 None，不抛栈。"""
    found: dict[str, str | None] = {}
    for name in GEN_FILES:
        try:
            with open(os.path.join(tools_dir, name), "r", encoding="utf-8") as fh:
                found[name] = read_prefix(fh.read())
        except (OSError, UnicodeError):
            found[name] = None
    return found


def check_prefixes(found: dict[str, str | None], expected: dict[str, str]) -> list[str]:
    """按 expected 键序比对；None 只报未声明，值不等报前缀不符；不跨文件查重。"""
    problems: list[str] = []
    for name in expected:
        got = found.get(name)
        if got is None:
            problems.append(f"生成器 {name} 未声明前缀")
        elif got != expected[name]:
            problems.append(f"生成器 {name} 前缀 {got}，期望 {expected[name]}")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tools", default="tools")
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    if not os.path.isdir(args.tools):
        problems = ["生成器目录读取失败"]
    else:
        problems = check_prefixes(read_all(args.tools), GEN_FILES)

    for p in problems:
        print(f"[问题] {p}")
    if args.v:
        print(f"已检查 {len(GEN_FILES)} 个生成器")
    print(f"共 1 份生成器清单 · {len(problems)} 条问题")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
