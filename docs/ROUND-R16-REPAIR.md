# R16 · 修理结算上线（D23 第二半）+ 验收 T-0009 / T-0010

> 2026-11-16 11:18–11:45 · M2 收尾 · 派 **T-0012**

## 1. 卡板

| 卡 | 结果 | 依据 |
|---|---|---|
| T-0010 `RepairSpec` 解析器 | **VERIFIED** | 3/3 机器验收 + S7 独立探针 **38/38**（不复用 S8 的断言） |
| T-0009 `/redtale item upgrade` | **VERIFIED**（2/3 全过，第 3 条部分） | 代码复核 7 条逐条对上；挂行已补；控制台执行返回 `A player is required` = 已注册并走到 `getPlayerOrException`；`rev` 落后 → 计数 → 客户端 tooltip 立刻变仍挂账（需真人） |
| T-0011 基底 lang 脚本 | 仍 `OPEN`（S8 本轮未领） | — |
| **T-0012** `/redtale content check` | **新派** | `rev` 漏加检测（C8），在制品 2/3 |

## 2. 自研：修理链路（M2 最后一块）

新增/改动：

| 文件 | 内容 |
|---|---|
| `items/Repairer.java`（新） | `apply` / `matches` / `pickTarget` / `specOf` / `describe` —— **唯一结算点** |
| `items/ItemContent.java` | 第 **12** 字段 `repair`（`RepairSpec.Spec`）；护甲基底**一并**丢弃 `use` 与 `repair` 并 WARN；`describe()` 加「修理品」 |
| `items/ContentItem.java` | `use()` 拆成 `useConsumable` / `useRepair` 两个分支；`notify()` 加 `connection == null` 守卫 |
| `core/command/RedtaleCommand.java` | `item upgrade` 挂行（T-0009 遗留）；`/redtale repair <slot>` 与 `repair probe [id]` |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` + `items/demo_whetstone.json5` |
| `resources/redtale/defaults/items/demo_whetstone.json5`（新） | `mode: amount` / `amount: 120` / `targets: []` / `consume_item: true` |

四条口径见 `PLAN.md` §13.19（R16-a~d）。要点：

- **一份结算**：`/redtale repair` 与右键走同一个 `Repairer.apply`，命令里没有「管理员版逻辑」。
- **目标选择**：副手优先 → 否则背包里**损伤最重**且匹配的一件；工具按**对象身份**排除，绝不修到自己。
- **失败不收钱**：只有 `APPLIED` 且 `consume_item` 才扣；创造模式不扣。
- **不进组件**：与 `use` 段同口径，改配置 + reload 即生效，不必 `rev`+1。

## 3. 测试服实测（起停 4 次，均已停）

```
/redtale reload   → 已载入 1 条脚本 + 8 件装备内容（17 份配置文件）；全量刷新已排程 43 个区块
/redtale items    → demo_whetstone · amulet · 0 条属性 · 不可损坏 · 修理品 · rev 1
/redtale repair probe
  repair 段：mode amount / amount 120 / percent 0.0 / targets 0 项 / consume_item true
  受损目标（demo_longsword，损伤 600/1200）：损伤 600 → 480（应 −120），工具 2 → 1（修好 ✓，扣件 ✓，返回 CONSUME）
  满耐久目标：工具 2 → 2（未扣件 ✓）
  不可损坏目标（原版石头）：工具 2 → 2（未扣件 ✓）
  结论：修好 ✓ 扣件 ✓ 满耐久不扣 ✓ 不可修不扣 ✓ → 全过
```

- `/redtale item upgrade` 与 `/redtale repair 0` 在控制台均返回 `A player is required to run this command here` → 两条命令**已注册**且走到 `getPlayerOrException`，签名可用。
- 日志 `ERROR` 计数 **4**（R7 起的 RecipeManager 既有噪声：simplyswords mythicmetals_compat/* ×2、brewinandchewin kombucha、minecraft:red_dye），redtale 自身 **0** ERROR / 0 WARN。
- `build --offline` exit 0；`selftest` 仍 **70 PASS**。
- 测试服已停（25567/25577 无监听），构建锁已删。

## 4. 三条新事实（务必记住）

1. **`FakePlayer` 的副手是「写得进、读不出」**：`setItemInHand(OFF_HAND, x)` 确实把物品放进 `player.getInventory().offhand`，但同一时刻 `getItemInHand(OFF_HAND)` 与 `getItemBySlot(EquipmentSlot.OFFHAND)` **都返回 `air`**。
   → 探测/结算读副手统一用 `inventory.offhand.get(0)`；对真玩家两条路径等价。
   → 这也是第一版 `repair probe` 报「修好了但没扣件」的真因：目标根本没读到（`NO_TARGET` → `PASS`），而断言只看「损伤变小了没有」，被 0 < 600 蒙过去了。**断言要看具体差值，不能只看单调性。**
2. **`FakePlayer` 默认 `instabuild = true`**：不置 `false` 就永远验不出「扣件」。
3. **`ItemStack#use` 的返回值可当分支判据**：真修好 = `CONSUME`，失败 = `PASS`。先看枚举比先看计数更早发现「分支没进去」。

## 5. 下一步（R17）

1. 验收 **T-0011**（14 基底 lang）/ **T-0012**（`rev` 漏加检测，三次运行验收）。
2. **M3 起步**：先补 `docs/ITEM-MODEL.md` §5「孔位与套装」schema，再派解析器（顺序不能反）。
3. **惰性兜底 ⑤**：`DamageLayer` + `/redtale` 读路径调 `ItemUpgrader.ensure`（不放 tooltip）。
4. 挂账第 15 轮：一切要真人客户端才能验的项（见 `PLAN.md` §13.19 第 5 条）。
