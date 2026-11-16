# 上游能力盘点（第一轮，纯 jar 证据）

采集时间：2026-11-15　来源：`D:\redtale-work\upstream\*.jar`（原始位于 `C:\Users\AkiFish3\Downloads`）
方法：`zipfile` 读条目 / `plugin.yml` / 内置默认配置。**未反编译代码**，所有类名均为 jar 内真实路径。

## 0. 一个必须先说的发现：五个插件不是完整依赖面

| 插件 | plugin.yml 里的硬依赖 | 我们手上有没有 |
|---|---|---|
| MMOItems 6.10 | `MythicLib` | ❌ 缺 |
| MMOCore 1.13 | `MythicLib` | ❌ 缺 |
| MMOInventory 1.9 | `MythicLib` | ❌ 缺 |
| EcoEnchants 2026.46 | `eco`（软：`libreforge`） | ❌ 缺 |
| MythicMobs 5.14 | 无（自带依赖库） | ✅ |

**MythicLib 才是那套系统的公共底座**（属性、伤害计算、占位符、兼容层），MMOItems/MMOCore/MMOInventory 都只依赖它。要评估移植量，得先看 MythicLib。

## 1. 规模

| 插件 | 条目 | class 数 | 备注 |
|---|---|---|---|
| MMOItems | 1025 | 676 | 最大包 `net/Indyuce/mmoitems/stat`(117) |
| MMOCore | 954 | 667 | `experience/source`(64)、`gui`(48) |
| MMOInventory | 124 | 86 | 小，纯槽位逻辑 |
| EcoEnchants | 258 | 119 | `enchant`(27)、`display`(18) |
| MythicMobs | 13391 | 12417 | `skills/mechanics`(424)，内含 jOOQ/jedis/caffeine |

## 2. MMOItems：物品 = 数据 + 属性滚动

内置 `default/item/*.yml` 是完整的物品定义样例（armor / dagger / greatsword / staff / wand / material / miscellaneous）。关键结构（摘自 `default/item/armor.yml`）：

```yaml
DRAGON_HELMET:
  base:
    material: DIAMOND_HELMET
    max-durability: 5500.0
    tier: EPIC
    max-health: 4.0
    armor-toughness: 3.0
    magic-damage-reduction:      # 属性可以是标量，也可以是可滚动的区间
      base: 3.0
      spread: 0.05
      max-spread: 0.17
    element:
      fire: { defense: { base: 5.0, scale: 0.0, spread: 0.07, max-spread: 0.2 } }
      darkness: { defense: { base: 12.0, ... } }
    gem-sockets: [Magenta, Black]   # 宝石孔
    set: DRAGON                     # 套装归属
    required-level: 12.0
    enchants: { fire_protection: 4.0 }
```

→ 移植的核心不是「物品注册」，而是**属性值模型 `{base, scale, spread, max-spread}` + 属性 ID 注册表 + 宝石/套装/等级门槛**。

属性 ID 从类常量里能扫到 363 个候选串，其中确认属于 stats 的包括：
`ATTACK_DAMAGE` `ATTACK_SPEED` `ARMOR_TOUGHNESS` `CRITICAL_STRIKE_CHANCE` `CRITICAL_STRIKE_POWER` `BLOCK_POWER` `BLOCK_RATING` `BLUNT_POWER` `BLUNT_RATING` `DAMAGE_REDUCTION` `DEFENSE_PERCENT` `COOLDOWN_REDUCTION` `ARROW_VELOCITY` `ARROW_SHOOTING` `BLOCK_BREAK_SPEED` `ACCESSORY_SET` `CUSTOM_STATS` 等。
（扫描含噪声，精确清单要反编译 `ItemStats` 类。）

## 3. MMOCore：等级/经验/职业

`experience/source` 下 66 个类，约 22 个真实经验源，每个都是 `XxxExperienceSource` + `$Manager` + `$1` 三件套：
`BrewPotion` `Climb` `CraftItem` `DamageDealt` `DamageTaken` `Eat` `EnchantItem` `FishItem` `FromExperience` `KillMob` `MineBlock` `Move(MovingType)` `PlaceBlock` …（还有更多未截断）

→ 抽象成「经验源注册表 + 事件监听 + 权重配置」，每个源一个实现类，可数据开关。

## 4. MMOInventory：槽位 + 限制

槽位类：`CustomSlot` `SlotType`(+5 匿名子类) `VanillaSlotHandler`
限制类：`ClassRestriction` `LevelRestriction` `LoreTagRestriction`(`CheckType`/`Operation`) `PermissionRestriction`

→ 4 种限制足够覆盖；槽位挂 **Curios**（1.21.1 服上已装 9.5.1）。

## 5. EcoEnchants：本身就是数据驱动

jar 内 `enchants/` 有 100+ 个 `.yml`（`abrasion` `adrenaline` `bleed` `blast_mining` …）加 `_example.yml` 模板。模板字段：

```
display-name / description(支持 %level% 与自定义占位符) / placeholder / placeholders
type / targets / conflicts / required(id:level [remove]) / rarity / max-level
tradeable / discoverable{chests,fishing,mob-drops,raids} / enchantable
hide-from-enchantgui / drag-and-drop{enabled,price} ...
```

→ **这就是我们要做的「附魔接口」的现成 schema**；实现映射到 1.21.1 原生 `Enchantment` 数据注册表 + 自定义 effect component，附魔书就是原版 `EnchantedBookItem`，铁砧/砂轮/图书管理员交易天然一致。

## 6. MythicMobs：怪物是数据，机制是代码

`skills/mechanics` 424 个类（代码实现的「机制」），而怪物、技能组、掉落、刷怪点全是 YAML。
**这正是我们方案 4（模板实体 + 数据驱动）要复刻的分工**，也证明了「每周 1 只新怪」这种节奏不需要改代码。

## 7. 补齐的两个公共底座（2026-11-15 09:21 入库）

| 文件 | 条目 / class | 结构 | 许可证 |
|---|---|---|---|
| `MythicLib-1.7.jar` | 2756 / **2505** | 全部在 `io/lumine/mythic`；`plugin.yml` 自称 "Hard dependency for the MMO plugins"，带 `/ml` 命令（healthscale / tempstat / debug） | 闭源（Premium 依赖） |
| `eco-2026.46-modrinth.jar` | 8364 / **7891** | `com/willfp/eco`(7240) + 内置 `org/jetbrains/exposed`(613，SQL ORM) | **MIT** |
| `libreforge`（GitHub） | Gradle 多模块：`core` `lib` `loader` `gradle-plugin` `ecohub` | 用 Kotlin 写的「YAML 脚本引擎」，给开发者提供 effect / condition / trigger 扩展点 | **BUSL 1.1**（非生产用途；生产需商业授权） |

**MythicLib 2505 个类**——比 MMOItems 本体还大。属性、伤害管线、占位符、GUI、兼容层几乎都在这里，MMOItems/MMOCore/MMOInventory 三个插件加起来 1429 个类，靠在它上面。

## 8. 关键发现：EcoEnchants 的附魔其实是「脚本」

`enchants/bleed.yml` 全文：

```yaml
display-name: "Bleed"
description: "Gives a &a%placeholder%%&r chance to cause your opponent to bleed..."
placeholder: "1.5 * %level%"
type: normal
targets: [sword]
conflicts: []
rarity: legendary
max-level: 7
tradeable: true
discoverable: true
enchantable: true
effects:
  - id: bleed                 # ← 代码里注册好的「效果」
    args: { chance: "1.5 * %level%", damage: 1, interval: 15, amount: "2 * %level%" }
    triggers: [melee_attack]  # ← 代码里注册好的「触发点」
conditions: []
```

jar 内共 **103 个附魔定义**（abrasion / adrenaline / bleed / lifesteal / veinminer / telekinesis …），全部只有元数据 + `effects[]` + `conditions[]`，没有一行代码。

→ 真正的产品不是「附魔」，而是 **effect / trigger / condition 三个注册表 + 一套数值表达式**。附魔只是它的一个使用面。
→ 推论：MMOItems 的技能、MMOItems 的宝石效果、怪物技能，理论上都该复用同一套脚本层。这决定架构（见 `PLAN.md` §4）。

## 9. 许可证矩阵（决定能不能抄代码）

| 来源 | 许可 | 能不能把代码搬进本模组 |
|---|---|---|
| eco | MIT | ✅ 可以（保留版权声明） |
| libreforge | **BUSL 1.1** | ❌ **不行**。非生产用途才免费；跑在公开服上属于生产使用，需商业授权。只能「看思路，自己写」 |
| EcoEnchants | 未见 LICENSE 文件，闭源分发 | ❌ 只参考 YAML 结构与行为 |
| MMOItems / MMOCore / MMOInventory / MythicLib | Premium 闭源 | ❌ 只参考行为 |
| MythicMobs | 闭源 | ❌ 只参考行为 |

这条必须写死：**libreforge 是 BUSL，不是开源**。之前说的「允许参考」仅限行为与配置结构，代码一律自研。

## 10. 待补

1. ~~MythicLib / eco~~ ✅ 已入库；libreforge 只有源码仓（BUSL，不移植）。
2. 反编译（Vineflower）才能拿到：MythicLib 的属性 ID 与伤害管线入口、MMOItems 的技能（ability）实现、宝石结算、套装结算。
3. 你们实际用到的 MMOItems 类型/属性（不做格式兼容，但影响 M1 最小集合）。
