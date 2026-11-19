"""check_commands.py —— 命令树挂载对账（T-0071 / R75）。

core/command/ 下每一个 XxxCommand.java 都要在 RedtaleCommand 里挂一行
`.then(XxxCommand.node())` 或 inline `.executes(Xxx::yyy)`。漏挂时命令永远不存在，
而 build / check_all 仍全绿。本脚本双向核对「磁盘命令类」与「根命令引用集」。

只读 .java 文本：剥注释后取 `XxxCommand` token，不解析 JavaAST、不 import 别的 check。
接线（把本脚本加进 check_all 的 TOOLS）归 S7，本脚本不碰。

用法：
    python tools/check_commands.py
    python tools/check_commands.py --cmd <dir> --root <file> -v

退出码：0 = 双向一致；1 = 漏挂 / 幽灵引用 / 读取失败。
"""
from __future__ import annotations

import argparse
import os
import re
import sys

_ROOT = "RedtaleCommand.java"
_TOKEN = re.compile(r"\b([A-Z][A-Za-z0-9]*Command)\b")


def _strip_comments(text: str) -> str:
    """逐字符剥 // 行尾与 /*...*/ 块注释；字符串/字符字面量原样保留。"""
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            i += 2
            while i < n and text[i] != "\n":
                i += 1
            out.append(" ")
            continue
        if c == "/" and nxt == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            i += 2
            out.append(" ")
            continue
        if c == '"' or c == "'":
            quote = c
            out.append(c)
            i += 1
            while i < n:
                out.append(text[i])
                if text[i] == "\\" and i + 1 < n:
                    out.append(text[i + 1])
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def read_classes(cmd_dir: str) -> list[str]:
    """命令目录下 *Command.java（排除根命令），去 .java 后缀按字典序返回。"""
    return sorted(
        name[:-5]
        for name in os.listdir(cmd_dir)
        if name.endswith("Command.java") and name != _ROOT
    )


def read_refs(root_text: str) -> set[str]:
    """剥注释后取全文 XxxCommand token 集合（含 RedtaleCommand 自身）。"""
    return set(_TOKEN.findall(_strip_comments(root_text)))


def check_mounts(classes: list[str], refs: set[str]) -> list[str]:
    """两类问题按序：①磁盘类未挂进根命令；②引用无对应命令类。"""
    problems: list[str] = []
    class_set = set(classes)
    for name in classes:
        if name not in refs:
            problems.append("命令类 %s 未挂进 RedtaleCommand" % name)
    for name in sorted(refs - class_set - {_ROOT[:-5]}):
        problems.append("挂载引用 %s 无对应命令类" % name)
    return problems


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="命令树挂载对账")
    ap.add_argument("--cmd", default="src/main/java/com/redtale/project/core/command")
    ap.add_argument("--root", default=None)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    root = args.root if args.root is not None else os.path.join(args.cmd, _ROOT)

    problems: list[str] = []
    classes: list[str] = []
    refs: set[str] = set()
    try:
        classes = read_classes(args.cmd)
    except Exception:
        problems.append("命令目录读取失败")
    try:
        with open(root, "r", encoding="utf-8", errors="replace") as f:
            text = f.read()
    except Exception:
        problems.append("根命令读取失败")
        text = ""
    else:
        refs = read_refs(text)

    if not problems:
        problems = check_mounts(classes, refs)
    if args.v:
        print("已检查 %s · 命令类 %d 个 · 挂载引用 %d 个"
              % (root, len(classes), len(refs - {_ROOT[:-5]})))
    for msg in problems:
        print("[问题] %s" % msg)
    print("共 1 份命令树 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
