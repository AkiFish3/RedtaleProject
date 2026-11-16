# R12 · 物品更新落地（D33 的 `ensure` + 三个刷新点）

日期：2026-11-16 07:03~07:20 · 里程碑 **M2**（D33）
卡板：验收 **T-0006 → VERIFIED**、派 **T-0008**（消耗品 `use` 段解析器）

## 1. 本轮做了什么

| # | 内容 | 位置 |
|---|---|---|
| 1 | `ItemUpgrader.ensure(ItemStack)` = D33 唯一写回点（4 条出口） | `items/ItemUpgrader.java`（+`Status` / `Result`） |
| 2 | 三个被动刷新点：登录 / 换维度 / 复活 / 开容器 / 区块加载 | `core/hook/ItemUpgradeHooks.java`（新建） |
| 3 | 事件注册 1 行 | `core/hook/GameHooks.java` 构造器 |
| 4 | 派单 T-0008（消耗品 `use` 段解析器，D23 第一层） | `docs/TASKS.md` §5 |

## 2. `ensure` 的四条出口（照 `ITEM-UPDATE.md` §4.3）

| 出口 | 条件 | 动作 |
|---|---|---|
| `NOT_CONTENT` | 无 `redtale_project:content` 组件 | 立刻返回（热路径，每件物品都要过一次） |
| `ORPHAN` | 定义已删 | **一个数值都不改**（§4.4 不静默销毁玩家资产） |
| `UNCHANGED` | `schema` 与 `rev` 都相等 | 返回，成本一次 int 比较 |
| `UPGRADED` | 其余 | 重建 `CUSTOM_NAME`/`RARITY`/`MAX_DAMAGE`/`ATTRIBUTE_MODIFIERS`/`preset_id`，写回新 `content_rev` |

重建时的数值分派（§4.3 第 1、2 条）：

```
for key, oldValue in data.rolled_stats:
    key 不在新定义 stats        → 丢弃 + 逐个 LOG.info 记账（键名 / 旧值 / rev 变化）
    key 在 data.rolled_keys     → 保留 oldValue（玩家 roll 的，不许动）
    否则                        → 取新定义的值（定义型，跟新定义走）
遍历旧键集 → 新定义新增的键不会凭空出现（不白送）
```

词条只删「定义里已不存在」的 id，**且词条表为空时原样保留**——没装词条配置不等于「全服词条作废」。

## 3. 实测（测试服 `E:\server-test`，1 次启动）

启动 `Done` 后 `[redtale] 已载入 1 条脚本 + 5 件装备内容（14 份配置文件）`，4 条 `RecipeManager` ERROR 是 R7 起的既有噪声。

取样件：`config/redtale_project/items/t_upg_sword.json5`（`rev: 3`，`stats {attack_damage: 9.0, physical_damage: 0.25}`，`durability 500`，`rarity epic`）。
四件物品用手写组件放进箱子，再 `forceload remove/add` 强制区块重载 → `ChunkEvent.Load` 触发扫描：

| 组 | 初始组件 | 结果（`data get block`） | 判定 |
|---|---|---|---|
| A rev 0，`rolled_keys=[generic.attack_damage]`，`rolled_stats {attack_damage 5.0, physical_damage 0.1, ghost_key 1.0}` | 见左 | `attack_damage` **5.0 保留**、`physical_damage` **0.1 → 0.25**（跟新定义）、`ghost_key` 删除并记日志、`content_rev` **0 → 3**、`custom_name` 更新、`rarity epic`、`max_damage 500` | ✅ UPGRADED |
| B rev 3（= 定义） | `{physical_damage 0.31}` | 组件**逐字未变**（连 `custom_name` 都没写） | ✅ UNCHANGED |
| C `content_id: ghost_sword` | `{physical_damage 0.42}` | 组件**逐字未变** | ✅ ORPHAN |
| D（首轮，键未归一） | `attack_damage`（裸名） | 被当作「定义里没有」删掉 | ⚠️ 见下 |

日志原文：

```
[redtale/item-update] [UPGRADED] t_upg_sword rev 0→3：物品更新：t_upg_sword 键 ghost_key 已从定义删除（旧值 1.0），rev 0→3
[redtale/item-update] 刷新点 chunk[minecraft:overworld] 更新 1 件（累计 2）
```

`/redtale reload` 后 `status` 正常：`内容 5 件装备 / 14 个基底物品`；`ERROR` 计数仍为 4（无新增）。测试服已停（25567 端口关闭）。

## 4. D 组暴露的两条事实（写进口径，不是 bug）

1. **`rolled_stats` 的键必须用归一后的全名**（`minecraft:generic.attack_damage`，不是 `attack_damage`）。
   `ItemContent.parse` 走 `AttributeSpecs.resolve` 归一，`plan` 用「定义 stats 的键集」比对，
   手写组件时写裸名 → 被当成「定义里没有」→ 删键（日志有记录，不会静默）。
   **我们自己建的物品永远不会踩**（`ItemFactory.build` 写的就是归一后的键），但**运营用 `/give` 手写组件会踩**。
2. **`ContentData` 构造器是 `Map.copyOf`**，`rolled_stats` 的迭代序在 record 构造后已不可保证
   （`ImmutableCollections` 每 JVM 随机 SALT）。所以「保旧 Map 迭代序」只能保证「与传入 Map 一致」，
   跨 JVM 启动不保证——只影响删键日志顺序，不影响数值。T-0006 验收时已记一条。

## 5. 没做的（明确挂账）

- **刷新点 ④（reload 后全量分帧扫描）**：要跨 tick 排程，本轮不做。当前覆盖 = 登录 / 换维度 / 复活 / 开容器 / 区块加载 + 下次登录补齐。
- **读路径惰性兜底 ⑤**（tooltip / 伤害结算 / 命令）：`ItemTooltipHook` 是客户端也会触发的事件，改写物品必须限定服务端上下文；放在结算路径更合适，留给下一轮与 `DamageLayer` 一起接。
- **U6（箱子里的物品重载区块后更新）**：本轮**已闭合**（就是上面 A/B/C 组用的手段）。
- **真人登录 / 背包 / 副手 / 末影箱**：无客户端，挂账。

## 6. 下一步（R13）

1. 验收 T-0007（`/redtale item scan`）与 T-0008（消耗品 `use` 段解析器）。
2. `ensure` 接进结算路径（惰性兜底 ⑤），与 `DamageLayer` 同批。
3. 刷新点 ④：`/redtale reload` 之后按 tick 分帧扫已加载区块（每 tick N 个区块）。
4. M2 剩最后一块：消耗品右键生效 + 修理（D23），`ConsumableSpec` 交付后接线。
