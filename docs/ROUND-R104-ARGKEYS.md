# R104 · 验收 T-0099 + 接线 48 + 不派卡（派前全类排除留证）

时间：2026-11-22 10:1x · 队列：S7·移植工程（兼 S8 派单/验收）

## 验收 T-0099（`check_arg_keys.py`）

`[DONE]` → `[VERIFIED]`，3/3 + S7 独立六夹具 6/6：

| 项 | 命令 | 结果 |
|---|---|---|
| 基线 | `python check_arg_keys.py` | `共 1 份原语参数键对账表 · 0 条问题` EXIT=0 |
| -v | 同上 `-v` | `已检查 6 份配置 · 13 处带 args 的原语调用 · 16 个原语` |
| 夹具 A~F | 每次清空夹具目录、两目录并存、独立复跑 | EXIT=1/0/1/1/0/1，逐条文案逐字对上：A `raito` / B 0 条 / C `txt` / D `ammount` / E `per_levelx` / F `fallof` 恰好 1 条且 -v `已检查 1 份配置 · 2 处带 args 的原语调用 · 16 个原语` |
| 静态 | `wc -l`=170 ≤200 / `def check_calls`=1 / 禁用 grep=0 / py_compile OK / `--skills NO_SUCH --scripts NO_SUCH` 恰好 2 条目录读取失败 EXIT=1 不抛栈 | 全过 |

S7 探针首轮漏建空的对侧目录，多出 2 条「目录读取失败」——按 R65-a 修正探针（两目录都建）后 6/6 全过，**不打回 S8**（S8 自测与本轮复跑零偏差）。夹具已清。

S8 口径请裁两项，均采纳不打回：

1. 卡面写「六夹具各自含两目录各一份」但 F 期望 `-v` 的 n=1；接口写明 n = 两目录文件数之和，同时给两份则 n=2。裁定：**接口公式为准，F 只在 skills 侧放一份取证**；这不是实现偏差，是卡面夹具描述与 n 公式的自相矛盾（R79-a 家族第八次）。
2. `_loads` 在单引号 / `//` 归一之外补裸键加引号。真实配置形如 `id: 'greeting'`，不补则基线读成 0 调用；属解析 shim 的必要部分，采纳（R90-b 同类）。

接线 `check_all` 47→**48**，总入口跑成 48 红 0；七门禁全 0：check_checkall 1 清单、check_tasks 99 卡在制品 0、check_ownership 1 卡/2 声明、check_contracts 64 条、check_docs 119 份/502 引用、check_rounds、checkall 自身。

## 本轮不派卡（WIP 0，但无合格真洞）

按 R99-b / R92-b「不造永远绿的尺子」：本轮在制品 0 < 3，但 Explore 只读全量扫 49 个 check（48 检查 + 总入口）/ 36 份默认配置 / 全部 Java，四个候选逐条不满足硬判据，**不派**：

| 候选 | 静默与基线 | 不派理由 |
|---|---|---|
| professions `grants.<src>.<key>` | `ProfessionSpec.java:65-78` 裸串精确比对，写歪回落 default/0 零 WARN；配置注释自认「写错不会报错」，基线 11 条 | 键是原版物品/方块 id 与 tag，离线无真值（`check_professions.py:85-101` 只能核形态）；与已排除的 `spawn_rules.match` 同因 |
| skill_trees `exclusive_with` | `SkillTreeSpec.java:22-25` 登记进 ROOT_KEYS/NODE_KEYS 免告警，`parse():29-83` 从不读取，写任何值静默吞 | 默认配置 0 处引用，基线空 → 造尺永远绿 |
| `gem/affix.exclusive_group` | `Socketer.java:97-100` / `AffixRoller.java:111,149` 有 `EXCLUSIVE_CONFLICT` 状态且命令面专测 | 设计行为，非静默失效；自由字符串无冻结集 |
| `items.set_id` ↔ `sets.pieces` 双向 | 单边漏配确实漏计数 | 留池旧项，出口明确（`/redtale validate`）已复评 |

其余复评全部排除：drops（warnOnce+兜底）、repair.targets（空基线）、consume / repair.mode（clamp+WARN）、`%var`（源码注释写明有意保留）、附魔 slots/tag（原版 `EquipmentSlot` 无冻结集）、passive.id（死代码）、points_per_level（卡面太薄）、check_progress 219 行拆卡（跨文件重构 + 需要取舍，S7 独占）。对账网仍第 30 例。

## S7 最小一步：内容面覆盖盘点（只盘点，不改配置）

| 面 | 注册表/源码事实 | 默认配置现状 | 结论 |
|---|---|---|---|
| 基底物品 | `ItemBase.java:16-37` 14 个基底 | `items/` 7 份（demo 剑/甲/袍/汤/磨刀石 + 2 宝石） | 14 基底已注册，示例内容覆盖 7 个 id，属示范量 |
| 属性 | `AttributeSpecs.java:46` `EXPECTED_COUNT = 80`，`:152-153` 启动自检；`check_attrs` 3 份来源 0 问题 | 属性表固定 80（64 实用 + 16 预留） | 已封冻，零 diff |
| 实体原型 | `MobSpec.java:19-20` 8 原型（melee/ranged/caster/summoner/charger/flyer/elite/ambient）；`RedtaleEntities.java:40-52` 8 个 EntityType | `mobs/` 2 份（ember_guard / ash_caster） | 6 个原型暂无默认怪，属内容缺口而非代码缺口；补哪 6 只需内容设计，S7 独占，不在本轮动手 |
| 技能/脚本 | `SKILLS.md` 定 25 原语、实注册 13（Q-022） | 4 技能 / 2 脚本 | 按 Q-022 默认「按需补」，不补原语 |

未改任何 `src/` 或默认配置；下一轮若仍无真洞，按 PLAN §13 推进内容面时先由 S7 定死 6 只新怪的完整字段再考虑派单。

## 口径（§13.107 R104-a~e）

- a：**卡面夹具描述与接口公式冲突时，接口公式优先**（S8 按公式取证不判偏差），本轮 F 的 n=1 成立；R79-a 家族第八次。
- b：**S7 探针自身写错按 R65-a 修探针，不打回 S8**——六夹具首轮多报目录读取失败即此类。
- c：**WIP < 3 不是必须派卡的充分条件**；全类排除后无「两把旧尺全绿 + 运行期静默 + 合法集可冻结 + 基线非空」四满足候选时，按 R92-b/R99-b 不派，写清留证。
- d：**原版注册表 id/tag 无离线真值的候选一律排除**（professions grants 与 spawn match 同因），不能靠抄一份静态名单造尺。
- e：**裸名纪律第二十二次**：本轮在建/新文档一律裸名 `check_arg_keys.py`，不写全路径。

## 环境

`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服端口无 LISTEN。
