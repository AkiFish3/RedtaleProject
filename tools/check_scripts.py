"""脚本配置起飞前对账（T-0054）：只读，--dir 指向配置根，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, re, sys
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRIGGERS = {"manual", "player_join"}
EFFECTS = {
    "buff", "console_log", "damage", "damage_aoe", "dash", "debuff", "grant_exp",
    "heal", "knockback", "message", "projectile", "pull", "stun",
}
CONDITIONS = {"always", "health_below", "chance"}
SINGLE_KEYS = {"id", "trigger", "cooldown_ticks", "conditions", "effects"}


def loads(text):
    """单引号字面量转双引号后交 clean_json5（与运行期共用同一份清洗口径）。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i); j = n if j < 0 else j
            out.append(text[i:j]); i = j; continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n); out.append(text[i:j]); i = j; continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1] if text[j + 1] == "'" else text[j:j + 2]); j += 2; continue
                buf.append('\\"' if text[j] == '"' else text[j]); j += 1
            out.append('"' + "".join(buf) + '"'); i = min(j + 1, n); continue
        out.append(c); i += 1
    return json.loads(clean_json5("".join(out)))


def planned():
    """读 docs/SKILLS.md §4 表格第 2 列反引号 id（规划表）；读不到返回空集。"""
    ids, inside = set(), False
    try:
        with open(os.path.join(ROOT, "docs", "SKILLS.md"), "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                if line.startswith("## 4."):
                    inside = True; continue
                if inside and line.startswith("## "):
                    break
                if inside:
                    parts = line.split("|")
                    if len(parts) > 2:
                        m = re.search(r"`([^`]+)`", parts[2])
                        if m: ids.add(m.group(1))
    except Exception:
        return set()
    return ids


def collect(root):
    """扫 scripts/*.json5 文件名升序；返回 [(rel, data|None, err)]。"""
    out, folder = [], os.path.join(root, "scripts")
    for name in sorted(os.listdir(folder)):
        path = os.path.join(folder, name)
        if not name.endswith(".json5") or not os.path.isfile(path):
            continue
        rel = "scripts/" + name
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = loads(raw)
        except Exception as ex:
            out.append((rel, None, "JSON5 解析失败：" + str(ex))); continue
        if not isinstance(data, dict):
            out.append((rel, None, "顶层不是对象")); continue
        out.append((rel, data, None))
    return out


def check_script(s, plan):
    """返回一条脚本对象的 (问题列表, 提示列表)，均不含 rel 与前缀。"""
    probs, notes = [], []
    for k in s:
        if k not in SINGLE_KEYS:
            notes.append("未知键 %s（运行期忽略）" % k)
    ident = s.get("id")
    tag = ident.strip() if isinstance(ident, str) else str(ident)
    if not isinstance(ident, str) or not ident.strip():
        probs.append("id 缺失或不是字符串")
    trig = s.get("trigger")
    if not isinstance(trig, str) or not trig.strip():
        probs.append("trigger 缺失或不是字符串")
    elif trig not in TRIGGERS:
        probs.append("未知 trigger '%s'（现有 manual,player_join）" % trig)

    if "effects" not in s:
        probs.append("脚本 %s 没有可执行的 effect" % tag)
    elif not isinstance(s["effects"], list):
        probs.append("effects 必须是数组")
    elif not s["effects"]:
        probs.append("脚本 %s 没有可执行的 effect" % tag)
    else:
        for i, e in enumerate(s["effects"]):
            if not isinstance(e, dict):
                probs.append("effects[%d] 不是对象" % i); continue
            typ = e.get("type")
            if not isinstance(typ, str):
                probs.append("effects[%d] 缺少 type" % i)
            elif typ not in EFFECTS:
                suffix = "（M5 未实现）" if typ in plan else ""
                probs.append("effects[%d].type 未注册%s：%s" % (i, suffix, typ))
            if "args" in e and not isinstance(e["args"], dict):
                probs.append("effects[%d].args 不是对象" % i)

    if "conditions" in s:
        cs = s["conditions"]
        if not isinstance(cs, list):
            probs.append("conditions 必须是数组")
        else:
            for i, c in enumerate(cs):
                if not isinstance(c, dict):
                    probs.append("conditions[%d] 不是对象" % i); continue
                typ = c.get("type")
                if typ not in CONDITIONS:
                    probs.append("conditions[%d].type 非法：%s" % (i, typ))
                if "args" in c and not isinstance(c["args"], dict):
                    probs.append("conditions[%d].args 不是对象" % i)

    if "cooldown_ticks" in s:
        v = s["cooldown_ticks"]
        if isinstance(v, bool) or not isinstance(v, int) or not 0 <= v <= 1000000000:
            probs.append("cooldown_ticks 必须是 0..1000000000 的整数")
    return probs, notes


def main():
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    parser = argparse.ArgumentParser(description="脚本配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args()
    os.chdir(ROOT)

    root = os.path.abspath(args.dir)
    if not os.path.isdir(os.path.join(root, "scripts")):
        print("[问题] 没有 scripts/ 目录（" + args.dir + "）")
        print("共 0 个文件 · 1 条问题")
        return 1

    plan, files = planned(), collect(root)
    problems, notes, seen = [], [], {}
    for rel, data, err in files:
        if data is None:
            problems.append((rel, err)); continue
        if "scripts" in data:
            val = data["scripts"]
            if not isinstance(val, list):
                problems.append((rel, "scripts 必须是数组")); scripts = []
            else:
                scripts = val
            for k in data:
                if k != "scripts":
                    notes.append((rel, "未知键 %s（运行期忽略）" % k))
        elif "id" in data or "trigger" in data:
            scripts = [data]
        else:
            problems.append((rel, "既没有 scripts 数组，也不像单条脚本（缺 id / trigger）"))
            continue
        for i, sc in enumerate(scripts):
            if not isinstance(sc, dict):
                problems.append((rel, "scripts[%d] 不是对象" % i)); continue
            ps, ns = check_script(sc, plan)
            problems.extend((rel, p) for p in ps)
            notes.extend((rel, n) for n in ns)
            ident = sc.get("id")
            if isinstance(ident, str) and ident.strip():
                key = ident.strip()
                if key in seen:
                    problems.append((rel, "id " + key + " 与 " + seen[key] + " 重复"))
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
