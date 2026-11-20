# R88 · 2026-11-21 04:2x · 验 T-0083 + check_all 31→32 + 派 T-0084（怪物装备 id 对账）

## 验收 T-0083 `tools/check_effect_target.py` → VERIFIED

- 3/3：①基线 `共 2 份效果 target 值域表 · 0 条问题` EXIT=0、`-v` 含 `已检查 2 份脚本 · 4 份技能 · 5 个 target 原语`；②六夹具各自干净目录 A `taret`·B `CAST`·C `3`·D `true` 逐字各 1 条 `effects[0].args.target 必须是 caster 或 target` EXIT=1、E `stun:target`·F `damage:whatever` 各 0 条 EXIT=0；③wc=129≤200、`def check_effects`=1、禁用 grep=0、`py_compile` OK、双 NO_SUCH 恰好 2 条 `目录读取失败` EXIT=1 不抛栈。
- S7 独立边界 11/11：None / effects 非数组 / 缺失合法 / effect 非 dict / args 非 dict / args 缺失合法 / 多 effect 下标 / 未知 type 跳过 / None target / 单引号 shim / 行注释剥离。S8 自测数字零偏差、不打回（R87 结转）。
- 接线 `check_all` 31→32 全绿（`共 32 个脚本 · 跑成 32 · 跳过 0 · 红 0`）。`build/effectfix/` 已清。

## 派 T-0084 `check_mob_equipment.py` —— 怪物装备物品 id ↔ 14 基底对账（对账网第十五例）

- 真洞：`check_mobs.check_mob` 只核 `equipment[slot]` 是带冒号的 id、从不核 id 真能解析；运行期 `MobSpawner.applyEquipment` 对未知 id 只 `skips.add("装备 X 的物品 id 未知：")`，而 `Result.skips()` 全仓零读取（`MobSpawnCommand` 只看 `message()`、`SpawnReplacer` 只在 `!ok` 时 warnOnce）→ 怪物光着手出生、无红无 WARN、全 check 绿。
- 形态：跨配置引用（同 T-0079 damage_type），红tale 命名空间 path 必须 ∈ 14 基底（`ItemBase` enum 写死），外部命名空间跳过。基线离线核 2 份怪 / 4 处引用 / 0 问题。
- 接口 `BASE_IDS` / `SLOTS` / `read_mobs` / `check_equipment` / `main` 由 S7 定死；单文件 ≤200 行、零接线、六夹具、验收 ≤3 条。在制品 1/3。

## S7 自身最小一步 = 派前全类排除（R84-b/R86-b 延续）

按判据扫同批候选逐条排除，不硬凑：
- `dash.args.direction`（只认 look/back、拼错回退 look）：配置里**零引用**，派了是永远绿的红线（R84-b）→ 不派。
- `damage_aoe` 的 amount/radius/falloff/max_targets：运行期全有 `clamp01`/`Math.min`/`Math.max`（R85-d）→ 不派。
- `curve.mode`：拼错有 `warn(...)` 且 `check_progress` 已核回退 + 对应键 → 非静默、已覆盖 → 不派。
- `cooldown_ticks`：check_scripts/check_skills 已核值域、运行期 clamp → 不派。
- affix/set 的 stats 属性 id：`check_items._stats` 已核 80 条表 → 不派。
- drops 的 item/chance/min/max：`check_mobs.check_drop` 已核 → 不派。
- 技能树 attribute/requires/skill：`check_trees` 已核 → 不派。
- 附魔 `%var.xxx%` 占位符：设计上原样保留（console_log 可见）→ 非纯静默，暂不派。
- **落点**：mobs `equipment[slot]` 物品 id —— 唯一「两份 check 都绿 + 运行期失败无出口」的真洞。

## 新口径 §13.91 R88-a~c

- a) 对账网第十五例 = 怪物装备引用，形态从字符串枚举扩回跨配置引用。
- b) 「收集了失败但没人读」也是静默失败——判据看玩法是否悄悄退化，不看代码里有没有记；有夹但结果没出口同样算洞。
- c) 派前全类排除留证，避免对账网无限递归；找不出真洞才转内容面。

## 环境

- 本轮 `src/` 零 diff、未构建、未启停任何服、无构建锁（纯 Python 轮次）。正式服 9376 常驻未动；测试服 25567/25577 无 LISTEN。
- 全套 0 问题：`check_all` 32/32、`check_docs`、`check_tasks` 84 卡在制品 1、`check_ownership` 1 卡/2 声明、`check_contracts`、`check_rounds`。

## 收尾

`docs/ROUND-R88-MOBEQUIP.md`、`PLAN.md` §13 R88 主表 + §13.91、`docs/ROUNDS.md`、`docs/CONTRACTS.md` §4.48、`docs/TASKS.md`（T-0083 VERIFIED + T-0084 OPEN + 池 C34）、`tools/check_all.py`、协作板 §3。

## 下轮

验 T-0084（过后 check_all 32→33）；对账网已十五例，按 R88-a 判据核实后无真洞转内容面；Q-021/022 无人答；真人 A~G 等 Aki。
