"""脚本 conditions args 值域对账（T-0082）：只读静态扫描，0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SCRIPTS = os.path.join(ROOT, "src/main/resources/redtale/defaults/scripts")
RANGE_KEYS = {"health_below": "ratio", "chance": "chance"}


def _loads(text):
    """单引号字面量归一化后交 clean_json5。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(text[i:j])
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j])
            i = j
            continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1] if text[j + 1] == "'" else text[j:j + 2])
                    j += 2
                    continue
                buf.append('\\"' if text[j] == '"' else text[j])
                j += 1
            out.append('"' + "".join(buf) + '"')
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return json.loads(clean_json5("".join(out)))


def read_scripts(scripts_dir: str) -> list[tuple[str, object | None]]:
    """扫 scripts/*.json5（文件名升序），返回 (文件名, dict|None)；解析/读失败记 None。"""
    try:
        names = sorted(f for f in os.listdir(scripts_dir)
                       if f.endswith(".json5") and os.path.isfile(os.path.join(scripts_dir, f)))
    except OSError:
        return []
    out = []
    for name in names:
        data = None
        try:
            with open(os.path.join(scripts_dir, name), "r", encoding="utf-8", errors="replace") as f:
                parsed = _loads(f.read())
            if isinstance(parsed, dict):
                data = parsed
        except Exception:
            pass
        out.append((name, data))
    return out


def _in_range(value):
    """[0,1] 数字；bool 不算数字。"""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False
    return 0 <= value <= 1


def check_args(data: dict | None) -> list[str]:
    """只核两类已知条件的 args 值域；always/未知 type 跳过，文案不含 [问题] 前缀。"""
    if data is None:
        return ["脚本解析失败"]
    conds = data.get("conditions", [])
    if not isinstance(conds, list):
        return ["conditions 必须是数组"]
    issues = []
    for i, cond in enumerate(conds):
        if not isinstance(cond, dict):
            issues.append(f"conditions[{i}] 必须是对象")
            continue
        typ = cond.get("type")
        key = RANGE_KEYS.get(typ) if isinstance(typ, str) else None
        if key is None or "args" not in cond:
            continue
        args = cond["args"]
        if not isinstance(args, dict):
            issues.append(f"conditions[{i}].args 必须是对象")
            continue
        if key in args and not _in_range(args[key]):
            issues.append(f"conditions[{i}].args.{key} 必须是 [0,1] 的数字")
    return issues


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--scripts", default=DEFAULT_SCRIPTS)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    problems, scripts = [], []
    if not os.path.isdir(args.scripts):
        problems.append("脚本目录读取失败")
    else:
        scripts = read_scripts(args.scripts)
        for name, data in scripts:
            for msg in check_args(data):
                problems.append(f"{name}: {msg}")
    if args.v:
        print(f"已检查 {len(scripts)} 份脚本 · {len(RANGE_KEYS)} 个值域键")
    for p in problems:
        print(f"[问题] {p}")
    print(f"共 1 份脚本条件值域表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
