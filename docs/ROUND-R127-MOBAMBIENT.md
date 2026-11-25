# R127 · 2026-11-25 11:0x · 验收 T-0121（`flyer` 原型）+ 真机 7 只 + 派 T-0122（`ambient` 原型）

## 1. 卡板

| 卡 | 状态变化 | 证据 |
|---|---|---|
| T-0121 `mobs/gale_harrier.json5`（`flyer` 原型） | `[DONE]` → **`[VERIFIED]`** | 三条验收逐条全过，见 §2 |
| T-0122 `mobs/gloom_moth.json5`（`ambient` 原型） | 新派 `[OPEN]` | 池 C71 最后一格；派前全量预验，见 §4 |

在制品 `1/3`（`check_tasks.py`：`共 122 张卡 · 在制品 1 · 问题 0 · 提示 0`）。

## 2. T-0121 验收（3/3）

| # | 命令 | 实测末行 | 期望 | 判 |
|---|---|---|---|---|
| 1 | `check_mobs.py` | `共 9 个文件 · 0 条问题` | 同 | ✅ |
| 1 | `check_mob_skills.py -v` | `已检查 7 份怪物 · 4 条技能 id · 10 条 skills 引用` + `共 1 份怪物技能引用表 · 0 条问题` | 同 | ✅ |
| 2 | `check_equipment_slots.py --mobs … -v` | `已检查 7 份怪物 · 11 处自家基底装备引用 · 14 个基底` + `0 条问题` | 同 | ✅ |
| 2 | `check_mob_levels` / `_scales` / `_skill_chance` / `_skill_cooldowns` | 四把末行均 `· 0 条问题` | 0 问题 | ✅ |
| 2 | `check_defaults.py` | `清单 41 条 · 磁盘 41 个 · 问题 0 条` | 同 | ✅ |
| 3 | `check_all.py` | `共 67 个脚本 · 跑成 67 · 跳过 0 · 红 0` | 同 | ✅ |
| 3 | `check_ownership.py` | `共 1 份卡板 · 0 条问题` | 0 问题 | ✅ |
| 3 | id 行 | 恰好 1 行 `  id: "gale_harrier",`（第 5 行） | 1 行且 = 文件名 | ✅ |

**改动逐字核对**：`ConfigStore.java` diff 恰 `+2` 行（1 注释 + 1 路径，缩进 12 空格，位置在 `"mobs/iron_horn_charger.json5",` 之后）；`gale_harrier.json5` 688 B、与卡面逐字一致。S8 零偏差，不打回。

## 3. 构建 + 真机（7 只 / 7 原型）

**构建**（S7 取锁 `gradle build --offline`，`BUILD SUCCESSFUL in 37s`，锁已删）：
- `build/libs/redtale_project-0.1.0.jar` = **689534 B**（上轮 688855 B）。
- `unzip -l` → `redtale/defaults/mobs/` **7 份**（新增 `gale_harrier.json5` 688 B）。

**真机（测试服 1 启 1 停，正式服全程未触碰）**：

| 观察点 | 实测 |
|---|---|
| 启动 | `Done (2.178s)!` |
| 配置落地 | `config/redtale_project/mobs/gale_harrier.json5` 688 B @ 11:01；旧六份 mtime 未变（17 日 / 18 日 / 24 日 22:32 / 25 日 04:41） |
| `redtale mob list` | `怪物定义 7 只（原型 7 种）`，新增 `掠风隼 gale_harrier · 原型 flyer · 等级 6 · 装备 1 件 · 技能 1 条 · 体积 ×1.00` |
| `redtale mob check` | `7 只 / 技能引用 10 条 / 掉落表 1 张 · 引用完整 ✓` |
| `redtale reload` | `7 只怪物 … 45 份配置文件`（44→45） |
| ERROR | 启动前 4 条 = 他模 recipe 解析噪声（`simplyswords` / `brewinandchewin` / `minecraft:red_dye`），与 R126 逐字相同；**`Done` 之后零 ERROR** |
| 停服 | `stop` 后 25567 / 25577 无 LISTEN；正式服 PID 9376 仍占 25565/25575 未动 |

## 4. 派 T-0122（`ambient` 原型，8/8 最后一格）

- **口径**：`ambient` 纯氛围样本 —— 无技能、无装备、无 `drops`，只做「血量低 + 体积小」的装饰怪（R126 自定，非范围决策）。
- **取值**：`gloom_moth` / 幽尘蛾 · `level 2` · `scale 0.5` · `max_health { base: 6, per_item_level: 1 }` · `movement_speed 0.02`。属性键均已在 `AttributeSpecs` 注册。
- **独占文件**：新 JSON5 + `ConfigStore.java` 恰好 **2 行**（1 注释 + 1 路径，插在 `"mobs/gale_harrier.json5",` 之后）。正文行数与代码块行数**逐字一致**（R126-a 自检）。
- **派前全量预验**（完整 `defaults/` 副本 + 本文件，跑完即删）：
  - `check_mobs` → `共 10 个文件 · 0 条问题`
  - `check_mob_skills -v` → `8 份怪物 · 4 条技能 id · 10 条引用` + `0 条问题`（**引用条数不变**，本怪零技能）
  - `check_equipment_slots -v` → `8 份怪物 · 11 处引用 · 14 个基底` + `0 条问题`（**引用条数不变**，本怪零装备）
  - 四把值域尺 → 均 `· 0 条问题`
- 卡面明令**不取构建锁、不 build**（R126-d），构建留 S7 下轮一次性做。

## 5. 口径（§13.130）

| 编号 | 口径 |
|---|---|
| R127-a | **内容卡验收 = 三条命令 + 逐字 diff 双证**：命令全绿只证明「配置能解析」，`ConfigStore.java` 的 `+2` 行与 JSON5 字节数必须与卡面逐字对账，否则 S8 可能多改一行而检查仍绿。 |
| R127-b | **真机看「新增那条」不看总数**：`mob list` 总数 7 只本身不构成证据，必须核新增行三列（等级 / 装备件数 / 体积）与卡面 JSON5 一致（R126-c 延伸）。 |
| R127-c | **旧 mtime 未变是配置落地的第二证据**：新文件出现 + 旧文件 mtime 不动，才能排除「默认配置覆盖了玩家改过的配置」。 |
| R127-d | **零技能 / 零装备卡要冻结「引用条数不变」**：`ambient` 的验收写死 `10 条 skills 引用` / `11 处装备引用` 不变，避免 S8 顺手给氛围怪加技能却仍全绿。 |
| R127-e | **裸名纪律第四十五次**：本轮文档只写裸名（`check_mobs.py` / `ConfigStore.java` / `gloom_moth.json5`），不写 `tools/` 全路径、不写探针目录。 |

## 6. 环境

- `src/`：本轮合流 S8 的 1 份新 JSON5 + `ConfigStore.java` 2 行（归 S7）。
- 构建 1 次（37s，锁取后即删）；测试服 1 启 1 停；正式服未启停、未改任何文件。
- 七门禁全 0：`check_all` 67/67、`check_tasks` 122 卡在制品 1、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership`、`check_checkall`。

## 7. 下一步

1. 验 T-0122（过后 `check_defaults` 42/42、构建 + 真机 8 只 8 原型）→ M6 内容面「8 原型 8 怪」收口。
2. 收口后转 M7：专业 / 技能树真机验证，或 lang 覆盖缺口（`check_lang` 现 14 键面）。
3. Q-021 / Q-022 / Q-023 无答复继续按默认走；真人窗口 A~F 等 Aki。
