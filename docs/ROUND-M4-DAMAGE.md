# R4 · M1 收尾：L2 伤害乘区（2026-11-15 21:55~22:05）

> 对应 `PLAN.md` §13.4 第 1 项：「`settle()` 接进 `LivingIncomingDamageEvent`（L2 乘区 +
> damage type 白名单 `redtale_project:*`），顺带验证 `CriticalHitEvent` 是否仍可作为暴击入口」。

## 1. 结论

1. **L2 乘区已上线并通过端到端实测**。同一把武器（同一数值）在三种条件下打出 10 / 10 / 12：
   - 我们的伤害类型 + 攻击者无属性 → **10**（×1.0，证明因子来自属性，不是写死）
   - 我们的伤害类型 + 攻击者 `physical_damage = 0.2` → **12**（×1.2）
   - 原版 `minecraft:generic` 同样 10 → **10**（白名单外，原样放行）
2. `settle()` 只有一份实现：实打（事件）与 `/redtale damage probe`（演算）走同一个方法。
3. M1 的三条出口条件，前两条（属性注册表 + `StatValue` 四段式 + 伤害管线）已具备；
   第三条「显示在 Lore」属于 M2 的 tooltip（D20），仍未做。

## 2. 产出

| 文件 | 作用 |
|---|---|
| `core/damage/MultiplierRules.java` | 乘区白名单（`ns:*` / 精确 id / `*`）+ 百分比来源属性；纯字符串匹配，可离线测 |
| `core/damage/DamageLayer.java` | `LivingIncomingDamageEvent`（**LOWEST**）上乘以因子 |
| `core/damage/DamageTrace.java` | 最近 8 次结算 + 三类计数，服务 `/redtale damage` |
| `core/math/DamageRules.java`（改） | 新增第三段 `multiplier` |
| `resources/data/redtale_project/damage_type/verification_strike.json` | 自建伤害类型（`scaling: never`），验收与将来技能伤害的样板 |
| `redtale/defaults/damage.json5`（改） | 新增 `multiplier` 段，默认 `["redtale_project:*"]` |
| `tools/gen_attr_lang.py`（改） | 新增 `EXTRA` 字典，让非属性 lang 键（死亡消息）不被重新生成抹掉 |
| `core/command/RedtaleCommand.java`（改） | `/redtale damage`、`damage probe`、`damage check <id>` |

## 3. 三条实现决定（写下来免得以后改坏）

### 3.1 只乘因子，不写绝对值

```java
Settle.Result r = Settle.settle(base, percent, List.of(), caps, sanity);
double factor = r.value() / base;          // ← 语义：倍数
event.setAmount((float) (base * factor));
```

数学上等价于 `setAmount(r.value())`，但后者会让下一个维护者顺手把别人的加成抹掉
（`DAMAGE-PIPELINE.md` §2 的那条硬约束靠代码形状守住，不靠注释）。

### 3.2 优先级 LOWEST

跑在最后，读到的就是所有人算完的数。这同时回答了 `DAMAGE-PIPELINE.md` §10 的
「是否需要在 LOWEST 才能作用在所有 mod 之后」——**是**，且实测无冲突。

### 3.3 白名单是硬边界

只处理 `redtale_project:*`。别人的伤害（`minecraft:generic` 等）只记一笔计数
（`passthrough_external`），不碰 `amount`。理由不是保守：服上 12~19 个 mod 在同一个事件里
加减乘，我们多乘一次就再也说不清是谁改的数字。

## 4. 实测记录（`E:/server-test`，NeoForge 21.1.257）

启动：`Done (2.449s)!`，属性挂载 `99 个实体类型 × 66 条自建属性（实际新增 6534 处）`。

准备（RCON）：

```
summon minecraft:zombie ~ ~ ~ {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["rt_target"]}
attribute @e[tag=rt_target] minecraft:generic.max_health base set 500
attribute @e[tag=rt_target] minecraft:generic.armor base set 0        // 排除护甲减免干扰
attribute @e[tag=rt_attacker] redtale_project:physical_damage base set 0.2
```

三组：

| # | 命令 | 目标 Health | 实际伤害 | 判定 |
|---|---|---|---|---|
| 1 | `/damage @e[tag=rt_target] 10 redtale_project:verification_strike by @e[tag=rt_plain]` | 500 → **490** | 10 | ×1.0（攻击者无该属性）✅ |
| 2 | `/damage @e[tag=rt_target] 10 redtale_project:verification_strike by @e[tag=rt_attacker]` | 490 → **478** | 12 | **×1.2** ✅ |
| 3 | `/damage @e[tag=rt_target] 10 minecraft:generic` | 478 → **468** | 10 | 白名单外放行 ✅ |

`/redtale damage` 输出（同上三组之后）：

```
[redtale] 伤害规则：闸门 全关（D16 默认） · 兜底 ceiling 1000000000 · 日志阈值 10000（采样 10%）
  乘区：伤害类型 redtale_project:* · 白名单外只观测不改值 · 百分比属性 physical_damage
  观测：进场 5 次 · 白名单内 2 次 · 实际改值 1 次
  最近一次放行：minecraft:generic（别人的伤害，原样通过）
  最近 2 次结算：
    redtale_project:verification_strike  10 × 1 = 10（百分比池 0）
    redtale_project:verification_strike  10 × 1.2 = 12（百分比池 0.2）
```

`/redtale reload` 后计数归零（`进场 0 次`），确认 reload 会重置观测，
不会把上一份配置的战绩算进新配置。

`/redtale damage check redtale_project:verification_strike` → `会乘`；
`/redtale damage check minecraft:generic` → `放行`。

## 5. 踩坑（记下来，下轮别再踩）

1. **`StringArgumentType.string()` 不吃冒号**：`redtale_project:xxx` 会在 `:` 处报
   `Expected whitespace to end one argument`。带引号能过，但丑。正解是
   `ResourceLocationArgument.id()` —— 它专门解析 `namespace:path`。
   （`/redtale attr` 仍是 `string()`，所以那边只能用 `physical_damage` 这种裸名。）
2. **`ResourceLocationArgument.getId(ctx, name)` 不抛 `CommandSyntaxException`**（javap 实测），
   写 try/catch 会直接编译失败。
3. **`ResourceLocation` 没有 `isValidResourceLocation`**（1.21.1），只有 `tryParse` /
   `isValidPath` / `isValidNamespace`。
4. **原版 server jar 里没有 `data/`**：想抄 `damage_type` 的 JSON 格式，得去翻已装 mod
   （`farmersrespite` / `bookshelf` / `FarmersDelight` 各有一份），别在 server jar 里
   `unzip -p data/...`。格式确认：
   `{"message_id": "...", "scaling": "never|when_caused_by_living_non_player|always", "exhaustion": 0.0, "effects": "hurt"}`。
5. **`scaling: never` 是为了验收确定性**：`when_caused_by_living_non_player` 会按难度缩放
   （easy 0.5 / normal 1.0 / hard 1.5），10 点会变成 5 或 15，对账时会被当成 bug。
   真实技能伤害该用哪个 scaling 到 M5 再定。
6. **测试靶子必须 `armor base set 0`**：僵尸自带 2 点护甲，不减到 0 的话 10 点只会掉 8~9，
   和我们的因子混在一起看不出是谁干的。

## 6. 未验证 / 留给下一轮

- **玩家上下文的实打**：无客户端，只能用命令造伤害。真人挥剑打出的 `minecraft:player_attack`
  目前**不经过我们的乘区**（白名单只放 `redtale_project:*`）——这是设计，不是缺口：
  自定义武器在 M2 会带自己的伤害类型。
- **条件乘区**（`pve_damage` / `pvp_damage` / `undead_damage`）：需要先按目标分类，
  等 M2 的条件乘区一起做。当前 `percent_attributes` 只开无条件项 `physical_damage`。
- **`CriticalHitEvent`**：本轮未接（M1 没有暴击来源），留到 M2 与 `critical_strike_chance` 一起验。
- **属性值口径**：本轮把 `physical_damage = 0.2` 当 +20%。这条要 Aki 确认（Q-014），
  因为它决定所有内容配置与 tooltip 的写法。
