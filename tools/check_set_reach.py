#!/usr/bin/env python3
"""套装档位装备槽可达性对账：sets/*.json5 JOIN items/*.json5 与冻结槽位表。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

# 14 个基底 → 6 个装备槽（S7 冻结；Curios 接入后由 S7 改本表）
SLOT_OF = {
    "sword": "MAINHAND",
    "dagger": "MAINHAND",
    "axe": "MAINHAND",
    "hammer": "MAINHAND",
    "spear": "MAINHAND",
    "staff": "MAINHAND",
    "bow": "MAINHAND",
    "ring": "MAINHAND",
    "amulet": "MAINHAND",
    "shield": "OFFHAND",
    "helmet": "HEAD",
    "chestplate": "CHEST",
    "leggings": "LEGS",
    "boots": "FEET",
}


def ident(v):
    """字符串取冒号后最后一段；非字符串返空串。"""
    if not isinstance(v, str):
        return ""
    return v.rsplit(":", 1)[-1]


def _load(folder, name):
    try:
        with open(os.path.join(folder, name), "r", encoding="utf-8", errors="replace") as fh:
            return json.loads(clean_json5(fh.read()))
    except Exception:
        return None


def read_items(root):
    """键 = ident(id)，同键先写赢；目录不可读返 None。"""
    folder = os.path.join(root, "items")
    try:
        names = sorted(os.listdir(folder))
    except OSError:
        return None
    result = {}
    for name in names:
        if not name.endswith(".json5"):
            continue
        if not os.path.isfile(os.path.join(folder, name)):
            continue
        data = _load(folder, name)
        if not isinstance(data, dict) or not isinstance(data.get("id"), str):
            continue
        key = ident(data["id"])
        if key not in result:
            result[key] = data
    return result


def read_sets(root):
    """返回 [(rel, data)]，文件名升序；目录不可读返 None。"""
    folder = os.path.join(root, "sets")
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
        result.append(("sets/" + name, data))
    return result


def reachable_slots(pieces, items):
    """pieces 经 ident 归一后 JOIN base，按不同装备槽去重计数。"""
    if not isinstance(pieces, list):
        return 0
    slots = set()
    for piece in pieces:
        item = items.get(ident(piece))
        if not isinstance(item, dict):
            continue
        base = item.get("base")
        if not isinstance(base, str):
            continue
        slot = SLOT_OF.get(base)
        if slot is not None:
            slots.add(slot)
    return len(slots)


def _pool_problems(pool, items):
    out = []
    sid = ident(pool.get("id"))
    bonuses = pool.get("bonuses")
    if not isinstance(bonuses, list):
        return out
    cap = reachable_slots(pool.get("pieces"), items)
    for bonus in bonuses:
        if not isinstance(bonus, dict):
            continue
        count = bonus.get("count")
        if isinstance(count, bool) or not isinstance(count, int) or count < 1:
            continue
        if count > cap:
            out.append("套装 %s 的 %d 件档永远不可达（可达装备槽 %d）" % (sid, count, cap))
    return out


def unreachable(sets, items):
    """每条不可达档的文案，按文案升序、稳定。"""
    out = []
    for _rel, pool in sets:
        out.extend(_pool_problems(pool, items))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description="套装档位装备槽可达性对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    items = read_items(args.dir)
    sets = read_sets(args.dir)
    if items is None or sets is None:
        print("[问题] items/sets 目录读取失败")
        print("共 1 份套装档位可达性表 · 1 条问题")
        return 1

    pieces_total = 0
    tiers = 0
    slots_total = 0
    problems = []
    for rel, pool in sets:
        pieces = pool.get("pieces")
        if isinstance(pieces, list):
            pieces_total += len(pieces)
        bonuses = pool.get("bonuses")
        if isinstance(bonuses, list):
            for bonus in bonuses:
                if not isinstance(bonus, dict):
                    continue
                count = bonus.get("count")
                if not isinstance(count, bool) and isinstance(count, int) and count >= 1:
                    tiers += 1
        slots_total += reachable_slots(pieces, items)
        for msg in unreachable([(rel, pool)], items):
            problems.append((rel, msg))

    if args.verbose:
        print("已检查 %d 份套装 · %d 件引用 · %d 个档位 · %d 个可达装备槽"
              % (len(sets), pieces_total, tiers, slots_total))
    for rel, msg in sorted(problems, key=lambda x: x[1]):
        print("[问题] %s %s" % (rel, msg))
    print("共 1 份套装档位可达性表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
