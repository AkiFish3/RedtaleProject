#!/usr/bin/env python3
"""原语注册对账：builtin 类文件 + Builtins 内联 record ↔ register(new Xxx())。

只读 .java 文本，剥注释后做正则提取；不解析 JavaAST，不 import 其它 check。
"""

import argparse
import os
import re

DEFAULT_BUILTIN = "src/main/java/com/redtale/project/core/script/builtin"
SUFFIXES = ("Effect.java", "Condition.java")
RE_RECORD = re.compile(
    r"\brecord\s+([A-Z][A-Za-z0-9]*)\s*\(\s*\)\s*implements\s+TriggerType\b"
)
RE_REG = re.compile(r"\bnew\s+([A-Z][A-Za-z0-9]*)\s*\(\s*\)")


def strip_comments(text: str) -> str:
    """剥掉 // 行尾与 /* */ 块注释，保留换行；字符串字面量不当注释处理。"""
    out = []
    i = 0
    n = len(text)
    in_block = False
    while i < n:
        if in_block:
            end = text.find("*/", i)
            if end < 0:
                break
            i = end + 2
            in_block = False
        elif text.startswith("//", i):
            end = text.find("\n", i)
            if end < 0:
                break
            i = end
        elif text.startswith("/*", i):
            in_block = True
            i += 2
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def read_classes(builtin_dir: str) -> list[str]:
    """builtin 目录下 *Effect.java / *Condition.java 的类名，字典序。"""
    return sorted(
        name[:-5]
        for name in os.listdir(builtin_dir)
        if name.endswith(SUFFIXES)
    )


def read_records(builtins_text: str) -> set[str]:
    """Builtins.java 内联的 record … implements TriggerType 类名。"""
    return set(RE_RECORD.findall(strip_comments(builtins_text)))


def read_regs(builtins_text: str) -> set[str]:
    """剥注释后全文里的 new Xxx() 引用集。"""
    return set(RE_REG.findall(strip_comments(builtins_text)))


def check_registration(classes: list[str], records: set[str], regs: set[str]) -> list[str]:
    """双向对账：漏注册在前，幽灵引用在后，各自按名字排序。"""
    issues = []
    for name in sorted((set(classes) | records) - regs):
        issues.append("原语 %s 未注册进 Builtins" % name)
    for name in sorted(regs - set(classes) - records):
        issues.append("注册引用 %s 无对应原语类" % name)
    return issues


def main() -> int:
    ap = argparse.ArgumentParser(description="检查脚本原语是否都注册进 Builtins")
    ap.add_argument("--builtin", default=DEFAULT_BUILTIN, help="builtin 类目录")
    ap.add_argument("--regs", default=None, help="Builtins.java 路径")
    ap.add_argument("-v", action="store_true", help="打印计数摘要")
    args = ap.parse_args()
    regs_path = args.regs or os.path.join(args.builtin, "Builtins.java")

    classes = []
    records = set()
    regs = set()
    issues = []
    try:
        classes = read_classes(args.builtin)
    except OSError:
        issues.append("原语目录读取失败")
    try:
        with open(regs_path, "r", encoding="utf-8") as fh:
            text = fh.read()
        records = read_records(text)
        regs = read_regs(text)
    except OSError:
        issues.append("注册表读取失败")
    if not issues:
        issues.extend(check_registration(classes, records, regs))

    if args.v:
        print(
            "已检查 %s · 原语类 %d 个（内联 %d）· 注册引用 %d 个"
            % (regs_path, len(classes), len(records), len(regs))
        )
    for msg in issues:
        print("[问题] %s" % msg)
    print("共 1 份原语注册表 · %d 条问题" % len(issues))
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
