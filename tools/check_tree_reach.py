"""技能树 requires 可达性对账（T-0087）：只读，0=干净 1=有问题。"""
from __future__ import annotations
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_TREES = "src/main/resources/redtale"


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def _loads(raw: str):
    """clean_json5 + 单引号兜底；先原样解析，失败再转单引号串。"""
    try:
        return json.loads(clean_json5(raw))
    except Exception:
        pass
    out, i, n = [], 0, len(raw)
    while i < n:
        c = raw[i]
        if c == '"':
            j = i + 1
            while j < n and raw[j] != '"':
                j += 2 if raw[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(raw[i:j])
            i = j
            continue
        if c == "'":
            j, buf = i + 1, ['"']
            while j < n and raw[j] != "'":
                buf.append('\\"' if raw[j] == '"' else raw[j])
                j += 1
            buf.append('"')
            out.append("".join(buf))
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return json.loads(clean_json5("".join(out)))


def read_nodes(data: dict | None) -> dict[str, list[str]] | None:
    """节点 -> 合法前置；缺节点表/空表返 None，坏元素丢弃不报。"""
    if not isinstance(data, dict):
        return None
    nodes = data.get("nodes")
    if not isinstance(nodes, dict) or not nodes:
        return None
    keys = set(nodes.keys())
    out = {}
    for name, node in nodes.items():
        reqs = node.get("requires", []) if isinstance(node, dict) else []
        if not isinstance(reqs, list):
            reqs = []
        out[name] = [r for r in reqs
                     if isinstance(r, str) and r in keys and r != name]
    return out


def unreachable(nodes: dict[str, list[str]]) -> list[str]:
    """固定点求可达集；环上及依赖环的节点全部返回，按名升序。"""
    reachable = {name for name, reqs in nodes.items() if not reqs}
    changed = True
    while changed:
        changed = False
        for name, reqs in nodes.items():
            if name in reachable:
                continue
            if all(req in reachable for req in reqs):
                reachable.add(name)
                changed = True
    return sorted(set(nodes) - reachable)


def read_trees(root: str) -> list[tuple[str, dict | None, str | None]] | None:
    """扫 <root>/skill_trees/*.json5；目录不可读返 None，不抛栈。"""
    folder = os.path.join(root, "skill_trees")
    try:
        names = sorted(os.listdir(folder))
    except OSError:
        return None
    out = []
    for name in names:
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        rel = "skill_trees/" + name
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                raw = f.read()
        except OSError as ex:
            out.append((rel, None, str(ex)))
            continue
        try:
            data = _loads(raw)
        except Exception as ex:
            out.append((rel, None, str(ex)))
            continue
        if not isinstance(data, dict):
            out.append((rel, None, "顶层不是对象"))
            continue
        out.append((rel, data, None))
    return out


def main() -> int:
    _utf8_stdout()
    parser = argparse.ArgumentParser(description="技能树前置可达性对账")
    parser.add_argument("--trees", default=DEFAULT_TREES)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    os.chdir(ROOT)
    root = os.path.abspath(args.trees)
    # 仓库默认根下还有一层 defaults/；仅默认参数命中时回落，显式路径不动。
    if args.trees == DEFAULT_TREES and not os.path.isdir(os.path.join(root, "skill_trees")):
        alt = os.path.join(root, "defaults")
        if os.path.isdir(os.path.join(alt, "skill_trees")):
            root = alt

    files = read_trees(root)
    if files is None:
        print("[问题] skill_trees 目录读取失败")
        print("共 0 份技能树可达性表 · 1 条问题")
        return 1

    problems = 0
    total_nodes = 0
    total_dead = 0
    for rel, data, err in files:
        if err is not None:
            print("[问题] " + rel + " 配置读取失败：" + err)
            problems += 1
            continue
        nodes = read_nodes(data)
        if nodes is None:
            print("[问题] " + rel + " nodes 缺失/不是对象/为空")
            problems += 1
            continue
        total_nodes += len(nodes)
        dead = unreachable(nodes)
        total_dead += len(dead)
        for name in dead:
            print("[问题] " + rel + " 节点 " + name
                  + " 永远不可达（requires 成环或依赖不可达节点）")
            problems += 1

    if args.v:
        print("已检查 " + str(len(files)) + " 棵树 · " + str(total_nodes)
              + " 个节点 · " + str(total_dead) + " 个不可达")
    print("共 " + str(len(files)) + " 份技能树可达性表 · " + str(problems) + " 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
