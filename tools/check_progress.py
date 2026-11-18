"""等级/职业配置起飞前对账（T-0041）：只读，--dir 可指向运营配置根，退出码 0=干净 1=有问题。"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ATTR_JAVA = "src/main/java/com/redtale/project/core/attr/AttributeSpecs.java"
ATTR_RE = re.compile(r'\b(?:v|cb|c)\("([A-Za-z0-9_.:-]+)"')
KEY_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def _utf8_stdout():
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass


def read_attrs():
    """80 条属性 id 只从 AttributeSpecs.java 正则读，不手抄。"""
    path = os.path.join(ROOT, ATTR_JAVA)
    if not os.path.isfile(path):
        return None, path
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        ids = set(ATTR_RE.findall(f.read()))
    return (ids or None), path


def normalize(token, ids):
    """照 AttributeSpecs#resolve 的口径归一到 BY_ID 的键。"""
    t = token.strip()
    if t in ids:
        return t
    if ":" not in t:
        for pfx, head in (("generic.", "minecraft:generic."), ("minecraft.", "minecraft:"), ("redtale_project.", "")):
            if t.startswith(pfx):
                k = head + t[len(pfx):]
                if k in ids:
                    return k
    if t.startswith("redtale_project:"):
        k = t[len("redtale_project:"):]
        return k if k in ids else None
    if t.startswith("minecraft:"):
        k = "minecraft:generic." + t[len("minecraft:"):]
        return k if k in ids else None
    k = "minecraft:generic." + t
    return k if k in ids else None


def clean_json5(text):
    """去 // 行注释 + 裸键加引号 + 去尾逗号，再交 json.loads。"""
    out, stack, prev = [], [], ""
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            prev = '"'
            i = j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c.isspace():
            out.append(c)
            i += 1
            continue
        if c in "{[":
            stack.append(c)
            out.append(c)
            prev = c
            i += 1
            continue
        if c in "}]":
            k = len(out) - 1
            while k >= 0 and out[k].isspace():
                k -= 1
            if k >= 0 and out[k] == ",":
                out[k] = ""
            if stack:
                stack.pop()
            out.append(c)
            prev = c
            i += 1
            continue
        if stack and stack[-1] == "{" and prev in ("{", ",") and (c.isalpha() or c == "_"):
            m = KEY_RE.match(text, i)
            if m:
                j = m.end()
                k = j
                while k < n and text[k].isspace():
                    k += 1
                if k < n and text[k] == ":":
                    out.append('"' + m.group(0) + '"')
                    prev = '"'
                    i = j
                    continue
        out.append(c)
        prev = c
        i += 1
    return "".join(out)


def collect(root):
    """返回 [(相对路径, data|None, 解析错误)]，levels 先 jobs 后，文件名升序。"""
    files = []
    for folder in ("levels", "jobs"):
        folder_path = os.path.join(root, folder)
        if not os.path.isdir(folder_path):
            continue
        for name in sorted(os.listdir(folder_path)):
            if not name.endswith(".json5"):
                continue
            full = os.path.join(folder_path, name)
            if not os.path.isfile(full):
                continue
            rel = folder + "/" + name
            with open(full, "r", encoding="utf-8", errors="replace") as f:
                raw = f.read()
            try:
                data = json.loads(clean_json5(raw))
            except Exception as ex:
                files.append((rel, None, str(ex)))
                continue
            if not isinstance(data, dict):
                files.append((rel, None, "顶层不是对象"))
                continue
            files.append((rel, data, None))
    return files


def check_file(data, ids):
    probs = []
    ident = data.get("id")
    if "id" not in data: probs.append("id 缺失")
    elif not isinstance(ident, str): probs.append("id 不是字符串")
    elif not ident.strip(): probs.append("id 为空")
    elif ":" not in ident: probs.append("id 不含 ':'")

    attrs = data.get("attributes")
    if isinstance(attrs, dict):
        for key in attrs:
            if normalize(key, ids) is None:
                probs.append("属性 " + str(key) + " 不在 80 条表内")

    curve = data.get("curve")
    if not isinstance(curve, dict):
        probs.append("curve 缺失或不是对象")
    else:
        mode = curve.get("mode")
        if mode not in ("geometric", "polynomial"):
            mode = "geometric"
        need = ("base", "growth") if mode == "geometric" else ("base", "exponent")
        miss = [k for k in need if k not in curve]
        if miss:
            probs.append("curve 缺 " + "/".join(miss) + "（" + mode + "）")

    if "max_level" in data:
        value = data["max_level"]
        if isinstance(value, bool) or not isinstance(value, int) or value < 2:
            probs.append("max_level 必须是 >=2 的整数")
    return probs


def main():
    _utf8_stdout()
    parser = argparse.ArgumentParser(description="等级/职业配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    args = parser.parse_args()
    os.chdir(ROOT)

    root = os.path.abspath(args.dir)
    levels_dir = os.path.join(root, "levels")
    jobs_dir = os.path.join(root, "jobs")
    if not os.path.isdir(levels_dir) and not os.path.isdir(jobs_dir):
        print("没有 levels/ 与 jobs/ 目录（跳过）")
        return 0

    ids, attr_path = read_attrs()
    if ids is None:
        print("[问题] 读不到 AttributeSpecs.java（路径 %s）" % attr_path)
        print("共 0 个文件 · 1 条问题")
        return 1

    files = collect(root)
    problems = []
    seen = {}
    for rel, data, err in files:
        if data is None:
            problems.append((rel, "JSON5 解析失败：" + err))
            continue
        for msg in check_file(data, ids):
            problems.append((rel, msg))
        ident = data.get("id")
        if isinstance(ident, str) and ":" in ident:
            seen.setdefault(ident.strip(), []).append(rel)

    for ident, rels in sorted(seen.items()):
        if len(rels) > 1 and any(r.startswith("levels/") for r in rels) and any(r.startswith("jobs/") for r in rels):
            target = next(r for r in rels if r.startswith("jobs/"))
            other = next(r for r in rels if r.startswith("levels/"))
            problems.append((target, "id " + ident + " 与 " + other + " 重复"))

    for rel, msg in problems:
        print("[问题] " + rel + ": " + msg)
    if problems:
        print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
        return 1
    print("共 %d 个文件 · 没有发现问题 ✓" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
