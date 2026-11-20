"""伤害类型引用 ↔ datapack 注册对账（T-0079）：只读，退出码 0=干净 1=有问题。"""
from __future__ import annotations

import argparse
import json
import os
import sys

from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DATA = "src/main/resources/data"
DEFAULT_CFG = "src/main/resources/redtale/defaults"
DAMAGE_EFFECTS = {"damage", "damage_aoe"}
_bad_files: list[str] = []


def load_errors() -> list[str]:
    """单份注册 JSON 解析失败时的坏文件相对路径快照（按路径升序）。"""
    return sorted(_bad_files)


def _loads(text: str):
    """单引号字面量归一化后交 clean_json5（沿用 check_display 的既有口径）。"""
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


def read_registered(data_dir: str) -> set[str]:
    """取 redtale_project/damage_type 下已注册裸 id；恒含 generic；坏文件另记 load_errors。"""
    out = {"generic"}
    folder = os.path.join(data_dir, "redtale_project", "damage_type")
    try:
        names = sorted(n for n in os.listdir(folder) if n.endswith(".json"))
    except OSError:
        return out
    for name in names:
        full = os.path.join(folder, name)
        if not os.path.isfile(full):
            continue
        try:
            with open(full, "r", encoding="utf-8", errors="replace") as fh:
                json.load(fh)
        except Exception:
            _bad_files.append("redtale_project/damage_type/" + name)
            continue
        out.add(name[:-5])
    return out


def _bare(value) -> str | None:
    """只认 redtale_project: 命名空间或裸名；其它命名空间跳过。"""
    if not isinstance(value, str) or not value.strip():
        return None
    if ":" not in value:
        return value
    ns, _, rest = value.partition(":")
    return rest if ns == "redtale_project" and rest else None


def _parse(full: str):
    try:
        with open(full, "r", encoding="utf-8", errors="replace") as fh:
            return _loads(fh.read())
    except Exception:
        return None


def read_refs(cfg_dir: str) -> list[tuple[str, str]]:
    """扫 skills/scripts 的 damage 原语与 damage.json5 的 category_attributes。"""
    refs: list[tuple[str, str]] = []
    for sub in ("skills", "scripts"):
        folder = os.path.join(cfg_dir, sub)
        try:
            names = sorted(os.listdir(folder))
        except OSError:
            continue
        for name in names:
            if not name.endswith(".json5"):
                continue
            full = os.path.join(folder, name)
            if not os.path.isfile(full):
                continue
            data = _parse(full)
            effects = data.get("effects") if isinstance(data, dict) else None
            if not isinstance(effects, list):
                continue
            for eff in effects:
                if not isinstance(eff, dict) or eff.get("type") not in DAMAGE_EFFECTS:
                    continue
                args = eff.get("args")
                bare = _bare(args.get("damage_type")) if isinstance(args, dict) else None
                if bare is not None:
                    refs.append((sub + "/" + name, bare))
    data = _parse(os.path.join(cfg_dir, "damage.json5"))
    mult = data.get("multiplier") if isinstance(data, dict) else None
    cats = mult.get("category_attributes") if isinstance(mult, dict) else None
    if isinstance(cats, list):
        for item in cats:
            bare = _bare(item.get("type")) if isinstance(item, dict) else None
            if bare is not None:
                refs.append(("damage.json5", bare))
    return sorted(refs)


def check_refs(refs: list[tuple[str, str]], registered: set[str]) -> list[str]:
    """逐条查裸 id 是否注册；同 (路径, id) 只报首次。"""
    seen, out = set(), []
    for path, bare in sorted(refs):
        if (path, bare) in seen:
            continue
        seen.add((path, bare))
        if bare not in registered:
            out.append(f"{path} 引用未注册的伤害类型 {bare}")
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="伤害类型引用对账")
    ap.add_argument("--data", default=DEFAULT_DATA)
    ap.add_argument("--cfg", default=DEFAULT_CFG)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    if not os.path.isdir(a.data) or not os.path.isdir(a.cfg):
        print("[问题] 伤害类型注册表读取失败")
        print("共 1 份伤害类型表 · 1 条问题")
        return 1
    _bad_files.clear()
    registered = read_registered(a.data)
    refs = read_refs(a.cfg)
    problems = ["伤害类型注册表读取失败 · " + rel for rel in load_errors()]
    problems += check_refs(refs, registered)
    if a.v:
        print(f"已检查 {len(refs)} 处引用 · {len(registered)} 个注册类型")
    for msg in problems:
        print("[问题] " + msg)
    print(f"共 1 份伤害类型表 · {len(problems)} 条问题")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
