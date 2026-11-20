"""怪物装备物品 id 对账（T-0084）：只读静态扫描，0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MOBS = os.path.join(ROOT, "src/main/resources/redtale/defaults/mobs")
BASE_IDS = {"sword", "dagger", "axe", "hammer", "spear", "staff", "bow",
            "shield", "helmet", "chestplate", "leggings", "boots", "ring", "amulet"}
SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet"}
NS = "redtale_project:"


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


def read_mobs(dir_path: str) -> list[tuple[str, object | None]]:
    """扫 <dir_path> 下 *.json5（文件名升序），返回 (文件名, dict|None)；失败记 None。"""
    try:
        names = sorted(f for f in os.listdir(dir_path)
                       if f.endswith(".json5") and os.path.isfile(os.path.join(dir_path, f)))
    except OSError:
        return []
    out = []
    for name in names:
        data = None
        try:
            with open(os.path.join(dir_path, name), "r", encoding="utf-8", errors="replace") as f:
                parsed = _loads(f.read())
            if isinstance(parsed, dict):
                data = parsed
        except Exception:
            pass
        out.append((name, data))
    return out


def check_equipment(data: dict | None) -> list[str]:
    """只核 equipment 槽名与红tale 物品 id 是否命中 14 基底；文案无 [问题] 前缀。"""
    if data is None:
        return ["配置解析失败"]
    equipment = data.get("equipment", {})
    if equipment is None:
        return []
    if not isinstance(equipment, dict):
        return ["equipment 必须是对象"]
    issues = []
    for slot in sorted(equipment, key=str):
        value = equipment[slot]
        slot_name = str(slot)
        if slot_name not in SLOTS:
            issues.append(f"equipment.{slot_name} 未知槽名")
        if value is None or value == "":
            continue
        if not isinstance(value, str):
            issues.append(f"equipment.{slot_name} 物品 id 必须是字符串")
            continue
        if ":" not in value:
            issues.append(f"equipment.{slot_name} 物品 id 必须带命名空间")
            continue
        prefix, path = value.split(":", 1)
        if prefix == "redtale_project" and path not in BASE_IDS:
            issues.append(f"equipment.{slot_name} 物品 id 未命中 14 基底：{path}")
    return issues


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mobs", default=DEFAULT_MOBS)
    ap.add_argument("-v", action="store_true")
    opts = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    mobs = []
    problems = []
    if not os.path.isdir(opts.mobs):
        problems.append("mobs目录读取失败")
    else:
        mobs = read_mobs(opts.mobs)
        for name, data in mobs:
            for msg in check_equipment(data):
                problems.append(f"{name}: {msg}")
    if opts.v:
        refs = 0
        for _, data in mobs:
            if not isinstance(data, dict):
                continue
            equipment = data.get("equipment", {})
            if isinstance(equipment, dict):
                refs += sum(1 for v in equipment.values() if v is not None and v != "")
        print(f"已检查 {len(mobs)} 份怪物 · {refs} 处装备引用 · 14 个基底")
    for p in problems:
        print(f"[问题] {p}")
    print(f"共 1 份怪物装备 id 表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
