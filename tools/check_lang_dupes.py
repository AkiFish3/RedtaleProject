"""单个 lang JSON 文件内部的重复键对账（只读、零接线）。"""
from __future__ import annotations

import argparse
import json
import os

LANGS = ("zh_cn", "en_us")
DEFAULT_DIR = "src/main/resources/assets/redtale_project/lang"


def read_dupes(path: str) -> tuple[list[tuple[str, int]], str | None]:
    """收集任一对象内出现次数 >1 的键；嵌套对象按局部 key 报，不拼路径。"""
    counts: dict[str, int] = {}

    def hook(pairs: list[tuple[str, object]]):
        local: dict[str, int] = {}
        for key, _ in pairs:
            local[key] = local.get(key, 0) + 1
        for key, n in local.items():
            if n > 1 and n > counts.get(key, 0):
                counts[key] = n
        return dict(pairs)

    try:
        with open(path, "r", encoding="utf-8") as fh:
            json.load(fh, object_pairs_hook=hook)
    except (OSError, json.JSONDecodeError):
        return ([], "读取失败")
    return (sorted(counts.items()), None)


def report(name: str, dupes: list[tuple[str, int]]) -> list[str]:
    """按输入序生成问题文案。"""
    return [f"{name} 重复键：{key}（出现 {n} 次）" for key, n in dupes]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", default=DEFAULT_DIR)
    ap.add_argument("-v", action="store_true")
    args = ap.parse_args()

    problems: list[str] = []
    totals: dict[str, int] = {}
    for name in LANGS:
        dupes, err = read_dupes(os.path.join(args.lang, name + ".json"))
        totals[name] = len(dupes)
        if err:
            problems.append(f"{name}.json 读取失败")
        problems.extend(report(name, dupes))

    if args.v:
        print(
            f"已扫 2 个语言文件 · zh_cn {totals['zh_cn']} 条重复 "
            f"· en_us {totals['en_us']} 条重复"
        )
    for msg in problems:
        print(f"[问题] {msg}")
    print(f"共 2 个语言文件 · {len(problems)} 条问题")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
