# R97 · 2026-11-22 · 验 T-0092 + 接线 40→41 + 派 T-0093（物品绑定技能完整 id 引用）

## 验收（T-0092，3/3 PASS）

| 项 | 期望 | 实测 |
|---|---|---|
| 基线 | `共 1 份套装档位可达性表 · 0 条问题` EXIT=0 | 逐字一致 EXIT=0 |
| `-v` | `已检查 1 份套装 · 2 件引用 · 2 个档位 · 2 个可达装备槽` | 逐字一致 |
| 六夹具 A~F | EXIT=1/0/1/0/0/1 | 逐字一致（A 2 件档·可达 1；C 3 件档·可达 2；F 2 套/4 件/3 档/3 槽，a 的 2 件档 1 条） |
| 静态 | wc≤200、`def unreachable`=1、禁用 grep=0、py_compile OK、`--dir NO_SUCH` 1 条读失败 | 173 / 1 / 0 / OK / 1 条 EXIT=1 |

六夹具独立干净目录，探针 `build/setreachfix/` 已清。S8 零偏差，不打回。

## 派单（T-0093，在制品 1/3）

`check_ability_refs.py`：items 的 `abilities[]` ↔ skills 完整字符串 id。接口 S7 定死：`read_ids` / `read_items` / `unmatched` / `main`；**禁止** `path_of` / `split(":")` 取后段（这正是洞本身）。基线 7 物品 / 4 技能 id / 1 引用 / 0 问题；六夹具 A~F；单文件零接线，VERIFIED 后 R98 接 `check_all`（应 `跑成 42`）。

真洞证据链：
- `check_items.check_item:71-74` 是唯一核 abilities 的尺子，用 `path_of()`（冒号后段）比对；`ids_of()` 同样归一时收技能 id → 写歪 ns（`redtale_projectX:gale_slash`）归一后照样命中，全绿。
- `ItemContent.parseAbilities:178-204` 只校验「数组/字符串/非空/含冒号/不重复」，从不校验技能是否存在。
- `SkillRegistry.get()` 按完整字符串 key 取；`SkillEngine.cast` 未命中返 `NO_SKILL`；`ContentItem.useAbility:99-101` 打一次性 WARN 后 `pass`，物品还能丢能存。
- 默认配置注释明写「技能 id 不存在不报错（施放时日志 WARN）」，但无机器尺子 → 对账网第二十四例，首次核「命名空间段本身」。

派前全类排除：mobs `skills[].skill`（同源但运行期无自动 WARN、怪不放技能更隐晦，留下一张卡）、items `set_id` 命名空间（接近已验 T-0092 套装档位、重叠风险高，留池）、`operation`/属性 id/`skill_trees.classes`/`unlock_skill`（check_trees/check_numeric/check_skills 已覆盖，Java 侧本就按 path 比对与 check 一致）、`spawn_rules.match`/`repair.targets`（留池）。

## 本轮其他

- 接线 `check_all` 40→41；全套门禁 0 问题（check_all 41/41、check_checkall/tasks/contracts/docs/rounds/ownership 全绿；check_tasks 93 卡在制品 1、check_contracts 57 条、check_docs 112 份/471 引用）。
- `src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服 9376 常驻未动、测试服 25567/25577 无 LISTEN。
- 裸名纪律第十五次：派单当轮文档与台账里在建脚本一律裸名 `check_ability_refs.py`。
