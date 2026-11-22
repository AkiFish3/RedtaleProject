"""怪物装备槽位 ↔ 基底槽位对账（T-0098）：只读静态扫描，0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MOBS = os.path.join(ROOT, "src/main/resources/redtale/defaults/mobs")
NS = "redtale_project:"
# 由 S7 从 ItemBase 冻结，S8 不读源码。
SLOT_OF = {
    "sword": "MAINHAND", "dagger": "MAINHAND", "axe": "MAINHAND",
    "hammer": "MAINHAND", "spear": "MAINHAND", "staff": "MAINHAND",
    "bow": "MAINHAND", "ring": "MAINHAND", "amulet": "MAINHAND",
    "shield": "OFFHAND",
    "helmet": "HEAD", "chestplate": "CHEST", "leggings": "LEGS", "boots": "FEET",
}
SLOTS = ("mainhand", "offhand", "head", "chest", "legs", "feet")


def _loads(text):
    """单引号字面量归一化后交 clean_json5（沿用既有 shim 口径）。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(text[i:j])
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            i = j
            continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1] if text[j + 1] == "'" else text[j:j + 2])
                    j += 2
                    continue
                buf.append('\\"' if text[j] == '"' else text[j])
                j += 1
            out.append('"' + "".join(buf) + '"')
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return json.loads(clean_json5("".join(out)))


def read_mobs(root: str):
    """扫 <root>/*.json5（文件名升序）→ (文件名, data|None)；目录异常返 None。"""
    try:
        names = sorted(f for f in os.listdir(root)
                       if f.endswith(".json5") and os.path.isfile(os.path.join(root, f)))
    except OSError:
        return None
    out = []
    for name in names:
        data = None
        try:
            with open(os.path.join(root, name), "r", encoding="utf-8", errors="replace") as f:
                parsed = _loads(f.read())
            if isinstance(parsed, dict):
                data = parsed
        except Exception:
            pass
        out.append((name, data))
    return out


def mismatches(rows):
    """自家 ns 且 path ∈ 14 基底的引用，槽名与基底声明槽不符才报；文案升序去重。"""
    out = []
    for name, data in rows:
        if not isinstance(data, dict):
            continue
        equipment = data.get("equipment")
        if not isinstance(equipment, dict):
            continue
        mid = data.get("id")
        if not isinstance(mid, str):
            mid = name[:-6] if name.endswith(".json5") else name
        for slot, value in equipment.items():
            s = str(slot)
            if s not in SLOTS:
                continue
            if not isinstance(value, str) or not value.startswith(NS):
                continue
            base = value[len(NS):]
            if base not in SLOT_OF:
                continue
            target = SLOT_OF[base]
            if target != s.upper():
                out.append(f"怪物 {mid} 的装备槽 {s} 放了 {base}，该基底只能在 {target} 槽生效")
    return sorted(set(out))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mobs", default=DEFAULT_MOBS)
    ap.add_argument("-v", action="store_true")
    opts = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    mobs = read_mobs(opts.mobs)
    if mobs is None:
        print("[问题] mobs 目录读取失败")
        print("共 1 份怪物装备槽位对账表 · 1 条问题")
        return 1
    problems = []
    for name, data in mobs:
        for msg in mismatches([(name, data)]):
            problems.append((name, msg))
    problems.sort(key=lambda t: t[1])
    if opts.v:
        k = 0
        for _, data in mobs:
            if not isinstance(data, dict):
                continue
            equipment = data.get("equipment")
            if isinstance(equipment, dict):
                k += sum(1 for v in equipment.values()
                         if isinstance(v, str) and v.startswith(NS))
        print(f"已检查 {len(mobs)} 份怪物 · {k} 处自家基底装备引用 · 14 个基底")
    for name, msg in problems:
        print(f"[问题] {name} {msg}")
    print(f"共 1 份怪物装备槽位对账表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
