"""物品/词条/词条池/套装起飞前对账（T-0051）：只读，--dir 覆盖，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, sys
from check_progress import clean_json5, read_attrs, normalize

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASES = set("sword dagger axe hammer spear staff bow shield helmet chestplate leggings boots ring amulet".split())
RARITIES = {"common", "uncommon", "rare", "epic"}
KINDS = {"prefix", "suffix", "core"}
SUBS = (("items", "item"), ("affixes", "affix"), ("affix_pools", "pool"), ("sets", "set"))
ATTR_IDS = set()  # check_affix/check_set 签名无 ids 参数，归一表由 main 注入

def path_of(token: str) -> str:
    if not isinstance(token, str): return ""
    return token.split(":", 1)[1] if ":" in token else token

def ids_of(root: str, sub: str) -> set[str]:
    out = set(); folder = os.path.join(root, sub)
    if not os.path.isdir(folder): return out
    for name in sorted(os.listdir(folder)):
        full = os.path.join(folder, name)
        if not name.endswith(".json5") or not os.path.isfile(full): continue
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                obj = json.loads(clean_json5(f.read()))
        except Exception: continue
        if isinstance(obj, dict) and isinstance(obj.get("id"), str):
            out.add(path_of(obj["id"]))
    return out

def collect(root: str, sub: str) -> list[tuple[str, dict | None, str | None]]:
    out = []; folder = os.path.join(root, sub)
    if not os.path.isdir(folder): return out
    for name in sorted(os.listdir(folder)):
        full = os.path.join(folder, name)
        if not name.endswith(".json5") or not os.path.isfile(full): continue
        rel = sub + "/" + name
        with open(full, "r", encoding="utf-8", errors="replace") as f: raw = f.read()
        try: data = json.loads(clean_json5(raw))
        except Exception as ex:
            out.append((rel, None, "JSON5 解析失败：" + str(ex))); continue
        if not isinstance(data, dict):
            out.append((rel, None, "JSON5 解析失败：顶层不是对象")); continue
        out.append((rel, data, None))
    return out

def _int(v): return isinstance(v, int) and not isinstance(v, bool)
def _num(v): return isinstance(v, (int, float)) and not isinstance(v, bool)
def _in(v, s): return isinstance(v, str) and v in s

def _stats(stats, probs):
    if not isinstance(stats, dict): return
    for key in stats:
        if normalize(str(key), ATTR_IDS) is None:
            probs.append("属性 " + str(key) + " 不在 80 条表内")

def check_item(rel: str, data: dict, sets: set, skills: set) -> list[str]:
    p = []
    if not isinstance(data.get("id"), str): p.append("id 缺失/非字符串")
    if not _in(data.get("base"), BASES): p.append("base 非法：" + str(data.get("base")))
    if "rarity" in data and not _in(data["rarity"], RARITIES):
        p.append("rarity 非法：" + str(data["rarity"]))
    _stats(data.get("stats"), p)
    gem = data.get("gem")
    if isinstance(gem, dict):
        _stats(gem.get("stats"), p)
        if "tier" in gem and (not _int(gem["tier"]) or gem["tier"] < 1):
            p.append("gem.tier 必须是 >=1 的整数")
    if "set_id" in data and path_of(data["set_id"]) not in sets:
        p.append("set_id 未命中：" + str(data["set_id"]))
    ab = data.get("abilities", [])
    if isinstance(ab, list):
        for a in ab:
            if path_of(a) not in skills: p.append("abilities 未命中：" + str(a))
    sock = data.get("sockets")
    if isinstance(sock, dict):
        c = sock.get("count")
        if not _int(c) or not 0 <= c <= 6: p.append("sockets.count 必须是 [0,6] 的整数")
        t = sock.get("types")
        if isinstance(t, list):
            for x in t:
                if not isinstance(x, str): p.append("sockets.types 含非字符串")
    wa = data.get("weapon_attributes")
    if wa is not None and (not isinstance(wa, str) or ":" not in wa):
        p.append("weapon_attributes 必须含 ':'")
    if "rev" in data and (not _int(data["rev"]) or data["rev"] < 1):
        p.append("rev 必须是 >=1 的整数")
    return p

def check_affix(rel: str, data: dict) -> list[str]:
    p = []
    if not isinstance(data.get("id"), str): p.append("id 缺失/非字符串")
    if not _in(data.get("kind"), KINDS): p.append("kind 非法：" + str(data.get("kind")))
    if "weight" in data and (not _num(data["weight"]) or data["weight"] <= 0):
        p.append("weight 必须是 >0 的数")
    il = data.get("item_level")
    if isinstance(il, dict):
        lo, hi = il.get("min"), il.get("max")
        if not _num(lo) or not _num(hi) or lo > hi: p.append("item_level min/max 非法")
    _stats(data.get("stats"), p)
    return p

def check_pool(rel: str, data: dict, bases: set, affix_ids: set) -> list[str]:
    p = []
    if not isinstance(data.get("id"), str): p.append("id 缺失/非字符串")
    applies = data.get("applies_to", [])
    if isinstance(applies, list):
        for t in applies:
            if not isinstance(t, str) or ":" not in t or t.split(":", 1)[0] != "redtale_project" or path_of(t) not in bases:
                p.append("applies_to 非法：" + str(t))
    sl = data.get("slots")
    if isinstance(sl, dict):
        for k, v in sl.items():
            if k not in KINDS or not _int(v) or v < 0: p.append("slots 非法：" + str(k))
    for kind in KINDS:
        seg = data.get(kind)
        if not isinstance(seg, dict): continue
        for k, v in seg.items():
            if path_of(k) not in affix_ids: p.append(kind + " 未命中词条：" + str(k))
            if not _num(v) or v <= 0: p.append(kind + " 权重非法：" + str(k))
    return p

def check_set(rel: str, data: dict, item_ids: set) -> list[str]:
    p = []
    if not isinstance(data.get("id"), str): p.append("id 缺失/非字符串")
    if "name" in data and not isinstance(data["name"], str): p.append("name 必须是字符串")
    pieces = data.get("pieces", [])
    if not isinstance(pieces, list): pieces = []
    for x in pieces:
        if path_of(x) not in item_ids: p.append("pieces 未命中：" + str(x))
    bs = data.get("bonuses", [])
    if isinstance(bs, list):
        for b in bs:
            if not isinstance(b, dict): p.append("bonuses 项不是对象"); continue
            c = b.get("count")
            if not _int(c) or not 1 <= c <= len(pieces): p.append("bonuses.count 非法：" + str(c))
            _stats(b.get("stats"), p)
    return p

def main() -> int:
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    global ATTR_IDS
    parser = argparse.ArgumentParser(description="物品/词条/词条池/套装配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args(); os.chdir(ROOT)
    root = os.path.abspath(args.dir)
    ids, attr_path = read_attrs()
    if ids is None:
        print("[问题] 读不到 AttributeSpecs.java（路径 " + attr_path + "）")
        print("共 0 个文件 · 1 条问题"); return 1
    ATTR_IDS = ids
    set_ids, skill_ids = ids_of(root, "sets"), ids_of(root, "skills")
    affix_ids, item_ids = ids_of(root, "affixes"), ids_of(root, "items")
    files, problems, seen = [], [], {}
    for sub, kind in SUBS:
        if not os.path.isdir(os.path.join(root, sub)):
            print("提示 没有 " + sub + "/ 目录，跳过"); continue
        here = collect(root, sub); files.extend(here)
        for rel, data, err in here:
            if data is None:
                problems.append((rel, err)); continue
            if kind == "item": probs = check_item(rel, data, set_ids, skill_ids)
            elif kind == "affix": probs = check_affix(rel, data)
            elif kind == "pool": probs = check_pool(rel, data, BASES, affix_ids)
            else: probs = check_set(rel, data, item_ids)
            problems.extend((rel, m) for m in probs)
            ident = data.get("id")
            if isinstance(ident, str):
                key = (sub, path_of(ident))
                if key in seen: problems.append((rel, "id " + ident + " 与 " + seen[key] + " 重复"))
                else: seen[key] = rel
    for rel, msg in problems: print("[问题] " + rel + " · " + msg)
    print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main())
