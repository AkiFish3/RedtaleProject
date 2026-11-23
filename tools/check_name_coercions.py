"""Offline ruler for silent name coercions in item and tree definitions."""
import argparse
import json
import os
import re

_KEY = re.compile(r'([{,]\s*)([A-Za-z_$][A-Za-z0-9_$]*)(\s*:)|(,)(\s*)(?=[}\]])')


def _shape(text):
    out = []
    mask = []
    q = None
    esc = False
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if q:
            if esc:
                out.append("'" if c == "'" else "\\" + c)
                esc = False
            elif c == "\\":
                esc = True
            elif c == q:
                out.append('"')
                q = None
            else:
                out.append('\\"' if c == '"' else c)
            mask.append(False)
            i += 1
            continue
        if c in "'\"":
            q = c
            out.append('"')
            mask.append(False)
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            end = j + 2 if j >= 0 else n
            seg = text[i:end]
            out.extend("\n" if x == "\n" else " " for x in seg)
            mask.extend([True] * len(seg))
            i = end
            continue
        out.append(c)
        mask.append(True)
        i += 1

    def fix(m):
        if m.group(2) is not None:
            s = m.start(2)
            if all(mask[s:s + len(m.group(2))]):
                return m.group(1) + '"' + m.group(2) + '"' + m.group(3)
            return m.group(0)
        s = m.start(4)
        if all(mask[s:m.end(5)]):
            return m.group(5)
        return m.group(0)

    return _KEY.sub(fix, "".join(out))


def _loads(text):
    try:
        data = json.loads(_shape(text))
    except Exception:
        return None
    return data if isinstance(data, dict) else None


def _scan(directory, prefix):
    try:
        names = sorted(x for x in os.listdir(directory) if x.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(directory, name), encoding="utf-8") as handle:
                data = _loads(handle.read())
        except Exception:
            data = None
        rows.append((prefix + "/" + name, data))
    return rows, True


def read_items(items_dir):
    return _scan(items_dir, "items")


def read_trees(trees_dir):
    return _scan(trees_dir, "skill_trees")


def check_names(item_rows, tree_rows):
    out = []
    for prefix, data in item_rows:
        if not isinstance(data, dict) or "name" not in data:
            continue
        if not isinstance(data["name"], str):
            out.append(prefix + ": items.name 非字符串静默改写（解析零 WARN）")
    for prefix, data in tree_rows:
        if not isinstance(data, dict):
            continue
        if "name" in data and not isinstance(data["name"], str):
            out.append(prefix + ": skill_trees.name 非字符串静默 null（解析零 WARN）")
        nodes = data.get("nodes")
        if isinstance(nodes, dict):
            for nid in sorted(nodes):
                node = nodes[nid]
                if isinstance(node, dict) and "name" in node and not isinstance(node["name"], str):
                    out.append(prefix + ": nodes." + nid + ".name 非字符串静默回落 tree.node." + nid + "（解析零 WARN）")
    return sorted(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    items, ok_items = read_items(os.path.join(args.dir, "items"))
    trees, ok_trees = read_trees(os.path.join(args.dir, "skill_trees"))
    problems = []
    if not ok_items:
        problems.append("[问题] items 目录读取失败")
    if not ok_trees:
        problems.append("[问题] skill_trees 目录读取失败")
    problems.extend("[问题] " + x for x in check_names(items, trees))
    if args.v:
        ni = sum(1 for _, d in items if isinstance(d, dict) and "name" in d)
        tn = sum(1 for _, d in trees if isinstance(d, dict) and "name" in d)
        nn = 0
        for _, d in trees:
            nodes = d.get("nodes") if isinstance(d, dict) else None
            if isinstance(nodes, dict):
                nn += sum(1 for x in nodes.values() if isinstance(x, dict) and "name" in x)
        print("已检查 %d 份物品 · %d 处物品 name · %d 份技能树 · %d 处树 name · %d 处节点 name · 3 个静默改写落点" % (len(items), ni, len(trees), tn, nn))
    for problem in problems:
        print(problem)
    print("共 1 份名称静默改写口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
