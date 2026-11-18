"""附魔配置起飞前对账（T-0044）：只读，不写盘、不生成 datapack。退出码 0=干净 1=有问题。"""
import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def _loads(text):
    """单引号字面量转双引号后交 clean_json5 —— 与运行期共用同一份清洗口径。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(text[i:j]); i = j; continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(text[i:j]); i = j; continue
        if c == "'":
            j, buf = i + 1, []
            while j < n and text[j] != "'":
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j + 1] if text[j + 1] == "'" else text[j:j + 2])
                    j += 2; continue
                buf.append('\\"' if text[j] == '"' else text[j]); j += 1
            out.append('"' + "".join(buf) + '"'); i = min(j + 1, n); continue
        out.append(c); i += 1
    return json.loads(clean_json5("".join(out)))


def load_script_ids(root: str) -> set[str]:
    """读 root/scripts/*.json5 的 id 字段，返回集合。"""
    folder = os.path.join(root, "scripts")
    ids = set()
    if not os.path.isdir(folder):
        return ids
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = _loads(raw)
        except Exception:
            continue
        if isinstance(data, dict) and isinstance(data.get("id"), str) and data["id"].strip():
            ids.add(data["id"].strip())
    return ids


def collect(root: str) -> list:
    """[(相对路径, data|None, 解析错误字符串)]，文件名升序。"""
    folder = os.path.join(root, "enchantments")
    files = []
    if not os.path.isdir(folder):
        return files
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json5"):
            continue
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        rel = "enchantments/" + name
        with open(full, "r", encoding="utf-8", errors="replace") as f:
            raw = f.read()
        try:
            data = _loads(raw)
        except Exception as ex:
            files.append((rel, None, type(ex).__name__))
            continue
        if not isinstance(data, dict):
            files.append((rel, None, "非对象顶层"))
            continue
        files.append((rel, data, None))
    return files


def _script_refs(node, out):
    if isinstance(node, dict):
        if node.get("type") == "redtale_project:scripted":
            out.append(node.get("script"))
        for value in node.values():
            _script_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            _script_refs(value, out)


def check_file(data: dict, script_ids: set[str]) -> list[str]:
    """返回该文件的问题文案（空 list = 干净）。"""
    probs = []
    ident = data.get("id")
    if not isinstance(ident, str) or not ident.strip() or ":" not in ident:
        probs.append("id 缺失或不含命名空间")
    ml = data.get("max_level")
    if isinstance(ml, bool) or not isinstance(ml, int) or ml < 1 or ml > 255:
        probs.append("max_level 必须是 1~255 的整数")
    si = data.get("supported_items")
    if not isinstance(si, str) or not si.strip():
        probs.append("supported_items 缺失")
    desc = data.get("description")
    if not isinstance(desc, str) or not desc.strip():
        probs.append("description 缺失（没有中文名）")
    refs = []
    _script_refs(data.get("effects"), refs)
    names = sorted(script_ids)
    tail = "（现有 %d 个：%s）" % (len(names), ", ".join(names))
    for script in refs:
        if not isinstance(script, str):
            probs.append("scripted 效果缺少 script 字段")
        elif script not in script_ids:
            probs.append("effects 引用的脚本 " + script + " 不存在" + tail)
    return probs


def main() -> int:
    _utf8_stdout()
    parser = argparse.ArgumentParser(description="附魔配置对账")
    parser.add_argument("--dir", default="src/main/resources/redtale/defaults")
    parser.add_argument("--datapack", default="")
    args = parser.parse_args()
    os.chdir(ROOT)

    root = os.path.abspath(args.dir)
    if not os.path.isdir(os.path.join(root, "enchantments")):
        print("没有 enchantments/ 目录（跳过）")
        return 0

    script_ids = load_script_ids(root)
    files = collect(root)
    problems, hints, seen, names = [], [], {}, {}
    for rel, data, err in files:
        if data is None:
            problems.append((rel, "JSON5 解析失败：" + err))
            continue
        for msg in check_file(data, script_ids):
            problems.append((rel, msg))
        ident = data.get("id")
        if isinstance(ident, str) and ":" in ident:
            key = ident.strip()
            if key in seen:
                problems.append((rel, "id " + key + " 与 " + seen[key] + " 重复"))
            else:
                seen[key] = rel
                name = key.split(":", 1)[1]
                if name:
                    names.setdefault(name, rel)

    if args.datapack:
        dp = os.path.join(os.path.abspath(args.datapack), "enchantment")
        if os.path.isdir(dp):
            disk = sorted(f[:-5] for f in os.listdir(dp)
                          if f.endswith(".json") and os.path.isfile(os.path.join(dp, f)))
            for name in sorted(set(names) - set(disk)):
                problems.append((names[name], "未生成：" + name + ".json"))
            for name in sorted(set(disk) - set(names)):
                hints.append(("enchantment/" + name + ".json", "仅磁盘有：" + name + ".json"))

    for rel, msg in problems:
        print("[问题] " + rel + ": " + msg)
    for rel, msg in hints:
        print("[提示] " + rel + ": " + msg)
    if problems:
        print("共 %d 个文件 · %d 条问题" % (len(files), len(problems)))
        return 1
    print("共 %d 个文件 · 没有发现问题 ✓" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
