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
    "check_damage_whitelist.py",   # 伤害白名单安全网：damage.json5 multiplier.damage_types[] 形态 —— R91 接上
    "check_tree_reach.py",         # 技能树可达性安全网：nodes[*].requires 图可达性 —— R92 接上
    "check_enchant_exclusive.py",  # 附魔互斥引用安全网：exclusive_set 引用形态 —— R93 接上
    "check_gem_types.py",          # 宝石孔位安全网：gem.type ↔ sockets.types 开放词汇可达性 —— R94 接上
    "check_enchant_args_sync.py",  # 附魔参数同步安全网：scripted.args ↔ 脚本 effects[].args 数值 —— R95 接上
    "check_affix_reach.py",        # 词条可达安全网：item_level + kind ↔ 池段槽位 —— R96 接上
    "check_set_reach.py",          # 套装档位可达安全网：bonuses[].count ↔ pieces 覆盖装备槽数 —— R97 接上
    "check_ability_refs.py",       # 物品技能引用安全网：items abilities[] ↔ skills 完整字符串 id —— R98 接上
    "check_mob_skills.py",         # 怪物技能引用安全网：mobs skills[].skill ↔ skills 完整字符串 id —— R99 接上
    "check_affix_pool_refs.py",    # 词条池引用安全网：池三段键 ↔ 词条定义完整字符串 id —— R100 接上
    "check_repair_active.py",      # 修理有效性安全网：repair.mode ↔ amount/percent 激活字段 —— R101 接上
    "check_category_types.py",     # 伤害类别安全网：category_attributes[].type 完整形态 —— R102 接上
    "check_equipment_slots.py",    # 装备槽位安全网：mobs equipment[slot] ↔ 基底声明槽位 —— R103 接上
    "check_arg_keys.py",           # 原语参数键安全网：skills/scripts args 键 ↔ 原语签名 —— R104 接上
    "check_use_args.py",           # 消耗品脚本参数键安全网：items use.script[] args ↔ 原语签名 —— R106 接上
    "check_mob_levels.py",         # 怪物等级安全网：mobs level 1..1000 整数（bool 不算）—— R107 接上
    "check_mob_scales.py",         # 怪物体积安全网：mobs scale [0.25,4.0] 有限数字（bool 不算）—— R108 接上
    "check_drop_rolls.py",         # 掉落掷骰安全网：drops[].rolls 显式值 ∈ [1,16] 整数 —— R109 接上
    "check_mob_skill_chance.py",   # 怪物技能概率安全网：skills[].chance 显式值 ∈ [0,1] —— R110 接上
    "check_mob_skill_cooldowns.py",  # 怪物技能冷却安全网：skills[].cooldown_ticks ∈ [1,1000000] 整数 —— R111 接上
    "check_drop_values.py",          # 掉落数值安全网：xp / min / max 绝对边界 —— R112 接上
    "check_tree_ppl.py",             # 技能树点数安全网：points_per_level 非负整数形态 —— R113 接上
    "check_profession_base.py",      # 专业起点经验安全网：curve.base 零值/硬顶静默改写 —— R114 接上
    "check_item_durability.py",      # 物品耐久安全网：durability 小数截断 / int 硬顶静默改写 —— R115 接上
    "check_use_ints.py",             # 消耗品整数安全网：use 四处整数小数截断静默改写 —— R116 接上
    "check_affix_weights.py",        # 词条权重安全网：定义/池段权重小数 (int) 静默截断 —— R117 接上
    "check_affix_levels.py",         # 词条等级安全网：item_level.min/max 小数 (int) 静默截断 —— R118 接上
    "check_tree_icons.py",           # 技能树图标安全网：icon 非字符串/空白静默回落 —— R119 接上
    "check_damage_aoe_args.py",      # 范围伤害参数安全网：radius/falloff/max_targets 静默改写 —— R120 接上
    "check_socket_fallbacks.py",     # 孔位回落安全网：sockets.types / gem.type / exclusive_group 静默回落 —— R121 接上
    "check_name_coercions.py",       # 名称安全网：items/tree/node name 非字符串静默改写 —— R122 接上
    "check_socket_discards.py",      # 孔位丢弃安全网：count=0/缺键但 types 非空整段早退 —— R123 接上
    "check_spawn_int_saturation.py", # 刷怪整数安全网：priority/per_chunk_cap 超 int 静默饱和 —— R124 接上
    "check_progress_base.py",        # 进度曲线安全网：levels/jobs curve.base 超硬顶静默夹顶 —— R129 接上
    "check_enchant_definition_ints.py",  # 附魔定义整数字段安全网：weight/anvil_cost 非整数形态静默透传 —— R130 接上
    "check_enchant_cost_forms.py",   # 附魔代价字段安全网：min_cost/max_cost 形态静默透传 —— R131 接上
    "check_enchant_slot_forms.py",   # 附魔槽位字段安全网：slots 形态静默透传 —— R133 接上
    "check_item_models.py",          # 资源面安全网：基底 ↔ 模型 ↔ 原版贴图三方对账 —— R136 接上
    "check_weapon_attr_ref.py",      # 引用面安全网：weapon_attributes 外部引用 ↔ BetterCombat 内置动作 —— R137 接上
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
