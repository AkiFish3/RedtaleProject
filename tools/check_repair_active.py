"""修理模式激活字段有效性对账：离线、单文件、零接线。"""

import argparse
import json
import os

from check_progress import clean_json5

DEFAULT_DIR = "src/main/resources/redtale/defaults"


def _load(path):
    try:
        with open(path, "r", encoding="utf-8") as fh:
            data = json.loads(clean_json5(fh.read()))
    except (OSError, ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_items(root):
    """返回 (rel, data) 保序列表；items 目录不可读返 None。"""
    directory = os.path.join(root, "items")
    try:
        names = sorted(
            n for n in os.listdir(directory)
            if n.endswith(".json5") and os.path.isfile(os.path.join(directory, n))
        )
    except OSError:
        return None
    items = []
    for name in names:
        data = _load(os.path.join(directory, name))
        if data is None or not isinstance(data.get("id"), str):
            continue
        items.append(("items/" + name, data))
    return items


def check_repair(items):
    """只核合法 mode 选中的数值字段；返回按文案升序的报错行。"""
    out = []
    for _rel, data in items:
        repair = data.get("repair")
        if not isinstance(repair, dict):
            continue
        mode = repair.get("mode")
        if mode not in ("amount", "percent"):
            continue
        value = repair.get(mode)
        bad = (
            value is None
            or isinstance(value, bool)
            or not isinstance(value, (int, float))
        )
        if not bad and mode == "amount" and not isinstance(value, int):
            bad = True
        if not bad and not value > 0:
            bad = True
        if bad:
            out.append(
                "物品 %s 的 repair.mode=%s 缺少有效的 %s（必须 > 0）"
                % (data["id"], mode, mode)
            )
        elif mode == "percent" and value > 1:
            continue
    return sorted(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    items = read_items(args.dir)
    if items is None:
        print("[问题] items 目录读取失败")
        print("共 1 份修理有效性表 · 1 条问题")
        return 1

    problems = check_repair(items)
    if args.v:
        segments = sum(
            1 for _rel, data in items if isinstance(data.get("repair"), dict)
        )
        print("已检查 %d 份物品 · %d 条 repair 段" % (len(items), segments))
    found = []
    for rel, data in items:
        for message in check_repair([(rel, data)]):
            found.append((rel, message))
    for rel, message in sorted(found):
        print("[问题] %s %s" % (rel, message))
    print("共 1 份修理有效性表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
