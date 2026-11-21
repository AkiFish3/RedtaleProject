#!/usr/bin/env python3
"""物品绑定技能完整 id 引用对账：abilities[] 字符串元素按完整字符串命中技能表。"""
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


def read_items(root):
    """返回 [(rel, data)]，文件名升序；目录不可读返 None。"""
    folder = os.path.join(root, "items")
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
        result.append(("items/" + name, data))
    return result


def unmatched(items, skill_ids):
    """abilities 字符串元素未命中完整 id 的文案列表，按文案升序、稳定。"""
    out = []
    for _rel, item in items:
        abilities = item.get("abilities")
        if not isinstance(abilities, list):
            continue
        iid = item["id"]
        for skill in abilities:
            if not isinstance(skill, str):
                continue
            if skill in skill_ids:
                continue
            out.append("物品 %s 绑定的技能 %s 未命中技能表完整 id" % (iid, skill))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description="物品绑定技能完整 id 引用对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    items = read_items(args.dir)
    skills = read_ids(args.dir, "skills")
    if items is None or skills is None:
        print("[问题] items/skills 目录读取失败")
        print("共 1 份物品技能引用表 · 1 条问题")
        return 1

    refs = 0
    for _rel, item in items:
        abilities = item.get("abilities")
        if isinstance(abilities, list):
            refs += sum(1 for skill in abilities if isinstance(skill, str))

    if args.verbose:
        print("已检查 %d 份物品 · %d 条技能 id · %d 条 abilities 引用"
              % (len(items), len(skills), refs))

    problems = []
    for rel, item in items:
        for msg in unmatched([(rel, item)], skills):
            problems.append((rel, msg))
    for rel, msg in sorted(problems, key=lambda x: x[1]):
        print("[问题] %s %s" % (rel, msg))
    print("共 1 份物品技能引用表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
