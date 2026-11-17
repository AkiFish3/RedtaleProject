# R30 · M5-B 物品绑定技能 + 冷却减免（2026-11-17 10:31-10:47）

## 一句话

技能从「命令能放」变成「**武器右键能放**」：`ItemContent` 加 `abilities`（15→16 字段）、
`ContentData.abilities` 真正被写入、`ContentItem#use` 接上第三条分支、`cooldown_reduction` 属性接进引擎。
K7 四组实测全过。

## 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0024 | 验收 | **`[VERIFIED]`** 3/3（build EXIT=0 / `AFFIX_PENDING`=0、`词条池 `=1 / 测试服 `content list` 五计数对账 + 10 行 × 7 字段） |
| T-0025 | 保持 `[OPEN]` | S8 未交付，继续挂着 |
| T-0026 | **新派** | `buff` 原语（新建 `BuffEffect.java` + `Builtins` 1 行），在制品 2/3 |

T-0024 的卡面期望 `demo_longsword rev 1` 是陈旧值（R19 孔位 +1、R20 `set_id` +1，现在定义就是 2）；
实测 2 与定义一致，按「期望写错、行为正确」判过，并在卡里记了原因。

## 改动清单

| 文件 | 改了什么 |
|---|---|
| `items/ItemContent.java` | 加第 16 字段 `List<String> abilities`；`parseAbilities` 逐项校验（非字符串 / 空串 / 缺命名空间 / 重复 → 各一条告警并跳过）；`describe()` 加「技能 N」 |
| `items/ItemFactory.java` | `ContentData.abilities` 由恒空改为写 `content.abilities()` |
| `items/ItemUpgrader.java` | 重建时取**新定义**的 `abilities`（与 `set_id` 同口径），不再照抄旧值 |
| `items/ContentItem.java` | `use` 第三条分支：`use → repair → abilities.get(0)`；新增 `definitionOf` / `contentIdOf`；`SkillEngine.Result` 四态映射到 `CONSUME` / `FAIL` / `PASS` |
| `items/ContentTooltip.java` | 加「技能 N：id · id」行（数值不重复写，D20 同源） |
| `core/skill/SkillEngine.java` | 新增 `cooldownReduction(ServerPlayer)`；`tryStart` 第 4 参从写死 `0.0` 改为读它 |
| `core/command/SkillCommand.java` | 新增 `/redtale skill use "<内容id>"`（FakePlayer 真调 `ItemStack#use`，A/B/C/D 四组） |
| `resources/.../items/demo_longsword.json5` | 加 `abilities: ["redtale_project:gale_slash"]`，`rev` 2→3 |

`ContentData` **一个字段都没动**，`CURRENT_SCHEMA` 仍 = 2：`abilities` 字段 M0 就预制好了，只是从来没人填。
这是 D17「注册表项预制」的直接回报 —— 加技能绑定没有第二次 schema 迁移。

## 实测（测试服起停 2 次，均已停，25567/25577 无 LISTEN）

```
[redtale] 右键探针 demo_longsword（FakePlayer 真调 ItemStack#use；第一条技能 redtale_project:gale_slash）：
  定义 abilities 1 条 [redtale_project:gale_slash]
  组件 abilities [redtale_project:gale_slash] · cooldown_reduction 0.0
  A 组 首次右键：返回 CONSUME · 木桩掉血 6 · redtale_project:gale_slash 剩余冷却 120 tick（定义 120）
  B 组 立刻再按：返回 FAIL · 木桩掉血 0（冷却期必须 FAIL 且 0 掉血）
  C 组 无 abilities 的 gem_ruby：返回 PASS · 冷却记录 0（应 PASS / 0，证明不是凡右键都放技能）
  D 组 cooldown_reduction +0.5：返回 CONSUME · 剩余冷却 60 tick（定义 120 的 50% 应 ≈ 60）
  ✓ K7 成立：abilities → 组件 → 右键施放 → 冷却拦截 → 冷却减免生效
```

- 掉血 6 = `gale_slash` 的 `damage` 白值 6.0，日志 `[script] damage · Cow 1000.00 → 994.00（白值 6.00，类型 redtale_skill_physical，已结算）`。
- `build --offline` EXIT=0（jar **392955 B**）；`selftest` **78 PASS**（本轮没有新增纯函数，故条数不变）。
- redtale 零 ERROR / 零异常 WARN；4 条 `RecipeManager` 失败是 R7 起就有的既有噪声。
- 日志 `全量刷新完成：34 区块（跳过 1032）/ 4 实体 / 升级 0 件` —— 测试服里没有 rev 落后的 `demo_longsword` 实例，迁移分支本轮未触发。

## 未验证（挂账）

1. **真人客户端的 tooltip「技能 N：…」行**：服务端 `lines()` 已产出，渲染要客户端（与 tooltip 其余行同一笔挂账）。
2. **`ItemUpgrader` 的 abilities 迁移分支**：要一件 `content_rev 2` 的 `demo_longsword` 存量件才能触发，测试服没有。

## 三条留给下一轮的事实

1. `ContentData.abilities` 早就存在 → **加绑定不用 schema 迁移**。下次给内容加字段先翻 `ContentData`，别急着 +1。
2. `FakePlayer` 不 tick，所以「手持装备的 `ATTRIBUTE_MODIFIERS`」永远刷不进 `AttributeMap`（R5 的结论在 M5-B 再验一次）：
   验属性类改动必须显式 `addTransientModifier`，否则会得「属性没接上」的假结论。
3. `ItemStack#use` 的返回值是最快的分支证据：`CONSUME` = 真做了事、`FAIL` = 拦住了、`PASS` = 不归我管。
   四组探针就是按这三种返回值设计的，比数日志快得多。
