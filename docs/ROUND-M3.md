# ROUND-M3 · 数值核心 + 声明式攻速 + 第一个 mixin

> 2026-11-15 20:54–21:08 · S7 移植队列 · 里程碑 **M1（进行中）**
> 本轮三件事：①纯 Java 数值核心（`StatValue` / `settle` / 攻速曲线）带离线单测；
> ②`attack_speed.json5` 声明式攻速表 + 急迫 applier；③项目第一个 mixin 进 jar 并实测生效。

## 1. 结论先行

| 项 | 状态 | 证据 |
|---|---|---|
| 数值核心 + 单测 | ✅ 28/28 通过 | `gradle selftest --offline` → `[selftest] PASS 28` |
| 攻速声明式表 + 热重载 | ✅ | 改 `soft_cap: 4.0` → reload → 1024 次/秒实测 **5 tick**（§4.3） |
| mixin 进 jar 并生效 | ✅ | 日志 `Mixing PlayerAttackSpeedMixin ... into net.minecraft.world.entity.player.Player`；`/redtale speed` 反射命中 handler 名 |
| **急迫双计 bug** | ✅ 已修 | 1.21.1 原版急迫**自己就加攻速**，我们默认 0.10/级导致急迫 II = +40%。改为自动去重后实测 **4 → 4.8**（§3） |

## 2. 新增文件

```
core/math/Caps.java            闸门（percentCap/condCap/condCount/damageCap，默认 0 = 关）
core/math/Sanity.java          异常保护（负数归零 / 1e9 硬顶 / 超阈值记日志，不改值）
core/math/Settle.java          唯一的 settle()：base ×(1+Σp) ×Πcond，L3 兜底
core/math/StatValue.java       四段式 {base, scale, spread, maxSpread}，roll 后立即 clamp 存下
core/math/AttackSpeedCurve.java 攻速换算（纯函数，mixin / attr / tooltip 共用）
core/math/DamageRules.java     damage.json5 解析
core/math/AttributeNumbers.java 数字格式化（去浮点噪声，单份实现）
core/attr/AttackSpeedRules.java  attack_speed.json5 解析 + 期望值计算
core/attr/AttackSpeedApplier.java 把规则写成 ATTACK_SPEED 的 transient modifier（每 5 tick）
mixin/PlayerAttackSpeedMixin.java  @Inject getCurrentItemAttackStrengthDelay @RETURN
resources/redtale_project.mixins.json
resources/redtale/defaults/attack_speed.json5 / damage.json5
src/selftest/java/.../SelfTest.java
```

`build.gradle` 新增 `selftest` 源集与任务（不引 JUnit：离线环境拉不到测试依赖）。

## 3. 关键发现：1.21.1 原版急迫**已经**加攻速（D19 前提需更正）

### 3.1 现象

`/redtale speed probe` 首版实测：

```
急迫 II → 4 → 5.76（期望 ×1.2 = 4.8）
  [minecraft:effect.haste]          ADD_MULTIPLIED_TOTAL 0.2
  [redtale_project:haste_attack_speed] ADD_MULTIPLIED_TOTAL 0.2
```

5.76 = 4 × 1.2 × 1.2 —— 两个 20% 乘区在打架。属性上除我们以外，还有一个
`minecraft:effect.haste`。

### 3.2 根因（server jar 字节码，非推测）

`javap -c net/minecraft/world/effect/MobEffects` → `DIG_SPEED` 的构造：

```
88: getstatic  Attributes.ATTACK_SPEED
91: ldc        String effect.haste
93: invokestatic ResourceLocation.withDefaultNamespace
96: ldc2_w     0.10000000149011612d
99: getstatic  AttributeModifier$Operation.ADD_MULTIPLIED_TOTAL
102: invokevirtual MobEffect.addAttributeModifier
105: invokestatic register("haste", ...)
```

即：**`minecraft:haste` 每级 +10% 攻速，是 1.21.1 原版自己做的**。
`PLAN.md` D19 写的是「原版不加，我们自己按 0.10/级换算」—— 这条在 **1.20.1 成立、1.21.1 不成立**
（1.20.1 的 `Player.getDigSpeed` 硬编码，1.21.1 才把 ATTACK_SPEED 挂进效果）。

顺带纠正另一个字段名坑：`MobEffects` 里没有 `HASTE`，只有 **`DIG_SPEED`**（注册 id 仍是 `minecraft:haste`）。

### 3.3 处理（不推翻 D19，只去重）

按 D18 铁律「同一来源只算一次」，`AttackSpeedApplier` 写急迫 modifier 前先查
`<ns>:effect.<path>`（急迫即 `minecraft:effect.haste`）：

- 存在 → 让位，不再写我们那份，日志打一条（每个效果一次）；
- 不存在 → 我们自己写 `redtale_project:haste_attack_speed`。

好处：D19 的「急迫 I +10% … VIII +80%」**数值不变**，只是提供方从我们变成原版；
哪天原版改了/被数据包删了，我们的规则自动补上。配置文件没改一个数字。

实测（去重后）：

```
急迫 II + 写入 0 条：4 → 4.8（期望 ×1.2 = 4.8），实际 delay 4.167 tick
  [minecraft:effect.haste] ADD_MULTIPLIED_TOTAL 0.2
移除急迫后：4（已还原，无残留加成 ✓）
```

已开 Q-013 让 Aki 确认（默认就是上面这套去重）。

## 4. 验收记录（全部在本机测试服实跑）

### 4.1 单测（离线，无 Minecraft 运行时）

```
D:\redtale-work\tools\gradle-8.8\bin\gradle selftest --offline
[selftest] PASS 28 项
```

覆盖：caps 全关/逐项开启、damageCap、负数归零、NaN/Infinity 输入、1e9 硬顶、
`spread` clamp 与 roll 边界、`soft_cap` 曲线（raw=4 得 63%）、地板（delay 0 / NaN / 配置 0）。

### 4.2 mixin 真的进了 jar 且生效

三重证据：

1. `unzip -l` → `redtale_project.mixins.json` + `com/redtale/project/mixin/PlayerAttackSpeedMixin.class`；
   `mods.toml` 末尾有 `[[mixins]] config = "redtale_project.mixins.json"`。
2. 启动日志（`-Dmixin.debug.verbose=true`）：
   `Selecting config redtale_project.mixins.json` → `Preparing ... (1)` →
   `Mixing PlayerAttackSpeedMixin from redtale_project.mixins.json into net.minecraft.world.entity.player.Player`。
3. `-Dmixin.debug.export=true` 导出的 `Player.class` 里确有
   `private void handler$zdi000$redtale_project$redtale$applyAttackSpeedRules(CallbackInfoReturnable)`。

> 坑：`/redtale speed` 第一版用 `startsWith("redtale$")` 判注入，永远报「未注入」——
> Mixin 0.8.7 会给处理方法套 `handler$<唯一码>$<modid>$` 前缀。判据改成「名字里含 `redtale`」。

### 4.3 端到端：FakePlayer 真调玩家方法

测试服无客户端 → 用 `FakePlayerFactory.getMinecraft(level)`（NeoForge 提供、不需要客户端），
改 `ATTACK_SPEED` 基值后**真调** `getCurrentItemAttackStrengthDelay()`：

```
/redtale speed probe
  attack_speed 1.6   → 理论 12.5 tick，实际 12.5 tick（原样）
  attack_speed 4     → 理论 5 tick，  实际 5 tick   （原样）
  attack_speed 8     → 理论 2.5 tick，实际 2.5 tick （原样）
  attack_speed 1024  → 理论 0.02 tick，实际 1 tick  （触地板）
  attack_speed 0.4   → 理论 50 tick， 实际 50 tick  （原样）
```

前三条证明**默认路径零改动**（不饱和时不重算浮点）；第四条证明 D19 的物理地板真的在 mixin 里生效。

热重载同一条链（D14/D18）：

```
改 config/redtale_project/attack_speed.json5 → soft_cap: 4.0 → /redtale reload
  attack_speed 1024  → 实际 5 tick（被 soft_cap 压到 5 tick）
```

这与 `docs/DAMAGE-PIPELINE.md` §9 验收点 3「`attackSpeedSoftCap=4.0` 时 raw=1024 实际冷却 = 5 tick」
**逐字对上**。改回 `0.0` → reload → 恢复 1 tick。

## 5. 踩到的坑

| 坑 | 现象 | 处理 |
|---|---|---|
| `MobEffects.HASTE` 不存在 | 编译错误「找不到符号 HASTE」 | 字段名是 `DIG_SPEED`，注册 id 才是 `minecraft:haste` |
| record 字段直接访问 | `Caps.percentCap` 报「private 访问」 | 同包也不行，必须 `percentCap()` |
| `Settle` 漏了 `damageCap` | 单测抓出「damageCap=5 却得 33.75」 | 闸门要在 L3 兜底之前 `Math.min` |
| `roll()` 只 clamp 不写回 | 单测抓出 spread 存的是 2 而生效是 1 | 摇完立刻 clamp 再存，三处读同一个数 |
| `soft_cap=4` 与 `1024` 比较用 `<4` | 单测误报（`1-e^-256` 在 double 里就是 1.0） | 改成 `<=4 && >3.99` |
| 默认配置自带一条告警 | 每次 reload 都报「minecraft:haste 已忽略」 | 只有值非零才告警（默认那行是给人看的示例） |
| 中文输出在 GBK 控制台乱码 | `gradle selftest` 输出全是 `?` | 单测标签改 ASCII，注释仍用中文 |

## 6. 未验证（不要当已完成）

1. **真实在线玩家的 `attack_speed` 拆解**：仍然没有客户端/在线玩家。FakePlayer 验证的是
   mixin 与属性链路，**没有验证**「玩家手持武器 → 物品 modifier → delay」这一段
   （`ItemAttributeModifiers` 在 FakePlayer 上不会自动挂载，本轮直接改基值）。
2. **BetterCombat 双持 ×1.2 与本 mixin 的叠加顺序**：它调我们的方法后再套 `max(cap)`，
   理论上正确，但没实测（需要客户端双持）。
3. **`effect_attack_speed` 的通用分支**：默认表里除急迫外全是 0，所以那条代码路径**未跑过**
   （只有 `parses` 与 `describe()` 验证过）。
4. **NBT/重登**：modifier 用 transient，理论上不落盘；没做「急迫 → 退出 → 重进」的实测。

## 7. 下一步（R4 建议）

1. 玩家上下文补验（有客户端时）：`/redtale attr attack_speed` + 手持不同武器看 delay 变化。
2. `settle()` 接进 `LivingIncomingDamageEvent`（L2 乘区 + tag 白名单 `redtale_project:*`），
   `CriticalHitEvent` 是否可用的待验证项一并做掉。
3. M2 起步：`ContentData` 组件 + 14 基底物品 + 白板化（D21）验证件 —— tooltip 与 settle 同源。
4. tooltip（D20）：`ItemTooltipEvent` 追加「单次伤害 + 攻速 tick/次每秒」，复用 `AttackSpeedCurve`。
