# R138 · 2026-11-27 18:3x~19:0x · 验 T-0132（`pieces` ↔ `set_id` 归属尺，接线 73→74）+ 派 T-0133（词条 3→8 条，`core` 段首开）

## 0. 一句话

T-0132 `[DONE]` → `[VERIFIED]`（3/3，S8 零偏差），`check_all` **73→74**；派 T-0133 —— 随机词条内容面从 3 条扩到 8 条，并把 `sword_common` 的 `core` 段从 `slots: 0` 开到 1，**让 `AffixRoller` 的第三分支第一次有内容**。

## 1. 卡板：T-0132 `[DONE]` → `[VERIFIED]`

`tools/check_set_pieces_sync.py`（S8 15:27 交付，91 行）。三条验收逐字复跑。

| 验收 | 命令 | 卡面期望 | 实测 |
|---|---|---|---|
| 1 | `python check_set_pieces_sync.py` | `共 2 份套装定义 · 5 处 pieces 引用 · 0 条问题` EXIT=0 | 逐字一致，EXIT=0 |
| 1 | `-v` | `已检查 2 份套装 · 5 处 pieces 引用 · 12 份物品 · 跳过 0 处（物品不存在）` | 逐字一致 |
| 2 | 六夹具 A~F（`build/setpieces_s7/`） | 1 / 1 / 0(跳过 1) / 0 / 0 / 2（顺序 `demo_robe`→`demo_stew`） | 6/6 逐字对，EXIT 1,1,0,0,0,1 |
| 3 | `wc` / `py_compile` / `--dir NO_SUCH` / 禁词 / 三签名 | 91 / 无输出 / 2 条 / 0 / 齐 | 全对 |

六夹具逐字（S7 独立复跑，副本由 `build/setpieces_s7/` 现造，未复用 S8 的 `build/setpieces_s8/`）：

```
A  ashen_vow.pieces: demo_boots → demo_robe   ashen_vow: demo_robe 的 set_id 是 emberforged（不是 ashen_vow）   EXIT=1
B  ashen_vow.pieces + demo_stew               ashen_vow: demo_stew 没有 set_id                                 EXIT=1
C  ashen_vow.pieces + nope_item               0 条 · -v 跳过 1 处（物品不存在）                                EXIT=0
D  原样副本                                   0 条                                                             EXIT=0
E  demo_helmet.set_id → nope_set              0 条（存在性归 check_items，本尺不管）                            EXIT=0
F  A+B                                        2 条，顺序 demo_robe → demo_stew（piece 字典序）                  EXIT=1
```

读失败形态：`--dir NO_SUCH` → 恰 2 条（先 `套装目录读取失败`、后 `物品目录读取失败`）、EXIT=1、不抛栈。

设计核对（读码，非卡面声明）：`_scan()` 第 67 行把判据 3 收紧成 `path_of(raw) != path_of(sid) and path_of(raw) in known` —— 「`set_id` 指向一个不存在的套装」不报（夹具 E 的形态），与卡面口径一致；反方向（物品有 `set_id` 但套装未收录）完全不读，`demo_robe` 的合法单向差异不会被误伤。

**接线**：`check_all` **73→74**（`共 74 个脚本 · 跑成 74 · 跳过 0 · 红 0`）。夹具副本已删（`build/setpieces_s8` / `build/setpieces_s7`）。

## 2. 派单：T-0133（词条 3→8 + `core` 段首开）

**为什么是内容卡而不是再派一把尺**：R120~R137 连续 18 轮基本都在补对账网，尺子已 74 把；而 M2 出口要的「随机词条」内容面只有 3 条、池子 `slots` 是 `prefix 1 / suffix 1 / core 0` —— 一件武器最多摇 2 条，`AffixRoller.java:102` 的 `{prefix, suffix, core}` 三元循环里 `core` 一直是 `slots=0` 的死分支。内容面的洞比尺子面的洞更贴近「能不能玩」。

**取值口径（本卡最要紧的一条）**：只写**被结算真读到**的属性。现查（`grep -rn` 排除 `AttributeSpecs` / `StatFormat`）：
- 真被读：`physical_damage`（`damage.json5` 的 `percent_attributes`）、`pvp_damage` / `pve_damage` / `undead_damage`（`conditional_attributes`）、`critical_strike_chance` / `critical_strike_power`（`CritRules`）、`magical_damage`（`category_attributes`）。
- **零引用**：`lifesteal` / `armor_penetration` / `damage_reduction` / `magic_resistance` / `skill_damage` / `dodge_rating`（`AttributeSpecs` + `StatFormat` 之外无命中）。写进词条只会让 tooltip 说谎 → 本卡不碰，记进「等 M1 收口后才有意义的内容面」。

**派前整份副本预验**（R136-b，`build/affixs7/` 写入本卡全部内容后跑）：

| 尺 | 基线（现树） | 候选副本 |
|---|---|---|
| `check_affix_reach -v` | `3 份词条定义 · 1 个词条池 · 2 个有效槽位段` | `8 份 · 1 个 · 3 个有效槽位段` |
| `check_affix_pool_refs -v` | `3 条词条定义 · 3 条词条引用` | `8 条 · 8 条` |
| `check_affix_weights` / `check_affix_levels` / `check_numeric` / `check_items` | 0 / 0 / 0 / `18 个文件 0 条` | 0 / 0 / 0 / `23 个文件 0 条` |

预验同时纠正了一处**我自己的写卡错**：原本打算写「`check_items` 与基线同数」，实测该尺按目录内全部 `.json5` 计总数（12 items + 3 affixes + 1 pool + 2 sets = 18），加 5 份后是 23 —— 卡面已改成断言 `共 23 个文件 · 0 条问题`（R137-a 同族：别把数字当常量，要么让它自己算要么写对）。

**为何 `core` 段可以先开**：`AffixRoller.roll()` 对三类槽位是同一个循环（`int[] slots = {prefixSlots, suffixSlots, coreSlots}` + `String[] kinds`），`pick()` 只按 `def.kind()` 过滤 —— 引擎侧零改动，纯内容即可通电。这也意味着验收要由我在测试服上真机摇一件武器看「3 条词条」。

## 3. 本轮口径（§13.141）

| 编号 | 口径 |
|---|---|
| R138-a | **「没内容的分支」等于「没验证的分支」**：`core` 段代码写了 18 轮、`slots` 恒 0，从未被任何一次摇号走到。补内容不只是「多两条词条」，是把一条死分支点亮 —— 派内容卡时要顺手问「这条路上有没有从没被走过的那一段」。 |
| R138-b | **词条只写结算真读的属性**：80 条属性注册齐全 ≠ 80 条都有消费者。`lifesteal` / `armor_penetration` 这类在注册表里躺着、`grep` 零引用的属性，写进装备就是「tooltip 显示 +30% 实际 0」的静默谎言，比缺内容更坏。派内容卡前先 `grep` 一遍属性消费者。 |
| R138-c | **夹具副本要自己造，别复用 S8 的目录**：S8 用 `build/setpieces_s8/`、我用 `build/setpieces_s7/` —— 同一目录会被「我以为的干净副本」污染（S8 可能改了没清），独立副本是零成本的隔离。 |
| R138-d | **验收断言里的总数先自己跑一遍再写进卡**：`check_items` 计的是「目录内全部 `.json5`」而不是「items 数」，我按语义想当然写成「同数」。凡是卡面写死的数字，都是 S7 亲手跑出来的才算数。 |
| R138-e | **裸名纪律第五十六次**：只写裸名，不写全路径（`check_docs` 按路径存在性判红）。 |

## 4. 环境

- `src/` 零 diff（本轮只动 `tools/check_all.py` 一行 + 文档）；**未构建**（纯 Python/文档，无编译面）。
- 无构建锁（`.queue/build.lock` 不存在）；**未启停测试服**（25567/25577 全程无 LISTEN）；**正式服未触碰**（25565 无监听，等 Aki 手动拉）。
- 夹具副本 `build/setpieces_s7/`、`build/affixs7/` 用完即删。

## 5. 带给 Aki 的

- **Q-025**（本轮新写）：非武器装备（护甲/饰品）tooltip 要不要打自身属性行。现状：`demo_helmet` 的 `armor 6 / max_health 2` 在物品上完全看不见，只有「套装 灰烬誓约 0/3」。默认走 **A：加一行「本件属性」，数值取 `ItemStack#forEachModifier` 的组件实挂量**（D21 白板化后如实，与 `/redtale attr` 同源）。
- Q-021 / Q-022 / Q-023 / Q-024 仍无答复，按各自默认继续。

## 6. 收尾

`docs/ROUND-R138-PIECESSYNC.md`（本文）、PLAN §13 R138 + §13.141、`docs/ROUNDS.md`、`docs/TASKS.md`（T-0132 VERIFIED + T-0133 OPEN）、`tools/check_all.py`（74）、协作板 §3。
