"""weapon_attributes 外部引用 ↔ BetterCombat 内置动作对账（T-0130，只读）。"""
import argparse
import glob
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_progress import clean_json5

DEFAULT_DIR = "src/main/resources/redtale/defaults"
ITEMS_SUB = "items"
OWN_NS = "redtale_project"
OWN_SUB = "weapon_attributes"
BC_NS = "bettercombat"
BC_PATH = "data/bettercombat/weapon_attributes/"
DEFAULT_JAR_DIR = "E:/server-test/mods"
JAR_GLOB = "*bettercombat*.jar"

STATS = {"files": 0, "refs": 0, "bc": 0, "own": 0, "other": 0, "actions": 0, "jar": ""}


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def refs_of(dir_path) -> list[tuple[str, str]]:
    """返回 [(id, raw str)]，按文件名升序；只收显式写了 weapon_attributes 的物品。"""
    out = []
    path = os.path.join(dir_path, ITEMS_SUB)
    if not os.path.isdir(path):
        return out
    for name in sorted(os.listdir(path)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(path, name)
        if not os.path.isfile(full):
            continue
        STATS["files"] += 1
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                data = json.loads(clean_json5(f.read()))
        except Exception:
            continue
        if not isinstance(data, dict) or "weapon_attributes" not in data:
            continue
        item_id = data.get("id")
        if not isinstance(item_id, str) or not item_id:
            item_id = name[:-6]
        raw = data["weapon_attributes"]
        out.append((item_id, raw if isinstance(raw, str) else str(raw)))
    return out


def bc_actions(jar_path) -> set[str]:
    """BetterCombat jar 里 data/bettercombat/weapon_attributes/*.json 的裸动作名。"""
    actions = set()
    with zipfile.ZipFile(jar_path) as z:
        for name in z.namelist():
            if not name.startswith(BC_PATH) or not name.endswith(".json"):
                continue
            tail = name[len(BC_PATH):]
            if not tail or "/" in tail:
                continue
            actions.add(tail[:-5])
    return actions


def _jar_of(jar_dir):
    hits = sorted(p for p in glob.glob(os.path.join(jar_dir, JAR_GLOB)) if os.path.isfile(p))
    return hits[0] if hits else ""


def check(dir_path, jar_dir) -> tuple[int, list[str]]:
    STATS.update({"files": 0, "refs": 0, "bc": 0, "own": 0, "other": 0, "actions": 0, "jar": ""})
    items_path = os.path.join(dir_path, ITEMS_SUB)
    if not os.path.isdir(items_path):
        return 1, ["物品目录读取失败：" + dir_path]
    refs = refs_of(dir_path)
    jar = _jar_of(jar_dir)
    if not jar:
        return 1, ["BetterCombat jar 未找到：" + jar_dir]
    try:
        actions = bc_actions(jar)
    except Exception:
        return 1, ["BetterCombat jar 未找到：" + jar_dir]
    STATS["jar"] = os.path.basename(jar)
    STATS["actions"] = len(actions)
    STATS["refs"] = len(refs)
    problems = []
    for item_id, raw in sorted(refs, key=lambda r: r[0]):
        if ":" not in raw:
            problems.append("%s: weapon_attributes 不是 namespace:path 形式（%s）" % (item_id, raw))
            continue
        ns, path = raw.split(":", 1)
        if not ns or not path:
            problems.append("%s: weapon_attributes 不是 namespace:path 形式（%s）" % (item_id, raw))
            continue
        if ns == BC_NS:
            STATS["bc"] += 1
            if path not in actions:
                problems.append("%s: bettercombat:%s 在 BetterCombat 内置动作里不存在" % (item_id, path))
        elif ns == OWN_NS:
            STATS["own"] += 1
            cfg = os.path.join(dir_path, OWN_SUB, path + ".json5")
            if not os.path.isfile(cfg):
                problems.append("%s: redtale_project:%s 缺配置 weapon_attributes/%s.json5" % (item_id, path, path))
        else:
            STATS["other"] += 1
    return (1 if problems else 0), problems


def main(argv) -> int:
    _utf8_stdout()
    ap = argparse.ArgumentParser(description="weapon_attributes 引用对账")
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("--jar-dir", default=DEFAULT_JAR_DIR)
    ap.add_argument("-v", action="store_true")
    ns = ap.parse_args(argv)
    code, problems = check(ns.dir, ns.jar_dir)
    for line in problems:
        print(line)
    if ns.v:
        print("已检查 %d 份物品 · %d 处引用（bettercombat %d / redtale_project %d / 外部 %d） · BetterCombat 内置动作 %d 个 · jar %s" % (
            STATS["files"], STATS["refs"], STATS["bc"], STATS["own"], STATS["other"], STATS["actions"], STATS["jar"]))
    print("共 %d 份物品内容 · %d 处 weapon_attributes 引用 · %d 条问题" % (STATS["files"], STATS["refs"], len(problems)))
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
