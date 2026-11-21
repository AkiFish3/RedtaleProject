# R102 · 验 T-0097 + 接线 check_all 45→46 + 派 T-0098（怪物装备槽位 ↔ 基底槽位）

时间：2026-11-22 07:2x · 队列：S7（派单 + 验收） / S8（执行）

## 1. 验收 T-0097（`check_category_types.py`）

| 项 | 结果 |
|---|---|
| 验收 1 基线 | `共 1 份伤害类别类型表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 1 份伤害配置 · 2 处类别 type 引用` |
| 验收 2 六夹具 | A=0 · B=`skill_magical` · C=`minecraft:skill_magical` · D=`skill_magicalx` · E=`redtale_project:*` · F=1 条且 `-v` 2 处；逐字 EXIT=0/1/1/1/1/1（独立干净目录六轮） |
| 验收 3 静态 | wc=83≤200、`def unmatched`=1、禁用 grep=0（归一/前缀/Java/接线均无）、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条 `damage.json5 读取失败` EXIT=1 不抛栈 |

S7 附加函数边界 4 形态（`strip` / 空串跳过 / 非字符串跳过 / 非 dict 元素跳过）通过；S8 自测与 S7 复跑零偏差，不打回。卡 `T-0097` 改 `[VERIFIED]`，`tools/check_all.py` 追加一行后总入口 45→46。

## 2. 本轮 S7 最小一步：全类排除 + 源码逐字核实 + 反事实

派卡前用只读调研扫全部 45 个 check、默认配置与 Java 消费端，六个候选逐条排除：

| 候选 | 排除理由 |
|---|---|
| 附魔 `effects[].script` ↔ 脚本 id | `check_enchants` 与 `check_enchant_args_sync` 都会红；运行期另有 `LOG.warn` |
| 技能树 `unlock_skill` 完整 id | `SkillTreeRegistry` 有 WARN，且只有一把尺覆盖 |
| 技能树 `operation` 枚举 | `check_trees` 已对非法值判红（反向存在假阳性，不是静默洞） |
| `crit` 的 `chance_attribute` / `power_attribute` | `check_numeric` 已按 80 条属性表判红 |
| `exclusive_group` | 开放词汇，两份独立标签相等、无冻结常量集可比对 |
| 附魔 `min_cost` / `max_cost` | 透传原版，离线无法证明无 clamp（R86-b） |
| `points_per_level` 静默回落 | 单字段、基线 1 处，卡面太薄（R96-c 复评结论不变） |

**落点**：`mobs/*.json5` 的 `equipment[slot]` 与基底声明槽位不一致。

- 源码逐字：`ItemBase` 明写 14 基底的 `EquipmentSlotGroup`（剑/匕首/斧/锤/矛/法杖/弓/戒指/护符 → MAINHAND、盾 → OFFHAND、四件护甲 → HEAD/CHEST/LEGS/FEET）；`ItemFactory` 把属性 modifier 注册到 `content.base().slot()` 的槽位组；`MobSpawner.applyEquipment` 只按配置槽名 `setItemSlot` + 物品 id 解析，`worn++` 与生成消息照记、零 WARN。
- 两把旧尺：`check_mobs.check_mob` 只核 equipment 值带冒号；`check_mob_equipment.check_equipment` 只核槽名 ∈ 6 槽与 path ∈ 14 基底 —— `head: "redtale_project:sword"` 两者全绿。
- 反事实验证（R99-e）：临时把默认 `ember_guard` 的 `chest: "redtale_project:chestplate"` 改成 `redtale_project:ring`，`check_mobs` 与 `check_mob_equipment` 均 EXIT=0、其余 43 把内容尺也全绿；随后已还原，默认配置零 diff。
- 基线离线核：2 份怪物 / 4 处自家基底引用 / 14 基底 / 0 问题。

派单 `T-0098`：单文件 `check_equipment_slots.py`、零接线、六夹具 A~F、`NS`/`SLOT_OF`/`SLOTS`/`read_mobs`/`mismatches`/`main` 接口由 S7 定死，S8 不读源码；在制品 1/3。独占文件在 `VERIFIED` 前 S7 不改。

## 3. 口径（写入 PLAN §13.105）

- **R102-a**：对账网第 29 例 = 「物品基底 ↔ 目标槽位」语义一致性。前 28 例核引用/值域/形态/图，本例核「两边都合法但组合起来不生效」；判据仍是 R83-b「配置层全绿、运行期静默退化」。
- **R102-b**：**合法 id ≠ 合法组合**。旧尺分别核槽名与物品 id，都绿；只有冻结 `SLOT_OF` 后才能判组合。跨语言常量仍由 S7 从源码取数写进卡面（R96-b 延续），S8 不推断、不读源码。
- **R102-c**：**反事实必须覆盖「两把旧尺仍绿」**（R99-e 落地）：临时改一处真配置、逐把跑旧尺 + 总入口，再还原；若旧尺已红则洞不存在，不派卡。
- **R102-d**：**留池候选复评结论可以不派**：本轮调研的七类候选中，有 WARN/已有尺/开放词汇/基线过薄者全部留池，不硬凑永远绿的红线（R92-b/R99-b 延续）。
- **R102-e**：**裸名纪律第二十次**：本轮在建脚本在台账与文档里只写裸名 `check_equipment_slots.py`，夹具目录只写占位名，不写全路径，避免 `check_docs` 把未建文件当断链。

## 4. 环境与收尾

- `src/` 零 diff（反事实探针已还原）；未构建、未启停任何服、无构建锁；正式服 25565/25575（PID 9376）常驻未动，测试服 25567/25577 无 LISTEN。
- 七门禁：`check_all` 46/46、`check_tasks`（98 卡 / 在制品 1）、`check_contracts`（63 条）、`check_docs`、`check_rounds`、`check_checkall`、`check_ownership` 全 0 问题。
- 收尾：`PLAN.md` §13 R102 + §13.105、`docs/ROUNDS.md`、`docs/TASKS.md`（T-0097 VERIFIED + T-0098 OPEN + 池 C48）、`docs/CONTRACTS.md` §4.61/§4.62、`tools/check_all.py`、协作板 §3。
- 下一步（R103）：验 T-0098（过后 `check_all` 46→47）；无新真洞则转内容面（14 基底 / 80 属性 / 8 实体默认配置、README 与真机材料）；Q-021 / Q-022 无人答、真人 A~G 等 Aki。
