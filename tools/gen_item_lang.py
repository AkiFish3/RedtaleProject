#!/usr/bin/env python3
"""从 ItemBase.java 生成 14 个基底物品的 lang 键（**合并**写入，不整体覆写）。

用法：
    python tools/gen_item_lang.py

为什么不复用 gen_attr_lang.py：那个脚本是**整体覆写**（表即真相源），
而本脚本只拥有 `item.redtale_project.*` 这 14 个键 —— 其余的
`attribute.redtale_project.*` 66 条、死亡消息等必须原样带出，
否则任何一次重跑都会把别人的键抹掉。

id 来源：正则扫 ItemBase.java 枚举常量行的第一个字符串字面量
（`    SWORD("sword", Kind.WEAPON, ...` → `sword`），不手工重抄列表。
名称表写在下面，缺项 → 打警告并用 id 兜底，不崩。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE_FILE = ROOT / "src/main/java/com/redtale/project/items/ItemBase.java"
LANG_DIR = ROOT / "src/main/resources/assets/redtale_project/lang"

# 枚举常量 = 4 空格缩进 + 大写常量名 + 括号 + 首个字符串字面量
ENUM_RE = re.compile(r'^\s{4}([A-Z][A-Z0-9_]*)\(\s*"([^"]+)"')
KEY_PREFIX = "item.redtale_project."

ZH = {
    "sword": "剑",
    "dagger": "匕首",
    "axe": "斧",
    "hammer": "锤",
    "spear": "长枪",
    "staff": "法杖",
    "bow": "弓",
    "shield": "盾",
    "helmet": "头盔",
    "chestplate": "胸甲",
    "leggings": "护腿",
    "boots": "靴子",
    "ring": "戒指",
    "amulet": "护符",
}

EN = {
    "sword": "Sword",
    "dagger": "Dagger",
    "axe": "Axe",
    "hammer": "Hammer",
    "spear": "Spear",
    "staff": "Staff",
    "bow": "Bow",
    "shield": "Shield",
    "helmet": "Helmet",
    "chestplate": "Chestplate",
    "leggings": "Leggings",
    "boots": "Boots",
    "ring": "Ring",
    "amulet": "Amulet",
}


def parse_ids() -> list[str]:
    """按文件顺序取枚举常量的第一个字符串字面量，去重保序。"""
    text = BASE_FILE.read_text(encoding="utf-8")
    found: list[str] = []
    for line in text.splitlines():
        m = ENUM_RE.match(line)
        if m and m.group(2) not in found:
            found.append(m.group(2))
    return found


def merge_into(path: Path, names: dict[str, str], ids: list[str]) -> int:
    """读现有 lang → 只覆盖 item.redtale_project.<id> → 整体写回。返回写入键数。"""
    data: dict[str, str] = {}
    if path.exists():
        raw = path.read_text(encoding="utf-8")
        if raw.strip():
            try:
                data = json.loads(raw)
            except json.JSONDecodeError as exc:
                print(f"!! {path.name} 解析失败（{exc}），已跳过，未写入", file=sys.stderr)
                return -1
            if not isinstance(data, dict):
                print(f"!! {path.name} 顶层不是对象，已跳过，未写入", file=sys.stderr)
                return -1

    before = sum(1 for k in data if k.startswith(KEY_PREFIX))
    for item_id in ids:
        data[KEY_PREFIX + item_id] = names.get(item_id, item_id)

    text = json.dumps(dict(sorted(data.items())), ensure_ascii=False, indent=2) + "\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print(f"  写入 {path.relative_to(ROOT)}（{len(data)} 键，item 键 {before} → "
          f"{sum(1 for k in data if k.startswith(KEY_PREFIX))}）")
    return len(data)


def main() -> int:
    if not BASE_FILE.exists():
        print(f"!! 找不到 {BASE_FILE.relative_to(ROOT)}", file=sys.stderr)
        return 2

    ids = parse_ids()
    if not ids:
        print(f"!! {BASE_FILE.name} 里没解析到枚举 id，正则失配了", file=sys.stderr)
        return 2
    if len(ids) != 14:
        print(f"!! 解析到 {len(ids)} 个 id（预期 14）：{', '.join(ids)}", file=sys.stderr)

    for table, tag in ((ZH, "zh"), (EN, "en")):
        missing = [i for i in ids if i not in table]
        if missing:
            print(f"!! {tag} 名称表缺 {', '.join(missing)}，已用 id 兜底", file=sys.stderr)

    print(f"解析到 {len(ids)} 个基底 id：{', '.join(ids)}")
    payloads = (
        (LANG_DIR / "zh_cn.json", ZH),
        (LANG_DIR / "en_us.json", EN),
    )
    for path, table in payloads:
        if merge_into(path, table, ids) < 0:
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
