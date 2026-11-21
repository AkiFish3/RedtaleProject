"""词条池词条 id 完整字符串引用对账：离线、单文件、零接线。"""

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


def read_defs(root):
    """收完整字符串 id，不改写、不取后段。目录不可读返 None。"""
    got = _files(root, DEF_SUB)
    if got is None:
        return None
    directory, names = got
    ids = set()
    for name in names:
        data = _load(os.path.join(directory, name))
        if data is None:
            continue
        value = data.get("id")
        if isinstance(value, str):
            ids.add(value)
    return ids


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


def unmatched(pools, def_ids):
    """段内字符串键按完整字符串命中 def_ids；返回按文案升序的报错行。"""
    out = []
    for _rel, data in pools:
        pool_id = data.get("id")
        for kind in KINDS:
            segment = data.get(kind)
            if not isinstance(segment, dict):
                continue
            for key in segment:
                if isinstance(key, str) and key not in def_ids:
                    out.append(
                        "词条池 %s 的 %s 段引用了未定义的词条 %s" % (pool_id, kind, key)
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

    def_ids = read_defs(args.dir)
    pools = read_pools(args.dir)
    if def_ids is None or pools is None:
        print("[问题] affixes/affix_pools 目录读取失败")
        print("共 1 份词条池引用表 · 1 条问题")
        return 1

    problems = unmatched(pools, def_ids)
    if args.v:
        print("已检查 %d 份词条池 · %d 条词条定义 · %d 条词条引用"
              % (len(pools), len(def_ids), _ref_count(pools)))
    for rel, data in pools:
        for message in unmatched([(rel, data)], def_ids):
            print("[问题] %s %s" % (rel, message))
    print("共 1 份词条池引用表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
