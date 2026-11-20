"""起飞前对账总入口（R59）：依次跑全部 `check_*.py`，只汇总末行与退出码。

为什么需要它：十个对账脚本各自只认自己那段配置，运营上线前要记十条命令、
还得逐条看退出码。这里做成**一个入口一件事**——跑完给一张表，谁红了看谁。
本脚本**不重实现任何检查**，只做进程编排：子进程的末行原样转述，不解析、不判断。

用法：
    python tools/check_all.py               # 全跑
    python tools/check_all.py --only items  # 只跑名字含 items 的
    python tools/check_all.py -v            # 失败时把子进程完整输出打出来

退出码：0 = 全绿（含「脚本未实现，跳过」）；1 = 至少一个红；2 = 一个都没跑成。
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# 顺序即台账习惯的顺序：先地基（配置清单），再内容面，最后运营面。
TOOLS = (
    "check_defaults.py",   # 默认配置清单双向对账
    "check_progress.py",   # 等级 / 职业
    "check_items.py",      # 物品 / 词条 / 词条池 / 套装
    "check_mobs.py",       # 怪物 / 掉落 / 刷怪规则
    "check_enchants.py",   # 附魔 → 脚本
    "check_skills.py",     # 技能原语
    "check_trees.py",      # 技能树
    "check_scripts.py",    # 脚本层
    "check_lang.py",       # lang 键覆盖（T-0055 落地后自动接上）
    "check_deps.py",       # 依赖面 / 命名面（D6 / D11 / D12）—— R60 接上
    "check_attrs.py",      # 80 条属性表三向对账（docs ↔ java ↔ lang）—— R61 接上
    "check_numeric.py",    # 数值面四份：attack_speed / crit / damage / progression —— R62 接上
    "check_contracts.py",  # 契约文档 docs/CONTRACTS.md ↔ 源码单向漂移 —— R63 接上
    "check_docs.py",       # 文档面：仓库内路径引用存在性 + ROUND 孤儿提示 —— R64 接上
    "check_tasks.py",      # 卡板自检：状态 / 重复 id / 在制品 / 验收段 / 台账漂移 —— R66 接上
    "check_professions.py",  # 专业面：professions/*.json5 起飞前对账 —— R67 接上
    "check_spawn.py",       # 刷怪全局配置：spawn.json5 主配置（per_tick_cap）—— R72 接上
    "check_display.py",     # 显示口径：display.json5 安全网 —— R74 接上
    "check_checkall.py",    # 清单安全网：TOOLS ↔ 磁盘 check_*.py 双向对账 —— R75 接上
    "check_commands.py",    # 命令树安全网：*Command.java ↔ RedtaleCommand 挂载引用 —— R76 接上
    "check_primitives.py",  # 原语注册安全网：builtin 类 + 内联 record ↔ Builtins 注册 —— R77 接上
    "check_gen_prefix.py",  # 生成器前缀安全网：gen_*.py ↔ OWNED_PREFIX/KEY_PREFIX —— R78 接上
    "check_rounds.py",      # 轮次索引安全网：ROUNDS.md ↔ 磁盘 ROUND-*.md —— R79 接上
    "check_primitive_ids.py",  # 原语 id 安全网：Effect id() ↔ SKILLS.md 表 —— R80 接上
    "check_lang_dupes.py",     # lang 内部安全网：单份 JSON 重复键 —— R81 接上
    "check_ownership.py",      # 卡板安全网：WIP 卡独占文件重叠 —— R82 接上
    "check_event_registration.py",  # 事件总线安全网：@SubscribeEvent ↔ EVENT_BUS.register —— R83 接上
    "check_damage_types.py",       # 伤害类型安全网：配置 damage_type 引用 ↔ datapack 注册 —— R84 接上
    "check_spawn_conditions.py",   # 刷怪条件安全网：spawn_rules conditions 七维值域 —— R85 接上
    "check_skill_args.py",         # 技能条件值域安全网：conditions args [0,1] —— R86 接上
    "check_script_args.py",        # 脚本条件值域安全网：conditions args [0,1] —— R87 接上
    "check_effect_target.py",      # 效果 target 枚举安全网：effects[].args.target ∈ {caster,target} —— R88 接上
    "check_mob_equipment.py",      # 怪物装备 id 安全网：mobs equipment[slot] ↔ 14 基底 —— R89 接上
    "check_use_script.py",         # 消耗品脚本原语安全网：items use.script[].effect ↔ 27 原语 —— R90 接上
)


def run(script: Path, verbose: bool) -> tuple[str, int, str]:
    """跑一个对账脚本；返回 (末行, 退出码, 完整输出)。异常也算一条红，不中断整轮。"""
    try:
        proc = subprocess.run(
            [sys.executable, str(script)],
            cwd=str(ROOT.parent), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=180,
        )
    except Exception as ex:  # 超时 / 起不来
        return ("起不来：%s" % ex, 1, "")
    out = (proc.stdout or "").rstrip()
    last = out.splitlines()[-1] if out else "（无输出）"
    if proc.returncode != 0 and proc.stderr.strip() and verbose:
        out = out + "\n" + proc.stderr.rstrip()
    return (last, proc.returncode, out)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    only = ""
    verbose = "-v" in sys.argv
    for i, a in enumerate(sys.argv):
        if a == "--only" and i + 1 < len(sys.argv):
            only = sys.argv[i + 1]

    picked = [n for n in TOOLS if only in n]
    if not picked:
        print("没有匹配的脚本：--only %s" % only)
        return 2

    rows, details, skipped, failed, ran = [], [], 0, 0, 0
    for name in picked:
        script = ROOT / name
        if not script.is_file():
            rows.append((name, "（未实现，跳过）", "-"))
            skipped += 1
            continue
        last, code, out = run(script, verbose)
        rows.append((name, last, "OK" if code == 0 else "红 %d" % code))
        if code != 0:
            failed += 1
            details.append((name, out))
        ran += 1

    width = max(len(r[0]) for r in rows)
    for name, last, verdict in rows:
        print("%-*s  %-6s  %s" % (width, name, verdict, last))
    for name, out in details:
        print("\n--- %s 完整输出 ---" % name)
        print(out)
    print("\n共 %d 个脚本 · 跑成 %d · 跳过 %d · 红 %d" % (len(picked), ran, skipped, failed))

    if ran == 0:
        return 2
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
