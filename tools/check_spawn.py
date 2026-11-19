"""spawn.json5 主配置起飞前对账（T-0067）：只读单文件，退出码 0=干净 1=有问题。"""
import argparse, json, math, os, sys
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_FILE = "src/main/resources/redtale/defaults/spawn.json5"
ALLOWED = {"replace_enabled", "per_tick_cap"}
CAP_MSG = "per_tick_cap 必须是 1~4096 的整数（写 0 不会关闭，会回落 8）"

def check_spawn(data: dict) -> list[str]:
    """返回问题消息列表（不含文件名与前缀）；检查项 1~5。"""
    if not isinstance(data, dict):
        return ["根节点必须是对象"]
    problems = []
    if "replace_enabled" in data and not isinstance(data["replace_enabled"], bool):
        problems.append("replace_enabled 必须是布尔值")
    if "per_tick_cap" in data:
        v = data["per_tick_cap"]
        if isinstance(v, bool) or not isinstance(v, (int, float)):
            problems.append("per_tick_cap 必须是数字")
        elif not (1 <= v <= 4096 and math.isfinite(v) and float(v).is_integer()):
            problems.append(CAP_MSG)
    for key in data:
        if key not in ALLOWED:
            problems.append("未知键 " + str(key))
    return problems

def main() -> int:
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    ap = argparse.ArgumentParser(description="spawn.json5 主配置对账")
    ap.add_argument("--file", default=DEFAULT_FILE)
    ap.add_argument("-v", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    try:
        with open(os.path.abspath(a.file), "r", encoding="utf-8", errors="replace") as f:
            data = json.loads(clean_json5(f.read()))
    except Exception:
        problems = ["解析失败"]
    else:
        problems = check_spawn(data)
    if a.v:
        print("已检查 " + a.file + " · 7 项")
    for msg in problems:
        print("[问题] " + a.file + " · " + msg)
    print("共 1 个文件 · %d 条问题" % len(problems))
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main())
