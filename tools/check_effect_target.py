"""效果 target 枚举值域对账（T-0083）：只读静态扫描，0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SCRIPTS = os.path.join(ROOT, "src/main/resources/redtale/defaults/scripts")
DEFAULT_SKILLS = os.path.join(ROOT, "src/main/resources/redtale/defaults/skills")
TARGET_TYPES = {"buff", "debuff", "grant_exp", "heal", "stun"}
VALID_TARGETS = {"caster", "target"}


def _loads(text):
    """单引号字面量归一化后交 clean_json5（沿用既有 shim 口径）。"""
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


def read_configs(dir_path: str) -> list[tuple[str, object | None]]:
    """扫 <dir_path> 下 *.json5（文件名升序），返回 (文件名, dict|None)；失败记 None。"""
    try:
        names = sorted(f for f in os.listdir(dir_path)
                       if f.endswith(".json5") and os.path.isfile(os.path.join(dir_path, f)))
    except OSError:
        return []
    out = []
    for name in names:
        data = None
        try:
            with open(os.path.join(dir_path, name), "r", encoding="utf-8", errors="replace") as f:
                parsed = _loads(f.read())
            if isinstance(parsed, dict):
                data = parsed
        except Exception:
            pass
        out.append((name, data))
    return out


def check_effects(data: dict | None) -> list[str]:
    """只核五种 target 原语的 args.target 枚举；其它 type 跳过，文案无 [问题] 前缀。"""
    if data is None:
        return ["配置解析失败"]
    effects = data.get("effects", [])
    if not isinstance(effects, list):
        return ["effects 必须是数组"]
    issues = []
    for i, effect in enumerate(effects):
        if not isinstance(effect, dict):
            issues.append(f"effects[{i}] 必须是对象")
            continue
        typ = effect.get("type")
        if not isinstance(typ, str) or typ not in TARGET_TYPES:
            continue
        if "args" not in effect:
            continue
        args = effect["args"]
        if not isinstance(args, dict):
            issues.append(f"effects[{i}].args 必须是对象")
            continue
        if "target" in args:
            value = args["target"]
            if not isinstance(value, str) or value not in VALID_TARGETS:
                issues.append(f"effects[{i}].args.target 必须是 caster 或 target")
    return issues


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--scripts", default=DEFAULT_SCRIPTS)
    ap.add_argument("--skills", default=DEFAULT_SKILLS)
    ap.add_argument("-v", action="store_true")
    opts = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    problems, scripts, skills = [], [], []
    for category, path, sink in (("scripts", opts.scripts, scripts), ("skills", opts.skills, skills)):
        if not os.path.isdir(path):
            problems.append(f"{category}目录读取失败")
            continue
        sink.extend(read_configs(path))
        for name, data in sink:
            for msg in check_effects(data):
                problems.append(f"{category}/{name}: {msg}")
    if opts.v:
        print(f"已检查 {len(scripts)} 份脚本 · {len(skills)} 份技能 · 5 个 target 原语")
    for p in problems:
        print(f"[问题] {p}")
    print(f"共 2 份效果 target 值域表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
