#!/usr/bin/env python3
"""文档面起飞前对账（路径引用 + 孤儿轮次文档）。

扫 --docs 下 *.md（不递归、跳 TASKS.md）+ --root/PLAN.md 的 `| R` 台账行，
按字面取路径 token，验文件是否还在；另提示未被任何文档引用的 ROUND-* 孤儿。
不解引用、不展开通配、不校验链接锚点、不改任何 .md。提示不计问题数、不影响退出码。
"""
import argparse, os, re

TICK = re.compile(r"`([^`]*)`")
PATH = re.compile(r"(?:docs|tools|src|build)/[A-Za-z0-9_./-]+\.(?:md|py|java|json5|json)")
PYCALL = re.compile(r"python3?\s+(tools/[A-Za-z0-9_./-]+\.py)")


def tokens(line):
    """返该行两类 token 的出现次数列表（含重复、含 .. 已滤）。"""
    out = [seg for seg in TICK.findall(line) if PATH.fullmatch(seg)]
    out += PYCALL.findall(line)
    return [t for t in out if ".." not in t]


def read(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def main():
    ap = argparse.ArgumentParser(description="文档面起飞前对账")
    ap.add_argument("--docs", default="docs")
    ap.add_argument("--root", default=".")
    a = ap.parse_args()

    if not os.path.isdir(a.docs):
        print("[问题] 文档目录不存在：" + a.docs)
        print("共 0 份文档 · 0 条路径引用 · 0 条提示 · 1 条问题")
        return 1
    mds = sorted(f for f in os.listdir(a.docs)
                 if f.endswith(".md") and os.path.isfile(os.path.join(a.docs, f)))
    if not mds:
        print("[问题] 文档目录里没有 *.md：" + a.docs)
        print("共 0 份文档 · 0 条路径引用 · 0 条提示 · 1 条问题")
        return 1

    bodies = {f: read(os.path.join(a.docs, f)) for f in mds}
    plan = os.path.join(a.root, "PLAN.md")
    plan_text = read(plan) if os.path.isfile(plan) else ""

    # 窄集合（只它做路径存在性）：docs md 跳 TASKS.md + PLAN.md
    targets = []
    for f in mds:
        if f == "TASKS.md":
            continue
        targets.append((os.path.relpath(os.path.join(a.docs, f), a.root).replace("\\", "/"),
                        bodies[f], False))
    if plan_text:
        targets.append(("PLAN.md", plan_text, True))

    refs, problems = 0, []
    for rel, text, is_plan in targets:
        for no, line in enumerate(text.splitlines(), 1):
            if is_plan and not line.startswith("| R"):
                continue
            for t in tokens(line):
                refs += 1
                if not os.path.exists(os.path.join(a.root, t)):
                    problems.append("%s:%d 引用的路径不存在：%s" % (rel, no, t))

    # 孤儿判定宽集合：所有 docs md（含 TASKS.md）全文 + PLAN.md 全文
    hints = []
    for f in mds:
        if not f.startswith("ROUND-"):
            continue
        others = [b for k, b in bodies.items() if k != f]
        if plan_text:
            others.append(plan_text)
        if not any(f in b for b in others):
            hints.append("未被任何文档引用的孤儿：" + f)

    for p in problems:
        print("[问题] " + p)
    for h in hints:
        print("[提示] " + h)
    print("共 %d 份文档 · %d 条路径引用 · %d 条提示 · %d 条问题" % (
        len(targets), refs, len(hints), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
