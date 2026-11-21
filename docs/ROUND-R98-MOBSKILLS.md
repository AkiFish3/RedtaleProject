# R98 · 2026-11-22 02:3x~02:4x · 验 T-0093 + check_all 41→42 + 派 T-0094（怪物绑定技能完整 id 引用对账）

## 1. 验收 T-0093 `check_ability_refs.py` → VERIFIED

卡面三条验收全部机器跑过，S7 独立复跑与 S8 自测逐字一致、零偏差不打回。

| # | 命令 / 判据 | 结果 |
|---|---|---|
| 1 | `python tools/check_ability_refs.py` | 末行 `共 1 份物品技能引用表 · 0 条问题` EXIT=0 |
| 1 | `python tools/check_ability_refs.py -v` | `已检查 7 份物品 · 4 条技能 id · 1 条 abilities 引用` EXIT=0 |
| 2 | 六夹具 A~F（干净目录重来） | EXIT=1/0/1/0/0/1；A 写歪 ns / C `minecraft:` / F q 写歪各 1 条；B/D/E 0 条；D `-v` 1/2/2、F `-v` 2/2/2 逐字 |
| 3 | 静态 + 边界 | wc=110≤200；`def unmatched`=1；禁用 grep=0（`path_of`/`split(":")` 均未出现）；`py_compile` OK；`--dir NO_SUCH` 恰好 1 条 `items/skills 目录读取失败` EXIT=1 不抛栈 |

六夹具探针 `build/abilityrefsfix/` 与自建 pyc 已清。卡板状态 `[DONE]` → `[VERIFIED]`。

## 2. 接线 check_all 41→42

`tools/check_all.py` 的 `TOOLS` 末尾追加：

```python
"check_ability_refs.py",       # 物品技能引用安全网：items abilities[] ↔ skills 完整字符串 id —— R98 接上
```

跑 `python tools/check_all.py`：`共 42 个脚本 · 跑成 42 · 跳过 0 · 红 0`。

七门禁全 0：check_checkall / check_tasks（94 卡在制品 1）/ check_contracts / check_docs / check_rounds / check_ownership。

## 3. 派单 T-0094 `check_mob_skills.py`（怪物绑定技能完整 id 引用对账）

在制品 1/3。接口由 S7 定死，`read_ids` / `read_mobs` / `unmatched` / `main` 四个签名逐字写进卡面；禁止 `path_of` / `split(":")` 取后段比对（洞本身）。基线 2 怪 / 4 技能 id / 3 条 skills 引用 / 0 问题。六夹具 A~F + 三条机器验收命令全部写进卡面。

## 4. 本轮 S7 最小一步：派前全类排除留证 + 离线核基线

Explore 只读全量扫 tools check + defaults json5 + Java，逐条排除后落真洞：

| 候选 | 判定 | 依据 |
|---|---|---|
| items abilities | 排除 | T-0093 刚用完整字符串尺子堵住 |
| mobs equipment | 排除 | T-0084 `check_mob_equipment.py` + 运行期 `MobCheckCommand` 双覆盖 |
| mobs drops | 留池 | 离线同源（`check_mobs.py:96-98` 也 `path_of`），但运行期 `MobDrops:117-120` 有 `warnOnce` + 原版兜底，静默形态弱 |
| items set_id | 留池 | 同源（离线归一 / 运行期完整 key），但 `/redtale validate` 可手工发现 |
| spawn_rules.match | 排除（不同源） | 比对值来自 `BuiltInRegistries.ENTITY_TYPE`，原版 registry 非本项目配置表 |
| repair.targets | 排除 | 基线空 + 三写法 union 无单一比较对 |
| **mobs skills[].skill** | **真洞，派卡** | 离线两边 `path_of` 归一全绿；运行期完整 key 取不到 → 零日志静默重试、怪照常出生 |

真洞证据链（逐字代码 + 行号见 Explore 原始输出，摘要）：

- `tools/check_mobs.py:88-95` 核 skills：`if path_of(sk) not in skill_ids`，而 `ids_of:34` 收技能表 id 时也 `path_of` → 写歪 ns / 裸名全命中。
- `src/main/java/com/redtale/project/mobs/MobSpec.java:198-207` `skill()`：只校验非空字符串，不查冒号、不校验存在。
- `.../core/skill/SkillRegistry.java:50-57` `get(id)`：`byId.get(id)` 完整字符串 key。
- `.../core/skill/SkillEngine.java:158-166` `castMob()`：`def == null` → `NO_SKILL`，**零 LOG**。
- `.../mobs/MobSkillCaster.java:82-84,102-121`：`NO_SKILL` 只进 detail 字符串、`tick` 只取 `.cast()`、detail 被丢弃，每 10 tick 静默重试。
- `.../mobs/MobSpawner.java:57-92` `spawn()`：不读 `def.skills()`，怪照常出生。
- 唯一能发现的运行期手段是手工 `/redtale mob check`（`MobCheckCommand.java:62-68` 用完整字符串 `has()`），不进离线 check 链。

离线核基线逐字：`mobs/ash_caster.json5:25-26` 2 条 + `ember_guard.json5:25` 1 条 = 3 条，全部 `redtale_project:` 全名；技能表 4 个 id（`arc_bolt`/`ember_burst`/`gale_slash`/`quake_slam`）；当前 `check_mobs.py` 与 `check_ability_refs.py` 全绿——全绿正是洞的表现。

## 5. 环境 / 收尾

- `src/` 零 diff；纯 Python 轮次；未取构建锁、未 build、未启停任何服；正式服 9376 常驻未动、测试服 25567/25577 无 LISTEN。
- 踩坑预防：派单当轮所有文档与台账在建脚本一律裸名 `check_mob_skills.py`（R63-e 后第十六次）。
- 口径沉淀：PLAN §13.101（R98-a~e）。契约 §4.58。派单池 C44。
