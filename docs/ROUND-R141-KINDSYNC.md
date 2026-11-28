# R141 · 验 T-0135（词条池段名 ↔ `kind` 尺）+ 接线 `check_all` 74→75 + 派 T-0136（武器线三基底内容）

- 日期：2026-11-28 13:0x
- 里程碑：M3（宝石/套装/词条）防线收口；M2 武器线内容面推进
- 本轮派/验：**验 T-0135 → VERIFIED**；**派 T-0136**（`axe` / `spear` / `staff` 三把武器）

## 1. 验收 T-0135（3/3，S8 零偏差不打回）

S8 于 07:38 交付 `tools/check_affix_kind_sync.py`（123 行，单文件零接线）。

| 验收 | 命令 | 结果 |
|---|---|---|
| 1 基线 | `python tools/check_affix_kind_sync.py` | `共 1 份词条段类型对账表 · 0 条问题`，EXIT 0 |
| 1 基线 -v | 加 `-v` | `已检查 2 份词条池 · 12 条词条定义 · 12 条词条引用` |
| 2 六夹具 A | 干净副本 | `0 条问题` EXIT 0 |
| 2 六夹具 B | `armor_common.prefix` 只放 `vital` | 1 条 `词条池 armor_common 的 prefix 段放了 suffix 类词条 vital`，EXIT 1 |
| 2 六夹具 C | `suffix` 只放 `plated` | 1 条 `… suffix 段放了 prefix 类词条 plated`，EXIT 1 |
| 2 六夹具 D | `sword_common.core` 加 `sturdy: 80` | 1 条 `词条池 sword_common 的 core 段放了 prefix 类词条 sturdy`，EXIT 1 |
| 2 六夹具 E | B+C 同池 | **2 条**，`prefix…vital` 在前、`suffix…plated` 在后，EXIT 1 |
| 2 六夹具 F | `prefix` 只放 `ghost: 10` | `0 条问题` EXIT 0（存在性归别的尺，符合卡面「不核」清单） |
| 3 形态 | `wc -l` / import / 禁词 / py_compile / `--dir NO_SUCH` | 123、1、0、无输出、恰 1 条 `[问题]` + `1 条问题` + EXIT 1 无栈 |

夹具目录 `build/affixkind_r141/`（A~F 各一份整份 `defaults/` 副本，只改一处），**用后已删**；未复用 R140 的 `build/affixkind_s7/`。

**结论**：文案与卡面**逐字一致**，输出顺序与 `sorted` 要求一致，S8 零偏差，不打回。

## 2. 接线 `check_all` 74 → 75

`tools/check_all.py` 的 `TOOLS` 末尾追加 `"check_affix_kind_sync.py",      # 段类型面安全网：词条池段名 ↔ 词条 kind（错则整段空）—— R141 接上`。

- `check_all`：`共 75 个脚本 · 跑成 75 · 跳过 0 · 红 0`，EXIT 0。
- 七门禁全 0：`check_all` 75/75、`check_tasks` 136 卡 · 在制品 1 · 问题 0、`check_contracts` 117、`check_docs` 156 份/635 引用、`check_rounds`、`check_ownership`、`check_checkall`。

## 3. 派 T-0136（武器线三基底内容）

在制品 **1/3**（本卡）。卡面接口由 S7 定死，三份 JSON5 全文写进卡面，`ConfigStore` 恰 +3 行。

**取证（R141 现查）**：

- `grep -H 'base:' items/*.json5` → 12 份内容只覆盖 **8** 个基底（sword / dagger / hammer / helmet / chestplate / leggings / boots / amulet）；`axe` / `spear` / `staff` / `bow` / `shield` / `ring` **六个基底零内容**，而 D1 要求武器全做。
- `E:/server-test/mods/[冒险]bettercombat-neoforge-2.4.0+1.21.1.jar` → `data/bettercombat/weapon_attributes/` 有 **33** 个内置动作，含 `axe.json` / `spear.json` / `staff.json`（`check_weapon_attr_ref` 基线 33 逐字一致）。**无 `shield.json`**。
- 14 基底模型已齐（T-0127）→ 本卡不需要新模型。
- `sword_common.applies_to` 已含 `redtale_project:axe` → 新战斧自动进词条池。
- **派前整份副本预验**（`build/weapons_s7_r141/`，已删）：`check_items` 15 文件/0、`check_numeric` 4/0、`check_affix_reach` 2 池/12 定义/0、`check_name_coercions` 0、`check_weapon_attr_ref -v` 15 份/6 处/33 动作/0 问题。
- 三把只写有消费者的五个属性键（R138-b），`skill_damage` 等零消费者属性不碰；`rev: 1`（R136-b）。

**本卡不夹带** `bow` / `shield` / `ring`：`bow` 需弹道/弹药（M2 未覆盖）、`shield` 无 BetterCombat 动作、`ring` 依赖 Curios（D11 可选前置）→ 池 C85，待 Aki 拍板。

## 4. 本轮口径（§13.144）

| 编号 | 口径 |
|---|---|
| R141-a | **验收尺的打印顺序与计数来源不是同一件事**：`check_affix_kind_sync` 逐池打印（`read_pools` 文件序），末行计数取全量 `sorted` 长度。两者在单池时看不出差别，跨池时「打印乱序但计数对」是合法状态；不要为了「看起来整齐」去改 S8 的输出层。 |
| R141-b | **派内容卡前先数基底覆盖率**：「D1 武器全做」这种范围决策落地到什么程度，`grep -H 'base:'` 一条命令就能数出来（8/14）。数出来才发现六个基底零内容 —— 这类缺口不会被任何尺报，因为尺核的是「写了的东西对不对」，不是「该写的东西写了没」。 |
| R141-c | **内容卡的动作引用一律现查 jar**：`axe` / `spear` / `staff` 三个动作名不看文档、不看记忆，直接 `unzip -l` 列 2.4.0 jar 的 `weapon_attributes/`（33 个）。同一次列目录顺带证伪了 `shield` —— **不查就不会知道盾牌根本没动作可挂**。 |
| R141-d | **一个卡只收一个基底族的三个单位**：`axe`/`spear`/`staff` 同族（近战武器、动作现成、无新依赖）成一张卡；`bow`/`shield`/`ring` 三个各有硬依赖，**不夹带**。夹带的代价是「卡里有一条永远验不过的验收」。 |
| R141-e | **裸名纪律第五十九次**：只写裸名（`check_affix_kind_sync.py` / `ConfigStore.java` / `demo_greataxe.json5`），不写 `tools/`、`src/main/java/` 全路径；jar 文件名用通配 `*bettercombat*.jar` 或 `--jar-dir`。 |

## 5. 环境

- `src/` 本轮**零改动**（只读代码取证）；改的只有 `tools/check_all.py` 一行 + 文档。
- 未构建、未取构建锁、未启停任何服；正式服 `E:/server` 全程未触碰（25565 无监听，等 Aki 手动拉）；测试服 25567 / 25577 未启。
- 夹具 `build/affixkind_r141/` 与 `build/weapons_s7_r141/` 均已删净。

## 6. 下轮

1. 验 T-0136：静态三条 → 构建 → 测试服 `reload` **62 → 65 份**、`redtale items` 件数 12 → 15、`tooltip demo_greataxe` / `demo_spear` / `demo_battlestaff` 各出一条攻速口径（`tick · 次/秒`）。
2. M7 真机进服仍等 Aki 桌面可交互（`quickPlayMultiplayer` 已证无效，R132）。
3. Q-021 ~ Q-025 等 Aki；池 C85（`bow`/`shield`/`ring`）需先定语义才能派。

## 7. 附带观察（未派，池 C86）

`applies_to` 的 `*` 通配是**引擎接受、校验面拒绝**的不对称：`AffixRoller.java:53,68` 的 `poolFor` 明写接受 `"*"` / 裸名 / 全名三种；`check_items.check_pool` 要求含 `:` 且 ns 为 `redtale_project`，`ValidateCommand:104` 对裸名直接报问题（R21-e 故意为之）。裸名是**有意的纪律**（写歪就报），但 `*` 因此成为**校验面不可达分支**——同 R138 `core` 死分支同族。放开属语义决策（通配池按文件序遮蔽所有别的池），且要同时改 `check_items.py` + `ValidateCommand.java`，**默认 A = 维持现状**，已登记池 C86。
