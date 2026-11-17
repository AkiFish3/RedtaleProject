# R37 · 验收 T-0031（`dash`）+ 8 个模板 EntityType（M6 第三步第 1 小步）

时间：2026-11-17 18:52-19:55 · 里程碑：M5-C（原语 9/24）+ M6 第三步开工

## 0. 结论先行

| 项 | 结果 |
|---|---|
| T-0031 `dash` | **VERIFIED（3/3）**，另 S7 独立探针 **34/34** |
| 派单 | **T-0033**（`projectile` v1，只做「射出去」）；在制品 T-0032+T-0033 = 2/3 |
| 自研 | `mobs/RedtaleMob` + `mobs/RedtaleEntities`（**8 个模板 `EntityType`**），`/summon` **8/8** 成功 |
| 构建 | `build --offline` exit 0，jar **454110 B**；`selftest` 78 PASS |
| 测试服 | 起停 3 次，全部已停（25567/25577 无 LISTEN），redtale 零 ERROR |

## 1. T-0031 验收（`dash`）

S8 交 136 行、`Builtins` +2 行、探针 31/31。我跑的是卡面三条 + 自己一份独立探针（不复用它的断言）：

| 断言 | 结果 |
|---|---|
| `gradle build --offline` | exit 0（jar 449435 B） |
| `wc -l DashEffect.java` | **136**（卡面上限 160） |
| `grep -c setDeltaMovement` / 禁用词 | **1** / **0** |
| jar 内 `script/builtin/DashEffect.class` | **1** |
| `git diff --stat Builtins.java` | **+2 -0** |
| `S7DashProbe`（五个必过例 + 夹取 + EPS 三点 + 符号 + 长度=power + 常量 + 纯函数） | **34/34** |

S8 提的五条 NEED-S7，**全部采纳，一条不打回**：

1. 卡面写 `apply(...)`，冻结接口是 `run(...)` —— **我写错了**。`EffectType` 只有 `run`，改 `apply` 要动 12 个实现。
2. `ctx.caster()` 不存在（只有 `player()` / `target()` / `vars()`），复用 `DamageEffect.caster` 正确。
3. `var look = caster.getLookAngle()` 规避 `Vec3` import，符合白名单。
4. `motion` 退化到 `+X` 不告警 —— 纯函数该保持纯，`apply` 侧也不需要补。
5. `getAsDouble() + catch` 判「非数字」，与 `KnockbackEffect.raw` 同口径。

## 2. `dash` 真机验证（测试服）

一次性 `setDeltaMovement` 这条路径要证明两件事：**我们算得对**、**服务端会采纳**。分成两段取证。

### 2.1 `run()` 真跑（`skills/t_dash.json5` + `skill probe`）

```
[script] dash · [Minecraft] · look · 力度 1.60 · 上抛 0.35 · 速度 (1.600, 0.350, 0.000)
[script] dash · [Minecraft] · back · 力度 1.00 · 上抛 0.00 · 速度 (-1.000, 0.000, -0.000)
WARN dash 的 args.power <= 0，已跳过（不会退化成按 0.8 冲，R33-c）
WARN dash 的 args.up 不是数字，按缺省 0.2 处理
WARN dash 的 args.direction 只认 look|back（away 等近义词不收），按 look 处理
```

- `look` / `back` 符号相反 ✔；`power:0` 那条**零位移**（无 INFO 行）✔；两处非数字/近义词各 1 条 WARN 且回落缺省 ✔。
- 注意 `power:0` 时 `up`/`direction` 的告警**不出现** —— 早返回在取它们之前，与卡面行为 3 一致。

### 2.2 服务端是否采纳写出的速度

同一只 husk：`data merge entity … {Motion:[0.0d,0.0d,0.6d]}` → 一秒后 `Pos` 从 `z=12.8` 到 `z=29.8`。⇒ 写 `Motion` 有效，`dash` 的落地语句（`setDeltaMovement` + `hurtMarked`）具备实际效果。

## 3. M6 第三步第 1 小步：8 个模板 `EntityType`

| 文件 | 内容 |
|---|---|
| `mobs/RedtaleMob.java`（新，~60 行） | `extends Monster`；**一份**出厂属性表（HP 20 / 速度 0.25 / 攻击 2 / 护甲 0 / 视野 24），8 原型共用 |
| `mobs/RedtaleEntities.java`（新，~85 行） | `DeferredRegister<EntityType<?>>` + 8 条（`melee`/`ranged`/`caster`/`summoner`/`charger`/`flyer`/`elite`/`ambient`）+ 尺寸表 + `EntityAttributeCreationEvent` + `byBase()` / `baseIds()` |
| `RedtaleProject.java` | +3 行（register + attributes 监听） |

实测：`/summon redtale_project:<b>` 逐个跑 → **8/8 `Summoned new entity.redtale_project.*`**；
`Health 20.0`、`generic.scale` 基值 1.0、`redtale_project:physical_damage` 基值 0.0 存在
（⇒ `EntityAttributeModificationEvent.getTypes()` 覆盖到新类型，内容属性可直接写）。

**8 个 id 与 `MobSpec.BASES` 逐字相同**（R37-b），所以 `base` 字段不需要任何映射表。

## 4. 三条新事实（写进 PLAN §13.40）

1. **`server-*-srg.jar` 里没有 `MobType`**（`javap` + `unzip -l` 双证）→ 想让 8 个原型算亡灵，
   只能往 `#minecraft:undead` **实体标签**追加 id（`replace:false`，R23 手法）。v1 未做。
2. **无玩家在线时怪物 AI 不跑、但物理照跑** —— 同一只 husk 十几秒内 `Motion`/`Pos` 逐位不变（AI 没走），
   但 `data merge Motion` 立刻位移 17 格。⇒ 「怪自己攻击 → `post_attack` → 脚本」这类验法**必须有真人在线**，
   本轮因此改成 `skill probe`（FakePlayer）+ 直接写 `Motion` 两段取证。
3. `/summon` 显示的是 lang 键 `entity.redtale_project.melee`（14 基底物品同款问题）→ 实体 lang 生成器待补。

## 5. 未验证（挂账）

- **玩家客户端侧的 `dash`**：`SKILLS.md` §4 第 9 行原写「分帧给速度」，v1 是一次性写速度，
  怪物没事（AI 接着走），玩家会被客户端校正拉回。分帧要一张跨 tick 的位移事务表（R37-a），留作 M5-C 后续。
- 8 个原型的 AI 差异（v1 全是 `Monster` 默认）、模型/音效/体积缩放。
- 挂账不变：V4 附魔台真刷出、tooltip 真人客户端、pvp 真目标、玩家被 `stun`、真人 `/redtale skill mana`、真人刷怪。

## 6. 夹具与现场

- 测试服装过 `skills/t_dash.json5` / `t_dash_bad.json5` / 临时改过 `scripts/enchants_soul_reap.json5`
  （想让尸壳命中触发 `dash`）—— **全部已还原/删除**，原因见 §4-2。
- 立靶实体（8 只 `r37t` + 1 只 `r37fall`）已 `kill`，平台留在 `y=99`（下一轮复用）。
- 构建锁未占；两个仓本轮有提交（见 `docs/TASKS.md` §2.9 收工入库）。
