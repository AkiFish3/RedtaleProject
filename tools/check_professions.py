"""专业配置起飞前对账（T-0062）：只读，--dir 指向 professions 目录，退出码 0=干净 1=有问题。"""
import argparse, json, math, os, sys
from check_progress import clean_json5, read_attrs

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOP_KEYS = {"id", "name", "max_level", "curve", "attributes", "grants"}
SOURCES = {"mine", "craft", "smelt"}
CURVE_MODES = {"geometric", "polynomial"}
NS = "redtale_project:"


def attr_ok(key, ids):
    """归一三形态：原样 / 去 redtale_project: / minecraft:→generic. 补位。"""
    if not isinstance(key, str):
        return False
    t = key.strip()
    if t in ids:
        return True
    if t.startswith(NS) and t[len(NS):] in ids:
        return True
    if t.startswith("minecraft:") and ("minecraft:generic." + t[len("minecraft:"):]) in ids:
        return True
    return False


def finite_number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


def grant_key_ok(k):
    if k == "default":
        return True
    if not isinstance(k, str):
        return False
    body = k[1:] if k.startswith("#") else k
    return bool(body) and ":" in body


def check(data, ids, attr_ids_ok):
    """返回本文件的问题消息列表（不含文件名与前缀）。"""
    p = []
    ident = data.get("id")
    if not isinstance(ident, str) or not ident.strip() or ":" not in ident:
        p.append("id 必须是非空且带命名空间的字符串")

    if "name" in data:
        v = data["name"]
        if not isinstance(v, str) or not v.strip():
            p.append("name 必须是非空字符串")

    if "max_level" in data:
        v = data["max_level"]
        if isinstance(v, bool) or not isinstance(v, int) or not 2 <= v <= 1000:
            p.append("max_level 必须是 2..1000 的整数：" + str(v))

    if "curve" in data:
        c = data["curve"]
        if not isinstance(c, dict):
            p.append("curve 必须是对象")
        else:
            mode = c.get("mode", "polynomial")
            if "mode" in c and mode not in CURVE_MODES:
                p.append("curve.mode 只认 geometric / polynomial：" + str(mode))
            if mode == "geometric":
                if "growth" in c:
                    g = c["growth"]
                    if isinstance(g, bool) or not isinstance(g, (int, float)) or g <= 1:
                        p.append("curve.growth 必须大于 1：" + str(g))
            elif "exponent" in c:
                e = c["exponent"]
                if isinstance(e, bool) or not isinstance(e, (int, float)) or e <= 0:
                    p.append("curve.exponent 必须是正数：" + str(e))

    if "attributes" in data:
        a = data["attributes"]
        if not isinstance(a, dict):
            p.append("attributes 必须是对象")
        else:
            for k, v in a.items():
                if attr_ids_ok and not attr_ok(k, ids):
                    p.append("属性 " + str(k) + " 不在 80 条属性表里")
                if not finite_number(v):
                    p.append("属性 " + str(k) + " 的值必须是有限数字：" + str(v))

    if "grants" in data:
        g = data["grants"]
        if not isinstance(g, dict):
            p.append("grants 必须是对象")
        else:
            for src, entries in g.items():
                if src not in SOURCES:
                    p.append("grants 的源 " + str(src) + " 未知（只认 mine/craft/smelt）")
                if not isinstance(entries, dict):
                    continue
                for k, v in entries.items():
                    if not grant_key_ok(k):
                        p.append("grants." + str(src) + " 的键 " + str(k) + " 既不是 tag 也不是带命名空间的 id")
                    if isinstance(v, bool) or not isinstance(v, int):
                        p.append("grants." + str(src) + "." + str(k) + " 的值必须是整数：" + str(v))
                    elif not 0 <= v <= 1000:
                        p.append("grants." + str(src) + "." + str(k) + " 的值必须在 0..1000：" + str(v))

    for k in data:
        if k not in TOP_KEYS:
            p.append("未知键 " + str(k) + "（已忽略）")
    return p


def collect(root):
    """扫 <root>/*.json5，文件名升序。返回 [(文件名, data|None, err)]。"""
    out = []
    if not os.path.isdir(root):
        return out
    for name in sorted(os.listdir(root)):
        full = os.path.join(root, name)
        if not name.endswith(".json5") or not os.path.isfile(full):
            continue
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = json.loads(clean_json5(raw))
        except Exception as ex:
            out.append((name, None, "解析失败：" + type(ex).__name__))
            continue
        if not isinstance(data, dict):
            out.append((name, None, "顶层不是对象"))
            continue
        out.append((name, data, None))
    return out


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="专业配置对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults/professions")
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    os.chdir(ROOT)

    root = os.path.abspath(args.dir)
    ids, _ = read_attrs()
    problems = []
    if ids is None:
        problems.append(("属性表", "属性表读不到"))
    files = collect(root)
    seen = {}
    for name, data, err in files:
        if data is None:
            problems.append((name, err))
            continue
        problems.extend((name, msg) for msg in check(data, ids, ids is not None))
        ident = data.get("id")
        if isinstance(ident, str) and ident.strip() and ":" in ident:
            key = ident.strip()
            if key in seen:
                problems.append((name, "id " + key + " 重复定义（先出现在 " + seen[key] + "）"))
            else:
                seen[key] = name

    if args.v:
        for name, data, err in files:
            print(("跳过" if data is None else "已检查") + " " + name + " · 11 项")
    for name, msg in problems:
        print("[问题] " + name + " · " + msg)
    print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
