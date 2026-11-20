"""原语 id ↔ SKILLS.md 原语表双向对账（单文件零接线）。"""

import argparse
import os
import re

DEBUG_IDS: set[str] = {"message", "console_log"}

ROW_RE = re.compile(r"^\|\s*\d+\s*\|")
ID_RE = re.compile(r"`([^`]+)`")
EFFECT_RE = re.compile(
    r'public\s+String\s+id\(\)\s*\{[^}]*?return\s+"([^"]+)"', re.S
)


def strip_comments(text: str) -> str:
    """剥 // 行尾与 /*...*/ 块注释；字符串内容原样保留。"""
    out: list[str] = []
    i, n = 0, len(text)
    block = False
    line = False
    quote = ""
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if block:
            if c == "*" and nxt == "/":
                block = False
                i += 2
                continue
            if c == "\n":
                out.append(c)
            i += 1
            continue
        if line:
            if c == "\n":
                line = False
                out.append(c)
            i += 1
            continue
        if quote:
            out.append(c)
            if c == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if c == quote:
                quote = ""
            i += 1
            continue
        if c in ("'", '"'):
            quote = c
            out.append(c)
            i += 1
            continue
        if c == "/" and nxt == "*":
            block = True
            i += 2
            continue
        if c == "/" and nxt == "/":
            line = True
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def read_table(text: str) -> tuple[list[str], list[str]]:
    """取 SKILLS.md §4 表格的全部 id 与标 ✅ 的 id（均按行序）。"""
    ids: list[str] = []
    done: list[str] = []
    in_section = False
    for line in text.splitlines():
        if line.startswith("## "):
            if in_section:
                break
            in_section = line.startswith("## 4.")
            continue
        if not in_section or not ROW_RE.match(line):
            continue
        fields = line.replace("\\|", "\u0000").split("|")
        if len(fields) < 6:
            continue
        m = ID_RE.search(fields[2])
        if not m:
            continue
        pid = m.group(1).strip()
        if not pid:
            continue
        ids.append(pid)
        if "✅" in fields[5]:
            done.append(pid)
    return ids, done


def read_effects(builtin_dir: str) -> list[tuple[str, str | None]]:
    """列 builtin 目录下 *Effect.java，取类名与 id() 字面返回值。"""
    try:
        names = sorted(
            n for n in os.listdir(builtin_dir) if n.endswith("Effect.java")
        )
    except OSError:
        return []
    effects: list[tuple[str, str | None]] = []
    for name in names:
        cls = name[:-5]
        try:
            with open(os.path.join(builtin_dir, name), encoding="utf-8") as f:
                text = strip_comments(f.read())
        except OSError:
            effects.append((cls, None))
            continue
        m = EFFECT_RE.search(text)
        effects.append((cls, m.group(1) if m else None))
    return effects


def check_ids(
    table: list[str],
    done: list[str],
    effects: list[tuple[str, str | None]],
) -> list[str]:
    """三类问题按序：未声明 id / id 不在表内 / 表标已实现但无类。

    第三类按验收口径判「无对应 Effect 类」：声明 id 命中，或类名按
    snake_case → PascalCase + Effect 命中，均视为有类（id 写歪时只报第二类）。
    """
    problems: list[str] = []
    seen: set[str] = set()

    def add(msg: str) -> None:
        if msg not in seen:
            seen.add(msg)
            problems.append(msg)

    allowed = set(table) | DEBUG_IDS
    class_names = {cls for cls, _ in effects}
    for cls, pid in effects:
        if pid is None:
            add(f"原语类 {cls} 未声明 id()")
    declared: set[str] = set()
    for cls, pid in effects:
        if pid is None:
            continue
        declared.add(pid)
        if pid not in allowed:
            add(f"原语 id {pid}（{cls}）不在 SKILLS.md 原语表")
    for pid in done:
        expected = "".join(part.capitalize() for part in pid.split("_")) + "Effect"
        if pid not in declared and expected not in class_names:
            add(f"SKILLS.md 原语 {pid} 标已实现但无对应 Effect 类")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description="原语 id ↔ SKILLS.md 原语表双向对账")
    ap.add_argument("--docs", default="docs/SKILLS.md")
    ap.add_argument(
        "--builtin",
        default="src/main/java/com/redtale/project/core/script/builtin",
    )
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    problems: list[str] = []
    table: list[str] = []
    done: list[str] = []
    docs_ok = True
    try:
        with open(args.docs, encoding="utf-8") as f:
            table, done = read_table(f.read())
    except OSError:
        docs_ok = False
        problems.append("原语表读取失败")

    builtin_ok = os.path.isdir(args.builtin)
    effects = read_effects(args.builtin) if builtin_ok else []
    if not builtin_ok:
        problems.append("原语目录读取失败")

    if docs_ok and builtin_ok:
        problems.extend(check_ids(table, done, effects))

    if args.v:
        print(
            f"已对账 {len(table)} 条表项 · {len(done)} 条标已实现 · "
            f"{len(effects)} 个 Effect 类"
        )
    for msg in problems:
        print(f"[问题] {msg}")
    print(f"共 1 份原语 id 表 · {len(problems)} 条问题")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
