"""lang 键覆盖起飞前对账（T-0055）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, re, sys
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ATTR_JAVA = "src/main/java/com/redtale/project/core/attr/AttributeSpecs.java"
ITEM_JAVA = "src/main/java/com/redtale/project/items/ItemBase.java"
ENTITY_JAVA = "src/main/java/com/redtale/project/mobs/RedtaleEntities.java"
ATTR_RE = re.compile(r'^\s+(v|c|cb)\("([^"]+)",', re.M)
ITEM_RE = re.compile(r'^\s{4}([A-Z][A-Z0-9_]*)\(\s*"([^"]+)"', re.M)
ENTITY_RE = re.compile(r'new Proto\("([a-z_]+)"')
PH_RE = re.compile(r"%\d+\$s|%s")
PREFIX = ("attribute", "item", "entity", "enchantment")


def read_ids(rel, rx, group):
    p = os.path.join(ROOT, rel)
    if not os.path.isfile(p):
        return []
    with open(p, "r", encoding="utf-8", errors="replace") as f:
        return [m.group(group) for m in rx.finditer(f.read())]


def expected(d):
    """返回 ([(类, key)], 附魔文件解析失败文案列表)。"""
    rows, errs = [], []
    for i in read_ids(ATTR_JAVA, ATTR_RE, 2):
        if not i.startswith("minecraft:"):
            rows.append(("attribute", "attribute.redtale_project." + i.split(":", 1)[-1]))
    for i in read_ids(ITEM_JAVA, ITEM_RE, 2):
        rows.append(("item", "item.redtale_project." + i))
    for i in read_ids(ENTITY_JAVA, ENTITY_RE, 1):
        rows.append(("entity", "entity.redtale_project." + i))
    folder = os.path.join(d, "enchantments")
    if os.path.isdir(folder):
        for name in sorted(os.listdir(folder)):
            full = os.path.join(folder, name)
            if not name.endswith(".json5") or not os.path.isfile(full):
                continue
            try:
                with open(full, "r", encoding="utf-8", errors="replace") as f:
                    data = json.loads(clean_json5(f.read()))
                ns, sep, path = data["id"].partition(":")
                key = "enchantment." + (ns if sep else "redtale_project") + "." + path.replace("/", ".")
                rows.append(("enchantment", key))
            except Exception as ex:
                errs.append("enchantments/" + name + " 解析失败：" + str(ex))
    return rows, errs


def read_lang(folder, name):
    p = os.path.join(folder, name + ".json")
    try:
        with open(p, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            raise ValueError("顶层不是对象")
        return data, None
    except Exception as ex:
        return None, name + ".json 无法解析：" + str(ex)


def pref4(data):
    return {k for k in data if isinstance(k, str) and k.split(".", 1)[0] in PREFIX}


def main():
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    ap = argparse.ArgumentParser(description="lang 键覆盖对账")
    ap.add_argument("--lang", default="src/main/resources/assets/redtale_project/lang")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    a = ap.parse_args()
    os.chdir(ROOT)
    folder = os.path.abspath(a.lang)
    rows, errs = expected(os.path.abspath(a.dir))
    exp = {k for _, k in rows}
    cls = {k: p for p, k in rows}
    order = {p: i for i, p in enumerate(PREFIX)}
    rank = lambda k: (order[cls[k]], k)
    probs, notes, langs = list(errs), [], {}
    for name in ("zh_cn", "en_us"):
        data, err = read_lang(folder, name)
        if err:
            probs.append(err); langs[name] = None; continue
        langs[name] = data
        actual = pref4(data)
        for k in sorted(actual - exp):
            notes.append("%s 孤儿键：%s（定义已不存在）" % (name, k))
        for k in sorted(actual):
            v = data[k]
            if not isinstance(v, str) or not v.strip():
                probs.append("%s 空值或非字符串：%s" % (name, k))
    z, e = langs["zh_cn"], langs["en_us"]
    if z is None or e is None:
        for name, data in (("zh_cn", z), ("en_us", e)):
            if data is None: continue
            for k in sorted(exp - pref4(data), key=rank):
                probs.append("%s 缺键：%s" % (name, k))
    else:
        z4, e4 = pref4(z), pref4(e)
        for k in sorted(exp - z4 - e4, key=rank):
            probs.append("zh_cn 缺键：%s" % k)
            probs.append("en_us 缺键：%s" % k)
        for k in sorted((exp & z4) - e4, key=rank):
            probs.append("只在 zh_cn 存在：%s" % k)
        for k in sorted((exp & e4) - z4, key=rank):
            probs.append("只在 en_us 存在：%s" % k)
        for k in sorted(exp & z4 & e4, key=rank):
            pz = set(PH_RE.findall(z[k] if isinstance(z[k], str) else ""))
            pe = set(PH_RE.findall(e[k] if isinstance(e[k], str) else ""))
            if pz != pe:
                probs.append("%s 占位符两侧不一致（zh_cn: %s / en_us: %s）" % (k, sorted(pz), sorted(pe)))
    for n in notes: print("提示 " + n)
    for p in probs: print("[问题] " + p)
    print("共 2 个文件 · %d 条问题" % len(probs))
    return 1 if probs else 0


if __name__ == "__main__":
    sys.exit(main())
