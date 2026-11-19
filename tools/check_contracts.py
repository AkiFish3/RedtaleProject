#!/usr/bin/env python3
"""契约文档 ↔ 源码单向对账（docs → java）。

只扫 CONTRACTS.md 里 `### 4.x` 标题行的反引号 token，验类名/成员名是否还在源码里。
不扫正文、不改文件、不连服务器。提示不计问题数。
"""
import argparse, os, re

CLASS = re.compile(r"[A-Z][A-Za-z0-9]{2,}")
MEMBER = re.compile(r"([A-Z][A-Za-z0-9]{2,})\.([A-Za-z_][A-Za-z0-9_]*)")
TOKEN = re.compile(r"`([^`]+)`")


def refs_from(docs_path):
    """返回 (段数, [(行号, 类, 成员或None)], 全文)。"""
    with open(docs_path, encoding="utf-8") as f:
        text = f.read()
    refs, segs = [], 0
    for no, line in enumerate(text.splitlines(), 1):
        if not line.startswith("### 4."):
            continue
        segs += 1
        for tok in TOKEN.findall(line):
            m = MEMBER.fullmatch(tok)
            if m:
                refs.append((no, m.group(1), m.group(2)))
                continue
            if CLASS.fullmatch(tok) and any(c.islower() for c in tok):
                refs.append((no, tok, None))
    return segs, refs, text


def java_index(root):
    """类 → 相对路径（首次出现），以及 core/ 下（排除两处）的类名列表。"""
    paths, core = {}, []
    for dp, _, files in os.walk(root):
        for fn in sorted(files):
            if not fn.endswith(".java"):
                continue
            cls = fn[:-5]
            rel = os.path.relpath(os.path.join(dp, fn), root).replace("\\", "/")
            paths.setdefault(cls, rel)
            parts = rel.split("/")
            if "core" not in parts:
                continue
            rest = parts[parts.index("core") + 1:]
            if not rest or rest[0] == "command":
                continue
            if rest[0] == "script" and len(rest) > 1 and rest[1] == "builtin":
                continue
            core.append(cls)
    return paths, core


def main():
    ap = argparse.ArgumentParser(description="契约文档 ↔ 源码单向对账")
    ap.add_argument("--docs", default="docs/CONTRACTS.md")
    ap.add_argument("--java", default="src/main/java")
    a = ap.parse_args()
    problems, hints = [], []

    if not os.path.isfile(a.docs):
        print("[问题] 契约文档读不到：" + a.docs)
        print("共 0 条契约 · 0 个声明类 · 0 条提示 · 1 条问题")
        return 1

    segs, refs, text = refs_from(a.docs)
    declared = {c for _, c, _ in refs}
    if not segs:
        print("[问题] 没有找到任何 ### 4.x 契约段")
        print("共 0 条契约 · 0 个声明类 · 0 条提示 · 1 条问题")
        return 1
    if not os.path.isdir(a.java):
        print("[问题] 源码目录不存在：" + a.java)
        print("共 %d 条契约 · %d 个声明类 · 0 条提示 · 1 条问题" % (segs, len(declared)))
        return 1

    paths, core = java_index(a.java)
    bodies = {}
    for no, cls, member in refs:
        rel = paths.get(cls)
        if rel is None:
            problems.append("第 %d 行声明的类 %s 不存在" % (no, cls))
            continue
        if member is None:
            continue
        if rel not in bodies:
            with open(os.path.join(a.java, rel), encoding="utf-8", errors="replace") as f:
                bodies[rel] = f.read()
        if not re.search(r"\b" + re.escape(member) + r"\b", bodies[rel]):
            problems.append("第 %d 行声明的 %s.%s 在 %s 里找不到" % (no, cls, member, rel))

    for cls in sorted(set(core)):
        if cls not in text:
            hints.append("未登记的实现类：" + cls)

    for p in problems:
        print("[问题] " + p)
    for h in hints:
        print("[提示] " + h)
    print("共 %d 条契约 · %d 个声明类 · %d 条提示 · %d 条问题" % (
        segs, len(declared), len(hints), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
