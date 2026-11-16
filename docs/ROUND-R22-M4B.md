# R22 · M4 第二块：`post_attack` → 脚本层真跑通（V9/V10）+ V8 数值对账

时间：2026-11-16 18:39–19:05 · 测试服 `E:\server-test`（起停 4 次，均已停、25567/25577 无 LISTEN）

## 1. 卡板

| 卡 | 结果 |
|---|---|
| T-0016 `/redtale set check` | **VERIFIED**（3/3 + 功能五跑，见 §2） |
| T-0018 `/redtale validate` 第 5 条 + 裸名提示 | **新派**（OPEN，独占 `ValidateCommand.java`） |
| 在制品 | T-0017（OPEN，S8 未领）+ T-0018 = **2/3** |

## 2. 验收 T-0016

三条机器可判：

1. `gradle build --offline` → exit 0（jar 323428 B）
2. `grep -c "SetCheckCommand" RedtaleCommand.java` = **1**（卡面写 `::node` 得 0 —— 静态调用用 `.`，S8 已如实报，接受）
3. jar 内 `core/command/SetCheckCommand.class` = **1**（无内部类）

功能验（RCON，四组坏定义 + 一组 `cap==0` 专测）：

```
套装校验：5 个定义 · 9 件 pieces · 问题 4 条
  t_bad1: piece ghost_piece 不存在
  t_bad2: piece t_a_sword 的 set_id 是 t_bad1（应为 t_bad2）
  t_bad3: 2 件档要求 2 件，但 pieces 最多能同时穿 1 件（同槽基底互斥）
  t_bad4: 1 件档重复（同一 modifier id 会互相覆盖）
```

- 一个检查各命中一条，**无串味**（t_bad3 的两件胸甲只报可达性、不报别的）。
- 连跑两次 `diff` 为空 → 顺序稳定。
- 移走坏定义 + `reload` → `1 个定义 · 2 件 pieces · 问题 0 条` + `（套装引用完整）`。
- `cap == 0` 专测（`t_bad5`：两个 ghost piece + 1/2 两档）→ 只有 2 条「不存在」+ 1 条「一件都穿不上」，**零逐档刷屏**，S8 提的三条口径全部按卡面成立。

## 3. M4-2：`post_attack` → L2 → L3 真跑通

### 3.1 做了什么

| 改动 | 位置 |
|---|---|
| 默认脚本 `enchants_soul_reap.json5`（`id: enchants/soul_reap`，`console_log` 打 `%var.*%`） | `src/main/resources/redtale/defaults/scripts/` + `ConfigStore.DEFAULT_FILES` |
| `ScriptContext#expand` 支持 `%var.<key>%`（未定义键原样保留） | `core/script/ScriptContext.java` |
| **`script` 字段改 `Codec.STRING`**（原 `ResourceLocation.CODEC`） | `enchants/ScriptedEnchantmentEffect.java` |
| `DamageTrace.skip(type, base)` + `recentPassthrough()`，`/redtale damage` 多打一行 | `core/damage/`、`core/command/RedtaleCommand.java` |

### 3.2 关键 bug（本轮最有价值的一条）

第一版把脚本写进附魔 JSON5 后，打怪**完全没有日志**。三段定位：

1. `/enchant` 能附上 → L0 在注册表 ✅
2. 日志出现 `附魔脚本 minecraft:enchants/soul_reap 不存在` → **id 被加了 `minecraft:` 命名空间**
3. 原因是 `ScriptedEnchantmentEffect.script` 用了 `ResourceLocation.CODEC`，裸 id `enchants/soul_reap`
   被补成 `minecraft:enchants/soul_reap`，而 `scripts/*.json5` 的 `id` 是纯字符串 → `fireById` 永不命中。

改成 `Codec.STRING` 后立刻通。**这类失败零报错**（只有一条「脚本不存在」WARN，很容易被当成「脚本还没写」），
所以排查纪律写进 `PLAN.md` §13.25：L0 → L2 → L3 三段分开验。

### 3.3 V9（脚本真跑）证据

尸壳持 `iron_sword[minecraft:enchantments={levels:{"redtale_project:soul_reap":2}}]` 打村女：

```
[script] [soul_reap] 触发 · 施法者 server · 等级 2 · heal_ratio=0.08 · per_level=0.02
```

`args` 的三个值（`0.08` / `0.02`）与 `enchant_level` 全部到位 → args → `vars` → effect 文本这条链通。
「施法者 server」是因为 `apply` 的第 4 参在 `post_attack` 下是**受害者**（村女，非玩家）→ `ScriptContext.ofServer`。

### 3.4 V10（缺脚本只 WARN 一次）

把 `scripts/enchants_soul_reap.json5` 的 `id` 改成 `enchants/soul_reap_v10` + `/redtale reload`
（**不能直接删文件**：`DEFAULT_FILES` 会在 reload 时把它重新落地），然后连打多次：

- WARN 计数 = **1**（`附魔脚本 enchants/soul_reap 不存在（附魔仍生效，只是这次触发没有执行体）`）
- 剑上的附魔组件仍在、伤害不变、服务端不崩。

### 3.5 V8 数值对账

同一只尸壳、同一只 10 万血村女，只换剑上的附魔等级，读 `/redtale damage` 的「最近放行进场值」：

| 武器 | 进场值 | 与白板差 |
|---|---|---|
| `iron_sword` | **8.0** | — |
| `+ super_sharpness I`（`linear base 2.0`） | **10.0** | **+2.0** |
| `+ super_sharpness III`（`2.0 + 1.0×2`） | **12.0** | **+4.0** |

差值逐位等于 JSON5 里写的数 ⇒ L0 附魔数值真的进结算，且我们的 L2 乘区没动 `minecraft:mob_attack`。

> 血采样法不可用：同窗口混着 `3.0`（另一只空手尸壳）与武器值，采样出 3/7/10 混值。
> 进场值才是逐事件的精确读数 —— 这就是本轮给 `DamageTrace` 加 passthrough 的原因。

## 4. 两条新事实（务必记住）

1. **`post_attack` 的 `apply(owner=...)` 是 affected 实体（受害者）**，攻击者武器从
   `EnchantedItemInUse.itemStack()` 取。写吸血/反伤类执行体时目标就是它。
2. **我们的 14 基底物品不在 `#minecraft:enchantable/sharp_weapon`**：item tag 是数据驱动、只列原版 id。
   实测 `/enchant @e ... minecraft:iron_sword` → `Applied`；同一句对 `redtale_project:sword` → `cannot support that enchantment`。
   修法（下轮）：生成 `data/redtale_project/tags/item/enchantable/weapon.json`，两份默认附魔的 `supported_items` 改指向它。

附带发现：`exclusive_set: "#redtale_project:exclusive_set/lifesteal"` 的 tag 文件不存在，启动期 WARN
`Not all defined tags for registry ... are present in data pack`。它不阻断加载（空 tag = 无互斥），但该有人管。

## 5. 环境

- `build --offline` exit 0（jar **325236 B**）、`selftest` **70 PASS**（未变）。
- 测试服 `Done (2.036s)`；redtale 零 ERROR，`ERROR` 仍 4 条（R7 起的 RecipeManager 既有噪声）。
- 构建锁已删；测试服已停（25567/25577 无 LISTEN）；测试期造的实体已 `kill`、`forceload remove all`。
- 测试夹具（4 组坏套装 + 5 件配套内容 + `t_bad5`）已移出 `config/`，环境回到 10 件内容 / 1 个套装。

## 6. 下一步（R23）

1. 验 T-0017 / T-0018。
2. 自建 item tag 生成（让内容武器能吃自家附魔）。
3. `soul_reap` 的执行体：需要 `heal` 原语 + `ScriptContext` 带 affected 实体；不加原语这份附魔只有日志没有玩法。
4. `exclusive_set` 缺失 tag 的处理（生成空 tag 或 `validate` 报出）。
