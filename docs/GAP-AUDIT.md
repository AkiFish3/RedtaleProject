# 功能面缺口审计（第二轮，2026-11-15 12:05）

方法：逐个打开 `D:\redtale-work\upstream\*.jar` 的**非 class 资源**（`default/`、`enchants/`、内置 yml），
再对 `E:\server-1.21.1\mods` 的 228 个 jar 做「谁已经在做这件事」的反向核对。
只有 `PLAN.md` / `docs/` 里找不到落点、且上游确实存在的项才算缺口。

## 0. 结论

缺口分两类，性质完全不同：

- **A 类 漏项**：上游有、我们没进里程碑。属于「要不要做」的选择题。
- **B 类 撞车**：正式服已装的 mod 正在做同一件事，且**已经注册了注册表项**。属于「必须先划边界，否则返工」的必答题。

B 类比 A 类紧急：属性、饰品槽、伤害乘区这三处一旦 M1 落地就改不动了。

## 1. A 类：上游有、PLAN 没落点

| # | 能力 | 证据（jar 内路径） | 现状 | 建议归属 |
|---|---|---|---|---|
| A1 | **随机词条（前缀/后缀）** | `MMOItems default/modifiers/example_modifiers.yml`：`chance` / `prefix` / `suffix` / `stats` / `group_example{max:3}` / `ability.on-hit` | PLAN 只有 `StatValue{base,scale,spread,maxSpread}`（数值滚动），**没有「词条池」概念** | M2，与掉落表同期 |
| A2 | **自定义工作台** | `default/crafting-stations/arcane-forge.yml`：`max-queue-size:10` / `crafting-time:5` / `conditions:['level{level=5}']` / `layout` / `items`（GUI 槽位定义） | 只有原版配方，没有「装备打造台」 | M2 尾 / M5 |
| A3 | **自定义矿脉生成** | `default/gen-templates.yml`：`replace:[STONE]` / `chunk-chance` / `depth:10=24` / `vein-size` / `vein-count` / `slime-chunk`；配合 `api/block` 包 | **零覆盖** | M6 或独立里程碑 |
| A4 | **消耗品与修理** | `default/item/consumable.yml`：`effects` / `restore-food` / `item-cooldown` / `repair:3.0` / `vanilla-eating` / `disable-right-click-consume` | 未列入 item type 集合 | M2（低成本，顺手做） |
| A5 | **专业 professions** | `MMOCore default/professions/{mining,smelting,woodcutting}.yml`：`exp-curve` / `on-mine.{block}.drop-table` / `options.vanilla-drops` / `regen.time` | M7 只有「等级/经验/职业/属性点」 | M7 扩展 |
| A6 | **技能树** | `default/skill-trees/warrior-paladin.yml`：`nodes.{id}.coordinates{x,y}` / `experience-table.triggers[]` / `max-point-spent:21` / `lores` | 只有「技能」，没有「天赋树/点数分配」 | M7 |
| A7 | **区域战利品箱** | `default/loot-chests/default_loot_chests.yml`：`bounds` / `spawn-period` / `algorithm-options{min-range,max-range,height}` / `tiers` | 无 | 可不做（见 B6） |
| A8 | **原版刷怪替换** | `MythicMobs ExampleRandomSpawns.yml`：`Chance:0.001` / `Priority` / `Action: REPLACE\|ADD` / `Conditions:[outside true]` | M6 只写「刷怪」，缺「把原版刷的怪换成我们的怪」这一层 | M6（运营刚需） |

已对齐、不算缺口：技能数据化（`MMOItems default/skill/*.yml` 10 个）、附魔 schema（EcoEnchants `enchants/*.yml` + `vanillaenchants.yml`）、怪物数据化（MythicMobs）、槽位四类限制（`MMOInventory slot/restriction`）、经验源（`MMOCore exp-sources.yml` + 22 个 `*ExperienceSource`）。

## 2. B 类：与正式服已装 mod 撞车

### B1 spell_power 的属性已经在册（最急）

`E:\server-1.21.1\mods\spell_power-neoforge-1.6.0+1.21.1.jar` 的 lang 里有 19 条属性名：

`spell_power.{generic,arcane,fire,frost,healing,lightning,soul,air,water,earth,blood,nature,shadow,holy,unholy}` + `critical_chance` / `critical_damage` / `haste` / `resistance.*`

我们的 `ATTRIBUTE-REGISTRY.md` 里 31~48 号是 `magical_damage`、`fire_damage`…`earth_damage`、`spell_critical_strike_*`、`spell_resistance`——**语义完全重叠、ID 不同**。
`config/spell_power/attributes.json` 里**没有开关**能关掉这批属性（`register_potions:false` 只管药水），它们一定在册。

三条路：

| 方案 | 做法 | 代价 |
|---|---|---|
| A 复用 | 直接用 `spell_power:fire` 等，D11 白名单破例 | 引入编译依赖，与 D11「零 mod 依赖」冲突 |
| B 并存隔离 | 我们的元素属性只给我们自己的技能吃，Armory 武器走 spell_power | 玩家面板两套「火焰法术强度」，长期混乱 |
| **C 只读桥接（建议）** | 不注册同义属性；结算时按 **ResourceLocation 查 `AttributeMap`**（`BuiltInRegistries.ATTRIBUTE.get(...)`）读取 `spell_power:*` 作为输入，代码零 import、零编译依赖 | 需要在 `DAMAGE-PIPELINE` 加一层「外部属性适配表」 |

C 保住 D11 的字面（不作前置、零 import），同时消掉双轨。我们的 16 条预留里本来就有 `air/soul/blood/unholy`，说明当初就是按 spell_power 的系别留的位。

### B2 spell_engine 的消耗模型会与 M7 的等级系统打架

`config/spell_engine/server.json5`（正式服现值）：

```
spell_book_creation_requirement: 1      // 法术书门槛 = 经验等级
spell_book_creation_cost: 1             // 花费 = 经验等级
spell_binding_level_cost_multiplier: 1  // 绑法术也吃经验等级
spell_cost_exhaust_multiplier: 1.0      // 法术消耗 = 饥饿值
spell_cost_durability_allowed: true     // 还吃物品耐久
haste_affects_cooldown: true            // spell_haste 直接减冷却
bypass_iframes: true                    // 法术绕过无敌帧
```

两处冲突：

1. **M7 一旦重写等级/经验（MMOCore 式独立等级），spell_engine 的 `levelCost` 就废了**——它读的是原版 XP 等级。要么我们保留原版 XP 不动（等级另建），要么改它的 `spell_*_level_cost_*` 为 0 并由我们代扣。
2. **资源双轨**：它用饥饿值当蓝，我们要做 `max_mana` / `mana_regeneration`。同一个法师玩家会有「书不耗蓝但耗饥饿」。

### B3 伤害乘区会套到 Armory 系法术上

`spell_engine-neoforge-1.10.9` 的 `net/spell_engine/neoforge/PlatformEventsImpl.class` 引用了 `LivingIncomingDamageEvent` / `LivingDamageEvent`。
→ 它打出的伤害**会经过我们的 L2 乘区**。若同时叠 spell_power 的 `spell_power:fire` 和我们自己的 `fire_damage`，就是双重加成。
定：乘区只对**带我们 tag 的 DamageSource** 生效，其余原样放行（这条要写进 D12 的补充，否则 M1 就写错）。

### B4 三套饰品系统并存

| jar | 依赖 |
|---|---|
| `curios-neoforge-9.5.1` | 无 |
| `elytraslot-neoforge-9.0.2` | `curios`, `caelus` |
| `accessories-neoforge-1.1.0-beta.53` | `owo`（自带 `accessories_compat_layer`） |
| `artifacts-neoforge-13.2.5` | 无硬依赖 |

`UPSTREAM-1-Inventory.md` §4 说「槽位挂 Curios」但**没有决策号**。建议定 **Curios**：版本成熟、已有 addon 在用；Accessories 是 beta 且绑 owo。这条不定，M3 的戒指/项链槽会做两遍。

### B5 lootr 接管所有箱子

`lootr-neoforge-1.11.38.127` + `twilight_lootr` 在跑 → 箱子战利品是**每人独立**的 wrapper。
我们的掉落表只能生成 `LootTable` 交给它，**不能自己往 BlockEntity 里塞**，否则玩家之间会互相看到/串号。

### B6 已经有现成实现，建议不自研

| 我们计划里的 | 服上已有的 | 建议 |
|---|---|---|
| MMOCore 任务 | `ftb-quests` + `ftb-teams` | 不自研，做数据对接 |
| MMOCore 传送点 | `waystones` | 不自研 |
| MMOCore 公会/队伍 | `ftb-teams` + `open-parties-and-claims` | 不自研，只接经验分享 |
| MMOCore GUI 里的 NPC 交互 | `easy_npc` + `CustomNPCs` | 不自研对话系统 |
| MMOCore 战利品箱（A7） | `bountiful`（悬赏板）+ `lootr` | 优先复用 |
| 权限等级 | `ftb-ranks` | 不自研 |

反过来，**A1 随机词条 / A2 工作台 / A5 专业 / A6 技能树 / A8 刷怪替换没有替代品**，这五件才是我们真正要写的。

### B7 enchantmentdisabler 已装但全默认（没启用）

`config/enchantmentdisabler/config.toml`：`disabledEnchantments = []`、`globalMaxLevel = 0`、`limitObtainableEnchantmentLevel = false`。

它有 `maxLevels` / `globalMaxLevel` 两个键，**能直接把原版附魔上限抬上去**。而我们的 §10 一档计划是「另做超级锋利、超级保护」。
两条路成本差一个量级：抬原版上限 = 改配置；做新附魔 = 做 schema + 生成 datapack + 处理互斥。**先定走哪条**，再动 M4。

### B8 经济没有 mod 在提供

`config/SDMEconomy/` 与 `config/SDMShop/` 只剩配置残留（含 `_migration_from_old_server/`），`mods/` 里没有对应 jar。
→ 货币归 **RedtaleEconomy**（Aki 指定的参考项目）。我们的修理、重铸、工作台、附魔都要扣费，需要一个**扣费接口**，不能自己记账。D 系列目前没有这条边界。

### B9 GUI/HUD 底座要不要用 owo-lib

`owo-lib-0.12.15.5-beta.1` 在服上（Accessories 依赖它）。它提供 Screen/HUD 基座。
不用它 → 自写 `AbstractContainerScreen` + 自定义 payload，工作量大但零依赖；用它 → 白名单破例，但省下 M1~M3 的 GUI 工期。

## 3. 建议补的决策（草案，等 Aki 点头再写进 PLAN）

| 号 | 内容 |
|---|---|
| D21 | 饰品槽位**只挂 Curios**，不碰 Accessories；`artifacts` 的物品走 Curios 映射 |
| D22 | 外部属性**只读不注册**：按 `ResourceLocation` 查 `AttributeMap` 读 `spell_power:*`，代码零 import（B1 方案 C） |
| D23 | 伤害乘区只作用于**我们打 tag 的 DamageSource**，其余放行（B3） |
| D24 | 经济**只调用 RedtaleEconomy 的扣费接口**，不自己记账；接口缺失时降级为「不收费 + 记日志」 |
| D25 | ~~原版附魔强化优先走 `enchantmentdisabler` 的 `maxLevels`，新附魔只用于「原版组件表达不了」的效果（B7）~~ **已被 Aki 12:56 推翻 → D32：另做自研附魔接口，不抬上限** |

## 4. 需要 Aki 拍的三条（都有默认）

1. **B1 spell_power**：C 只读桥接（默认）／A 复用／B 并存隔离。
2. **B2 等级与 XP**：保留原版 XP 给 spell_engine 用、我们的等级独立（默认）／接管 XP 并把它的 `levelCost` 归零。
3. **A 类哪几件进里程碑**：建议 **A1 词条 + A2 工作台 + A4 消耗品**进 M2/M5，**A5 专业 + A6 技能树**进 M7，**A3 矿脉**独立排期，**A7 战利品箱**不做（复用 bountiful/lootr）。

## 5. 裁决（2026-11-15 12:45，Aki 逐条答复）

| 项 | 裁决 | 落点 |
|---|---|---|
| A1 随机词条 | **做** | D22，`docs/ITEM-MODEL.md` §2，M2 |
| A2 自定义工作台 | **暂不做** | D27，留「配方 → 产物」接口 |
| A3 矿脉生成 | **暂不做** | D27，交 `worldweaver`/`lithostitched` |
| A4 消耗品与修理 | **做** | D23，`docs/ITEM-MODEL.md` §3，M2 |
| A5 专业 | **做，后置** | D24，M7 主体之后 |
| A6 技能树 | **做** | D25，`docs/SKILL-TREE.md`，M7 |
| A7 战利品箱 | 不做（复用 `bountiful` + `lootr`） | — |
| A8 原版刷怪替换 | **做** | D26，M6（钩子待技术验证） |
| B1 spell_power 属性重叠 | **只读桥接，不注册同义项** | D31 §12.1 |
| B2 spell_engine 吃 XP 等级 | **不动它，我们的等级独立** | D31 §12.4 |
| B3 乘区套到外部法术 | **只作用于 `redtale_project:*` 的 DamageType** | D31 §12.3 |
| B4 三套饰品系统 | **只认 Curios，且可选**；无 Curios 则整类不注册 | D29 |
| B5 lootr | 掉落表只生成 `LootTable`，不碰 BlockEntity | 沿用 |
| B6 已有替代 | 任务/队伍/传送/NPC/悬赏/权限 全部不自研 | 沿用 |
| B7 enchantmentdisabler | **另做自研附魔接口，不抬原版上限**（Aki 12:56：「本质上是为了测自制的附魔接口」）。disabler 保留但不启用 `maxLevels`，只做减法 | D32，`docs/ENCHANT-API.md` |
| B8 经济 | **可选桥接**（none/command/redtale），不拆第三个 mod | D28 |
| B9 GUI 底座 | **用 owo-lib**（薄封装，缺失降级原版 Screen） | D30 |
| 新增：装备白板化 | **做**（Aki 第 10 条要求） | D21，`docs/ITEM-MODEL.md` §1 |

> B7 结案（2026-11-15 12:56）：**另做**。M4 出口条件随之从「示例附魔能附上」改为「**新加一个附魔只写 JSON5、不编译**」（V11），因为抬上限验证不了接口。全文见 `docs/ENCHANT-API.md`。
