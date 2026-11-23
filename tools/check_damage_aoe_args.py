"""范围伤害原语 args 静默改写对账（T-0114，单文件零接线）。"""
import json
import math
import os
import re
import sys

MAX_RADIUS = 32.0
DEFAULT_FALLOFF, FALLOFF_MAX, DEFAULT_MAX_TARGETS = 0.5, 1.0, 0


def _clean(text):
    """去 // 注释，单引号归一为双引号并转义内嵌双引号。"""
    out = []
    i = 0
    single = False
    while i < len(text):
        c = text[i]
        if single:
            if c == "\\" and i + 1 < len(text):
                n = text[i + 1]
                out.append(n if n == "'" else ('\\"' if n == '"' else c + n))
                i += 2
                continue
            if c == "'":
                single = False
                out.append('"')
            elif c == '"':
                out.append('\\"')
            else:
                out.append(c)
            i += 1
            continue
        if c == "'":
            single = True
            out.append('"')
            i += 1
            continue
        if c == '"':
            j = i + 1
            while j < len(text):
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(text[i:j])
            i = j
            continue
        if c == "/" and text[i:i + 2] == "//":
            while i < len(text) and text[i] not in "\r\n":
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def _shape(text):
    """只给裸键加引号、只删尾逗号，均跳过字符串内容。"""
    out = []
    i = 0
    instr = False
    while i < len(text):
        c = text[i]
        if instr:
            out.append(c)
            if c == "\\" and i + 1 < len(text):
                out.append(text[i + 1])
                i += 2
                continue
            if c == '"':
                instr = False
            i += 1
            continue
        if c == '"':
            instr = True
            out.append(c)
            i += 1
            continue
        if c in "{,":
            m = re.match(r"(\s*)([A-Za-z_$][A-Za-z0-9_$]*)(\s*:)", text[i + 1:])
            if m:
                out.append(c + m.group(1) + '"' + m.group(2) + '"' + m.group(3))
                i += 1 + len(m.group(0))
                continue
        if c == ",":
            j = i + 1
            while j < len(text) and text[j].isspace():
                j += 1
            if j < len(text) and text[j] in "}]":
                i += 1
                continue
        out.append(c)
        i += 1
    return "".join(out)


def _loads(text):
    try:
        data = json.loads(_shape(_clean(text)))
    except (ValueError, TypeError): return None
    return data if isinstance(data, dict) else None


def _number(v):
    if isinstance(v, bool) or not isinstance(v, (int, float)): return False
    return isinstance(v, int) or math.isfinite(v)


def _read_dir(path, prefix):
    rows = []
    try:
        names = sorted(n for n in os.listdir(path) if n.endswith(".json5"))
    except OSError: return rows, False
    for name in names:
        try:
            with open(os.path.join(path, name), encoding="utf-8") as f:
                rows.append((prefix + name, _loads(f.read())))
        except (OSError, UnicodeError):
            rows.append((prefix + name, None))
    return rows, True


def _calls(rows):
    total = 0
    for _, data in rows:
        if not isinstance(data, dict):
            continue
        effects = data.get("effects")
        if not isinstance(effects, list):
            continue
        for item in effects:
            if (isinstance(item, dict) and item.get("type") == "damage_aoe"
                    and isinstance(item.get("args"), dict)):
                total += 1
    return total


def check_calls(skills, scripts):
    problems = []
    for rows in (skills, scripts):
        for name, data in rows:
            if not isinstance(data, dict):
                continue
            effects = data.get("effects")
            if not isinstance(effects, list):
                continue
            for i, item in enumerate(effects):
                if (not isinstance(item, dict) or item.get("type") != "damage_aoe"
                        or not isinstance(item.get("args"), dict)):
                    continue
                args = item["args"]
                base = "%s: effects[%d].args." % (name, i)
                v = args.get("radius")
                if _number(v) and v > MAX_RADIUS:
                    problems.append(base + "radius 值 " + repr(v) + " 超出 MAX_RADIUS=32.0，静默夹到 32.0（解析零 WARN）")
                v = args.get("falloff")
                if _number(v):
                    if v <= 0:
                        problems.append(base + "falloff 值 " + repr(v) + " 静默回落 0.5（文档声明 0 = 不衰减，解析零 WARN）")
                    elif v > FALLOFF_MAX:
                        problems.append(base + "falloff 值 " + repr(v) + " 静默夹到 1.0（解析零 WARN）")
                v = args.get("max_targets")
                if _number(v):
                    if v > 0 and v != int(v):
                        problems.append(base + "max_targets 值 " + repr(v) + " 被 (int) 静默截断（解析零 WARN）")
                    elif v < 0:
                        problems.append(base + "max_targets 值 " + repr(v) + " 静默回落 0（不限，解析零 WARN）")
    return sorted(problems)


def main():
    argv = sys.argv[1:]
    target = "src/main/resources/redtale/defaults"
    verbose = "-v" in argv
    if "--dir" in argv:
        idx = argv.index("--dir")
        if idx + 1 < len(argv): target = argv[idx + 1]
    skills, ok_s = _read_dir(os.path.join(target, "skills"), "skills/")
    scripts, ok_r = _read_dir(os.path.join(target, "scripts"), "scripts/")
    failed = (0 if ok_s else 1) + (0 if ok_r else 1)
    if verbose:
        print("已检查 %d 份技能 · %d 份脚本 · %d 处 damage_aoe 调用 · 3 个值域键"
              % (len(skills), len(scripts), _calls(skills) + _calls(scripts)))
    if failed:
        for _ in range(failed):
            print("[问题] 技能/脚本目录读取失败")
        print("共 1 份范围伤害参数口径对账表 · %d 条问题" % failed)
        return 1
    problems = check_calls(skills, scripts)
    for msg in problems:
        print("[问题] " + msg)
    print("共 1 份范围伤害参数口径对账表 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__": sys.exit(main())
