"""词条池在冻结物品等级下的等级/类型可达性对账（只读，退出码 0=干净 1=有问题）。"""
import argparse
import json
import math
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = "src/main/resources/redtale/defaults"
ITEM_LEVEL = 1
KINDS = ("prefix", "suffix", "core")


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def ident(v: object) -> str:
    """字符串取冒号后最后一段，非字符串返空串。"""
    return v.rsplit(":", 1)[-1] if isinstance(v, str) else ""


def _files(folder):
    if not os.path.isdir(folder):
        return None
    try:
        names = os.listdir(folder)
    except OSError:
        return None
    return sorted(n for n in names
                  if n.endswith(".json5") and os.path.isfile(os.path.join(folder, n)))


def _parse(path):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            data = json.loads(clean_json5(f.read()))
    except (OSError, ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_defs(root: str) -> dict | None:
    folder = os.path.join(root, "affixes")
    names = _files(folder)
    if names is None:
        return None
    defs = {}
    for name in names:
        data = _parse(os.path.join(folder, name))
        if data is None or not isinstance(data.get("id"), str):
            continue
        key = ident(data["id"])
        if key not in defs:
            defs[key] = data
    return defs


def read_pools(root: str) -> list[tuple[str, dict]] | None:
    folder = os.path.join(root, "affix_pools")
    names = _files(folder)
    if names is None:
        return None
    pools = []
    for name in names:
        data = _parse(os.path.join(folder, name))
        if data is None or not isinstance(data.get("id"), str):
            continue
        pools.append(("affix_pools/" + name, data))
    return pools


def covers(defn: dict) -> bool:
    iv = defn.get("item_level")
    if not isinstance(iv, dict):
        return False
    lo, hi = iv.get("min"), iv.get("max")
    for v in (lo, hi):
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v):
            return False
    return lo <= ITEM_LEVEL <= hi


def unreachable(pools: list[tuple[str, dict]], defs: dict[str, dict]) -> list[str]:
    problems = []
    for _rel, pool in pools:
        pid = ident(pool.get("id"))
        slots = pool.get("slots")
        if not isinstance(slots, dict):
            continue
        for kind in KINDS:
            count = slots.get(kind)
            if isinstance(count, bool) or not isinstance(count, int) or count <= 0:
                continue
            refs = pool.get(kind)
            ok = False
            if isinstance(refs, dict):
                for ref in refs:
                    key = ident(ref)
                    defn = defs.get(key)
                    if defn is not None and defn.get("kind") == kind and covers(defn):
                        ok = True
                        break
            if not ok:
                problems.append(
                    "词条池 {} 的 {} 槽无等级可达的同类型词条（物品等级 {}）".format(pid, kind, ITEM_LEVEL))
    return sorted(problems)


def _slots(pools):
    total = 0
    for _rel, pool in pools:
        slots = pool.get("slots")
        if not isinstance(slots, dict):
            continue
        for kind in KINDS:
            count = slots.get(kind)
            if isinstance(count, bool) or not isinstance(count, int) or count <= 0:
                continue
            total += 1
    return total


def main() -> int:
    _utf8_stdout()
    ap = argparse.ArgumentParser(description="词条等级/类型可达性对账")
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    root = args.dir if os.path.isabs(args.dir) else os.path.join(ROOT, args.dir)
    defs = read_defs(root)
    pools = read_pools(root)
    if defs is None or pools is None:
        print("[问题] affixes/affix_pools 目录读取失败")
        print("共 1 份词条等级可达性表 · 1 条问题")
        return 1
    problems = unreachable(pools, defs)
    if args.v:
        print("已检查 {} 份词条定义 · {} 个词条池 · {} 个有效槽位段 · 物品等级 {}".format(
            len(defs), len(pools), _slots(pools), ITEM_LEVEL))
    shown = []
    for rel, pool in pools:
        for msg in unreachable([(rel, pool)], defs):
            shown.append((msg, rel))
    shown.sort(key=lambda item: item[0])
    for msg, rel in shown:
        print("[问题] {} {}".format(rel, msg))
    print("共 1 份词条等级可达性表 · {} 条问题".format(len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
