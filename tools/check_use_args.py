"""离线对账：消耗品 use.script 调用的 args 具名键必须属于该原语合法键集。"""

import argparse
import json
import os
import re
import sys

# S7 冻结的 16 个原语合法 args 键集（脚本内不含源码文件名）
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
    """自带 JSON5 shim：行注释 / 单引号 / 裸键 / 尾逗号；失败或顶层非 dict 返 None。"""
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


def read_items(dir_path):
    """扫目录下 *.json5（文件名升序）；目录不可读返 ([], False)，单文件坏则 data=None 继续。"""
    try:
        names = sorted(x for x in os.listdir(dir_path) if x.endswith(".json5"))
    except OSError:
        return [], False
    rows = []
    for name in names:
        try:
            with open(os.path.join(dir_path, name), encoding="utf-8") as fh:
                rows.append((name, _loads(fh.read())))
        except (OSError, UnicodeError):
            rows.append((name, None))
    return rows, True


def _call_type(call):
    """先取非空字符串 effect，否则非空字符串 type，两者都无返 None。"""
    value = call.get("effect")
    if not isinstance(value, str) or not value:
        value = call.get("type")
    if not isinstance(value, str) or not value:
        return None
    return value


def _script_of(data):
    if not isinstance(data, dict):
        return []
    use = data.get("use")
    if not isinstance(use, dict):
        return []
    script = use.get("script")
    return script if isinstance(script, list) else []


def check_calls(rows):
    """产 '<文件>: use.script[<i>] (<type>) args 键 <k> 非合法参数'，按文案升序不去重。"""
    problems = []
    for rel, data in rows:
        for i, call in enumerate(_script_of(data)):
            if not isinstance(call, dict):
                continue
            typ = _call_type(call)
            if typ not in ARG_KEYS:
                continue
            args = call.get("args")
            if not isinstance(args, dict):
                continue
            valid = ARG_KEYS[typ]
            for key in args:
                if isinstance(key, str) and key not in valid:
                    problems.append(
                        "%s: use.script[%d] (%s) args 键 %s 非合法参数"
                        % (rel, i, typ, key)
                    )
    return sorted(problems)


def _args_calls(rows):
    total = 0
    for _rel, data in rows:
        for call in _script_of(data):
            if not isinstance(call, dict):
                continue
            if _call_type(call) in ARG_KEYS and isinstance(call.get("args"), dict):
                total += 1
    return total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--items", default="src/main/resources/redtale/defaults/items")
    ap.add_argument("-v", action="store_true")
    opts = ap.parse_args()
    rows, ok = read_items(opts.items)
    if not ok:
        print("[问题] items目录读取失败")
        print("共 1 份消耗品脚本参数键对账表 · 1 条问题")
        return 1
    problems = check_calls(rows)
    if opts.v:
        print("已检查 %d 份物品 · %d 条带 args 的 use.script 调用 · %d 个原语"
              % (len(rows), _args_calls(rows), len(ARG_KEYS)))
    lines = ["[问题] " + p for p in problems]
    for line in lines:
        print(line)
    print("共 1 份消耗品脚本参数键对账表 · %d 条问题" % len(lines))
    return 1 if lines else 0


if __name__ == "__main__":
    sys.exit(main())
