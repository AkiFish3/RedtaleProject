# R142 · 验 T-0136（三把武器内容，基底 11/14）+ 派 T-0137（附魔 2→5，D32 组件面）

- 日期：2026-11-28 19:1x
- 里程碑：M2（武器数量与攻速档位）内容面推进；M4（附魔接口 D32）数据点扩充
- 本轮派/验：**验 T-0136 → VERIFIED**；**派 T-0137**（三条新附魔，各一个原版 effect 组件）

## 1. 验收 T-0136（3/3 + 真机，S8 零偏差不打回）

S8 于 15:45 交付三份 `items/*.json5` + `ConfigStore.java` 恰 +3 行（卡禁 gradle，未构建）。

| 验收 | 命令 | 结果 |
|---|---|---|
| 1 `check_items` | `python tools/check_items.py` | `共 31 个文件 · 0 条问题` EXIT 0 |
| 1 `check_numeric` | 同上 | `共 4 个文件 · 0 条问题` |
| 1 `check_affix_reach -v` | 同上 | `12 份词条定义 · 2 个词条池 · 5 个有效槽位段` / `0 条问题` |
| 1 `check_name_coercions` | 同上 | `0 条问题` |
| 1 `check_weapon_attr_ref -v` | 同上 | `15 份物品 · 6 处引用（bettercombat 6）· 内置动作 33 个` / `0 条问题` |
| 1 `check_defaults` | 同上 | `清单 61 条 · 磁盘 61 个 · 问题 0 条` |
| 1 `check_all` | 同上 | `共 75 个脚本 · 跑成 75 · 跳过 0 · 红 0` EXIT 0 |
| 2 形态 | `git status` / `numstat` / `rev` / 禁词 | 5 条（3 `??` + `ConfigStore` + 卡板）/ `3 0` / 各 1 / 0 |
| 3 逐字 | `diff` × 3 | 与卡面代码块**零差异**，各 22 行（九段全对） |

### 真机（构建 + 测试服 1 启 1 停）

- 构建：取锁 → `gradle build --offline` → `BUILD SUCCESSFUL in 25s`，jar **702963 B**（R140 的 701476 + 1487，全来自三份 JSON5）→ 删锁。
- 启动：`Done (2.377s)`；`reload` → **65 份配置文件**（62 → 65）。
- `redtale items` → **`18 件装备内容 · 基底 14 个`**（15 → 18）。
- `redtale validate` → `18 件内容 / 2 个词条池 / 12 条词条定义 / 2 个套装 · 技能 4 条 · 问题 0 条（外部 weapon_attributes 引用 6 条未校验）`（引用 3 → 6）。
- redtale 相关 `WARN` / `ERROR`：**0** 条（日志 `E:/server-test/console_r142.log`）。
- 停服：`stop` → `ThreadedAnvilChunkStorage: All dimensions are saved` → `RCON Listener stopped`；25567 / 25577 无 LISTEN。

| 武器 | tooltip 单次伤害 | 攻速（D20 口径） | 动作 | 词条 |
|---|---|---|---|---|
| `demo_greataxe` | `≈ 10.62 /次（physical_damage +6.2%）` | `23.529 tick · 0.85 次/秒` | `bettercombat:axe` | 3（`executioner` · `of_the_bear` · `runed`） |
| `demo_spear` | `≈ 9.35 /次（physical_damage +10%）` | `17.391 tick · 1.15 次/秒` | `bettercombat:spear` | — |
| `demo_battlestaff` | `≈ 7 /次` | `16.667 tick · 1.2 次/秒` | `bettercombat:staff` | — |

**攻速三档第一次同时出现在内容里**（0.85 / 1.15 / 1.2）；D19 的物理地板 `min_attack_delay_ticks = 1.0` 在 0.85 档对应 23.5 tick，远未触及。

## 2. 派 T-0137：附魔 2 → 5（D32 数据点 2 → 5，组件面 1 → 4）

**动机**：D32 的硬指标是「加第 N 个附魔只写 JSON5、不编译」，落地至今只有 **2 个数据点**、**1 种原版组件**（`minecraft:damage`）。三条新附魔各用一个原版已证组件，引擎零改动：

| 文件 | 组件 | 语义 | max_level |
|---|---|---|---|
| `titan_grip.json5` | `minecraft:attributes` → `minecraft:generic.max_health` | 护甲 +最大生命（每级 +2） | 2 |
| `heavy_impact.json5` | `minecraft:knockback` | 武器击退加成（每级 +0.5） | 2 |
| `ember_bite.json5` | `minecraft:post_attack` + `minecraft:ignite` | 命中后点燃（每级 +2s） | 3 |

### 派前取证

1. 原版 1.21.1 附魔 JSON 在 `server-1.21.1-20240808.144430-extra.jar` 的 `data/minecraft/enchantment/`，共 **42 份**；三形状分别抄 `efficiency.json` / `knockback.json` / `fire_aspect.json`。**不凭记忆写组件**（R142-b）。
2. 引用标签全在原版 jar：`enchantable/armor`、`enchantable/sharp_weapon`、`exclusive_set/armor` → **不需要新建标签**（`EnchantableTags.java:127-141` 只兜自家命名空间的 `exclusive_set`）。
3. `minecraft:generic.max_health` 与 `AttributeSpecs.java:69` 逐字一致（1.21.1 仍带 `generic.`）。
4. **刻意不用 `attack_speed`**：附魔的 `minecraft:attributes` 给 `add_value`，与 D18 的 `ADD_MULTIPLIED_TOTAL` + 固定 modifier id 不是同一条路。
5. `EnchantmentGenerator.java:37-41` 白名单十键；`effects` **原样透传不校验** → 组件形状只能照抄原版样张。

### 派前预验发现：lang 键必须跑生成器（本轮第二条真发现）

在整份 `defaults/` 副本（`build/enchs7/`）上加三份附魔后：

- 附魔系尺全绿：`check_enchants` **5 文件 / 0**、`check_enchant_exclusive` **5 份 / 0**、`args_sync` / `cost_forms` / `slot_forms` / `definition_ints` 各 0、`check_numeric` 4/0、`check_name_coercions` 0。
- **但 `check_lang` 报 6 条缺键**（`enchantment.redtale_project.{titan_grip,heavy_impact,ember_bite}`，zh_cn / en_us 各 3）。
- 结论写进卡面：lang 只许 `tools/gen_enchant_lang.py` 改写（它是 `enchantment.redtale_project.*` 前缀的唯一拥有者），**手改算越界**。

### 三条候选证伪（不派重复卡）

- `items.rarity` 非法值 → `ItemContent.java:108-110` 有 WARN，`check_items.py:61-62` 已在役尺。
- `SocketSpec.types` 元素形态 → `SocketSpec.java:110-124` 六处 WARN（T-0115 已收）。
- `enchantments.exclusive_set` 指向自家缺失 tag → `EnchantableTags.java:127-141` 会补空 tag，不是洞。

## 3. 门禁

`check_all` **75/75 红 0**；`check_tasks` **137 卡 · 在制品 1 · 问题 0**；`check_ownership` 0；`check_docs` **157 份 / 640 引用 / 0 提示 / 0 问题**；`check_rounds`、`check_contracts`、`check_checkall` 全 0。

## 4. 环境

构建 1 次（锁取删，25s）；测试服 **1 启 1 停**；**正式服未触碰**（全程无 `java.exe` 驻留、25565 无监听）；预验副本 `build/enchs7/` 与 `E:/tmp/defs7/` 已删；无锁残留。

## 5. 遗留

- **M7 真机进服**仍等 Aki 桌面可交互（`quickPlayMultiplayer` 已证被忽略）。
- 池 C85（`bow` / `shield` / `ring`）需先定语义；C86（`applies_to` 通配）默认维持现状；C82（jar ↔ live 漂移观测面）仍不派。
- Q-021 ~ Q-025 等 Aki。
