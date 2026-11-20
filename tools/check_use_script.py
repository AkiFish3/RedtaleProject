"""消耗品 use.script 原语 id 对账（T-0085）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations
import argparse, json, os, sys
from check_progress import clean_json5
from check_scripts import planned, EFFECTS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VALID = planned() | EFFECTS


def _loads(text):
    """单引号字面量转双引号后交 clean_json5（沿用 check_scripts 清洗口径）。"""
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


def read_items(dir_path):
    """扫 *.json5（文件名升序），返回 [(文件名, dict|None)]；任何读/解析失败记 None。"""
    try:
        names = sorted(os.listdir(dir_path))
    except Exception:
        return []
    out = []
    for name in names:
        if not name.endswith(".json5"):
            continue
        path = os.path.join(dir_path, name)
        try:
            if not os.path.isfile(path):
                continue
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                raw = f.read()
            data = _loads(raw)
            if not isinstance(data, dict):
                data = None
        except Exception:
            data = None
        out.append((name, data))
    return out


def check_use_script(data):
    """返回该文件的问题列表（无文件名前缀）。"""
    if data is None:
        return ["配置解析失败"]
    use = data.get("use", {})
    if use is None:
        return []
    if not isinstance(use, dict):
        return ["use 必须是对象"]
    script = use.get("script", [])
    if script is None or (isinstance(script, list) and not script):
        return []
    if not isinstance(script, list):
        return ["use.script 必须是数组"]
    probs = []
    for i, entry in enumerate(script):
        if not isinstance(entry, dict):
            probs.append("use.script[%d] 不是对象" % i); continue
        typ = entry["effect"] if "effect" in entry else entry.get("type")
        if not isinstance(typ, str) or not typ.strip():
            probs.append("use.script[%d] 缺 effect/type 键或不是非空字符串" % i); continue
        if ":" in typ:
            continue
        if typ not in VALID:
            probs.append("use.script[%d].effect 未注册原语：%s" % (i, typ))
    return probs


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    parser = argparse.ArgumentParser(description="消耗品 use.script 原语对账")
    parser.add_argument("--items", default="src/main/resources/redtale/defaults/items")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()
    os.chdir(ROOT)

    missing = not os.path.isdir(args.items)
    files = [] if missing else read_items(args.items)
    problems = []
    calls = 0
    for name, data in files:
        problems.extend((name, p) for p in check_use_script(data))
        if isinstance(data, dict) and isinstance(data.get("use"), dict):
            script = data["use"].get("script")
            if isinstance(script, list):
                calls += len(script)

    if args.v:
        print("已检查 %d 份物品 · %d 条 use.script 调用 · %d 个合法原语" % (len(files), calls, len(VALID)))
    if missing:
        print("[问题] items目录读取失败")
    else:
        for name, p in problems:
            print("[问题] " + name + ": " + p)
    count = 1 if missing else len(problems)
    print("共 1 份消耗品脚本原语表 · %d 条问题" % count)
    return 1 if count else 0


if __name__ == "__main__":
    sys.exit(main())
