"""技能树配置起飞前对账（T-0049）：只读，--dir 可指向配置根，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, sys
from check_progress import clean_json5, read_attrs, normalize

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TYPES = {"attribute", "unlock_skill", "passive"}
OPS = {"add", "add_value", "add_multiplied_base", "add_multiplied_total"}


def path_of(token: str) -> str:
    """取第一个 ':' 之后；无冒号原样；None → ''。"""
    if token is None:
        return ""
    return token.split(":", 1)[1] if ":" in token else token


def collect(root: str) -> list[tuple[str, dict | None, str | None]]:
    """扫 skill_trees/*.json5，文件名升序。返回 [(rel, data|None, err)]。"""
    out = []
    folder = os.path.join(root, "skill_trees")
    if not os.path.isdir(folder):
        return out
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        rel = "skill_trees/" + name
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


def check_tree(rel: str, data: dict, ids: set, job_ids: set, skill_ids: set) -> list[str]:
    """返回本树的问题一句话列表（不含 rel、不含 [问题] 前缀）。"""
    probs = []
    ident = data.get("id")
    stem = os.path.basename(rel)[: -len(".json5")]
    if not isinstance(ident, str) or not ident.strip():
        probs.append("id 缺失/空/不是字符串")
    else:
        want = path_of(ident) if ":" in ident else ident
        if want != stem:
            probs.append("id " + ident + " 与文件名 " + stem + " 不一致")

    nodes = data.get("nodes")
    if not isinstance(nodes, dict) or not nodes:
        probs.append("nodes 缺失/不是对象/为空")
        return probs
    keys = set(nodes.keys())

    for name, node in nodes.items():
        tag = "节点 " + str(name) + " "
        if not isinstance(node, dict):
            probs.append(tag + "不是对象")
            continue
        reqs = node.get("requires", [])
        if not isinstance(reqs, list):
            probs.append(tag + "requires 不是数组")
        else:
            for req in reqs:
                if not isinstance(req, str) or req == name or req not in keys:
                    probs.append(tag + "requires 指向不存在/自身的节点 " + str(req))
        cost = node.get("cost")
        if isinstance(cost, bool) or not isinstance(cost, int) or cost < 0:
            probs.append(tag + "cost 必须是 >=0 的整数")

        effects = node.get("effects", [])
        if not isinstance(effects, list):
            probs.append(tag + "effects 不是数组")
            continue
        for eff in effects:
            if not isinstance(eff, dict):
                probs.append(tag + "effects 条目不是对象")
                continue
            typ = eff.get("type")
            if typ not in TYPES:
                probs.append(tag + "effects type 非法：" + str(typ))
                continue
            if typ == "attribute":
                raw = eff.get("attribute")
                if not isinstance(raw, str) or normalize(raw, ids) is None:
                    probs.append(tag + "属性 " + str(raw) + " 不在 80 条表内")
                if "operation" in eff and eff["operation"] not in OPS:
                    probs.append(tag + "operation 非法：" + str(eff["operation"]))
            elif typ == "unlock_skill":
                if skill_ids is not None:
                    sk = path_of(eff.get("skill"))
                    if sk not in skill_ids:
                        probs.append(tag + "skill 未命中：" + str(eff.get("skill")))
            else:
                pid = eff.get("id")
                if not isinstance(pid, str) or not pid.strip():
                    probs.append(tag + "passive id 缺失/空")

    if job_ids is not None:
        classes = data.get("classes", [])
        if not isinstance(classes, list):
            probs.append("classes 不是数组")
        else:
            for cls in classes:
                if path_of(cls) not in job_ids:
                    probs.append("classes 未命中职业：" + str(cls))

    total = sum(n.get("cost", 0) for n in nodes.values()
                if isinstance(n, dict) and isinstance(n.get("cost"), int)
                and not isinstance(n.get("cost"), bool))
    if "max_points" in data:
        mp = data["max_points"]
        if isinstance(mp, bool) or not isinstance(mp, int) or mp < 0:
            probs.append("max_points 必须是 >=0 的整数")
        elif mp < total:
            probs.append("max_points " + str(mp) + " 小于 Σcost " + str(total))
    return probs


def _id_set(root, folder):
    """读 jobs/ 或 skills/ 的 id（path_of 归一）；目录缺失返回 None 并打提示。"""
    d = os.path.join(root, folder)
    found = set()
    if not os.path.isdir(d):
        print("提示 没有 " + folder + "/ 目录，跳过对应交叉校验")
        return None
    for name in sorted(os.listdir(d)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(d, name)
        if not os.path.isfile(full):
            continue
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            try:
                obj = json.loads(clean_json5(f.read()))
            except Exception:
                continue
        if isinstance(obj, dict) and isinstance(obj.get("id"), str):
            found.add(path_of(obj["id"]))
    return found


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    parser = argparse.ArgumentParser(description="技能树配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args()
    os.chdir(ROOT)

    root = os.path.abspath(args.dir)
    ids, attr_path = read_attrs()
    if ids is None:
        print("[问题] 读不到 AttributeSpecs.java（路径 " + attr_path + "）")
        print("共 0 个文件 · 1 条问题")
        return 1

    job_ids = _id_set(root, "jobs")
    skill_ids = _id_set(root, "skills")

    files = collect(root)
    problems = []
    seen = {}
    for rel, data, err in files:
        if data is None:
            problems.append((rel, "JSON5 解析失败：" + err))
            continue
        for msg in check_tree(rel, data, ids, job_ids, skill_ids):
            problems.append((rel, msg))
        ident = data.get("id")
        if isinstance(ident, str) and ident.strip():
            key = path_of(ident) if ":" in ident else ident
            if key in seen:
                problems.append((rel, "id " + ident + " 与 " + seen[key] + " 重复"))
            else:
                seen[key] = rel
        if "max_points" not in data and isinstance(data.get("nodes"), dict):
            t = sum(n.get("cost", 0) for n in data["nodes"].values()
                    if isinstance(n, dict) and isinstance(n.get("cost"), int)
                    and not isinstance(n.get("cost"), bool))
            print("提示 " + rel + " · max_points 缺失，取 cost 合计 " + str(t))

    for rel, msg in problems:
        print("[问题] " + rel + " · " + msg)
    print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
