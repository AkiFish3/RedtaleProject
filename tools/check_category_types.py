"""伤害类别 type 完整形态对账：离线、单文件、零接线。"""

import argparse
import json
import os

from check_progress import clean_json5

DEFAULT_DIR = "src/main/resources/redtale/defaults"
CATEGORY_TYPES = frozenset((
    "redtale_project:skill_magical", "redtale_project:skill_physical",
))


def read_config(root):
    """只读 <root>/damage.json5；不可读/解析失败/非 dict 返 None，不抛栈。"""
    path = os.path.join(root, "damage.json5")
    try:
        with open(path, "r", encoding="utf-8") as fh:
            data = json.loads(clean_json5(fh.read()))
    except (OSError, ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def read_types(data):
    """按文件顺序取 category_attributes 内 dict 元素的 type 字符串（strip，空串跳过）。"""
    multiplier = data.get("multiplier")
    if not isinstance(multiplier, dict):
        return []
    attrs = multiplier.get("category_attributes")
    if not isinstance(attrs, list):
        return []
    out = []
    for entry in attrs:
        if not isinstance(entry, dict):
            continue
        value = entry.get("type")
        if not isinstance(value, str):
            continue
        value = value.strip()
        if value:
            out.append(value)
    return out


def unmatched(types):
    """每个非完整合法 id 出一条，重复逐条报；按文案升序、保稳定。"""
    out = []
    for value in types:
        if value not in CATEGORY_TYPES:
            out.append(
                "damage multiplier.category_attributes 的 type %s "
                "不是有效的类别伤害类型（必须精确写 redtale_project:skill_magical "
                "或 redtale_project:skill_physical）" % value
            )
    return sorted(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    data = read_config(args.dir)
    if data is None:
        print("[问题] damage.json5 读取失败")
        print("共 1 份伤害类别类型表 · 1 条问题")
        return 1

    types = read_types(data)
    problems = unmatched(types)
    if args.v:
        print("已检查 1 份伤害配置 · %d 处类别 type 引用" % len(types))
    for message in problems:
        print("[问题] damage.json5 %s" % message)
    print("共 1 份伤害类别类型表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
