# 热更新策略：把「换 jar」压到最低

> 状态：v1，2026-11-15。对应 Aki 的要求「减少更换 jar 的次数，最好能在服务端热更新新内容」。

## 0. 一句话

**把「内容」和「代码」彻底切开。** 内容 = 数据，热加载；代码 = 注册表项，启动冻结。
一次投入把「容器」预制够，之后绝大多数新内容都只是写配置。

## 1. 1.21.1 的硬边界

### 启动期冻结（改不了，只能常驻）

`BuiltInRegistries` 在 `Bootstrap` 阶段 freeze：

```
ITEM  BLOCK  ENTITY_TYPE  ATTRIBUTE  DATA_COMPONENT_TYPE
MOB_EFFECT  PARTICLE_TYPE  SOUND_EVENT  RECIPE_TYPE
BLOCK_ENTITY_TYPE  MENU_TYPE  ...
```

### datapack registry（可以运行时重载）

```
ENCHANTMENT  DAMAGE_TYPE  INSTRUMENT  JUKEBOX_SONG  CHAT_TYPE
BANNER_PATTERN  TRIM_PATTERN  TRIM_MATERIAL  DIMENSION_TYPE
CAT_VARIANT  FROG_VARIANT  WOLF_VARIANT  WOLF_SOUND_VARIANT
PAINTING_VARIANT  WorldGen（biome / noise / structure / template_pool / structure_set）
```

第三方扩展的 datapack registry 也在此列，例如 `bettercombat:weapon_attributes`（BetterCombat 用 `WeaponRegistrySync` 同步到客户端）。

**这条边界决定了全部设计**：能放进 datapack 的都能热更；放不进去的必须预制。

## 2. 四个「容器」：一次预制，长期不换

| 容器 | 数量 | 为什么必须预制 |
|---|---|---|
| **基底物品类型** | 14 | `ITEM` 冻结。剑/匕首/斧/锤/长矛/法杖/弓/盾/头盔/胸甲/护腿/靴子/戒指/护符 |
| **属性** | 80 | `ATTRIBUTE` 冻结。近战 12 / 防御 18 / 法术资源 18 / 采集移动 10 / 通用 6 / 预留 16（不上面板） |
| **实体原型** | 8 | `ENTITY_TYPE` 冻结。近战/远程/施法/召唤/冲锋/飞行/精英/环境 |
| **效果原语** | 32 | effect / trigger / condition 各若干，代码注册 |

宁可空着，也别不够。属性注册 80 条、其中 16 条空着的成本 ≈ 0；发现少一条而换 jar 的成本 = 一次发布 + 全网更新。

## 3. 一个通用数据组件承载所有内容

```java
// 只注册这一个类型，所有内容共用
public static final DataComponentType<ContentData> CONTENT =
    DataComponentType.<ContentData>builder()
        .persistent(ContentData.CODEC)
        .networkSynchronized(ContentData.STREAM_CODEC)
        .build();
```

`ContentData` 至少包含：

```json5
{
  content_id: "red_maple_longsword",   // 我们自己的内容 id
  set_id: "red_maple",                 // 套装（可空）
  sockets: [{ gem: "ruby_3", locked: false }],
  rolled_stats: { attack_damage: 7.3, critical_chance: 0.04 },  // 每件实例不同
  upgrade_level: 2,
  weapon_attributes: "redtale_project:longsword",
  abilities: ["cleave", "bleed_on_hit"],
}
```

好处：**新内容类型不需要新的组件类型**。以后想加「耐久词条」「附魔槽」，往 `ContentData` 里加字段即可（旧物品缺字段用默认值）。

## 4. 能热加 vs 不能热加（完整表）

| 内容 | 热加 | 手段 |
|---|---|---|
| 武器 / 装备 / 宝石 / 材料 | ✅ | 基底 Item + `ContentData` + 配置 |
| 套装（件数、加成） | ✅ | 配置 |
| 附魔 | ✅ | 生成 datapack JSON → `/reload` |
| 附魔的触发效果 | ✅ | effect 原语组合 |
| 武器动作 / hitbox（BetterCombat） | ✅ | 生成 `weapon_attributes` |
| 怪物（属性/技能/掉落/模型/音效/体积） | ✅ | 模板实体 + 配置 |
| 技能（组合原语） | ✅ | 配置 |
| 掉落表 / 刷怪规则 | ✅ | 配置 |
| 经验曲线 / 等级上限 / 职业开关 | ✅ | 配置 |
| 数值闸门 / 异常保护 | ✅ | 配置 |
| GUI 布局 / tooltip 模板 | ✅ | 配置（用我们自己的 DSL） |
| **新基底物品类型** | ❌ | 换 jar（14 个应该够） |
| **新属性** | ❌ | 换 jar（80 条含 16 条预留，够用） |
| **新实体原型** | ❌ | 换 jar（8 个应该够） |
| **新效果原语（组合不出来的）** | ❌ | 换 jar（32 个应该够） |
| **新的 mixin 点**（改原版/别家行为） | ❌ | 换 jar |
| 新维度 | ✅ | datapack |

## 5. 热加载怎么实现

| 内容 | 触发方式 | 实现 |
|---|---|---|
| 配置（json5） | `/redtale reload` 或文件监听 | 解析 → 校验 → 替换内存表 → 刷新在线玩家的属性/装备（重算 modifier） |
| 附魔 | `/redtale reload` 内部调 `server.reloadResources()` | 先生成到 `world/datapacks/redtale_project/`，再触发 reload |
| 武器动作 | 同上 | 生成 `data/<ns>/weapon_attributes/` |
| 客户端资源（模型/贴图/音效） | 客户端 F3+T，或服务端换 `server-resource-pack` | 资源包 |

三条注意：

1. **`/reload` 会重载全部 datapack registry**，不只是我们的。别的 mod 的 datapack 也会重建。所以不要频繁触发，只在内容变更时一次。
2. **reload 时在线玩家的属性要重算**：卸掉旧的 `AttributeModifier`（按固定 id），装新的。漏了会出现「配置改了但面板没变」。
3. **已存在的物品不会自动更新**：reload 只影响「新生成」和「重新计算」。改了某件装备的 base 值，已发出的那件仍是旧值——这是对的，避免偷改玩家财产；要统一改就写一次性迁移命令。

### 5.1 种子语义：默认配置只补缺，不覆盖（R139-a）

`ConfigStore.ensureDefaults()` 的行为是：`config/redtale_project/<name>` **存在就跳过**，不存在才从 jar 内 `/redtale/defaults/<name>` 写一份。含义三条：

| 场景 | 结果 |
|---|---|
| jar 里**新增**一份默认文件 | 下次启动 / reload 自动落盘，生效 |
| jar 里**修改**一份已有默认文件 | 已有服的 `config/` 里仍是旧内容，**不生效**（这是有意的：玩家改过的配置不能被升级抹掉） |
| 想推广默认值改动 | 得由运维把新默认覆盖上去（或写迁移），否则升级 jar 后行为不变 |

验收/发布时因此有一条纪律：**改了默认文件的内容卡，验之前必须确认 live `config/` 已是新内容**（`reload` 报的是「读了几份文件」，不是「内容是否等于 jar」）。R139 验收 T-0133 时就踩了一次：新加 5 条词条没问题（只加文件），但改过的 `sword_common.json5` 仍是 11-16 的旧版（`core: 0`），连摇 12 次全是 2 条旧词条，同步 live 后才出现第 3 条。


## 6. 代价，说清楚

| 代价 | 影响 | 缓解 |
|---|---|---|
| 创造模式物品栏只有 14 个条目 | 找东西不方便 | 自建内容图鉴 GUI（`/redtale codex`） |
| JEI / REI 不会列出我们的装备 | 玩家不知道怎么获取 | 同上；或生成「图鉴物品」 |
| `/give @s redtale_project:xxx` 不能按内容 id | 命令不直观 | `/redtale give <content_id> [count]` |
| 掉落表不能直接用原版 loot table 描述内容 | 需要我们的格式 | 我们的格式生成原版 loot table |
| 一件物品的「身份」在组件里，不在 id 里 | 调试要靠 NBT 看 | 提供 `/redtale inspect` |

## 7. 什么情况仍然要换 jar（预计频率）

| 情况 | 预计频率 |
|---|---|
| 发现少一条属性 | 一次性（M1 就该定完） |
| 想做新原型实体（比如「飞行轰炸型」） | 罕见，一年 0~1 次 |
| 组合不出来的新机制（比如「改地形」「吸蓝」） | 罕见，一年 1~2 次 |
| 新增 mixin 点（改别人行为） | 罕见 |
| 修 bug | Bug 修复版，正常发布节奏 |

换句话说：**内容更新不换 jar，代码更新才换 jar。**

## 8. 两个降低频率的技巧

1. **原语组合优先**：新技能/新附魔先用现有原语拼（`damage` + `apply_effect` + `spawn_particle` + `condition`）。拼不出来才加原语。MythicMobs 的 424 个机制里，绝大多数怪物技能只用了 30~40 个高频机制。
2. **预留冗余**：属性 80（含 16 条预留）、原语 32、原型 8。空着不花钱，缺了很贵。

## 9. 客户端侧的热更新

| 内容 | 能否热更 |
|---|---|
| 模型 / 贴图（GeckoLib `.geo.json` + png） | ✅ 资源包 F3+T |
| 音效（需要 `sounds.json` 注册） | ✅ 资源包 F3+T |
| 技能特效（粒子 / 拖尾） | ✅ 资源包 + 服务端数据 |
| 新的渲染类型（全新实体外观方式） | ❌ 需要改客户端代码 |

因为 D10 允许下发资源包，模型和音效都能跟着服务端内容一起更新，不需要玩家改整合包。
