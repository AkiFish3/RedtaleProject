# R5 · M2 第一步：内容数据组件 + 14 基底物品 + 白板化 + tooltip

> 2026-11-15 22:10–22:25 · S7 队列 · 对应 D17 / D20 / D21 / Q-014
> 本轮只推进「最小可验证的一步」：把「一件装备」这条最细的竖切打完（组件 → 物品 → 数值 → 显示），
> 不铺内容、不做词条/宝石/套装。

## 1. 一句结论

一件内容定义的装备，现在能：注册（14 基底）、白板化（写 20 护甲就是 20）、出 tooltip（单次伤害 + 攻速），
并且**服务端就能验收**（不需要客户端）。三组实测：`3 → 20 → 0`。

## 2. 产出（新增 9 类 + 3 份默认配置）

| 文件 | 作用 |
|---|---|
| `items/ContentData.java` | 唯一内容组件（7 字段，codec + stream codec） |
| `items/ContentComponents.java` | `DataComponentType` 注册（全模组只有 `redtale_project:content` 一个） |
| `items/ItemBase.java` | 14 基底枚举（槽位 / 护甲型 / 默认耐久） |
| `items/RedtaleItems.java` | 14 个 `Item` 注册（护甲用 `ArmorItem` + `LEATHER`，其余普通 `Item`） |
| `items/ItemContent.java` | `items/*.json5` 的解析与校验（语义错误只跳过这一条） |
| `items/ItemContentRegistry.java` | 不可变快照，reload 整份替换 |
| `items/ItemFactory.java` | 建栈：组件 + 名称 + 稀有度 + `MAX_DAMAGE` + **白板化 `ATTRIBUTE_MODIFIERS`** |
| `items/ContentTooltip.java` | D20 数值行（与 `settle()` / `AttackSpeedCurve` 同源） |
| `items/ItemTooltipHook.java` | `ItemTooltipEvent` 一处覆盖 14 基底 |
| `items/StatFormat.java` | 显示单位（`+20%` vs `+20`） |
| `redtale/defaults/items/demo_{longsword,chestplate,robe}.json5` | 三件验收件 |

命令新增：`/redtale items`、`/redtale give <内容id> [数量]`、`/redtale tooltip [内容id]`、`/redtale whitening`。
`/redtale status` 追加「内容 N 件装备 / 14 个基底物品」。

## 3. 实测证据

构建：`gradle build --offline` exit 0；`gradle selftest --offline` **46/46 PASS**（36 → 46，新增 10 条内容解析/显示单位）。
测试服：`Done (2.189s)`，14 mods，跑完已停服（端口 25567 不再监听）。

### 3.1 白板化（D21，本轮最关键）

```
/redtale whitening
  原版皮革胸甲          → 3（这是要被打掉的底材数值）
  demo_chestplate（写 20）→ 20  [OK] 白板化生效
  demo_robe（0 属性）    → 0  [OK] +0 占位堵住了回落
```

三组数对上 `docs/ITEM-MODEL.md` §1.2 的两条断言：组件非空 → 跳过底材；空列表 → 回落（所以必须 `+0` 占位）。

### 3.2 tooltip（D20）

```
/redtale tooltip demo_longsword
  示范长剑（redtale_project:sword）
  ≈ 9.6 /次（physical_damage +20%）
  攻速 12.5 tick · 1.6 次/秒（未持有，不含药水/急迫）
```

对账：玩家攻击伤害基值 **1.0**（见 §4 第 1 条）+ 攻击伤害 **+7** = 8.0；× (1 + 0.2) = **9.6**；
攻速 1.6 次/秒 → 20 / 1.6 = **12.5 tick**。与 `settle()` / `AttackSpeedCurve` 的单元测试值一致。

护甲件不再打印这两行（`demo_chestplate` → 「无武器数值行」），数据仍走原版属性行（+20 护甲）。

### 3.3 坏配置只跳过不中断（R1-a 复核）

放一份 `base: "katana"` 的坏文件后 `reload` → `已载入 1 条脚本 + 3 件装备内容（7 份配置文件），1 条告警`，
其余三件照常可用；删掉坏文件再 reload 恢复干净。文件名以 `_` 开头的会被整体跳过（R1-b，实测确认）。

### 3.4 回归

`/redtale speed probe` 仍全绿：`1024 → 触地板 1 tick`、急迫 II `4 → 4.8`（数值不变、无残留加成）。M1 未被打坏。

## 4. 三条新事实（都踩过，写下来）

1. **玩家的 `attack_damage` 基值是 1.0，不是注册表默认的 2.0。**
   证据：`Player#createAttributes()` 字节码里 `dconst_1` 直接塞给 `Attributes.ATTACK_DAMAGE`。
   tooltip 用 `Attributes.ATTACK_DAMAGE.value().getDefaultValue()`（=2.0）会把每把武器算多 1 点，
   实测写成 10.8 而非 9.6。已改成常量 `ContentTooltip.PLAYER_BASE_ATTACK_DAMAGE = 1.0`。
2. **装备属性不是 `setItemSlot` 立刻进 `AttributeMap` 的，要等 tick。**
   第一版 `whitening` 用 `FakePlayer.setItemSlot(CHEST, stack)` 再读 `ARMOR`，三组全 0（连原版皮革甲也 0）——
   因为 `LivingEntity` 每 tick 才 `collectEquipmentChanges` 刷新。改为读
   `ItemStack#forEachModifier(EquipmentSlot, …)` 的净结果，那是原版「组件/回落」分支的入口，
   0 tick、0 客户端就能验。
3. **自建属性基值全是 0，所以内容数值只能 `ADD_VALUE`，不能用 `ADD_MULTIPLIED_TOTAL`。**
   一开始按「百分比属性 → 乘总」写，会让「物理伤害 +20%」乘 0 变成 0。
   现在的口径：**数值一律平加，百分比只影响显示**（`StatFormat`），L2 乘区读属性值时再当百分比用。

另有一个工具链坑：Java 源码里写 `✓` / `✗`（U+2713/U+2717）会让 javac 在该行报
「需要 ')' 或 ','」（本机编码链问题）。诊断输出里统一用 `[OK]` / `[FAIL]`。

## 5. 口径决定（已按默认走，Aki 可推翻）

| # | 决定 | 理由 | 反悔成本 |
|---|---|---|---|
| R5-a | 内容 `stats` 一律 `ADD_VALUE`；百分比只影响显示 | 自建属性基值 0，乘算恒 0（§4 第 3 条） | 低：只改 `ItemFactory` |
| R5-b | `attack_speed` 在内容里写**目标次/秒**，建栈时换算成 `目标 − 4.0` | 与原版剑 `-2.4` 同形态；配置侧不用心算 | 低：已发物品需重发（M2 尚未投放） |
| R5-c | 护甲基底统一 `ArmorMaterials.LEATHER` | 白板化万一失效，退化方向是「很弱的甲」而不是「凭空 +8 护甲」 | 极低 |
| R5-d | 饰品（ring/amulet）临时挂 `MAINHAND` 槽，**不用 `ANY`** | `ANY` 会让「放背包就给属性」。M3 接 Curios 槽位时收窄 | 低 |
| R5-e | 护甲/饰品不打印「单次伤害 / 攻速」两行 | 不是武器，写了是噪声 | 低 |

## 6. 未验证（不装成功）

- **`/redtale give` 与真实玩家 tooltip**：没有客户端，`give` 需要玩家上下文，本轮没跑通端到端。
  组件的 `MAX_DAMAGE` / `CUSTOM_NAME` / 稀有度在**存档落盘再读回**后是否一致，同样未验。
- **`weapon_attributes` 字段目前只是写进组件**，还没生成 BetterCombat 需要的
  `data/<ns>/weapon_attributes/*.json`（M2 下一步）。字段现在无人读，所以「没报错」不等于「接好了」。
- **`strip_vanilla_effects`** 只解析不使用（要等 M4 附魔层）。
- **玩家穿满四件套后的护甲总值**：本轮只验了单件的 `forEachModifier` 净结果，没验 `AttributeMap` 汇总。

## 7. 下一步（R6）

1. **内容投放面**：`weapon_attributes` 生成器（写进 `world/datapacks/redtale_project/`，可 `/reload`）+ 一件真剑在
   BetterCombat 下挥得动（M2 出口条件的一半）。
2. **条件乘区**：`pve_damage` / `pvp_damage` / `undead_damage` 需要按目标分类，与 tooltip 同批做。
3. **`CriticalHitEvent`**：验它能否同时作为原版与 BetterCombat 的暴击入口。
4. **玩家上下文补验**（挂账 3 轮了）：需要一次带客户端的验收，包括 `give` → 手持 → tooltip → 平砍伤害对账。
