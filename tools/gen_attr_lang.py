#!/usr/bin/env python3
"""从 AttributeSpecs.java 生成 lang 文件（属性名唯一来源 = 那张 80 条表）。

用法：
    python tools/gen_attr_lang.py            # 生成
    python tools/gen_attr_lang.py --check    # 只校验，不写文件（CI/收工自检用）

为什么用脚本而不是手写两个 lang json：表里 66 条自建属性，手抄必错位；
而 lang 键一旦和 `RangedAttribute` 的 descriptionId 对不上，客户端就显示成
`attribute.redtale_project.xxx` 原文。

解析约定（改表时别破坏它）：每条一行，形如
    v("minecraft:generic.attack_damage", MELEE, "攻击伤害", "Attack Damage"),
    c("physical_damage", MELEE, "物理伤害", "Physical Damage"),
    cb("critical_strike_power", 2.0, MELEE, "暴击倍率", "Critical Strike Power"),
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC_FILE = ROOT / "src/main/java/com/redtale/project/core/attr/AttributeSpecs.java"
LANG_DIR = ROOT / "src/main/resources/assets/redtale_project/lang"

LINE_RE = re.compile(
    r'^\s+(v|c|cb)\("([^"]+)",\s*(?:[0-9.]+\s*,\s*)?([A-Z_]+),\s*"([^"]+)",\s*"([^"]+)"\)'
)

# 本脚本**只拥有** `attribute.redtale_project.*` 这 66 个键 + 下面 EXTRA 里列出的键，
# 其余键（`item.*` 14 / `entity.*` 8 / `enchantment.*` 2 / 他人写的 `death.*`）一律原样保留。
# R59 前是整体覆写：跑一次就把 93 键削成 67 键（`--check` 还报「过期」，误导人跑一次去「修」）。
OWNED_PREFIX = "attribute.redtale_project."

# 非属性类 lang 键（死亡消息、界面文案）。必须写在这里，
# 否则每次重新生成都会被抹掉——脚本是合并写入，但只保留自己拥有的键。
EXTRA = {
    "zh_cn": {
        "death.attack.redtale_verification_strike": "%1$s 被赤物语的校验打击击倒",
    },
    "en_us": {
        "death.attack.redtale_verification_strike": "%1$s was struck down by a Redtale verification strike",
    },
}


def preserved(path: Path, owned: dict[str, str]) -> dict[str, str]:
    """读现有 lang，返回「不属于本脚本」的键值（除 OWNED_PREFIX 与 EXTRA 之外的全部）。"""
    if not path.exists():
        return {}
    try:
        current = json.loads(path.read_text(encoding="utf-8"))
    except Exception as ex:  # 坏 JSON 不当静默覆盖的借口
        print(f"!! {path.name} 无法解析（{ex}），拒绝覆盖", file=sys.stderr)
        raise SystemExit(2)
    if not isinstance(current, dict):
        print(f"!! {path.name} 顶层不是对象，拒绝覆盖", file=sys.stderr)
        raise SystemExit(2)
    return {k: v for k, v in current.items() if not str(k).startswith(OWNED_PREFIX) and k not in owned}


def parse() -> list[dict]:
    rows = []
    for lineno, line in enumerate(SPEC_FILE.read_text(encoding="utf-8").splitlines(), 1):
        m = LINE_RE.match(line)
        if not m:
            continue
        kind, ident, group, zh, en = m.groups()
        rows.append(
            dict(lineno=lineno, kind=kind, id=ident, group=group, zh=zh, en=en,
                 vanilla=ident.startswith("minecraft:"))
        )
    return rows


def main() -> int:
    rows = parse()
    custom = [r for r in rows if not r["vanilla"]]
    total = len(rows)
    check = "--check" in sys.argv

    print(f"解析到 {total} 条（自建 {len(custom)} / 原版 {total - len(custom)}），来源 {SPEC_FILE.name}")
    if total != 80:
        print(f"!! 条数 {total} != 80，表与 docs/ATTRIBUTE-REGISTRY.md §2 不一致", file=sys.stderr)
        return 2

    zh: dict[str, str] = {}
    en: dict[str, str] = {}
    for r in custom:
        key = "attribute.redtale_project." + r["id"].split(":", 1)[-1]
        for bucket, val in ((zh, r["zh"]), (en, r["en"])):
            if key in bucket:
                print(f"!! {SPEC_FILE.name}:{r['lineno']} lang 键重复：{key}", file=sys.stderr)
                return 2
            bucket[key] = val

    payloads = {
        LANG_DIR / "zh_cn.json": zh,
        LANG_DIR / "en_us.json": en,
    }
    for path, data in payloads.items():
        owned = {**data, **EXTRA[path.stem]}
        merged = {**preserved(path, owned), **owned}
        text = json.dumps(dict(sorted(merged.items())), ensure_ascii=False, indent=2) + "\n"
        if check:
            old = path.read_text(encoding="utf-8") if path.exists() else ""
            status = "OK" if old == text else "过期"
            print(f"  {path.relative_to(ROOT)}：{status}（{len(merged)} 键，自有 {len(owned)}）")
            if old != text:
                return 1
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="\n")
            print(f"  写入 {path.relative_to(ROOT)}（{len(merged)} 键，保留他人 {len(merged) - len(owned)}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
