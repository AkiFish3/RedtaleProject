# R15 · 消耗品右键生效（D23 第二层）+ 验收 T-0008

> 2026-11-16 09:49–10:10 · 里程碑 **M2 出口剩余两块之一**（消耗品）；另一块（磨刀石修耐久）等 T-0010。
> 证据全部来自测试服 `E:\server-test`（21.1.257，本次起停 2 次，均已停服）。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| **T-0008**（消耗品 `use` 段解析器） | 验收 | **VERIFIED**（3/3 + S7 独立探针 `build/probe/S7ConsProbe.java` **48/48**） |
| **T-0011**（`tools/gen_item_lang.py`，原 T-0005 重派） | 派单 | 在制品 T-0009 / T-0010 / T-0011 = **3/3** 封顶 |

T-0008 的三条机器验收：`build --offline` exit 0 / `grep -c "public static Spec parse"` = 1 / jar 内 `items/ConsumableSpec` **3** 个 class（主类 + `$Effect` + `$Spec`）。

S7 独立探针不复用 S8 的断言，覆盖：两条非消耗品路径零告警、`use` 非对象、§3.1 样例逐字段、`use:{}` 六项缺省、越界夹取 8 条告警逐条、`chance` 恰为 0.0/1.0 不告警、坏项 7 条告警且幸存项正确、两表不可变、非数字字段走默认。

## 2. 自研：消耗品右键（本轮唯一功能）

### 2.1 结构

| 类 | 变更 |
|---|---|
| `items/ContentItem.java` | **新建**。`extends Item`，唯一行为 = 覆写 `use()` |
| `items/ItemContent.java` | 加第 11 字段 `ConsumableSpec.Spec consumable` + `isConsumable()`；解析末尾调 `ConsumableSpec.parse`，**护甲基底则丢弃并告警** |
| `items/RedtaleItems.java` | 非护甲基底由 `new Item(props)` 改 `new ContentItem(props)`（护甲仍是 `ArmorItem`） |
| `core/script/ScriptEngine.java` | 加 `public int runCalls(List<ScriptCall>, ScriptContext)`：跑「物品自带脚本」用，不经 trigger/condition/脚本冷却 |
| `core/script/builtin/MessageEffect.java` | 加 `ctx.player().connection == null` 守卫（FakePlayer 无连接时 `sendSystemMessage` 会 NPE） |
| `core/command/RedtaleCommand.java` | 加 `/redtale use probe [内容id]` |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` + `items/demo_stew.json5` |
| `resources/redtale/defaults/items/demo_stew.json5` | **新建**验收件（基底 `amulet`，2 效果 + 回复 6 + 冷却 400t + 2 条脚本） |

### 2.2 五条口径（本轮定，后续沿用）

| # | 口径 | 理由 |
|---|---|---|
| R15-a | 消耗品定义**不进 `ContentData`**，右键时按 `contentId` 现查 | 改 `use` 段 + `/redtale reload` 对存量物品立刻生效，不必 rev+1；D33 的迁移面也不扩大 |
| R15-b | `use` 段**只能挂非护甲基底**，护甲基底解析期丢弃 + WARN | 护甲基底是 `ArmorItem`，`use()` 的语义是「穿戴」；抢过来会让护甲穿不上，且失败方式很隐蔽 |
| R15-c | 客户端 `use()` 一律返回 `CONSUME`，**一切副作用只在服务端** | 客户端不知道有没有 `use` 段、够不够饿；双端各算一遍 = 双扣物品 |
| R15-d | `only_when_hungry` 不满足时返回 `FAIL`（不扣件、不冷却、不生效） | 与「用了但没效果」区分开：玩家能立刻看出是被门槛挡住 |
| R15-e | `use.script` 走 `ScriptEngine.runCalls`，**不进 `scripts/` 注册表** | 那段脚本属于这件物品，不是全服触发器；单条失败只记日志，其余照跑 |

## 3. 实测（`/redtale use probe`，FakePlayer 真调 `ItemStack#use`）

**A 组 `demo_stew`（`consume: always`，持 2 件，饱食 0/饱和 0）**

```
[redtale] 消耗品探测 demo_stew（FakePlayer，服务端真调 ItemStack#use；返回值 CONSUME）：
  use 段：效果 2 条 / 回复饱食 6 / 饱和 0.6 / 冷却 400t / 脚本 2 条 / consume=always
  使用前：饱食 0 饱和 0.0 效果 0 条 手中 2 件
  使用后：饱食 6 效果 2 条（minecraft:regeneration 200t×1, minecraft:saturation 1t×0）手中 1 件 冷却 已进入
  冷却拦截：饱食 6 → 6，件数 1 → 1（成立）
  结论：效果 ✓ 饱食 ✓ 扣件 ✓ 冷却 ✓ 拦截 ✓ → 五项全过
```

脚本层另有日志证据（`console_log` effect）：`[script] [redtale] demo_stew 被 [Minecraft] 使用（use.script）`
—— `%player%` 展开成 FakePlayer 名 `[Minecraft]`，证明 `runCalls` 真的跑了 `use.script` 的两条。

**B 组 `probe_hungry`（`consume: only_when_hungry`，S7 手放的验收件，不进默认包）**

```
  use 段：效果 0 条 / 回复饱食 4 / 饱和 0.2 / 冷却 100t / 脚本 0 条 / consume=only_when_hungry
  使用前：饱食 0 饱和 0.0 效果 0 条 手中 2 件
  使用后：饱食 4 效果 0 条（）手中 1 件 冷却 已进入
  冷却拦截：饱食 4 → 4，件数 1 → 1（成立）
  饱食门槛（consume=only_when_hungry，饱食 20/20）：件数 1 → 1（挡住）
  结论：效果 ✓ 饱食 ✓ 扣件 ✓ 冷却 ✓ 拦截 ✓ → 五项全过
```

**C 组 负路径**

| 输入 | 实测输出 |
|---|---|
| `redtale use probe demo_longsword` | `demo_longsword 没有 use 段（不是消耗品）` |
| `items/probe_armor_use.json5`（`base: chestplate` + `use`） | reload WARN：`… 的基底 chestplate 是护甲（右键 = 穿戴），use 段已忽略 …`；`/redtale use probe probe_armor_use` → 「没有 use 段」 |

清掉该验收件后 `redtale reload` = `7 件装备内容（16 份配置文件）`、**零告警**。

## 4. 新记的三条事实（下次别再踩）

1. **`FoodData` 可直改**：`setFoodLevel(int)` / `setSaturation(float)` / `setExhaustion(float)` 都是 public —— 造「饿 / 不饿」两组不需要 NBT、不需要真玩家。
2. **`sendSuccess` 的 lambda 在「发送时」求值，不是「注册时」**：本轮 `使用前：饱和 6.0` 的假象就是这么来的（读的是 `eat()` 之后的值）。要在 `use()` 之前把值落进 `final` 局部量。
3. **FakePlayer 没有 `connection`**：`ServerPlayer#sendSystemMessage` 会 NPE，被 `runCalls` 的 catch 记成 ERROR 而不是崩服 —— 这类「看起来只是日志脏」的报错要当功能缺陷修（已在 `MessageEffect` 加守卫）。

## 5. 未验证 / 挂账

- **真人右键**（客户端手势、`InteractionResult` 的表现、物品冷却的 HUD 饼图）：第 14 轮挂账，等一次带客户端的窗口。
- 消耗品在**创造模式**不扣件（`instabuild` 分支）只有代码路径，无实测（FakePlayer 不是创造）。
- 工具提示尚未显示「右键：使用」（D20 的显示口径只覆盖武器；消耗品加不加提示行待 M2 收尾时定）。

## 6. 下一步（R16）

1. **验收 T-0009 / T-0010 / T-0011**（三张在制）。T-0009 交货后 `RedtaleCommand` 的挂行由 S7 做。
2. **M2 最后一块**：`RepairSpec` 到位后做磨刀石结算 + `/redtale repair`。
3. **惰性兜底 ⑤**：`DamageLayer` 与 `/redtale` 读路径上调 `ItemUpgrader.ensure`（不放 tooltip，客户端也触发）。
4. 消耗品 tooltip 行（D20 延伸）+ `demo_stew` 的 `rev` 纪律检查。
