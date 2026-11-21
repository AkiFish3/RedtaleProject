"""附魔 scripted.args ↔ 脚本 effects[].args 数值同步对账（T-0090）。只读，退出码 0=干净 1=有问题。"""
import argparse
import json
import math
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTED = "redtale_project:scripted"
ALIASES = {"heal_ratio": "ratio"}


def _utf8():
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass


def _loads(text):
    """clean_json5 + 单引号字符串 shim（既有 check 工具口径）。"""
    try: return json.loads(clean_json5(text))
    except Exception: pass
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            i = j
        elif c == "'":
            j, body = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    nx = text[j + 1]
                    body.append(nx if nx == "'" else ('\\"' if nx == '"' else ('\\\\' if nx == "\\" else "\\" + nx)))
                    j += 2
                else:
                    body.append('\\"' if text[j] == '"' else text[j])
                    j += 1
            j = min(j + 1, n)
            out.append('"' + "".join(body) + '"')
            i = j
        else:
            out.append(c)
            i += 1
    return json.loads(clean_json5("".join(out)))


def _files(root, sub):
    path = os.path.join(root, sub)
    if not os.path.isdir(path): return None
    try: return path, sorted(n for n in os.listdir(path) if n.endswith(".json5"))
    except OSError: return None


def _read(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return _loads(f.read())
    except Exception:
        return None


def read_enchants(root):
    got = _files(root, "enchantments")
    if got is None: return None
    path, names = got
    out = []
    for name in names:
        data = _read(os.path.join(path, name))
        out.append(("enchantments/" + name, data if isinstance(data, dict) else None))
    return out


def read_scripts(root):
    got = _files(root, "scripts")
    if got is None: return None
    path, names = got
    out = {}
    for name in names:
        data = _read(os.path.join(path, name))
        if isinstance(data, dict) and isinstance(data.get("id"), str) and data["id"] not in out:
            out[data["id"]] = data
    return out


def calls(data):
    out = []

    def walk(node):
        if isinstance(node, dict):
            if node.get("type") == SCRIPTED:
                sid, args = node.get("script"), node.get("args")
                if isinstance(sid, str) and isinstance(args, dict):
                    out.append((sid, args))
            for value in node.values(): walk(value)
        elif isinstance(node, list):
            for value in node: walk(value)

    walk(data)
    return out


def _finite(value):
    return (not isinstance(value, bool) and isinstance(value, (int, float))
            and math.isfinite(value))


def numbers(data):
    out = {}
    effects = data.get("effects") if isinstance(data, dict) else None
    if not isinstance(effects, list): return out
    for item in effects:
        args = item.get("args") if isinstance(item, dict) else None
        if not isinstance(args, dict): continue
        for key, value in args.items():
            if _finite(value): out.setdefault(key, []).append(float(value))
    return out


def diffs(enchant_args, script_nums):
    out = []
    for key, value in enchant_args.items():
        if not _finite(value): continue
        target = ALIASES.get(key, key)
        fv = float(value)
        if target not in script_nums:
            out.append(f"脚本缺少参数 {target}（附魔声明 {key}={fv!r}）")
            continue
        for xv in script_nums[target]:
            if abs(xv - fv) > 1e-9:
                out.append(f"脚本参数 {target} 与附魔声明不一致（附魔 {fv!r} / 脚本 {xv!r}）")
                break
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=os.path.join(ROOT, "src/main/resources/redtale/defaults"))
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    _utf8()
    enchants, scripts = read_enchants(args.dir), read_scripts(args.dir)
    if enchants is None or scripts is None:
        print("[问题] enchantments/scripts 目录读取失败")
        print("共 0 份附魔脚本参数对账表 · 1 条问题")
        return 1
    problems, ncall, nnum, used = [], 0, 0, set()
    for rel, data in enchants:
        if data is None:
            problems.append(f"[问题] {rel} - 附魔配置解析失败")
            continue
        for sid, enchant_args in calls(data):
            ncall += 1
            nnum += sum(1 for value in enchant_args.values() if _finite(value))
            if sid not in scripts:
                problems.append(f"[问题] {rel} 引用的脚本 {sid} 不存在")
                continue
            used.add(sid)
            for msg in diffs(enchant_args, numbers(scripts[sid])):
                problems.append(f"[问题] {rel} {sid} {msg}")
    if args.v:
        print(f"已检查 {ncall} 条 scripted 调用 · {nnum} 个数值参数 · {len(used)} 份脚本")
    for line in problems: print(line)
    print(f"共 1 份附魔脚本参数对账表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
