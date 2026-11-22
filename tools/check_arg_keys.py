"""离线对账：原语调用 args 的具名键必须属于该原语合法键集。"""

import argparse
import json
import os
import re
import sys

# S7 冻结的 16 个原语签名（不读源码、不推断）
ARG_KEYS = {
    "message": frozenset({"text"}),
    "console_log": frozenset({"text"}),
    "heal": frozenset({"target", "ratio", "per_level", "flat"}),
    "damage": frozenset({"amount", "damage_type", "radius"}),
    "damage_aoe": frozenset({"amount", "radius", "falloff", "max_targets", "damage_type"}),
    "buff": frozenset({"target", "effect", "amplifier", "duration_ticks"}),
    "debuff": frozenset({"target", "effect", "amplifier", "duration_ticks"}),
    "knockback": frozenset({"strength", "radius"}),
    "pull": frozenset({"strength", "radius"}),
    "stun": frozenset({"target", "radius", "duration_ticks"}),
    "dash": frozenset({"power", "up", "direction"}),
    "projectile": frozenset({"entity", "speed", "on_hit"}),
    "grant_exp": frozenset({"amount", "target"}),
    "always": frozenset(),
    "health_below": frozenset({"ratio"}),
    "chance": frozenset({"chance"}),
}


def _loads(text):
    """单引号 / 行注释 JSON5 shim；失败或顶层非 dict 返 None。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c in ("'", '"'):
            q = c
            i += 1
            out.append('"')
            while i < n:
                if text[i] == "\\" and i + 1 < n:
                    out.append(text[i:i + 2])
                    i += 2
                    continue
                if text[i] == q:
                    out.append('"')
                    i += 1
                    break
                if q == "'" and text[i] == '"':
                    out.append('\\"')
                else:
                    out.append(text[i])
                i += 1
            continue
        out.append(c)
        i += 1
    cleaned = re.sub(r",\s*([}\]])", r"\1", "".join(out))
    cleaned = re.sub(r"([{,]\s*)([A-Za-z_$][\w$-]*)(\s*:)", r'\1"\2"\3', cleaned)
    try:
        data = json.loads(cleaned)
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def _files(directory):
    try:
        return sorted(x for x in os.listdir(directory) if x.endswith(".json5"))
    except OSError:
        return None


def read_configs(skills_dir, scripts_dir):
    skills_rows = []
    scripts_rows = []
    ok = True
    for directory, rows in ((skills_dir, skills_rows), (scripts_dir, scripts_rows)):
        names = _files(directory)
        if names is None:
            ok = False
            continue
        for name in names:
            try:
                with open(os.path.join(directory, name), encoding="utf-8") as fh:
                    rows.append((name, _loads(fh.read())))
            except (OSError, UnicodeError):
                rows.append((name, None))
    return skills_rows, scripts_rows, ok


def _calls_of(data, section):
    if not isinstance(data, dict):
        return []
    calls = data.get(section)
    return calls if isinstance(calls, list) else []


def _args_count(rows):
    total = 0
    for _rel, data in rows:
        for section in ("conditions", "effects"):
            for call in _calls_of(data, section):
                if not isinstance(call, dict):
                    continue
                if call.get("type") in ARG_KEYS and isinstance(call.get("args"), dict):
                    total += 1
    return total


def check_calls(rows, section):
    problems = []
    for rel, data in rows:
        for i, call in enumerate(_calls_of(data, section)):
            if not isinstance(call, dict):
                continue
            typ = call.get("type")
            if not isinstance(typ, str) or typ not in ARG_KEYS:
                continue
            args = call.get("args")
            if not isinstance(args, dict):
                continue
            valid = ARG_KEYS[typ]
            for key in args:
                if isinstance(key, str) and key not in valid:
                    problems.append(
                        "%s 的 %s[%d] (%s) args 键 %s 非合法参数"
                        % (rel, section, i, typ, key)
                    )
    return sorted(problems)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--skills", default="src/main/resources/redtale/defaults/skills")
    ap.add_argument("--scripts", default="src/main/resources/redtale/defaults/scripts")
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    skills_rows, scripts_rows, ok = read_configs(args.skills, args.scripts)
    failures = []
    for kind, directory in (("skills", args.skills), ("scripts", args.scripts)):
        if _files(directory) is None:
            failures.append("[问题] %s 目录读取失败" % kind)
    if not ok and not failures:
        failures.append("[问题] 配置目录读取失败")

    problems = []
    for rows in (skills_rows, scripts_rows):
        problems.extend(check_calls(rows, "conditions"))
        problems.extend(check_calls(rows, "effects"))
    problems = sorted(problems)

    if args.v:
        count = _args_count(skills_rows) + _args_count(scripts_rows)
        print("已检查 %d 份配置 · %d 处带 args 的原语调用 · 16 个原语"
              % (len(skills_rows) + len(scripts_rows), count))
    lines = failures + ["[问题] " + p for p in problems]
    for line in lines:
        print(line)
    print("共 1 份原语参数键对账表 · %d 条问题" % len(lines))
    return 1 if lines else 0


if __name__ == "__main__":
    sys.exit(main())
