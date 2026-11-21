# R92 · 2026-11-21 08:5x · 验 T-0087 + check_all 35→36 + 派 T-0088（附魔 exclusive_set 引用形态对账）

## 1. 验收 T-0087 `check_tree_reach.py`（3/3 + S7 边界 18/18）→ VERIFIED

- 验收1 基线：`python tools/check_tree_reach.py` → `共 1 份技能树可达性表 · 0 条问题` EXIT=0；`-v` → `已检查 1 棵树 · 10 个节点 · 0 个不可达`。
- 验收2 六夹具（`build/treereach/`，每组干净目录）逐字：A 二元环 a↔b=2 · B 三元环=3 · C root→a→b=0 · D a↔b+依赖 c=3 · E 自环=0（过滤为根） · F 非数组/不存在节点=0。EXIT=1/1/0/1/0/0 全对。
- 验收3 静态：wc=165≤200、`def unreachable`=1、禁用 grep=0、`py_compile` OK、`--trees NO_SUCH` 恰好 1 条 `skill_trees 目录读取失败` EXIT=1 不抛栈。夹具已清。
- S7 独立边界 18/18：`read_nodes` 七形态（None/空/nodes 非 dict/保序过滤/node 非 dict）＋ `unreachable` 八形态（空/全根/单链/diamond/纯环/依赖环/升序/混合）＋ `read_trees` 读失败 ＋ `_loads` 单双引号。
- 默认根回落经 `find` 证实：`src/main/resources/redtale/defaults/skill_trees/warrior_vanguard.json5` 是唯一树、顶层无 `skill_trees`，S8 加的回落逻辑生效。备注采纳不打回。

## 2. 接线 `check_all` 35→36

- `tools/check_all.py` TOOLS 末尾追加 `check_tree_reach.py`（R92）。接线前 `check_checkall` 已抓到「脚本未接进 check_all」1 条，接线后归零。
- 结果：`共 36 个脚本 · 跑成 36 · 跳过 0 · 红 0`。全套门禁：check_checkall / check_tasks（88 卡在制品 1）/ check_contracts（52 条）/ check_docs（107 份 443 引用）/ check_rounds / check_ownership 全 0 问题。

## 3. 派 T-0088 `check_enchant_exclusive.py`（单文件零接线，在制品 1/3）

- 真洞定位（`check_enchants` 与 `check_damage_*` 都不核此字段）：
  - 配置 `enchantments/soul_reap.json5:14` = `#redtale_project:exclusive_set/lifesteal`、`super_sharpness.json5:14` = `#minecraft:exclusive_set/damage`。
  - `EnchantmentGenerator.java:40` 把 `exclusive_set` 列入 `PASS_THROUGH`，`convert()` 原样透传，`numericKey()` 只校验数值键，不验引用形态（类注释明写不做 schema 校验）。
  - `EnchantableTags.java:141-168` 逐条扫 `exclusive_set`：`:141-148` 取字符串、非 `#` 开头 continue；`:154-155` 非自家 ns 直接 continue（**写歪命名空间正是从这里静默漏过**）；`:161-166` 自家 tag 缺文件 → 补空 tag + 一条 WARN。
  - 后果：引用 `redtale_projce` 之类的错 ns 时，生成器照写、自家逻辑跳过、原版按不存在 tag 当空集 → 本该互斥的附魔可共存，无红无 ERROR，一次性 WARN 也不会出现（因为非自家 ns 根本不进那段逻辑）。
- 基线 S7 离线核判定逻辑：2 条引用 / 0 问题（`#redtale_project:` 与 `#minecraft:` 均在白名单）。
- 接口 `VALID_NS` / `read_configs` / `check_ref` / `main` 由 S7 定死，六夹具 A~F，零接线（S7 下轮接 check_all 36→37）。

## 4. S7 最小一步 = 派前全类排除留证（R88-c/R89-c/R90-c/R91-b 延续）

派前逐条排除（Explore 只读全量扫描 37 份 json5 + 相关 Java + 36 个 check 交叉核）：

| 候选 | 结论 |
|---|---|
| `mobs.skills[].skill` 引用 | `check_mobs.py:88-95` 已核命中 skills/ → 排除 |
| 技能树 `cost`/`effects`/`unlock_skill`/`classes`/`max_points` | `check_trees.py:75-124` + `check_tree_reach.py` 已核；`passive.id` 只核非空但 `TreeUnlocks.passives()` 全仓无调用者（死代码，无玩法可退化）→ 排除 |
| `items` affix kind / 权重 / 池内引用 | `check_items.py:93-120` 已核；kind 运行期 WARN+跳过（R86-b 反例）→ 排除 |
| `professions.grants` 引用 | `check_professions.py:85-101` 核键格式与值域；引用全是原版 block/item id 与 tag，离线无注册表 → 排除 |
| `damage.json5` 其它字段 | `check_numeric.py:78-138` + `check_damage_whitelist.py` + `check_damage_types.py` 全覆盖 → 排除 |
| attribute 表重复 id | `check_attrs.py:69-75,128-129` 已双向查重 → 排除 |
| `repair.targets[]` | 真洞（解析期只验形态、运行期 `Repairer.matches()` 对拼错 id 恒 false → `TARGET_MISMATCH` 无日志），但默认件 `targets: []` 空、基线 0 引用 → 按 R91-b 留池，等内容铺开再派 |
| `exclusive_set` | 透传字段、基线 2 引用、写歪静默 → **本轮落卡** |

## 5. 环境 / 收工

- 本轮 `src/` 零 diff、纯 Python；未构建、未启停任何服、无构建锁；正式服 25565/25575（PID 9376）常驻未动、测试服 25567/25577 无 LISTEN。
- 卡板 88 张 / 在制品 1；check_all 36/36；check_docs 107 份 443 引用 0 问题。
- 留下：`docs/ROUND-R92-ENCHEXCLUSIVE.md`、`PLAN.md` §13 R92 + §13.95、`docs/TASKS.md`（T-0087 VERIFIED + T-0088 + 池 C38）、`docs/ROUNDS.md`、`tools/check_all.py`、协作板 §3。
