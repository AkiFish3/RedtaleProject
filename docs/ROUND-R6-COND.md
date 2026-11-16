# R6 · M2 第二步：条件乘区（pve / pvp / undead）

> 2026-11-15 23:26–23:52 · S7 · 测试服 `Done` 后已停服（端口 25567 不再监听）

## 1. 本轮做了什么

| 项 | 内容 |
|---|---|
| 新类 | `core/damage/TargetClass.java`（目标分类，唯一实现） |
| 改 | `MultiplierRules`：新增第 4 个组件 `conditionalAttributes`（`条件键 → 属性清单`）+ 三参兼容构造 + `describe()` 打印条件段 + `conditional_attributes` 解析 |
| 改 | `DamageLayer`：`sumPercent` 拆成 `sumAttributes(List)`，新增 `sumConditional(attacker, target, rules)` |
| 改 | `redtale/defaults/damage.json5`：新增 `multiplier.conditional_attributes`（默认三条全开） |
| 改 | `SelfTest`：46 → **55 PASS**（+9：分类 4 / 解析 3 / 加法不连乘 1 / 兼容 1） |
| 派单 | **T-0003**（词条定义/词条池解析器，D22 第一层）；**T-0001 前置已由 S7 代办**（BetterCombat 真实 schema 贴进卡里） |

构建：`gradle build --offline` exit 0（取 `.queue/build.lock`，跑完删）；`gradle selftest --offline` **55 PASS**。

## 2. 三条口径（后续沿用）

| # | 口径 | 理由 |
|---|---|---|
| R6-a | 条件项与无条件项**进同一个百分比池相加**，不连乘 | 物理 +20% 与「对亡灵 +30%」打到僵尸是 ×1.5，不是 ×1.56。与 `DAMAGE-PIPELINE` §2「先加后乘」一致；玩家对账时也按加法理解 |
| R6-b | 分类口径：玩家 → `pvp`；亡灵（`#minecraft:undead`）→ `pve` **+** `undead`；其它生物 → `pve` | 「对怪物伤害」覆盖全部非玩家目标，「对亡灵伤害」是它的子集加成。互斥会让同一件装备换目标时整段失效 |
| R6-c | 条件段**默认就开**，不设独立开关 | 三条属性基值都是 0，没有内容写它们时行为与关着完全一致（D1 全做，不为省事留开关） |

配置写法：整段删掉 = 用默认（三条全开）；显式写 `conditional_attributes: {}` = 关掉条件乘区；写错键（如 `typo_key`）→ 告警 + 跳过，不静默。

## 3. 实测（`redtale_project:verification_strike`，同一数值 10，攻击者 = 一只 husk）

| # | 攻击者属性 | 目标 | 期望 | 实测 |
|---|---|---|---|---|
| A | `pve_damage 0.2` | 牛 | ×1.2 | **10 × 1.2 = 12**（池 0.2） |
| B | `pve 0.2` + `undead 0.3` | 牛 | ×1.2（undead 不该算） | **10 × 1.2 = 12**（池 0.2） |
| C | 同上 | husk（亡灵） | ×1.5 | **10 × 1.5 = 15**（池 0.5） |
| D | 只剩 `pvp 0.5` | 牛 | ×1.0（pvp 不该算） | **10 × 1 = 10**（池 0） |

`/redtale damage` 的乘区行实测输出：

```
乘区：伤害类型 redtale_project:* · 白名单外只观测不改值 · 百分比属性 physical_damage
      · 条件乘区 pvp=pvp_damage / pve=pve_damage / undead=undead_damage
最近 4 次结算：
  redtale_project:verification_strike  10 × 1.2 = 12（百分比池 0.2）
  redtale_project:verification_strike  10 × 1.2 = 12（百分比池 0.2）
  redtale_project:verification_strike  10 × 1.5 = 15（百分比池 0.5）
  redtale_project:verification_strike  10 × 1 = 10（百分比池 0）
```

前提确认：`net.minecraft.tags.EntityTypeTags.UNDEAD` 与 `EntityType#is(TagKey)` 在 21.1.257 的 `server-srg` 里都存在（javap 实读，不是记忆）。

## 4. 未验证 / 遗留

1. **pvp 真实目标未验**（无客户端，没有真人玩家可打）。D 组只证明「pvp 项对非玩家不生效」，没证明「对玩家生效」。挂账第 6 轮（与攻速玩家上下文同一条缺口）。
2. **条件乘区分项显示**：`/redtale damage` 现在只打**合计池**（0.5），不拆「物理 0.2 / 亡灵 0.3」。拆分要改 `DamageTrace.Entry` + `RedtaleCommand` 的打印——`RedtaleCommand.java` 本轮属 **T-0002 独占文件**，故未动，记入 §13.10。
3. **`weapon_attributes` 只有数据没有组件**：`ItemFactory` 把 `content.weaponAttributes()` 写进 `ContentData`，但**从未**设成 BetterCombat 的 `bettercombat:weapon_attributes` 物品组件 —— 也就是说 T-0001 生成的 datapack 文件即使存在，物品也不会自动用上它。这是本轮查代码发现的新缺口（R7 待做，做法：拿 `DataComponentType.codec()` 用 JSON 反序列化，全程不 import BetterCombat 类）。
