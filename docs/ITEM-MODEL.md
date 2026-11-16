# 物品模型：白板化 / 词条 / 消耗品与修理

2026-11-15 定稿。对应 D21~D23、D27。上游证据见 `UPSTREAM-1-Inventory.md`，缺口来源见 `GAP-AUDIT.md` A1/A2/A4。

## 1. 白板化（D21，最重要的一条）

### 1.1 规则

> **基底材质只提供「外观 / 耐久 / 标签 / 能否穿戴」，一切数值由 `ContentData` 决定。**

玩家看到的、结算用的，都只有我们填的那个数：一件写着「20 护甲」的自定义胸甲，穿上就是 **20**，不是 `20 + 钻石胸甲自带 8 = 28`。

### 1.2 实现（2026-11-15 用 `neoforge-21.1.257-server.jar` 的字节码核对过）

先纠正一个容易搞错的前提：**1.21.1 里 `ArmorItem` / `SwordItem` / `DiggerItem` 都还在**（`net.minecraft.world.item.*`），
「`ArmorItem` 已并入 `Item`」是 1.21.2 之后的事。所以白板化不能靠「换掉类」，得靠组件的优先级。

关键在 `ItemStack.forEachModifier` 的真实分支（`javap -c` 实测）：

```
1. 读 DataComponents.ATTRIBUTE_MODIFIERS（缺省 = ItemAttributeModifiers.EMPTY）
2. 若 modifiers 列表【非空】 → 只用组件，走完直接跳过第 3 步
3. 若为空 → 回落到 item.getDefaultAttributeModifiers()
   （ArmorItem 覆写此方法，返回材质换算出的护甲/韧性；Item 基类返回 EMPTY）
4. 最后再叠加附魔带来的 modifier
```

**结论：只要我们给物品设一个「非空」的 `ATTRIBUTE_MODIFIERS` 组件，基底的材质加成就会被整段跳过。**
这正是 D21 要的效果，而且不用改类、不用自定义 `ArmorMaterial`：

```java
// 一件「20 护甲」的自定义胸甲：外观/穿戴/耐久仍来自钻石甲，数值只剩我们写的
new ArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.CHESTPLATE,
    new Item.Properties()
        .durability(600)
        .attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ARMOR, new AttributeModifier(我们的id, 20.0, ADD_VALUE), EquipmentSlotGroup.CHEST)
            .build()));
```

各 API 已核对存在：`Item.Properties#attributes(ItemAttributeModifiers)`、`#durability(int)`、
`ItemAttributeModifiers(java.util.List<Entry>, boolean)`、`ItemAttributeModifiers.builder()`、`ItemAttributeModifiers.EMPTY`。

> ⚠️ **必须记住的坑**：判定是「列表非空」而不是「组件存在」。所以**纯装饰装备（0 属性）如果给空列表，会回落到底材的数值**。
> 这类装备要显式塞一个 `+0` 的占位 modifier（或者干脆让它继承，看设计意图）。M1 验收项里加一条「0 属性装备穿上后 `armor` 仍为 0」。

### 1.3 三条边界（必须写进文档，否则以后扯皮）

| 项 | 是否被白板化 | 说明 |
|---|---|---|
| 原版属性（护甲/韧性/攻击/击退抗性/挖掘速度） | ✅ 全部清掉 | 只留我们配的数 |
| 原版附魔效果（保护、锋利、耐久） | ❌ **保留** | 它们是 effect component（`damage_protection` 等），不是属性；玩家自己附的，不该被抹掉 |
| 材质外观、物品标签（`#minecraft:swords`） | ❌ 保留 | 标签决定能不能被附魔/被配方识别 |

若某件装备要求「连附魔减伤也不吃」，用配置项 `strip_vanilla_effects: true`（移除 `enchantable` + 相关 tag），这是**逐物品**开关，不做全局默认。

### 1.4 与别人抢数值的风险

- `AttributeFix` 已装（去掉了 `RangedAttribute` 的 1024/30 上限）→ 20 护甲能正常生效；客户端同包，不会被钳。
- BetterCombat 读 `generic.attack_damage` 与 `weapon_attributes` → 白板后照常工作。
- `spell_power` 的 `enchantments_require_matching_attribute` 与本条无关。
- 别的 mod 若用 `ItemStack#getAttributeModifiers()` 求值，会读到我们的值（正确）；若硬编码 `instanceof ArmorItem`，1.21.1 已无此类，风险低。

### 1.5 已知代价

tooltip 里原版那行「+8 护甲」会消失，只剩我们生成的（与 D20 一致）。这是特性，不是 bug——但**必须**让 `/redtale attr` 能打印「基底被清空」这一事实，否则查数值时会以为丢东西。

## 2. 随机词条（D22，A1）

上游 `MMOItems default/modifiers/example_modifiers.yml` 给了现成语义：

```yaml
sharp:  { chance: 0.3, prefix: '&fSharp', stats: { attack-damage: 3 } }
group_example: { max: 3, modifiers: { sharp: 1, fiery: 1, lucky: 1 } }
fiery:  { prefix: '&cFiery', stats: { ability: { on-hit: { type: burn } } } }
lucky:  { prefix: '&fLucky', suffix: ..., stats: { critical-strike-chance: {base: 9, spread: .1} } }
```

照这个模型抄结构（不抄代码），但改用 JSON5 与我们的 `StatValue`：

```json5
// config/redtale_project/affixes/sharp.json5
{
  id: "sharp",
  kind: "prefix",                 // prefix | suffix | core
  weight: 100,                    // 池内权重（不是概率）
  item_level: { min: 1, max: 60 },// 可出现的物品等级区间
  exclusive_group: "damage",      // 同组最多出一条
  stats: {
    attack_damage: { base: 3.0, per_item_level: 0.08 },
  },
  abilities: [],                  // 复用脚本层：on-hit / on-kill / passive
  name_key: "affix.redtale.sharp",
}
```

词条池（决定「这件装备会 roll 出哪些」）：

```json5
// config/redtale_project/affix_pools/sword_common.json5
{
  id: "sword_common",
  applies_to: ["redtale_project:sword"],   // item type 或 tag
  slots: { prefix: 1, suffix: 1, core: 0 },// 每件装备的词条槽数
  prefix: { sharp: 100, heavy: 80, swift: 60 },
  suffix: { of_the_bear: 60, of_flame: 40 },
  reroll_cost: { currency: "coin", amount: 200 },
}
```

关键决定：

- **生成时机 = 掉落时**（`LootTable` 生成那一刻 roll），不是拾取时。否则同一件物品在不同人手里数值不同。
- 词条结果**存进 `ContentData.affixes[]`**，tooltip、结算、`/redtale attr` 三处读同一份 → 不会出现「写着 +3 打出 +5」。
- 条数上限是**投放层**（`slots`），不做全局钳制（D16）。
- 重铸 = 重 roll + 扣费（走 D28 经济桥）。词条不改变物品 ID，只改组件 → 可热更新。
- 命名用 `affix` 而不是上游的 `modifiers`：`modifiers` 在我们的代码里已被 `AttributeModifier` 占用，容易混。

## 3. 消耗品与修理（D23，A4）

### 3.1 消耗品

上游 `default/item/consumable.yml` 的字段：`effects` / `restore-food` / `item-cooldown` / `repair` / `vanilla-eating` / `disable-right-click-consume`。

```json5
{
  id: "hearty_stew",
  type: "consumable",
  base: "minecraft:rabbit_stew",       // 只提供外观
  use: {
    effects: [{ effect: "minecraft:regeneration", duration: 200, amplifier: 1 }],
    restore_food: 6, saturation: 0.6,
    cooldown_ticks: 400,               // 物品级冷却（ItemCooldowns）
    script: [{ effect: "heal", args: { amount: 4 } }],  // 脚本层，可留空
  },
  consume: "always",                   // always | only_when_hungry
}
```

### 3.2 修理

上游用 `repair: 3.0` 一个数字表示「修多少耐久」。我们做成独立 item type，因为它要能作用于**别的物品**：

```json5
{
  id: "whetstone",
  type: "repair",
  base: "minecraft:flint",
  repair: {
    targets: ["#minecraft:swords", "#minecraft:axes"],
    mode: "amount",                    // amount | percent
    amount: 120,                       // 或 percent: 0.25
    cost: { currency: "coin", amount: 50 },
    consume_item: true,
  },
}
```

配套：`/redtale repair [player] [slot]`（管理员/铁匠 NPC 用），走同一段结算。

**与白板化的交互**：白板化不碰 `MAX_DAMAGE` 以外的耐久逻辑，修理照常。

## 4. 明确不做（D27）

| 不做 | 理由 | 留的口子 |
|---|---|---|
| 自定义工作台（`crafting-stations`，队列/打造时长/GUI） | 运营上暂时不需要；原版配方 + KubeJS 已够 | 数据层只认「配方 → 产物」，将来加台子只是包一层 GUI + 计时，不动物品模型 |
| 自定义矿脉生成（`gen-templates`，`replace:[STONE]`/`vein-size`） | 同上；服上已有 `worldweaver`/`lithostitched` 做地形，插手矿脉收益低 | 若要做，落点是 `BiomeModifier` + `ConfiguredFeature`，与物品无关 |

这两条写在这里是为了**避免以后被当成漏项**：不是忘了，是决定不做。

## 5. 孔位与套装（M3，D1 的宝石 / 套装）

> 2026-11-16 R17 新增。**先定 schema 再派解析器**——S8 没有设计上下文，卡里给什么它写什么。

### 5.1 三层身份，各归各的容器

| 东西 | 存在哪 | 为什么不塞一起 |
|---|---|---|
| **孔位**（物品上的槽） | 内容定义 `sockets` 段（孔数 + 每孔类型） | 定义型字段，改它要 `rev`+1 |
| **宝石** | **另一件内容物品**的 `gem` 段 | 宝石是内容（D17：内容即数据），不是新注册表项也不是新组件 |
| **镶嵌结果** | `ContentData.sockets[]`（孔序，`""` = 空孔）+ `ContentData.gemStats{}` | 玩家资产，`rev` 变化**不许**冲掉（D33 / R10-c） |
| **套装** | 独立定义文件 `sets/*.json5`，物品只写 `set_id` | 件数加成是**派生值**，随穿脱实时变，不能写进物品 |

**关键取舍**：宝石数值**不并进 `rolled_stats`**，单独放 `gemStats`。理由：同一个属性若同时被词条与宝石加过，并进一个键后**拆宝石就扣不准**（词条那部分也会被扣）。两表分开 → 拆宝石 = 重算 `gemStats`，词条一个数都不动。

### 5.2 孔位布局（物品侧）

```json5
{
  id: "demo_longsword",
  sockets: {
    count: 2,
    types: ["offensive", "any"],   // 按孔序；短了末位补 "any"，长了截断
  },
}
```

- `sockets` 缺失 / `count <= 0` → 无孔（`Layout.EMPTY`）
- `count` 夹到 `[0, 6]`，超范围告警（6 是显示上限，不是平衡闸门）
- `types` 元素非空字符串；非法项 → 换成 `"any"` + 告警（不跳过，否则孔序会错位）
- **孔类型词汇开放，不做枚举校验**：`offensive` / `defensive` / `utility` / `any` 只是约定。匹配规则只有一条——「宝石的 `type` 为空 / `any` / 等于孔的 `type`」即为可镶。加新类型不换 jar。

### 5.3 宝石定义（`gem` 段）

```json5
{
  id: "gem_ruby",
  base: "amulet",                    // 只提供外观（14 基底，Q-016 同款取舍）
  rarity: "rare",
  gem: {
    type: "offensive",               // 缺省 "any"
    tier: 1,                         // 缺省 1，仅用于显示与排序
    exclusive_group: "ruby",         // 缺省 ""（空 = 不互斥）
    stats: { physical_damage: 0.05, attack_damage: { base: 2, per_item_level: 0.1 } },
  },
}
```

- `stats` 与词条**同一条解析与归一规则**（`AffixPools.parseStats` / `normalizeKey`，R11-b），值类型 `StatValue`，两种写法（裸数字 / 曲线对象）可混用
- `exclusive_group` 非空时：同一件装备上同组只能有一颗（判定在镶嵌时，解析器不管）
- 缺 `gem` 段 → **不是宝石**，返回 `null` 且不告警（与 `use` / `repair` 同口径）

### 5.4 套装定义（`sets/*.json5`）

```json5
{
  id: "emberforged",
  name: "炽锻",
  pieces: ["redtale_project:demo_longsword", "redtale_project:demo_chestplate", "redtale_project:demo_robe"],
  bonuses: [
    { count: 2, stats: { physical_damage: 0.1 } },
    { count: 3, stats: { attack_damage: 5 }, name: "炽锻·全" },
  ],
}
```

- 件数 = 玩家已穿戴（护甲 4 格 + 主手 + 副手）里 `set_id` 命中的**不同内容 id** 数；重复的两件只算一件
- `bonuses` 按 `count` 升序，**所有 `count <= 当前件数` 的档全部生效**（不是只取最高档）；同档多项相加
- 套装数值**永不进 `ContentData`**：每次装备变化重算，写进固定的 `AttributeMap` modifier id `redtale_project:set/<setId>/<count>`，先移除再加（与 D18 攻速同一条纪律）
- 物品侧只写 `set_id: "emberforged"`（定义型字段，改它要 `rev`+1）

### 5.5 M3 的落地顺序（谁做什么）

| 步 | 内容 | 归谁 |
|---|---|---|
| 1 | 本文档 §5（schema 定稿） | S7 ✅ 本轮 |
| 2 | `SocketSpec`（孔位布局 + 宝石定义解析，纯解析） | S8（T-0013） |
| 3 | `ContentData` 加 `sockets[]` / `gemStats{}`，schema 1 → 2 | S7（注册表结构 + codec） |
| 4 | 镶嵌 / 取出结算 + tooltip 孔位行 + `/redtale socket` | S7 ✅ R19 |
| 5 | `SetSpec`（套装定义解析，纯解析） | S8（T-0014） |
| 6 | 件数统计 + 属性下发 + tooltip | S7 |

**为什么 3 不能给 S8**：`ContentData` 是注册表项（组件类型启动期冻结），改 codec 会牵动 `ItemFactory` / `ItemUpgrader` / `ItemScanCommand` 三处读路径。
