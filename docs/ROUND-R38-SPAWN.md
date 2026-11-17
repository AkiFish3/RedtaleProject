# R38 · 验收 T-0032（`/redtale mob check`）+ `MobSpawner` + `/redtale mob spawn`（M6 第三步第 2 小步）

时间：2026-11-17 20:03-20:30 · 里程碑：M6 第三步（8 模板实体 → 可生成的真怪）

## 0. 结论先行

| 项 | 结果 |
|---|---|
| T-0032 `mob check` | **VERIFIED（3/3）**，测试服四步文案逐字对 |
| 派单 | **T-0034**（`tools/gen_entity_lang.py`）；在制品 T-0033+T-0034 = 2/3 |
| 自研 | `mobs/MobSpawner` + `core/command/MobSpawnCommand`（`mob list` / `mob spawn <id> [x y z]`） |
| 实测 | `spawn ember_guard` → **128 血 / attack 11 / speed 0.27 / armor 9 / scale 1.15**，剑+胸甲上身、名牌与 tag 正确 |
| 构建 | `build --offline` exit 0，jar **467339 B**；`selftest` **78 PASS** |
| 测试服 | 起停 2 次，均已停（25567/25577 无 LISTEN），redtale 零 ERROR |

## 1. T-0032 验收（`/redtale mob check`）

| 断言 | 结果 |
|---|---|
| `gradle build --offline` | exit 0（jar 457719 B） |
| jar 内 `core/command/MobCheckCommand.class` | **1** |
| `wc -l MobCheckCommand.java` | **88**（卡面上限 140） |
| `grep -c BuiltInRegistries.ITEM` / `MobCheckCommand.node()` | **1** / **1** |
| `git diff RedtaleCommand` | **+1 行**（`.then(MobCheckCommand.node())` 在 `SetCommand` 后、`SetCheckCommand` 前） |

功能四步（测试服 `Done (2.137s)`）：

1. 基线 → `[redtale] 怪物检查：2 只 / 技能引用 3 条 · 引用完整 ✓`
2. 放夹具 `mobs/t_badmob.json5`（`mainhand: demo_longsword` + `skills:[{skill:"redtale_project:nope"}]`）+ `/redtale reload`（29 份配置 / 3 只怪）→
   `3 只 / 技能引用 4 条 / 问题 2 个`，两行文案逐字对：
   - `  坏夹具 t_badmob: equipment.mainhand demo_longsword 不是已知物品 id`
   - `  坏夹具 t_badmob: 技能 redtale_project:nope 不存在（现有 3 条：redtale_project:arc_bolt, redtale_project:ember_burst, redtale_project:gale_slash）`
3. 连跑两次 `diff` 为空（幂等）
4. 删夹具 + reload → 回落 `引用完整 ✓`

四条 NEED-S7 **全采纳**：N = 技能总数（列前 5 个）；内容 id 不额外提示（只报「不是已知物品 id」）；
怪名缺省与 id 同名不去重；`size()==0` 只发一行。全部是卡面的本意，不打回。

## 2. `MobSpawner`：配置 → 实体

落地顺序（`MobSpawner.spawn`）：

```
byBase(base) → EntityType#create(Level)（单参）→ moveTo
  → 属性：ADD_VALUE 临时 modifier（id mob/<怪id>/<属性path>）
  → 体积：generic.scale 基值（1.0 不写）
  → 六槽：BuiltInRegistries.ITEM → setItemSlot
  → CustomName / tag redtale_mob/<id> / setPersistenceRequired
  → addFreshEntity → setHealth(getMaxHealth())
```

为什么属性走 `ADD_VALUE` 而不 `setBaseValue`：基值是「出厂值」，改了就看不出谁给的（D18 的可拆解性）；
固定 id 让「同一只怪重生成」是**覆盖**而不是叠加。代价是**加法口径**：

| 属性 | 出厂基值 | 配置 | 实测生效值 |
|---|---|---|---|
| `max_health` | 20 | `{base:60, per_item_level:4}` @ lv12 = 108 | **128.0** |
| `attack_damage` | 2 | 9 | **11.0** |
| `movement_speed` | 0.25 | 0.02 | **0.27** |
| `armor` | 0 | 6 | **9.0**（+ 胸甲 3） |
| `generic.scale` | 1.0 | 1.15（**基值**） | **1.15** |

`armor 9.0` 是意外的好证据：配置 6 + 内容胸甲的 `ATTRIBUTE_MODIFIERS` 3 同时生效，说明「装备属性」和「怪物配置属性」走的是同一张 `AttributeMap`，没有两套账。

## 3. 夹具 `t_spawn_bad`（负向面）

```json5
{ id: "t_spawn_bad", name: "夹具灵", base: "flyer", scale: 0.6, level: 3,
  attributes: { attack_damage: 4, spell_power: 2 },
  equipment: { mainhand: "demo_longsword", head: "minecraft:iron_helmet" } }
```

结果：`属性 1 条 · 装备 1 件 · 体积 ×0.60 · 20/20 血` + `⚠ 装备 mainhand 的物品 id 未知：demo_longsword`；
`ArmorItems[3]` 真有铁头盔、`attack_damage 6.0`（2+4）、`scale 0.6`。
同一夹具下 `mob check` 也报同一条 `equipment.mainhand demo_longsword 不是已知物品 id` → **两个观测面一致**。

`spell_power` 没有出现在 `skips` 里 —— 因为它在**解析层**就被 `AffixPools` 丢了（reload 一条告警：
`mobs/t_spawn_bad.json5.attributes: stats.spell_power 不是已知属性，已跳过`）。所以「属性没生效」要查 reload 告警，不是查 spawn 输出。

负向 id：`spawn nope` → `没有怪物 nope（现有 2 只：ash_caster, ember_guard）`。

## 4. 踩到的坑：未加载区块里生成会掉到地形

首测（未 `forceload`）连发两次 `spawn ember_guard 106 100 106`，命令都返回成功，但：

- `@e[type=redtale_project:elite]` **找不到实体**（`No entity was found`）
- `kill` 一次杀掉 **2 只**，`Pos` 都是 **y=54**（地表），不是 100

结论：`addFreshEntity` 对未加载区块也返回 true，实体随后掉到已算好的地形上；`@e` 选择器同样看不到未加载区块里的实体。
正确验法（本轮定稿，写进 PLAN §13.41 R38-d）：
`forceload add` → `fill` 平台 → `spawn <id> x y z` → `data get` / `attribute … get` → `kill` → `forceload remove`。

## 5. 留痕

- 源码：`mobs/MobSpawner.java`、`core/command/MobSpawnCommand.java`、`RedtaleCommand` +1 行
- 文档：`PLAN.md` §13 R38 + §13.41、`CONTRACTS.md` §2 / §4.22 / §5、`docs/TASKS.md`（T-0032 VERIFIED + T-0034）
- 测试服已停、夹具已删（`t_badmob` / `t_spawn_bad` 都不留）、`forceload` 已 remove、构建锁已删
- 挂账不变：客户端体积/名牌、8 原型 AI 差异、技能释放（M6 第 4 步）
