"""docs/TASKS.md 卡板自检（只读）：在制品上限 / 验收段缺失 / 台账漂移。"""
import re
import sys
from pathlib import Path

TITLE = re.compile(r"^### (T-\d{4}) · \[([A-Z]+)\]")
BOUND = re.compile(r"^### |^## ")
SECTION = re.compile(r"^- 验收(?=$|[：（])")
VALID = {"OPEN", "DOING", "DONE", "VERIFIED", "BOUNCED", "DROPPED"}
WIP = {"OPEN", "DOING", "BOUNCED"}


def cli(argv):
    root = Path(__file__).resolve().parent.parent
    verbose = False
    i = 0
    while i < len(argv):
        if argv[i] == "-v":
            verbose = True
            i += 1
        elif argv[i] == "--root" and i + 1 < len(argv):
            root = Path(argv[i + 1])
            i += 2
        else:
            i += 1
    return root, verbose


def cards(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    found = []
    for i, line in enumerate(lines):
        m = TITLE.match(line)
        if not m:
            continue
        end = len(lines)
        for j in range(i + 1, len(lines)):
            if BOUND.match(lines[j]):
                end = j
                break
        found.append((m.group(1), m.group(2), i + 1, lines[i + 1:end]))
    return found


def main(argv):
    root, verbose = cli(argv)
    tasks = root / "docs" / "TASKS.md"
    plan = root / "PLAN.md"
    problems, hints = [], []
    for path in (tasks, plan):
        if not path.is_file():
            problems.append((0, path.relative_to(root).as_posix() + "：文件不存在"))
    if problems:
        for _, msg in sorted(problems):
            print("[问题] " + msg)
        print("共 0 张卡 · 在制品 0 · 问题 %d · 提示 0" % len(problems))
        return 1
    found = cards(tasks)
    text = plan.read_text(encoding="utf-8")
    seen = {}
    wip = 0
    for cid, state, line, body in found:
        if state not in VALID:
            problems.append((line, "TASKS.md:%d %s 状态 %s 不在状态机里" % (line, cid, state)))
        if cid in seen:
            problems.append((line, "TASKS.md:%d %s 重复定义（先出现在第 %d 行）" % (line, cid, seen[cid])))
        else:
            seen[cid] = line
        if state in WIP:
            wip += 1
        if state in ("DONE", "VERIFIED") and not any(SECTION.match(x.strip()) for x in body):
            problems.append((line, "TASKS.md:%d %s 缺「- 验收」段" % (line, cid)))
        if state == "VERIFIED" and cid not in text:
            hints.append((line, "%s 未在 PLAN.md 出现（VERIFIED 应在 §13 记一行）" % cid))
    if wip > 3:
        problems.append((0, "- 在制品 %d 张 > 3（§2 规则 1）" % wip))
    for _, msg in sorted(problems):
        print("[问题] " + msg)
    if verbose:
        for _, msg in sorted(hints):
            print("[提示] " + msg)
    print("共 %d 张卡 · 在制品 %d · 问题 %d · 提示 %d" % (len(found), wip, len(problems), len(hints)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
