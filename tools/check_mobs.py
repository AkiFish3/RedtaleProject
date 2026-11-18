"""怪物/掉落/刷怪规则起飞前对账（T-0050）：只读，--dir 覆盖，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, sys
from check_progress import clean_json5, read_attrs, normalize

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASES = {"melee", "ranged", "caster", "summoner", "charger", "flyer", "elite", "ambient"}
SUBS = (("mobs", "mob"), ("drops", "drop"), ("spawn_rules", "rule"))

def path_of(token: str) -> str:
    """取第一个 ':' 之后；无冒号原样；None → ''。"""
    if not isinstance(token, str):
        return ""
    return token.split(":", 1)[1] if ":" in token else token

def ids_of(root: str, sub: str) -> set[str]:
    """读 <root>/<sub>/*.json5 的 id，按 path_of 归一。"""
    out = set()
    folder = os.path.join(root, sub)
    if not os.path.isdir(folder):
        return out
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                obj = json.loads(clean_json5(f.read()))
        except Exception:
            continue
        if isinstance(obj, dict) and isinstance(obj.get("id"), str):
            out.add(path_of(obj["id"]))
    return out

def collect(root: str, sub: str) -> list[tuple[str, dict | None, str | None]]:
    """扫一类配置，文件名升序；返回 [(rel, data|None, err)]。"""
    out = []
    folder = os.path.join(root, sub)
    if not os.path.isdir(folder):
        return out
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        rel = sub + "/" + name
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = json.loads(clean_json5(raw))
        except Exception as ex:
            out.append((rel, None, str(ex)))
            continue
        if not isinstance(data, dict):
            out.append((rel, None, "顶层不是对象"))
            continue
        out.append((rel, data, None))
    return out

def _num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)

def check_mob(rel: str, data: dict, ids: set, skill_ids: set, drop_ids: set) -> list[str]:
    probs = []
    if not isinstance(data.get("id"), str):
        probs.append("id 缺失/非字符串")
    if data.get("base") not in BASES:
        probs.append("base 非法：" + str(data.get("base")))
    if "scale" in data and (not _num(data["scale"]) or not 0 < data["scale"] <= 4):
        probs.append("scale 必须是 0<scale<=4 的数")
    attrs = data.get("attributes", {})
    if not isinstance(attrs, dict):
        probs.append("attributes 不是对象")
    else:
        for key in attrs:
            if normalize(str(key), ids) is None:
                probs.append("属性 " + str(key) + " 不在 80 条表内")
    equip = data.get("equipment", {})
    if not isinstance(equip, dict):
        probs.append("equipment 不是对象")
    else:
        for slot, item in equip.items():
            if item not in (None, "") and (not isinstance(item, str) or ":" not in item):
                probs.append("装备 " + str(slot) + " 不是带命名空间的 id：" + str(item))
    skills = data.get("skills", [])
    if not isinstance(skills, list):
        probs.append("skills 不是数组")
    else:
        for ref in skills:
            sk = ref.get("skill") if isinstance(ref, dict) else None
            if path_of(sk) not in skill_ids:
                probs.append("skill 未命中：" + str(sk))
    ref = data.get("drops")
    if ref and path_of(ref) not in drop_ids:
        probs.append("drops 未命中：" + str(ref))
    return probs

def check_drop(rel: str, data: dict, content_ids: set) -> list[str]:
    probs = []
    if not isinstance(data.get("id"), str):
        probs.append("id 缺失/非字符串")
    if "xp" in data and (not _num(data["xp"]) or data["xp"] < 0):
        probs.append("xp 必须是 >=0 的数")
    rows = data.get("drops", [])
    if not isinstance(rows, list):
        probs.append("drops 不是数组")
        return probs
    for row in rows:
        if not isinstance(row, dict):
            probs.append("drops 条目不是对象")
            continue
        item = row.get("item")
        if (not isinstance(item, str) or not item.strip()
                or not (path_of(item) in content_ids
                        or (":" in item and not item.startswith("redtale_project:")))):
            probs.append("item 未命中内容 id：" + str(item))
        lo, hi = row.get("min"), row.get("max")
        if not _num(lo) or not _num(hi) or lo > hi:
            probs.append("min/max 非法或 min>max")
        ch = row.get("chance")
        if not _num(ch) or not 0 <= ch <= 1:
            probs.append("chance 必须是 [0,1] 的数")
        rolls = row.get("rolls")
        if isinstance(rolls, bool) or not isinstance(rolls, int) or rolls < 1:
            probs.append("rolls 必须是 >=1 的整数")
    return probs

def check_rule(rel: str, data: dict, mob_ids: set) -> list[str]:
    probs = []
    if not isinstance(data.get("id"), str):
        probs.append("id 缺失/非字符串")
    if data.get("action") not in ("replace", "add"):
        probs.append("action 非法：" + str(data.get("action")))
    mob = data.get("mob")
    if path_of(mob) not in mob_ids:
        probs.append("mob 未命中：" + str(mob))
    ch = data.get("chance")
    if not _num(ch) or not 0 <= ch <= 1:
        probs.append("chance 必须是 [0,1] 的数")
    cap = data.get("per_chunk_cap")
    if not _num(cap) or cap < 0:
        probs.append("per_chunk_cap 必须是 >=0 的数")
    prio = data.get("priority")
    if isinstance(prio, bool) or not isinstance(prio, int):
        probs.append("priority 必须是整数")
    return probs

def main() -> int:
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    parser = argparse.ArgumentParser(description="怪物/掉落/刷怪规则配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args()
    os.chdir(ROOT)
    root = os.path.abspath(args.dir)
    ids, attr_path = read_attrs()
    if ids is None:
        print("[问题] 读不到 AttributeSpecs.java（路径 " + attr_path + "）")
        print("共 0 个文件 · 1 条问题")
        return 1

    skill_ids, drop_ids, content_ids, mob_ids = (
        ids_of(root, s) for s in ("skills", "drops", "items", "mobs"))
    files, problems, seen = [], [], {}
    for sub, kind in SUBS:
        if not os.path.isdir(os.path.join(root, sub)):
            print("提示 没有 " + sub + "/ 目录，跳过")
            continue
        here = collect(root, sub)
        files.extend(here)
        for rel, data, err in here:
            if data is None:
                problems.append((rel, "JSON5 解析失败：" + err))
                continue
            if kind == "mob":
                probs = check_mob(rel, data, ids, skill_ids, drop_ids)
            elif kind == "drop":
                probs = check_drop(rel, data, content_ids)
            else:
                probs = check_rule(rel, data, mob_ids)
            problems.extend((rel, msg) for msg in probs)
            ident = data.get("id")
            if isinstance(ident, str):
                key = (sub, path_of(ident))
                if key in seen:
                    problems.append((rel, "id " + ident + " 与 " + seen[key] + " 重复"))
                else:
                    seen[key] = rel
    for rel, msg in problems:
        print("[问题] " + rel + " · " + msg)
    print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
