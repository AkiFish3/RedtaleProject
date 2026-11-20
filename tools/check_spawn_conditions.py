"""刷怪规则 conditions 七维值域对账（T-0080）：只读静态扫描，0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_RULES = os.path.join(ROOT, "src/main/resources/redtale/defaults/spawn_rules")
DIMS = ("world", "biome", "light", "time", "y", "outside", "difficulty")
ARRAY_DIMS = ("world", "biome", "difficulty")
BOX_DIMS = ("light", "y")
TIME_VALUES = {"day", "night", "any"}
DIFFICULTIES = {"peaceful", "easy", "normal", "hard"}


def _loads(text):
    """单引号字面量归一化后交 clean_json5（沿用 check_display / check_damage_types 口径）。"""
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


def read_rules(rules_dir: str) -> list[tuple[str, object | None]]:
    try:
        names = sorted(f for f in os.listdir(rules_dir)
                       if f.endswith(".json5") and os.path.isfile(os.path.join(rules_dir, f)))
    except OSError:
        return []
    out = []
    for name in names:
        try:
            with open(os.path.join(rules_dir, name), encoding="utf-8") as fh:
                out.append((name, _loads(fh.read())))
        except Exception:
            out.append((name, None))
    return out


def _range(key, val):
    if not isinstance(val, dict):
        return [f"{key} 必须是对象"]
    if set(val) - {"min", "max"}:
        return [f"{key} 只含 min/max"]
    nums = []
    for bound in ("min", "max"):
        if bound in val:
            v = val[bound]
            if isinstance(v, bool) or not isinstance(v, (int, float)):
                return [f"{key} 值必须是数字"]
            nums.append(v)
    if key == "light" and any(v < 0 or v > 15 for v in nums):
        return [f"{key} 越界（0~15）"]
    if len(nums) == 2 and nums[0] > nums[1]:
        return [f"{key} min>max"]
    return []


def check_conditions(data: dict | None) -> list[str]:
    if data is None:
        return ["规则解析失败"]
    cond = data.get("conditions", {})
    if not isinstance(cond, dict):
        return ["conditions 必须是对象"]
    if not cond:
        return []
    issues = []
    for key, val in cond.items():
        if key not in DIMS:
            issues.append(f"未知 conditions 维度：{key}")
        elif key in ARRAY_DIMS:
            if not isinstance(val, list) or any(not isinstance(x, str) or not x for x in val):
                issues.append(f"{key} 必须是非空字符串数组")
            elif key == "difficulty":
                for x in val:
                    if x not in DIFFICULTIES:
                        issues.append(f"difficulty 非法值：{x}")
        elif key in BOX_DIMS:
            issues.extend(_range(key, val))
        elif key == "time":
            if not isinstance(val, str) or val not in TIME_VALUES:
                issues.append(f"time 非法值：{val}")
        elif key == "outside":
            if not isinstance(val, bool):
                issues.append("outside 必须是布尔值")
    return issues


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rules", default=DEFAULT_RULES)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    problems, rules = [], []
    if not os.path.isdir(args.rules):
        problems.append("刷怪规则目录读取失败")
    else:
        rules = read_rules(args.rules)
        for name, data in rules:
            for msg in check_conditions(data):
                problems.append(f"{name}: {msg}")
    if args.v:
        print(f"已检查 {len(rules)} 份规则 · {len(DIMS)} 个条件维度")
    for p in problems:
        print(f"[问题] {p}")
    print(f"共 1 份刷怪规则条件表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
