"""display.json5 起飞前对账（T-0069）：只静态检查单文件，退出码 0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_FILE = os.path.join(ROOT, "src/main/resources/redtale/defaults/display.json5")
KEYS = ("show_dps", "show_attribute_breakdown", "assume_target")


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def _loads(text):
    """单引号字面量归一化后交 clean_json5（沿用 check_scripts / check_enchants 既有口径）。"""
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


def _valid_id(value):
    """assume_target：去空白后含 :、无空白、无大写、两段都非空。"""
    if not isinstance(value, str):
        return False
    t = value.strip()
    if not t or ":" not in t:
        return False
    if any(c.isspace() or c.isupper() for c in t):
        return False
    ns, _, path = t.partition(":")
    return bool(ns) and bool(path)


def check_display(data: dict) -> list[str]:
    """按检查项顺序返回问题消息（不含文件名与前缀）。"""
    issues = []
    if not isinstance(data, dict):
        issues.append("根节点必须是对象")
        return issues
    for key in ("show_dps", "show_attribute_breakdown"):
        if key in data and not isinstance(data[key], bool):
            issues.append(key + " 必须是布尔值")
    if "assume_target" in data and data["assume_target"] is not None:
        v = data["assume_target"]
        blank = isinstance(v, str) and not v.strip()  # 空串/纯空白按 DisplaySpec#isBlank 回落
        if not blank and not _valid_id(v):
            issues.append("assume_target 不是合法 id")
    for key in data:
        if key not in KEYS:
            issues.append("未知键 " + str(key))
    return issues


def main() -> int:
    _utf8_stdout()
    ap = argparse.ArgumentParser(description="display.json5 起飞前对账")
    ap.add_argument("--file", default=DEFAULT_FILE)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()
    path = args.file
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            data = _loads(f.read())
    except Exception:
        issues = ["解析失败"]
    else:
        issues = check_display(data)
    if args.v:
        print("已检查 %s · 6 项" % path)
    for msg in issues:
        print("[问题] %s · %s" % (path, msg))
    print("共 1 个文件 · %d 条问题" % len(issues))
    return 1 if issues else 0


if __name__ == "__main__":
    sys.exit(main())
