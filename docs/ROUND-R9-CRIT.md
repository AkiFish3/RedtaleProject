# R9 · 近战暴击入口（M2）

> 2026-11-16 01:57-02:10 · 双队列 R4 轮 · 卡板动作：无 `[DONE]` 可验、在制品 3/3 已满 → 本轮**不派单**，全力做 M2 的暴击。

## 1. 结论先行

| 问题 | 答案 | 证据 |
|---|---|---|
| `CriticalHitEvent` 还能当暴击入口吗 | **能，且是唯一入口** | `CommonHooks.fireCriticalHit` 字节码：构造事件 → `EVENT_BUS.post` → `areturn`，**无任何门禁**，永远返回事件 |
| 能不能「原版没判暴击」时强制判成暴击 | **能** | 探测：`chance=1 / power=2.0` 掉血 100 → **200** |
| 我们的倍率是替换还是叠乘 | **替换** | 探测：`chance=1 / power=1.0` 掉血 **100**（若原版 1.5 另乘一次会是 150） |
| BetterCombat 会不会绕开这个入口 | 不会 | `bettercombat-neoforge-2.4.0+1.21.1.jar` 全量解包后 `grep -r CriticalHitEvent` = **0 命中**，它不自己算暴击 |

## 2. 做了什么

| 文件 | 内容 |
|---|---|
| `core/damage/CritRules.java`（新） | `crit.json5` 解析：`enabled` / `chance_attribute` / `power_attribute` / `apply_to_vanilla_crit` / `force_on_roll` / `max_chance`。全部字段类型不符都告警 + 用默认值，**绝不抛异常**（与 T-0001/0004 同口径） |
| `core/damage/CritLayer.java`（新） | `CriticalHitEvent` @ **LOWEST**：读攻击者两条属性 → 掷概率 → 改**倍率**（`setDamageMultiplier`）+ 必要时 `setCriticalHit(true)`；环形记录最近 8 次判定 |
| `core/hook/GameHooks.java`（+3 行） | 注册 `CritLayer` |
| `core/RedtaleCore.java`（+5 行） | 字段 / `critRules()` / reload 里 parse / `CritLayer.reset()` |
| `core/config/ConfigStore.java`（+1 行） | `crit.json5` 进默认落地清单 |
| `core/command/RedtaleCommand.java`（+~190 行） | `/redtale crit`（规则 + 计数 + 最近判定进出倍率）、`/redtale crit probe`（FakePlayer 真打木桩四组对照）、`setAttackStrengthTicker` 反射助手 |
| `resources/redtale/defaults/crit.json5`（新） | 默认全开、`max_chance: 0`（D16 不设上限） |

## 3. 实测（测试服 `E:\server-test`，jar 172409 B）

`Done (2.330s)`，`gradle build --offline` exit 0，`gradle selftest` **55 PASS**（未变）。

```
/redtale crit probe
  暴击率 0 · 倍率 2 → 掉血 100     ← 基线，不暴击
  暴击率 1 · 倍率 2 → 掉血 200     ← 强制暴击 ×2.0
  暴击率 1 · 倍率 1 → 掉血 100     ← 关键：倍率 1.0 就回到基线
  暴击率 0 · 倍率 2 → 掉血 100
  比值：×2.0 → 2 · ×1.0 → 1
  结论：✓ 我们的倍率是**替换**进场值（原版 1.5 不额外叠加）
```

```
/redtale crit
  观测：判定 4 次 · 暴击 2 次（其中强制 2 次）
    暴击率 0 · 倍率属性 2 · 原版暴击 否 · 掷中 否 · ×1（未改）
    暴击率 1 · 倍率属性 2 · 原版暴击 否 · 掷中 是 · ×1 → ×2
    暴击率 1 · 倍率属性 1 · 原版暴击 否 · 掷中 是 · ×1（未改）
    暴击率 0 · 倍率属性 2 · 原版暴击 否 · 掷中 否 · ×1（未改）
```

热重载（D14）：改 `force_on_roll: false` + `/redtale reload` → 四组全部掉血 100、判定行变成
`✓ 配置 force_on_roll=false → 掷中也不强制暴击（符合预期）`；改回 true 再 reload → 恢复 200。
配置从 8 份变 9 份，`crit.json5` 是自动落地的。

日志：`ERROR.*redtale` 0 条、`配置告警：crit` 0 条。测试服已停（25567 无监听）。

## 4. 四条口径（后续沿用）

| # | 口径 | 理由 |
|---|---|---|
| R9-a | 暴击**只走 `CriticalHitEvent`**，不在 `LivingIncomingDamageEvent` 自己 roll | 一个点覆盖原版与 BetterCombat；且暴击发生在伤害事件之前，与 L2 乘区不抢同一个数 |
| R9-b | 只写**倍率**（`setDamageMultiplier`），永不写绝对值 | 与 R4-a 同源：写成「这一刀打多少」会把前面十几个 mod 的加成一并抹掉 |
| R9-c | 原版跳跃暴击默认**换成我们的倍率**（`apply_to_vanilla_crit: true`） | Q-008 已定 `critical_strike_power` 基值照搬上游 2.0；两套倍率（1.5 / 2.0）共存会让玩家对不上账。改成 false 即保留原版 1.5，一行配置的事 |
| R9-d | 探测读数必须**同基数**（`attackStrengthTicker` 顶满、`invulnerableTime` 归零、木桩护甲 0） | `Player#attack` 的伤害 = `attack_damage × (0.2 + scale²×0.8)`，scale 不满或吃到无敌帧，比值就没有意义 |

## 5. 三条 1.21.1 事实（记牢）

1. `attackStrengthTicker` 在 **`LivingEntity` 里是 `protected`**（不是 `Player` 的 public 字段，也不是 1.20.1 那种写法）→ 命令类跨包只能反射；`invulnerableTime` / `hurtTime` 是 public，可直接写。
2. **1.21.1 没有 `EntitySpawnReason`**（那是 1.21.5+）→ 生成木桩用 `EntityType#create(Level)`；带原因的 `create(Level, EntitySpawnReason)` 编不过。
3. `CommonHooks.fireCriticalHit` 返回值**永远非 null**，所以「事件发了但没人采纳」不是常态 —— 一旦 `setCriticalHit` 无效，问题一定在 `Player#attack` 的 patch 侧，不要在事件层反复排查。

## 6. 卡板动作（本轮）

| 卡 | 动作 | 证据 |
|---|---|---|
| T-0003 词条解析器 | S8 02:12 交 `[DONE]` → **S7 验收 3/3 → `[VERIFIED]`** | ①`build --offline` exit 0（jar 181642 B）②`grep -c parseDefs`=1 / `parsePools`=1 ③jar 内 `items/AffixPools` **4** 条（主类 + 3 record） |
| T-0006 `ItemUpgrader.plan` | **新派**（D33 判定核心，纯函数、单文件、≤200 行） | 卡在 `docs/TASKS.md` §5，在制品回到 3/3（T-0004 / T-0005 / T-0006） |

**S8 提的 `stats` 形态偏差 = 我的卡的简化，认账不打回**：卡上写「值是数字」，而 `ITEM-MODEL.md` §2 样例是
`{base, per_item_level}` 曲线对象。`AffixPools` 已 `VERIFIED` 归 S7，R10 由我把 `Def.stats` 升到 `StatValue` 再接 roll，
不再让 S8 碰这条线。

## 7. 并行写入者（本轮踩到，记下来）

Aki 在 **02:00** 加了 **D33（物品更新与统一标记）** 与 `docs/ITEM-UPDATE.md`，并把
`ContentData` 从 7 字段扩到 **9**（`+schema` / `+contentRev`）、`ItemContent` 加 `rev`、`PLAN.md` M2 出口加「物品更新系统」。
本轮所有改动都只**叠加**在既有接口上（`crit.json5`、`CritLayer`、命令、`GameHooks`），没有碰 `ContentData` / `ItemFactory`，
所以 02:05 的合并 build 直接通过（exit 0）。**教训与 R1 相同：动手前重读 PLAN 章节结构，台账只追加。**

## 8. 未验证 / 挂账

- **原版跳跃暴击走我们倍率的真实路径**：FakePlayer 站地上不出原版暴击，只验了「强制暴击」分支。`vanillaCrit=true` 分支要真人跳砍，挂账（第 9 轮）。
- **法术暴击**（`spell_critical_strike_*`）没有入口：`CriticalHitEvent` 只覆盖近战；M5 技能落地时再定（很可能在技能执行器里 roll）。
- **BetterCombat 实际挥砍是否也走 `Player#attack`**：只证了「它不引用 `CriticalHitEvent`」；它可能自己调 `target.hurt(...)` 而完全绕过。要真人客户端挥一刀才知道，挂账。
