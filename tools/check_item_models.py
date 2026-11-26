"""基底 ↔ 模型 ↔ 原版贴图三方对账（T-0129 / R134 冻结接口）。"""
import argparse
import json
import os
import re
import sys
import zipfile

ENUM_SRC = "src/main/java/com/redtale/project/items/ItemBase.java"
MODELS_DIR = "src/main/resources/assets/redtale_project/models/item"
PARENT = "minecraft:item/generated"
TEX_ROOT = "assets/minecraft/textures/"
ITEM_TEX = TEX_ROOT + "item/"
DEFAULT_JAR = "E:/client/赤物语N客户端-0.0.1/.minecraft/versions/1.21.1-NeoForge/1.21.1-NeoForge.jar"

STATS = {"bases": 0, "models": 0, "layers": 0, "sprites": 0}


def bases_of(text):
    return re.findall(r'^\s{4}[A-Z_]+\("([a-z_]+)"', text, re.M)


def jar_sprites(path):
    with zipfile.ZipFile(path) as zf:
        names = [n for n in zf.namelist()
                 if n.startswith(ITEM_TEX) and n.endswith(".png")]
    # assets/minecraft/textures/item/iron_sword.png -> minecraft:item/iron_sword
    out = set()
    for name in names:
        rel = name[len(TEX_ROOT.split("/", 1)[0] + "/"):-4].replace("textures/", "", 1)
        out.add(rel.replace("/", ":", 1))
    return out


def check_model(path):
    name = os.path.basename(path)
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except (OSError, ValueError):
        return None, [f"{name} 不是合法 JSON"]
    bad = []
    keys = sorted(data) if isinstance(data, dict) else []
    if not isinstance(data, dict) or keys != ["parent", "textures"]:
        bad.append(f"{name} 顶层键应恰为 parent/textures，实际 {keys}")
        return None, bad
    if data["parent"] != PARENT:
        bad.append(f"{name} parent 应为 {PARENT}，实际 {data['parent']!r}")
    tex = data["textures"]
    tkeys = sorted(tex) if isinstance(tex, dict) else []
    if tkeys != ["layer0"]:
        bad.append(f"{name} textures 应恰有一个键 layer0，实际 {tkeys}")
        return data, bad
    layer0 = tex["layer0"]
    ns = layer0.split(":", 1)[0] if isinstance(layer0, str) else layer0
    if ns != "minecraft":
        bad.append(f"{name} layer0 命名空间应为 minecraft，实际 {ns!r}")
    return data, bad


def check(root, jar):
    problems = []
    enum_path = os.path.join(root, ENUM_SRC)
    model_path = os.path.join(root, MODELS_DIR)
    try:
        with open(enum_path, encoding="utf-8") as fh:
            bases = bases_of(fh.read())
    except OSError:
        problems.append(f"基底源码读取失败：{enum_path}")
        bases = []
    try:
        files = {os.path.splitext(n)[0]: n for n in os.listdir(model_path)
                 if n.endswith(".json")}
    except OSError:
        problems.append(f"模型目录读取失败：{model_path}")
        files = {}
    try:
        sprites = jar_sprites(jar)
        jar_ok = True
    except (OSError, zipfile.BadZipFile):
        problems.append(f"原版 jar 读取失败：{jar}")
        sprites = set()
        jar_ok = False
    STATS.update(bases=len(bases), models=len(files), sprites=len(sprites), layers=0)
    for base in bases:
        if base not in files:
            problems.append(f"基底 '{base}' 缺少模型 models/item/{base}.json")
    for stem in sorted(files):
        if stem not in bases:
            problems.append(f"模型 {files[stem]} 没有对应基底（多余文件）")
    layers = []
    for stem in sorted(files):
        if stem not in bases:
            continue
        data, bad = check_model(os.path.join(model_path, files[stem]))
        problems.extend(bad)
        tex = data.get("textures") if isinstance(data, dict) else None
        if isinstance(tex, dict) and "layer0" in tex:
            STATS["layers"] += 1
            layer0 = tex["layer0"]
            if isinstance(layer0, str) and layer0.split(":", 1)[0] == "minecraft":
                layers.append((files[stem], layer0))
    if jar_ok:
        for name, layer0 in layers:
            if layer0 not in sprites:
                problems.append(f"{name} layer0 '{layer0}' 在原版贴图里不存在")
    return (1 if problems else 0), problems


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--jar", default=DEFAULT_JAR)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args(argv)
    code, problems = check(args.root, args.jar)
    if args.v:
        print(f"已检查 {STATS['bases']} 个基底 · {STATS['models']} 份模型 · "
              f"{STATS['layers']} 处 layer0 · 原版贴图 {STATS['sprites']} 张")
    for problem in problems:
        print(problem)
    print(f"共 {STATS['bases']} 份基底物品模型 · {len(problems)} 条问题")
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
