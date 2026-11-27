# R137 · 2026-11-27 12:2x~12:5x · 验 T-0130 + 验 T-0131（三档套装首证）+ 修 `set probe` 假红 + 派 T-0132

## 0. 一句话

两张 `[DONE]` 卡全过 → `[VERIFIED]`；`check_all` **72→73**；**三档套装首次真机证明可达**（`ashen_vow` pass 6 / fail 0）；过程中发现并修掉 `set probe` 的「2 件 = 最高档」写死判据（对 3 档套装必假红，S7 自己的工具债）；派 T-0132（套装 `pieces` ↔ 物品 `set_id` 归属尺）。

## 1. 卡板：T-0130 `[DONE]` → `[VERIFIED]`

`tools/check_weapon_attr_ref.py`（S8 07:21 交付，135 行）。三条验收逐字复跑，**S8 零偏差，不打回**。

| 验收 | 命令 | 卡面期望 | 实测 |
|---|---|---|---|
| 1 | `python check_weapon_attr_ref.py` | `共 9 份物品内容 · 3 处 … · 0 条问题` EXIT=0 | **`共 12 份`** · 3 处 · 0 条 EXIT=0 |
| 1 | `-v` | `9 份物品 · 3 处引用（3/0/0） · 33 个 · jar <文件名>` | `12 份物品 · 3 处引用（3/0/0） · 33 个 · jar [冒险]bettercombat-neoforge-2.4.0+1.21.1.jar` |
| 2 | 六夹具 A~F（`build/waref_s7/`） | 各 1 条 / 逐字 / EXIT 1,1,1,0,0,1 | 6/6 逐字对 |
| 3 | `wc` / `py_compile` / 两路 `NO_SUCH` / 禁词 | 135 / 无输出 / 1 条 + 1 条 / 0 | 全对 |

**唯一偏差：物品数 9 → 12**。原因是 T-0131 同轮交付了 3 件护甲（`demo_helmet` / `demo_greaves` / `demo_boots`），而卡面写基线时 `items/` 还是 9 份。引用数仍是 3 处、动作表仍是 33 个、问题仍是 0 —— **判据本身没有被稀释**。按 R137-a 记「并发计数漂移」，不判打回，但**卡面/文档里不要再把 9 当常量断言**。

六夹具逐字（S7 独立复跑）：

```
A demo_dagger → bettercombat:greatsword   demo_dagger: bettercombat:greatsword 在 BetterCombat 内置动作里不存在   EXIT=1
B demo_warhammer → "bettercombat"         demo_warhammer: weapon_attributes 不是 namespace:path 形式（bettercombat） EXIT=1
C demo_longsword → redtale_project:nope   demo_longsword: redtale_project:nope 缺配置 weapon_attributes/nope.json5   EXIT=1
D demo_longsword → simplyswords:katana    0 条 · -v 外部 1                                                       EXIT=0
E 删 demo_warhammer 的键                  0 条 · -v 降到 2 处引用                                                EXIT=0
F A+B+C                                  3 条，顺序 dagger → longsword → warhammer（按 id 字典序）                EXIT=1
```

读失败形态：`--dir NO_SUCH` → 恰 1 条 `物品目录读取失败：NO_SUCH` EXIT=1；`--jar-dir NO_SUCH` → 恰 1 条 `BetterCombat jar 未找到：NO_SUCH` EXIT=1，均不抛栈。夹具副本已删。

**接线**：`check_all` **72→73**（`共 73 个脚本 · 跑成 73 · 跳过 0 · 红 0`）。

## 2. 卡板：T-0131 `[DONE]` → `[VERIFIED]`

三件护甲 + `ashen_vow` 三档套装 + `ConfigStore` 恰 4 行（S8 11:24 交付）。**S8 实现零偏差**；卡面有两处文案是 S7 写卡不严，本轮一并收拾。

| 验收 | 命令 | 实测 |
|---|---|---|
| 1 | `check_items.py` | `共 18 个文件 · 0 条问题` EXIT=0 |
| 1 | `check_set_reach.py` | `共 2 份套装档位可达性表 · 0 条问题` EXIT=0 |
| 1 | `check_set_reach.py -v` | `已检查 2 份套装 · 5 件引用 · 5 个档位 · 5 个可达装备槽` |
| 2 | `check_defaults.py` | `清单 48 条 · 磁盘 48 个 · 问题 0 条` |
| 2 | `check_numeric.py` | `共 4 个文件 · 0 条问题` |
| 2 | `check_all.py` | `73/73 红 0`（先把 T-0130 的尺接线才成立） |
| 2 | `check_ownership.py` | 0 问题 |
| 3 | `git diff --numstat …/ConfigStore.java` | `4 0` |
| 3 | 四份 JSON5 逐字解析 | 三件 `id`/`base`/`rev=1`/`set_id=ashen_vow` 与卡面一致；`pieces` 恰 3 项且与三件 id 逐字一致 |

**卡面偏差两处（S7 责任，非 S8）**：

1. 验收 1 写 `共 2 份套装档位可达性表`，但脚本 `check_set_reach.py:168` 把 `共 1 份` 写死在字符串里（R96 只有一套套装时的遗留）。**已把该行改成 `%d` 取实际份数**，现在产物与卡面同时成立。这是卡面/产物不同步的根治。
2. 验收 3 写 `git status --porcelain` 恰 5 路径，但 S8 必须改卡板状态行（+ `M docs/TASKS.md`），且上轮 T-0130 的未接线尺也在树里 —— 该条在并发轮次里天然不可满足，属 S7 写卡时没考虑并发。

**真机（构建 + 测试服）**：

| 项 | 实测 |
|---|---|
| 构建 | `BUILD SUCCESSFUL in 23s` / 二次 2s；jar **696635 B**（改 probe 前）/ **697063 B**（改后） |
| jar 条目 | `redtale/defaults/items/demo_{helmet,greaves,boots}.json5` + `redtale/defaults/sets/ashen_vow.json5` 四份都在包内 |
| 启动 | `Done (2.4s)`；`redtale status` → `内容 15 件装备 / 14 个基底物品`（15 = 12 份文件 + 3 件脚本注册：`probe_hungry` / `t_upg_sword` / `test_sword`） |
| `set list` | `ashen_vow · 灰烬誓约 · 3 件 · 档位 1/2/3` / `emberforged · 炽锻 · 2 件 · 档位 1/2` |
| `items` | 三件属性逐字：头盔 `最大生命 +2 / 护甲 +6`、胫甲 `护甲韧性 +1 / 护甲 +8`、战靴 `移动速度 +0.02 / 护甲 +5` |
| `tooltip` | 三件均 `[redtale] <中文名>（redtale_project:<base>）` + `套装 灰烬誓约 0/3` |
| `reload` | `52 份配置文件`；全量刷新 90 区块；零 ERROR |
| 日志 ERROR | 4 条，全是他模组配方解析（`simplyswords` ×2 / `brewinandchewing` / `minecraft:red_dye`），本模组 **0** 条 |
| 停服 | `stop` → `25567/25577` 无 LISTEN；正式服 `25565` 全程无监听（未触碰） |

## 3. 三档套装首证 —— 并发现 `set probe` 假红

首次跑 `redtale set probe ashen_vow`：

```
✗ 2 件：件数 2 · 档位值 0.0 · 攻击伤害 1.0 → 1.0
结论：有失败项（pass 4 / fail 1）
```

**这不是套装的问题，是探测器的判据写死了**。原实现（`core/command/SetCommand.java`）把两件事硬编码：

- 「第 2 件 = 最高档」→ 对 3 档套装，第 2 件时最高档（3）本就该不在位；
- 「最高档必给 `attack_damage`」→ `ashen_vow` 的 3 档给的是 `max_health + armor`，`attack_damage` 恒 0。

也就是说 **`emberforged`（恰好 2 件 2 档、2 档带 `attack_damage`）能过纯属巧合**，任何形状不同的套装都会假红 —— 这个探测器从 R96 起只能验一套。

**改法（S7 独占文件，本轮改）**：判据改为「按各档**声明的键**现解析属性，逐档核 `count <= 件数` 的档全在位、更高的档全不在位」（新增 `tierMismatches()` / `inPlay()` 两个私有方法），件数循环上界取 `min(档位数, 件数)`。今后新增几档套装都直接可验，不用再动命令。

改后：

```
套装探测 ashen_vow（FakePlayer 真穿真脱）：
  定义：pieces 3 / 档位 [1, 2, 3] / 命中内容 3 件
  ✓ 0 件：件数 0 · 无残留档位 modifier
  ✓ 1 件：件数 1 · 在档 [1] · 最大生命 20.0 → 22.0
  ✓ 2 件：件数 2 · 在档 [1, 2] · 最大生命 22.0 → 22.0
  ✓ 3 件：件数 3 · 在档 [1, 2, 3] · 最大生命 22.0 → 28.0
  ✓ 脱回 1 件：件数 1 · 在档 [1]
  ✓ clear：全部档位 modifier 已移除
  结论：全部通过（pass 6 / fail 0）
```

**读法**：3 件时 `22.0 → 28.0` 说明 tier1（`max_health 2`）与 tier3（`max_health 6`）**同时**生效（20 + 2 + 6 = 28），符合 D 层「所有 `count <= 件数` 的档全部生效」；2 件档不含 `max_health` 所以数值不动但 `在档 [1,2]` 表明它确实下发了。回归 `emberforged` **pass 5 / fail 0**（2 档套装少一步）。

## 4. 派 T-0132（`check_set_pieces_sync.py`，对账网第五十七例）

**取证（先证伪旧尺覆盖）**：

| 面 | 现状 |
|---|---|
| `grep -rn "set_id" tools/check_*.py` | 仅 `check_items.py:69,70` 两行，纯「指向的套装存在」 |
| `check_items.py:130-131` | 只核 `pieces` 引用的物品**存在** |
| `SetBonus.counts()`（`items/SetBonus.java:95-117`） | 计件**只**看 `ContentData.setId()`，**完全不读 `pieces`** |
| `pieces` 的消费者 | 只有 `check_set_reach.py`（槽覆盖）与 `SetCommand.probe`（真穿真脱） |

→ `pieces` 里塞一件 `set_id` 指向别处的物品：穿上它**不给本套装计数**，`set probe` 穿满仍报 ✗，而 `check_set_reach` 认定该槽可达 —— **全绿**。这是「静默放行」同族里最后一个没人管的缝。

**基线**：`items/` 12 份 / 带 `set_id` 6 件 / `sets/` 2 份 / `pieces` 引用 5 处 / 归属一致 5 处 / **0 条问题**。

**边界（重要）**：`demo_robe` 有 `set_id: emberforged` 但**故意**不在 `emberforged.pieces`（同为胸甲基底、互斥，`emberforged.json5` 注释已写明）。所以本尺**只核 `pieces → set_id` 单向**；反方向不报。`pieces` 引用不存在的物品也**跳过只计数**（存在性归 `check_items`，免得两张卡双红纠缠）。

卡面写死：三判据文案、`set_pieces/check/main` 三签名、六夹具 A~F、基线 2 套 / 5 处 / 0 条、`--dir NO_SUCH` 恰 2 条、禁接线（`check_checkall` 红 1 条属设计内红）。

## 5. 三条被证伪的尺子候选（记录以免下轮重挖）

| 候选 | 结论 |
|---|---|
| 「`items` / `sets` 的 `stats` 键 ↔ 80 条属性表」 | **已被覆盖**：`check_items.py:51-55` 的 `_stats()` 对 items / gems / **sets.bonuses[].stats** 三处都核（`check_items` 扫 `items`+`affixes`+`affix_pools`+`sets` 共 4 个子目录 = 18 文件），不在表内报 `属性 X 不在 80 条表内` |
| 「套装 `pieces` ↔ 装备槽覆盖」 | **已被覆盖**：`check_set_reach.py` 已是 `helmet→HEAD / leggings→LEGS / boots→FEET` 槽位表 |
| 「`enchantments` 的其余 PASS_THROUGH 键」 | R129~R131 已收 `weight`/`anvil_cost`/`min_cost`/`max_cost`/`slots` 五键，剩余键无「有基线 + 零覆盖」证据，不派 |

## 6. 观察项（未派卡）

护甲类 `redtale tooltip` **不打印属性行**：三件新护甲与存量的 `demo_chestplate` 同形，只显示 `套装 X 0/N`，`护甲 +6` / `最大生命 +2` 一条都不显示；武器则完整（单次伤害 + 攻速 + 词条 + 孔位）。D20 只规定了武器口径，非武器属性行属**显示面待定项**，等 Aki 或 M7 真机看到实际观感再定要不要补。

## 7. 环境与门禁

| 项 | 状态 |
|---|---|
| `src/` 改动 | 5 新建配置文件（T-0131，已合流）+ `ConfigStore.java` +4 行 + `SetCommand.java`（probe 重写，S7） |
| `tools/` 改动 | `check_weapon_attr_ref.py` 接线（73）、`check_set_reach.py` 末行取实际份数 |
| 构建 | 2 次（改 probe 前后各一），均 `BUILD SUCCESSFUL`，**无锁残留** |
| 测试服 | 2 启 2 停（`Done (2.4s)` → `stop` → 端口无 LISTEN）；跑完已停 |
| 正式服 | **未触碰**（25565 全程无 LISTEN，等 Aki 手动拉回） |
| 门禁 | `check_all` 73/73 红 0 · `check_tasks` 132 卡 / 在制品 1 / 问题 0 · `check_contracts` 116 条 · `check_ownership` 0 问题 |
