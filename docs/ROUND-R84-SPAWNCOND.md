# R84 · 验 T-0079 + 接线 check_all 27→28 + 派 T-0080（刷怪规则 conditions 六维值域对账）

## 卡板

- **T-0079 `check_damage_types.py` `[DONE]` → VERIFIED**（3/3 + S7 独立边界 7/7）：
  1. 基线 `python check_damage_types.py` → `共 1 份伤害类型表 · 0 条问题` EXIT=0；`-v` → `已检查 4 处引用 · 4 个注册类型`（skills 2 + damage.json5 category 2；注册 generic + 3 datapack）。
  2. 三夹具各自干净副本：A `skills/x.json5` 引用 `skill_ghost` 未注册 → EXIT=1 且恰好 1 条 `skills/x.json5 引用未注册的伤害类型 skill_ghost`；B data 加 `skill_ghost.json` → EXIT=0；C 引用改 `minecraft:generic` → EXIT=0（原版命名空间跳过）。逐字对上。
  3. `wc`=177≤200、`def check_refs`=1、禁用 grep=0、`py_compile` OK；`--data NO_SUCH` / `--cfg NO_SUCH` 均 EXIT=1 且恰好 1 条 `伤害类型注册表读取失败`、`末行 1 条问题`、不抛栈。
  - S7 独立边界 7/7（首跑 1 FAIL 是我探针期望写错——传 `ghost` 却断言 `skill_ghost`，去重逻辑 len=1 本就对，按 R65-a 改期望复测 7/7）：缺目录返 `{generic}` / 坏 JSON 跳过进 `load_errors()` / 重复引用只报首次 / `damage_aoe` 生效 / `minecraft:` 与非 damage 原语跳过 / 排序稳定。
  - **裁定 S8 提问**：卡面只写「坏文件另走 `load_errors`（见下）」未给函数形态与文案，S8 补 `load_errors() -> list[str]` 暴露坏文件相对路径 + CLI 打 `伤害类型注册表读取失败 · <rel>`——符合验收判据、不打回（R79-a/R80-a 机器判据优先的第四次生效）。

- **派 T-0080 `check_spawn_conditions.py`**（新建 ≤200 行，零接线，在制品 1/3）：核 `spawn_rules/*.json5` 的 `conditions` 七维（world/biome/light/time/y/outside/difficulty）形态与值域。接口 `read_rules` / `check_conditions` / `main` 由 S7 定死；六组夹具 A~F 逐字；基线 1 份规则 / 0 问题。

## 自己做了什么

- **接线** `tools/check_all.py` TOOLS 末尾追加 `check_damage_types.py`（R84 接上）→ `check_all` **27→28 全绿**：`共 28 个脚本 · 跑成 28 · 跳过 0 · 红 0`。接线后 `check_checkall` `共 1 份清单 · 0 条问题`、`check_tasks` `共 79 张卡 · 在制品 0 · 问题 0`。
- **派卡前对覆盖面的核实（ evidence）**：`check_skills`（原语 type）、`check_numeric`（damage 属性 id）、`check_enchants`（附魔→脚本）、`check_mobs`（mob base/skill/drops/属性/装备/spawn_rules 顶层）、`check_trees`（unlock_skill/职业/属性）、`check_lang`（孤儿键）、`check_damage_types`（damage_type 引用）——**`spawn_rules[].conditions` 七维无人核**：`check_mobs.check_rule` 停在顶层 id/action/mob/chance/per_chunk_cap/priority，从不进 conditions。写歪值（`time:"nite"` / `light.max:99` / `outside:1`）运行期按 SPAWN-RULES.md §5「求值失败=该维度不通过且不告警刷屏」，编译绿、全 check 绿、刷怪替换静默失效——这就是 T-0080 的依据。

## 环境 / 卡点

- 本轮 `src/` 零 diff（验收/接线/派卡/文档均不碰 Java）→ 未构建、未启停任何服、无构建锁；25567/25577/9376 无 LISTEN。
- 对账网已十一例，本轮核实覆盖面后确认：内容面（技能/数值取舍）属 S7 独占、原版 id 拼写离线无注册表可查，均不硬凑成 S8 卡；唯一干净真洞 = spawn conditions 值域，已派。
- Q-021（`respec` 免费）/ Q-022（原语按需补，13/25）无答复，继续按默认走；真人窗口 A~G 等 Aki。

## 收尾

- `docs/ROUND-R84-SPAWNCOND.md`（本文件）、`PLAN.md` §13 R84 + §13.87、`docs/ROUNDS.md`、`docs/TASKS.md`（T-0079 VERIFIED + T-0080 + 池 C29 结项/C30）、`tools/check_all.py`、协作板 §3。
