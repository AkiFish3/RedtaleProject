#!/usr/bin/env python3
"""怪物绑定技能完整 id 引用对账：skills[].skill 按完整字符串命中技能表。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5


def _load(folder, name):
    try:
        with open(os.path.join(folder, name), "r", encoding="utf-8", errors="replace") as fh:
            return json.loads(clean_json5(fh.read()))
    except Exception:
        return None


def read_ids(root, sub):
    """扫 <root>/<sub>/*.json5，收字符串 id 原文；目录不可读返 None。"""
    folder = os.path.join(root, sub)
    try:
        names = sorted(os.listdir(folder))
    except OSError:
        return None
    ids = set()
    for name in names:
        if not name.endswith(".json5"):
            continue
        if not os.path.isfile(os.path.join(folder, name)):
            continue
        data = _load(folder, name)
        if not isinstance(data, dict) or not isinstance(data.get("id"), str):
            continue
        ids.add(data["id"])
    return ids


def read_mobs(root):
    """返回 [(rel, data)]，文件名升序；目录不可读返 None。"""
    folder = os.path.join(root, "mobs")
    try:
        names = sorted(os.listdir(folder))
    except OSError:
        return None
    result = []
    for name in names:
        if not name.endswith(".json5"):
            continue
        if not os.path.isfile(os.path.join(folder, name)):
            continue
        data = _load(folder, name)
        if not isinstance(data, dict) or not isinstance(data.get("id"), str):
            continue
        result.append(("mobs/" + name, data))
    return result


def unmatched(mobs, skill_ids):
    """skills[].skill 未命中完整 id 的文案列表，按文案升序、稳定。"""
    out = []
    for _rel, mob in mobs:
        skills = mob.get("skills")
        if not isinstance(skills, list):
            continue
        mid = mob["id"]
        for entry in skills:
            if not isinstance(entry, dict):
                continue
            skill = entry.get("skill")
            if not isinstance(skill, str):
                continue
            if skill in skill_ids:
                continue
            out.append("怪物 %s 绑定的技能 %s 未命中技能表完整 id" % (mid, skill))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description="怪物绑定技能完整 id 引用对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    mobs = read_mobs(args.dir)
    skills = read_ids(args.dir, "skills")
    if mobs is None or skills is None:
        print("[问题] mobs/skills 目录读取失败")
        print("共 1 份怪物技能引用表 · 1 条问题")
        return 1

    refs = 0
    for _rel, mob in mobs:
        entries = mob.get("skills")
        if isinstance(entries, list):
            refs += sum(1 for e in entries
                        if isinstance(e, dict) and isinstance(e.get("skill"), str))

    if args.verbose:
        print("已检查 %d 份怪物 · %d 条技能 id · %d 条 skills 引用"
              % (len(mobs), len(skills), refs))

    problems = []
    for rel, mob in mobs:
        for msg in unmatched([(rel, mob)], skills):
            problems.append((rel, msg))
    for rel, msg in sorted(problems, key=lambda x: x[1]):
        print("[问题] %s %s" % (rel, msg))
    print("共 1 份怪物技能引用表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
