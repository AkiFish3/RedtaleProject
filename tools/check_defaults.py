"""
默认配置清单双向对账（T-0028）。

`ConfigStore.DEFAULT_FILES` 是白名单：放进 `resources/redtale/defaults/**` 但不进清单 → 默认配置静默不生成；
清单里写了但 jar 内没有该资源 → 启动 `ensureDefaults` 抛异常。本脚本把两个方向都对上。

只读：不改任何文件。无参数，路径相对仓库根。

退出码：0 = 全对齐；1 = 有问题（打完所有问题再退）；2 = 读不到源/目录。

用法：
    python tools/check_defaults.py; echo EXIT=$?
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = "src/main/java/com/redtale/project/core/config/ConfigStore.java"
DEFAULTS = "src/main/resources/redtale/defaults"
LITERAL = re.compile(r'"([A-Za-z0-9_./\-]+\.json5)"')


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def read_listed(path):
    """取 DEFAULT_FILES 块（到 `);` 为止）里的 *.json5 字面量，按出现顺序。"""
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()
    at = text.find("DEFAULT_FILES")
    if at >= 0:
        end = text.find("\n    );", at)
        text = text[at:] if end < 0 else text[at:end]
    return LITERAL.findall(text)


def read_disk(base):
    found = []
    for cur, _dirs, files in os.walk(base):
        for name in files:
            full = os.path.join(cur, name)
            found.append(os.path.relpath(full, base).replace(os.sep, "/"))
    return sorted(found)


def main():
    _utf8_stdout()
    os.chdir(ROOT)
    if not os.path.isfile(STORE):
        print("找不到 " + STORE)
        return 2
    if not os.path.isdir(DEFAULTS):
        print("找不到 " + DEFAULTS)
        return 2

    listed = read_listed(STORE)
    disk = read_disk(DEFAULTS)
    disk_set = set(disk)

    problems = []
    for name in sorted(set(listed) - disk_set):
        problems.append("[仅清单有] " + name)
    for name in sorted(disk_set - set(listed)):
        problems.append("[仅磁盘有] " + name)
    dup = sorted({n for n in listed if listed.count(n) > 1})
    for name in dup:
        problems.append("[清单重复] " + name)

    for line in problems:
        print(line)
    print("清单 %d 条 · 磁盘 %d 个 · 问题 %d 条" % (len(listed), len(disk), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
