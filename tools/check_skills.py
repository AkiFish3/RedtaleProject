"""技能配置起飞前对账（T-0053）：只读，--dir 指向配置根，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, re, sys
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONDITIONS = {"always", "health_below", "chance"}
DEBUG_PRIMITIVES = {"message", "console_log"}
TOP_KEYS = {"id", "name", "icon", "cooldown_ticks", "mana_cost", "conditions", "effects"}


def path_of(token):
    """有冒号取冒号后；无冒号原样。"""
    return token.split(":", 1)[1] if ":" in token else token


def primitives():
    """读 docs/SKILLS.md §4 表格第 2 列反引号 id。"""
    ids = set()
    inside = False
    try:
        with open(os.path.join(ROOT, "docs", "SKILLS.md"), "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                if line.startswith("## 4."):
                    inside = True
                    continue
                if inside and line.startswith("## "):
                    break
                if inside:
                    parts = line.split("|")
                    if len(parts) > 2:
                        m = re.search(r"`([^`]+)`", parts[2])
                        if m:
                            ids.add(m.group(1))
    except Exception:
        return set()
    return ids


def collect(root):
    """扫 skills/*.json5，文件名升序。返回 [(rel, data|None, err)]。"""
    out = []
    folder = os.path.join(root, "skills")
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5") or not os.path.isfile(os.path.join(folder, name)):
            continue
        rel = "skills/" + name
        with open(os.path.join(folder, name), "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = json.loads(clean_json5(raw))
        except Exception as ex:
            out.append((rel, None, "JSON5 解析失败：" + str(ex)))
            continue
        if not isinstance(data, dict):
            out.append((rel, None, "顶层不是对象"))
            continue
        out.append((rel, data, None))
    return out


def check_skill(rel, data, prim):
    """返回本文件 (问题列表, 提示列表)，均不含 rel 与前缀。"""
    probs, notes = [], []
    ident = data.get("id")
    if not isinstance(ident, str) or not ident.strip():
        probs.append("id 缺失或不是字符串")

    if "effects" not in data or not isinstance(data["effects"], list):
        probs.append("effects 缺失或不是数组")
    elif not data["effects"]:
        notes.append("effects 为空")
    else:
        for i, eff in enumerate(data["effects"]):
            if not isinstance(eff, dict):
                probs.append("effects[%d] 不是对象" % i)
                continue
            typ = eff.get("type")
            if typ not in prim:
                probs.append("effects[%d].type 非法：%s" % (i, typ))
            if "args" in eff and not isinstance(eff["args"], dict):
                probs.append("effects[%d].args 不是对象" % i)

    if "conditions" in data:
        conds = data["conditions"]
        if not isinstance(conds, list):
            probs.append("conditions 不是数组")
        else:
            for i, c in enumerate(conds):
                typ = c.get("type") if isinstance(c, dict) else None
                if typ not in CONDITIONS:
                    probs.append("conditions[%d].type 非法：%s" % (i, typ))

    if "cooldown_ticks" in data:
        v = data["cooldown_ticks"]
        if isinstance(v, bool) or not isinstance(v, int) or not 0 <= v <= 1000000000:
            probs.append("cooldown_ticks 必须是 0..1000000000 的整数")

    if "mana_cost" in data:
        v = data["mana_cost"]
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not 0 <= v <= 1e9:
            probs.append("mana_cost 必须是 0..1e9 的数字")

    if "icon" in data:
        v = data["icon"]
        if not isinstance(v, str) or any(ch.isspace() or ch.isupper() for ch in v):
            probs.append("icon 不是合法 id：" + str(v))

    for k in data:
        if k not in TOP_KEYS:
            notes.append("未知键 %s（运行期忽略）" % k)
    return probs, notes


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    parser = argparse.ArgumentParser(description="技能配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args()
    os.chdir(ROOT)

    found = primitives()
    if not found:
        print("[问题] 读不到 docs/SKILLS.md §4 原语表")
        print("共 0 个文件 · 1 条问题")
        return 1
    prim = found | DEBUG_PRIMITIVES

    root = os.path.abspath(args.dir)
    if not os.path.isdir(os.path.join(root, "skills")):
        print("[问题] 没有 skills/ 目录（" + args.dir + "）")
        print("共 0 个文件 · 1 条问题")
        return 1

    files = collect(root)
    problems, notes, seen = [], [], {}
    for rel, data, err in files:
        if data is None:
            problems.append((rel, err))
            continue
        ps, ns = check_skill(rel, data, prim)
        problems.extend((rel, p) for p in ps)
        notes.extend((rel, n) for n in ns)
        ident = data.get("id")
        if isinstance(ident, str) and ident.strip():
            key = path_of(ident)
            if key in seen:
                problems.append((rel, "id " + ident + " 与 " + seen[key] + " 重复"))
            else:
                seen[key] = rel

    for rel, n in notes:
        print("提示 " + rel + " · " + n)
    for rel, p in problems:
        print("[问题] " + rel + " · " + p)
    print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
