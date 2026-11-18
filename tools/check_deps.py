"""依赖面与命名面起飞前对账（D6 / D11 / D12 / BUILD）：只读，退出码 0=干净 1=有问题。

为什么不靠编译器：越界 import 只在「机器上恰好有那个 jar」时才报错，换台构建机就绿；
mods.toml 里多一条 required 依赖，编译期完全不看，运行期才拒绝加载。
"""
from __future__ import annotations
import argparse, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOML = "src/main/resources/META-INF/neoforge.mods.toml"
GRADLE = "build.gradle"
PROPS = "gradle.properties"
JAVA_DIR = "src/main/java"
RES_DIR = "src/main/resources"

# 只允许 neoforge / minecraft 走 required（D12：零 mod 依赖）
CORE = {"neoforge", "minecraft"}
# D11 前置严格白名单：允许声明，但当前一条都不该声明
WHITELIST = {"geckolib", "azurelib", "playeranimator", "architectury", "cloth_config",
             "curios", "resourcefullib", "attributefix", "kotlinforforge"}
IMPORT_OK = ("java.", "javax.", "net.minecraft.", "net.neoforged.", "com.mojang.",
             "com.google.", "com.redtale.project.", "org.slf4j.", "org.spongepowered.")
BANNED = ("mmoitems", "mmocore", "mmoinventory", "mythiclib", "mythicmobs", "ecoenchants")
MOD_ID = "redtale_project"


def rel(p):
    return os.path.relpath(p, ROOT).replace("\\", "/")


def read(p):
    with open(p, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def walk(base, exts, skip=()):
    for dirpath, _, names in os.walk(base):
        if any(s in dirpath.replace("\\", "/") for s in skip):
            continue
        for n in names:
            if n.lower().endswith(exts):
                yield os.path.join(dirpath, n)


def check_toml(probs):
    p = os.path.join(ROOT, TOML)
    if not os.path.isfile(p):
        probs.append(TOML + " 不存在（D11 的依赖面无从核对）")
        return 0
    text = read(p)
    if 'modId = "${mod_id}"' not in text:
        probs.append(TOML + " 缺 modId = \"${mod_id}\" 占位（mod_id 应从 gradle.properties 注入）")
    deps = [m.group(1) for m in re.finditer(r'^\s*modId\s*=\s*"([^"]+)"\s*$', text, re.M)]
    seen = 0
    for d in deps:
        if d.startswith("${"):
            continue
        seen += 1
        if d not in CORE and d not in WHITELIST:
            probs.append("越界前置：%s（D11 白名单外，D12 零 mod 依赖）" % d)
    if seen < 2:
        probs.append(TOML + " 只声明了 %d 条实体依赖（至少 neoforge + minecraft）" % seen)
    return 1


def check_props(probs):
    p = os.path.join(ROOT, PROPS)
    if not os.path.isfile(p):
        probs.append(PROPS + " 不存在")
        return 0
    m = re.search(r"^\s*mod_id\s*=\s*(\S+)\s*$", read(p), re.M)
    if not m:
        probs.append(PROPS + " 缺 mod_id")
    elif m.group(1) != MOD_ID:
        probs.append("mod_id 被改名：%s（应为 %s，PLAN 已定不得再改）" % (m.group(1), MOD_ID))
    return 1


def check_gradle(probs):
    p = os.path.join(ROOT, GRADLE)
    if not os.path.isfile(p):
        probs.append(GRADLE + " 不存在")
        return 0
    for i, line in enumerate(read(p).split("\n"), 1):
        s = line.strip()
        if s.startswith("//") or s.startswith("*"):
            continue
        if re.match(r"^(implementation|api)\s*[(\s]", s):
            probs.append("%s:%d 出现运行时依赖：%s" % (GRADLE, i, s))
        if "moddevgradle" in s.lower() or "neoforged.moddev" in s.lower() or "neoform-runtime" in s.lower():
            probs.append("%s:%d 引用 ModDevGradle / neoform-runtime（docs/BUILD.md 明令禁用）" % (GRADLE, i))
    return 1


def check_imports(probs):
    base = os.path.join(ROOT, JAVA_DIR)
    if not os.path.isdir(base):
        probs.append(JAVA_DIR + " 不存在")
        return 0
    n = 0
    for f in walk(base, (".java",)):
        n += 1
        for i, line in enumerate(read(f).split("\n"), 1):
            s = line.strip()
            if not s.startswith("import "):
                continue
            s = s[7:].strip()
            if s.startswith("static "):
                s = s[7:].strip()
            name = s.split(";")[0].strip()
            if not name.startswith(IMPORT_OK):
                probs.append("%s:%d 越界 import：%s" % (rel(f), i, name))
    return n


def check_names(probs):
    base = os.path.join(ROOT, RES_DIR)
    if not os.path.isdir(base):
        return 0
    n = 0
    # META-INF 是构建描述符，已在 check_toml 里核对，这里不重复扫
    for f in walk(base, (".json", ".json5", ".toml", ".txt", ".mcmeta"), skip=("/META-INF",)):
        n += 1
        low = read(f).lower()
        for b in BANNED:
            if b in low:
                probs.append("%s 出现上游专名「%s」（D6 禁用）" % (rel(f), b))
    return n


def main():
    global ROOT
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="依赖面与命名面对账")
    ap.add_argument("--root", default="")
    a = ap.parse_args()
    ROOT = os.path.abspath(a.root or ROOT)
    probs = []
    check_toml(probs)
    check_props(probs)
    check_gradle(probs)
    jf = check_imports(probs)
    rf = check_names(probs)
    print("java %d 个 · 资源 %d 个" % (jf, rf))
    for p in probs:
        print("[问题] " + p)
    print("共 4 份来源 · %d 条问题" % len(probs))
    return 1 if probs else 0


if __name__ == "__main__":
    sys.exit(main())
