# R91 · 验 T-0086 + check_all 34→35 + 派 T-0087（技能树 requires 可达性对账）

时间：2026-11-21 07:4x~07:5x（S7 移植工程队列）

## 1. 卡板

| 卡 | 动作 | 证据 |
|---|---|---|
| T-0086 `check_damage_whitelist.py` | `[DONE]` → **VERIFIED** | 验收 3/3，见 §2 |
| T-0087 `check_tree_reach.py` | 新派 `[OPEN]` | 独占文件为新建的 `check_tree_reach.py`（≤200 行），六夹具 A~F，基线 1 棵树/10 节点/0 不可达 |

在制品：1/3（T-0087）。未拆卡、未打回、无 BOUNCED。

## 2. T-0086 验收证据

1. 基线：`python tools/check_damage_whitelist.py` → `共 1 份伤害白名单 · 0 条问题` EXIT=0；`-v` → `已检查 1 条白名单 · 4 个注册类型`。
2. 六夹具（独立干净目录 `build/dmgwlf/`，用完即删）：A `redtale_project:skill_ghost` → 1 条「未注册伤害类型」；B `redtale_project:*` → 0；C `minecraft:player_attack` → 0；D `redtale_projce:*` → 1 条「未知命名空间通配」；E `redtale_project:skill_magical*` → 1 条「非法通配形态」；F 混合 → 0。逐字 EXIT=1/0/0/1/1/0。
3. 静态：`wc -l`=129≤200、`grep -c "def check_whitelist"`=1、禁用 grep=0、`py_compile` OK、`--cfg NO_SUCH` → 1 条 `damage.json5 读取失败` EXIT=1 不抛栈。

S8 无口径备注、零偏差，不打回。接线 `tools/check_all.py` TOOLS 34→**35**，全绿。

## 3. 本轮 S7 最小一步：派前全类排除

按 R84-b/R86-b/R88-c/R89-c/R90-c 逐类核实，只有 `skill_trees[*].nodes[*].requires` 构成真洞：

| 候选 | 结论 | 理由 |
|---|---|---|
| `spawn_rules[*].match[]` | 留池 | 形态可核但离线无实体注册表，只能判「带冒号」；与既有引用检查同局限 |
| `items[*].repair.targets[]` | 留池 | 默认件 `targets: []`；内容铺开后可与 items id 交叉核（R91-b） |
| `use.effects` / buff `args.effect` | 排除 | 原版 MobEffect id，运行期有 WARN，离线无注册表 |
| `professions.grants` tag/方块 id | 排除 | `check_professions.py` 已覆盖形态 + 离线无注册表 |
| `attack_speed.effect_attack_speed` | 排除 | 原版 effect id，离线无注册表 |
| `weapon_attributes` | 排除 | `/redtale validate` 已有离线同款校验 |
| conditions args / effects 数值 | 排除 | 已三例覆盖 / 运行期有 clamp |

实证：`tools/check_trees.py:68-74` 只核 requires 是数组、元素存在且 `!=name`；`src/main/java/com/redtale/project/core/progression/TreePoints.java:49-82` 的 `missingPrereqs/canTakeWithCap` 要求前置全在 `selected`；`SkillCommand` 只回「技能未解锁」。环 → 节点永不解锁 → `unlock_skill` 永久锁死，零 WARN。离线跑固定点判定：`warrior_vanguard.json5` 10 节点 / 0 不可达。

## 4. 环境

- `src/` 零 diff、纯 Python 轮次；未取构建锁、未构建、未启停任何服。
- 正式服 9376 常驻未动；测试服 25567/25577 无 LISTEN。
- 全绿：`check_all` 35/35、`check_checkall` 35/35、`check_tasks` 87 卡在制品 1、`check_contracts` 51 条、`check_docs`、`check_rounds` 0 问题。

## 5. 口径

见 `PLAN.md` §13.94 R91-a~c：①对账网第十八例 = 图结构可达性；②排除留证不等于永久否定候选；③派单当轮文档在建脚本写裸名。
