"""套装 pieces 成员 ↔ 物品 set_id 归属一致性对账（T-0132）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = "src/main/resources/redtale/defaults"
ITEMS_SUB = "items"
SETS_SUB = "sets"

def path_of(token):
    if not isinstance(token, str): return ""
    return token.split(":", 1)[1] if ":" in token else token

def _read(folder):
    """返回 (对象列表, 目录读失败路径或 None)；坏文件静默跳过。"""
    if not os.path.isdir(folder): return [], folder
    out = []
    for name in sorted(os.listdir(folder)):
        full = os.path.join(folder, name)
        if not name.endswith(".json5") or not os.path.isfile(full): continue
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                obj = json.loads(clean_json5(f.read()))
        except Exception:
            continue
        if isinstance(obj, dict): out.append(obj)
    return out, None

def _pairs(sets):
    out = []
    for obj in sets:
        sid = obj.get("id")
        pieces = obj.get("pieces")
        if not isinstance(sid, str) or not isinstance(pieces, list): continue
        for p in pieces:
            if isinstance(p, str): out.append((sid, p))
    out.sort()
    return out

def set_pieces(dir_path) -> list[tuple[str, str]]:
    sets, _ = _read(os.path.join(dir_path, SETS_SUB))
    return _pairs(sets)

def _scan(dir_path):
    probs = []
    sets, sets_err = _read(os.path.join(dir_path, SETS_SUB))
    if sets_err: probs.append("套装目录读取失败：" + sets_err)
    items, items_err = _read(os.path.join(dir_path, ITEMS_SUB))
    if items_err: probs.append("物品目录读取失败：" + items_err)
    owners = {}
    for obj in items:
        ident = path_of(obj.get("id"))
        if ident and ident not in owners: owners[ident] = obj
    known = set(path_of(obj.get("id")) for obj in sets if isinstance(obj.get("id"), str))
    pairs = _pairs(sets)
    skipped = 0
    for sid, piece in pairs:
        item = owners.get(path_of(piece))
        if item is None:
            skipped += 1
            continue
        raw = item.get("set_id")
        if not isinstance(raw, str) or not raw.strip():
            probs.append(sid + ": " + piece + " 没有 set_id")
        elif path_of(raw) != path_of(sid) and path_of(raw) in known:
            probs.append(sid + ": " + piece + " 的 set_id 是 " + raw + "（不是 " + sid + "）")
    return probs, (len(sets), len(pairs), len(items), skipped)

def check(dir_path) -> tuple[int, list[str]]:
    probs, _ = _scan(dir_path)
    return (1 if probs else 0), probs

def main(argv) -> int:
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    parser = argparse.ArgumentParser(description="套装 pieces ↔ 物品 set_id 归属对账")
    parser.add_argument("--dir", default=DEFAULT_DIR)
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args(argv)
    os.chdir(ROOT)
    probs, (sc, pc, ic, skipped) = _scan(os.path.abspath(args.dir))
    for line in probs: print(line)
    if args.v:
        print("已检查 %d 份套装 · %d 处 pieces 引用 · %d 份物品 · 跳过 %d 处（物品不存在）" % (sc, pc, ic, skipped))
    print("共 %d 份套装定义 · %d 处 pieces 引用 · %d 条问题" % (sc, pc, len(probs)))
    return 1 if probs else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
