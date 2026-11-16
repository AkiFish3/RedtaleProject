# ROUND M1-ATTR · 80 条属性注册 + 来源拆解（2026-11-15 19:54–20:10）

> 结论先行：**80 条属性全部落地，66 条自建属性在 99 个 EntityType 上挂载成功；`/redtale attr` 能把一个属性按 3 个乘区拆成逐条 modifier。**
> 构建 `BUILD SUCCESSFUL in 8s`，测试服 `Done (2.328s)`，验收后已停服。

## 1. 起点与范围

R1 结束时的状态：M0 达成（配置热载 + 脚本层 + `/redtale`），`src/` 里没有任何属性代码。
本轮按 `PLAN.md` §13.2 第 1、2 项推进 M1 的第一小步——**只做注册与可视化，不做伤害结算**。

| 做了 | 没做（下一轮） |
|---|---|
| 66 条自建属性注册 + 14 条原版引用成表 | `StatValue{base,scale,spread,maxSpread}` 与 `settle()` |
| `EntityAttributeModificationEvent` 挂到全部实体类型 | `attack_speed.json5` 声明式来源表（D18） |
| `/redtale attr <属性> [目标]` 三乘区拆解 | mixin `getCurrentItemAttackStrengthDelay`（D19 地板） |
| lang 生成器（zh_cn / en_us 各 66 键） | 伤害管线 L1/L2/L3（M1 后半） |

## 2. 产出的文件

| 文件 | 作用 |
|---|---|
| `core/attr/AttributeSpec.java` | 一条属性的纯数据声明（**零 Minecraft import**，可离线校验） |
| `core/attr/AttributeGroup.java` | A~F 六个分组，对应 `ATTRIBUTE-REGISTRY.md` §2 |
| `core/attr/AttributeSpecs.java` | **80 条总表（唯一事实来源）**，类初始化时断言条数 = 80、id 不重复 |
| `core/attr/RedtaleAttributes.java` | `DeferredRegister<Attribute>` + 挂载 + `RangedAttribute` 参数 |
| `core/attr/AttributeBreakdown.java` | 三乘区拆解，`/redtale attr` 与将来 tooltip 共用 |
| `core/command/RedtaleCommand.java` | 新增 `attr` / `attr list` 子命令 |
| `tools/gen_attr_lang.py` | 从总表生成两个 lang 文件，`--check` 可只校验 |
| `resources/assets/redtale_project/lang/{zh_cn,en_us}.json` | 66 键，由上面的脚本生成 |

## 3. 三条实测到的事实（都是字节码核对过的，别再猜）

### 3.1 注册了 ≠ 实体有

`DeferredRegister<Attribute>` 只是把类型塞进 `BuiltInRegistries.ATTRIBUTE`。玩家/怪物身上要出现
`AttributeInstance`，必须走 **`EntityAttributeModificationEvent`**（`IModBusEvent`，在 mod bus 上）。

实测证据（`logs/latest.log`）：

```
[19:59:03] [modloading-sync-worker/INFO] (RedtaleProject/Attr) 属性挂载完成：99 个实体类型 × 66 条自建属性（实际新增 6534 处）
```

99 × 66 = 6534，说明**没有一条**是「本来就存在」的——全是我们新挂的。
挂到**全部**类型而不是只挂玩家，是为了 M6 的模板怪直接能用同一批属性；代价可忽略。

### 3.2 `setSyncable(true)` 必须显式调

1.21.1 的 `Attribute` 构造器（`javap -c` 实测）只写 `defaultValue` 与 `descriptionId`，
`syncable` 字段停在 `false`。不调 `setSyncable(true)` 就不会同步到客户端，tooltip 会读过期值。
所以注册全部写成 `new RangedAttribute(...).setSyncable(true)`。

### 3.3 原版 `/attribute` 直接认我们的属性

```
> attribute @e[type=zombie,limit=1] redtale_project:damage_reduction get
Value of attribute Damage Reduction for entity Zombie is 0.0
```

注意它打印的是**英文名**而不是 `attribute.redtale_project.damage_reduction` 原文——说明 NeoForge
的服务端 `LanguageHook` 确实读了我们 jar 里的 `assets/redtale_project/lang/en_us.json`。
lang 键与 `RangedAttribute` 的 descriptionId 一旦对不上，这里会直接露馅。

## 4. `/redtale attr` 实测输出

命令形态：`/redtale attr <属性> [目标]`。目标缺省取命令执行实体，控制台必须给目标（否则无法离线验收）。

**三乘区混合（僵尸，`attack_damage` 加了 3 条 modifier）**：

```
> attribute @e[type=zombie,limit=1] minecraft:generic.attack_damage modifier add redtale_project:weapon_flat 5 add_value
> attribute ... modifier add redtale_project:set_bonus 0.25 add_multiplied_base
> attribute ... modifier add redtale_project:skill_buff 0.4 add_multiplied_total
> redtale attr attack_damage @e[type=zombie,limit=1]
[redtale] Zombie · minecraft:generic.attack_damage
攻击伤害 = 14（基础 3 · 平加 +5 · 乘基 ×1.25 · 乘总 ×1.4）
  id: minecraft:generic.attack_damage
  +5     平加  → 贡献 +5  [redtale_project:weapon_flat]
  +0.25  乘基  → 贡献 +2  [redtale_project:set_bonus]
  +0.4   乘总  → 贡献 +4  [redtale_project:skill_buff]
```

校验：(3+5)×1.25×1.4 = 14 ✓；贡献 5 + 2 + 4 + 基础 3 = 14 ✓。

**别人加的 modifier 也能看见（这是 D18 的关键）**：

```
> effect give @e[type=zombie,limit=1] minecraft:speed 120 2
> redtale attr movement_speed @e[type=zombie,limit=1]
移动速度 = 0.368（基础 0.23 · 乘总 ×1.6）
  +0.6  乘总  → 贡献 +0.138  [minecraft:effect.speed]
```

我们不判断「谁给的」，只遍历 `AttributeInstance.getModifiers()`。所以咖啡因、Ferocity、双持、
我们自己的规则将来全都在同一张表里，**tooltip 与结算同源**这条 D18 硬要求有了落地件。

**列表（80 条，预留不列）**：

```
> redtale attr
—— 近战与物理 ——
  攻击伤害  minecraft:generic.attack_damage  =  默认 2
  攻击速度  minecraft:generic.attack_speed  =  默认 4
  ...
[redtale] 共 64 条（另有 16 条预留不列） · 当前无实体上下文，未显示实际值
```

64 + 16 = 80，与文档一致。无实体时显示的是**注册表真实默认值**（原版行不是 0），有实体时显示实际值 + modifier 层数。

## 5. 踩到的坑（省下一次重走）

| 坑 | 现象 | 处理 |
|---|---|---|
| 后台进程随 shell 退出被杀 | `java ... &` 起来 30 秒后端口拒绝连接，日志停在 `RCON running` | 用受管后台任务启动，不要 `&` |
| `StringArgumentType.word()` 不吃冒号 | `/redtale attr minecraft:generic.attack_speed` → `Expected whitespace to end one argument` | 改用 `string()`（支持引号）；同时 `resolve()` 接受 `minecraft.generic.attack_speed` 这种点形态 |
| 生物在白天被烧死 | summon 后马上 `No entity was found` | 验收一律带 `Invulnerable:1b` |
| 原版 op 名记错 | `multiply_base` / `multiply_total` → `Incorrect argument` | 真名是 `add_multiplied_base` / `add_multiplied_total` |
| record 组件名 | `AttributeBreakdown` 里写 `add` 编译失败 | 组件叫 `addValue`，record 内部只能靠 `addValue` 或 `addValue()` |
| 展示默认值 | 原版行用 `spec.base()`（全是 0）会显示「攻击伤害 = 0」 | 改用 `holder.value().getDefaultValue()` |

## 6. 未验证（明确标注，不要当成已完成）

1. **玩家上下文的 `attack_speed` 拆解**：测试服无客户端、没有在线玩家，而绝大多数生物没有
   `minecraft:generic.attack_speed`（僵尸实测报「没有属性」）。所以 `20/value → tick` 那段换算
   **只在代码路径上存在，没有实机输出**。下次有玩家在线时补验，是 D19/D20 的验收前提。
2. **`minecraft:generic.scale` 对模板实体碰撞箱的缩放**（`ATTRIBUTE-REGISTRY.md` §6 第 2 条），归 M6。
3. `RangedAttribute` 的 ±1e9 边界只在注册参数里，**没有实测**超界钳制行为（需要一条 +2e9 的 modifier；
   原版 `/attribute` 会先拒绝超大数值）。这条留到 `settle()` 有单测时一起验。

## 7. 下一步（R3 建议顺序）

1. `StatValue{base,scale,spread,maxSpread}` + `settle()`：纯 Java，先写单测（离线可跑），再做管线。
2. `attack_speed.json5` 声明式表（D18）+ 急迫 0.10/级 applier（固定 modifier id，等级变化先移除再加）。
3. mixin `Player#getCurrentItemAttackStrengthDelay`（D19 的 `min_attack_delay_ticks = 1.0` 物理地板）
   —— 这是本项目第一个 mixin，需要新增 `redtale_project.mixins.json` 并在 `neoforge.mods.toml` 里声明。
4. 有玩家在线时补验 `/redtale attr attack_speed`（玩家上下文）。
