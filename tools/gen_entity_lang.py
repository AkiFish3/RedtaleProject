#!/usr/bin/env python3
"""从 RedtaleEntities.java 生成 8 个模板实体的 lang 键（**合并**写入，不整体覆写）。

用法：
    python tools/gen_entity_lang.py

只拥有 `entity.redtale_project.*` 这 8 个键 —— `attribute.*` 66 条、
`item.*` 14 条、死亡消息、附魔名必须原样带出，否则任何一次重跑都会把别人的键抹掉。

id 来源：正则扫 `new Proto("melee", 0.6F, ...` 的首个字面量，不手工重抄列表。
名称表写在下面，缺项 → 打警告并用 id 兜底，不崩。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENTITY_FILE = ROOT / "src/main/java/com/redtale/project/mobs/RedtaleEntities.java"
LANG_DIR = ROOT / "src/main/resources/assets/redtale_project/lang"
PROTO_RE = re.compile(r'new Proto\(\s*"([a-z_]+)"')
KEY_PREFIX = "entity.redtale_project."
EXPECTED = 8

ZH = {
    "melee": "近战",
    "ranged": "远程",
    "caster": "施法",
    "summoner": "召唤",
    "charger": "冲锋",
    "flyer": "飞行",
    "elite": "精英",
    "ambient": "环境",
}

EN = {
    "melee": "Melee",
    "ranged": "Ranged",
    "caster": "Caster",
    "summoner": "Summoner",
    "charger": "Charger",
    "flyer": "Flyer",
    "elite": "Elite",
    "ambient": "Ambient",
}


def parse_ids() -> list[str]:
    """按文件出现顺序取 Proto 的首个字面量，去重保序。"""
    text = ENTITY_FILE.read_text(encoding="utf-8")
    found: list[str] = []
    for m in PROTO_RE.finditer(text):
        if m.group(1) not in found:
            found.append(m.group(1))
    return found


def merge_into(path: Path, names: dict[str, str], ids: list[str]) -> int:
    """读现有 lang → 只覆盖 entity.redtale_project.<id> → 整体写回。返回写入键数。"""
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
    for entity_id in ids:
        data[KEY_PREFIX + entity_id] = names.get(entity_id, entity_id)

    text = json.dumps(dict(sorted(data.items())), ensure_ascii=False, indent=2) + "\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"  写入 {path.relative_to(ROOT)}（{len(data)} 键，entity 键 {before} → "
          f"{sum(1 for k in data if k.startswith(KEY_PREFIX))}）")
    return len(data)


def main() -> int:
    if not ENTITY_FILE.exists():
        print(f"!! 找不到 {ENTITY_FILE.relative_to(ROOT)}", file=sys.stderr)
        return 2

    ids = parse_ids()
    if not ids:
        print("!! RedtaleEntities.java 里没解析到原型 id，正则失配了", file=sys.stderr)
        return 2
    if len(ids) != EXPECTED:
        print(f"!! 解析到 {len(ids)} 个原型 id（预期 {EXPECTED}）：{', '.join(ids)}", file=sys.stderr)

    print(f"解析到 {len(ids)} 个原型 id：{', '.join(ids)}")

    for table, tag in ((ZH, "zh"), (EN, "en")):
        missing = [i for i in ids if i not in table]
        if missing:
            print(f"!! {tag} 名称表缺 {', '.join(missing)}，已用 id 兜底", file=sys.stderr)

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
