"""词条池段名 ↔ 词条 kind 一致性对账：离线、单文件、零接线。"""

import argparse
import json
import os

from check_progress import clean_json5

KINDS = ("prefix", "suffix", "core")
DEF_SUB = "affixes"
POOL_SUB = "affix_pools"


def _load(path):
    try:
        with open(path, "r", encoding="utf-8") as fh:
            data = json.loads(clean_json5(fh.read()))
    except (OSError, ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def _files(root, sub):
    directory = os.path.join(root, sub)
    try:
        names = sorted(
            n for n in os.listdir(directory)
            if n.endswith(".json5") and os.path.isfile(os.path.join(directory, n))
        )
    except OSError:
        return None
    return directory, names


def read_def_kinds(root):
    """收 affixes/*.json5 的 id → kind；只收合法 kind。目录不可读返 None。"""
    got = _files(root, DEF_SUB)
    if got is None:
        return None
    directory, names = got
    kinds = {}
    for name in names:
        data = _load(os.path.join(directory, name))
        if data is None:
            continue
        ident = data.get("id")
        kind = data.get("kind")
        if isinstance(ident, str) and isinstance(kind, str) and kind in KINDS:
            kinds[ident] = kind
    return kinds


def read_pools(root):
    """返回 (rel, data) 保序列表；目录不可读返 None。"""
    got = _files(root, POOL_SUB)
    if got is None:
        return None
    directory, names = got
    pools = []
    for name in names:
        data = _load(os.path.join(directory, name))
        if data is None:
            continue
        if not isinstance(data.get("id"), str):
            continue
        pools.append((POOL_SUB + "/" + name, data))
    return pools


def mismatches(pools, def_kinds):
    """段名与词条 kind 不符时报行；按文案升序 sorted 后返回。"""
    out = []
    for _rel, data in pools:
        pool_id = data.get("id")
        for kind in KINDS:
            segment = data.get(kind)
            if not isinstance(segment, dict):
                continue
            for key in segment:
                if isinstance(key, str) and key in def_kinds and def_kinds[key] != kind:
                    out.append(
                        "词条池 %s 的 %s 段放了 %s 类词条 %s"
                        % (pool_id, kind, def_kinds[key], key)
                    )
    return sorted(out)


def _ref_count(pools):
    total = 0
    for _rel, data in pools:
        for kind in KINDS:
            segment = data.get(kind)
            if isinstance(segment, dict):
                total += sum(1 for key in segment if isinstance(key, str))
    return total


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    def_kinds = read_def_kinds(args.dir)
    pools = read_pools(args.dir)
    if def_kinds is None or pools is None:
        print("[问题] affixes/affix_pools 目录读取失败")
        print("共 1 份词条段类型对账表 · 1 条问题")
        return 1

    problems = mismatches(pools, def_kinds)
    if args.v:
        print("已检查 %d 份词条池 · %d 条词条定义 · %d 条词条引用"
              % (len(pools), len(def_kinds), _ref_count(pools)))
    for rel, data in pools:
        for message in mismatches([(rel, data)], def_kinds):
            print("[问题] %s %s" % (rel, message))
    print("共 1 份词条段类型对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
