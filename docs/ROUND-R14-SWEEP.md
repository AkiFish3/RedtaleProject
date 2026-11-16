# R14 · D33 第 ④ 个刷新点（reload 后分帧全量刷新）

2026-11-16 08:27-08:50 · M2（物品更新系统收尾）· 卡板：验 T-0007、派 T-0010

## 1. 本轮做了什么

| # | 内容 | 文件 |
|---|---|---|
| R14-1 | 新类 `UpgradeSweep`：④ 号刷新点的分帧执行器 | `core/hook/UpgradeSweep.java`（新，~200 行） |
| R14-2 | `ItemUpgradeHooks` 改单例 + `ensure` 提升为包内可见（sweep 复用同一计数与日志） | `core/hook/ItemUpgradeHooks.java` |
| R14-3 | tick 挂载：`ServerTickEvent.Post` → `UpgradeSweep.get().tick()` | `core/hook/GameHooks.java`（+1 方法） |
| R14-4 | 触发点：`/redtale reload` 成功后才排程（**启动期不排**） | `core/command/RedtaleCommand.java` |
| R14-5 | 手动重排命令：`/redtale item sweep` | 同上 |

卡板：**T-0007 `[DONE]` → `[VERIFIED]`**（3/3）；**派 T-0010**（`RepairSpec` 解析器，D23 第二半）。在制品 T-0008 / T-0009 / T-0010 = 3/3。

## 2. 设计：为什么队列里存坐标而不是区块对象

- 排程时把「已加载区块」摊平成 `(ServerLevel, chunkX, chunkZ)` 队列，每 tick 取 8 个，**取的时候再 `getChunkNow` 一次**。
- 理由：排队期间区块可能被卸载，持有 `LevelChunk` 会让它无法回收（内存泄漏），且拿到手也可能已经失效。取不到就 `chunksSkipped++`，下次区块加载时 ③ 号钩子会补。
- 覆盖三层：在线玩家背包（立即）→ 掉落物 / 展示框（立即，走 `level.getAllEntities()`）→ 容器（排队，按区块）。

## 3. 实测（`E:\server-test`，两次启动，均已停服）

### 3.1 第一次（触发点还挂在 `RedtaleCore.reload()` 上）

- 启动即排程：`全量刷新已排程：25 个区块 / 0 个实体 / 背包 0 件` → `完成：25 区块（跳过 1746）/ 升级 0 件，耗时 307 ms`。
  **这是缺陷**：`ServerStartedEvent` 时 `MinecraftServer.isRunning()` 已经为真，原来的「`isRunning()` 兜住启动期」假设不成立（R8 的 `PackEnabler` 是同一形状的守卫，那里靠的是 2s 时间窗而不是这个）。
- 手写过期物品（`t_upg_sword` / `content_rev: 0` / `rolled_stats{attack_damage:5.0}` / `rolled_keys[attack_damage]`）放进**已强制加载**的区块箱子：
  - 写后读：无 `content_rev`、无 `attribute_modifiers`（= 基线）。
  - 等 6s 再读：**逐字未变**（说明 ②③ 钩子不会误触发，④ 才是唯一手段）。
  - `/redtale item sweep` → `52 个区块 / 升级 1 件`；再读：`content_rev: 3`、`attribute_modifiers` 重建（**amount 5.0 保留**，来自 `rolled_keys`）、`custom_name 更新测试剑` / `rarity epic` / `max_damage 500` 重建。

### 3.2 第二次（触发点移到 `/redtale reload`）

- **启动期零排程**（`grep upgrade-sweep` 无输出）。
- 写过期物品 → 5s 后仍逐字未变 → `/redtale reload`：
  - 命令回显：`已载入 1 条脚本 + 5 件装备内容（14 份配置文件）；全量刷新已排程 52 个区块`
  - 日志：`52 区块（跳过 1899）/ 0 个实体 / 升级 1 件，耗时 315 ms`
  - 6s 后箱子里的物品：`content_rev 0 → 3`，`rolled_stats` 里 `attack_damage` 仍 5.0，名称 / 稀有度 / 耐久上限重建。
- 实体分支：`summon minecraft:item` 一只 `content_rev: 0` 的掉落物 → `redtale item sweep` → 日志 `1 个实体`、`升级 1 件`；物品实体组件变 `content_rev: 3` + 名称 / 稀有度 / 面板（该件手写时没带 `rolled_stats`，所以面板只有一个 `+0` 占位，符合 D21 白板化）。

### 3.3 T-0007 验收（`/redtale item scan`）

- ①`gradle build --offline` exit 0；②`grep -c "ItemScanCommand::scan"` = 1；③jar 内 `core/command/ItemScanCommand.class` × 1。
- 测试服：0 名在线玩家时输出 `扫描 0 名玩家：0 件内容物品` + `（无内容物品）`，返回 0，零 ERROR。
- **挂账**：件数与 rev 分布的对账要真实在线玩家（`getPlayerList().getPlayers()`），`/redtale give` 也要玩家上下文 → 归入「需要一次带客户端的验收窗口」。

## 4. 关键事实（下轮直接复用，别再踩）

1. **`MinecraftServer.isRunning()` 在 `ServerStartedEvent` 阶段已为真**，不能拿它区分「启动期」与「运行期」。R8 的 `PackEnabler` 之所以没出问题，是因为它靠 2s 时间窗去重，不是靠这个守卫。
2. **`ChunkMap#getChunks()` 是 `protected`**，1.21.1 没有公开的「枚举已加载区块」接口。本类反射取一次并缓存；取不到就退化成「只扫实体 + 背包」（容器靠 ②③ 慢慢补），不抛异常。`ChunkMap.chunkMap` 字段本身是 `public final`。
3. **`ChunkHolder#getTickingChunk()` 只覆盖「区块 tick」档及以上**，出生点 / 强制加载区块要补 `getChunkToSend()`；两者都 null 的 holder 是「表里有、实际没加载」的占位（本次 1899 个），计入 `chunksSkipped` 属正常。
4. 掉落物与展示框**不在**方块实体表里，`§4.1 ③` 的 `getBlockEntitiesPos()` 扫不到它们，必须单独走 `level.getAllEntities()`。
5. 手写组件时 `rolled_stats` 键必须用**归一全名**（`minecraft:generic.attack_damage`），裸名会被 `plan` 当「定义里没有」删掉（R12 已记，本轮再次踩到同一坑的边界）。

## 5. 代价与闸门

- 每 tick 8 区块 → 本次 52 区块 **315~344 ms 总耗时**（≈ 21 tick，每 tick ~6ms 分摊）。两千区块量级的服约 250 tick（12s）扫完，全程只占单 tick 的固定一小块。
- 只在**管理员显式 `/redtale reload`** 后触发；`/redtale item sweep` 可手动重排（重入即重排，旧队列丢弃）。
- 未做：分帧量做成配置项（现在写死 8）。若正式服区块量大到卡顿，再提成 `item_update.chunks_per_tick`。

## 6. 下一步（R15）

1. **验收 T-0008 / T-0009 / T-0010**（三张都在制）。T-0008（`ConsumableSpec`）交付后由 S7 接 `Item#use`（消耗品右键生效），T-0010 交付后接着做 `/redtale repair`。
2. **惰性兜底 ⑤**（`§4.1` 兜底条）：`ItemTooltipHook` 客户端也会触发，改写必须限定服务端 → 放 `DamageLayer`（结算路径）与 `/redtale` 命令的读路径，不放 tooltip。
3. **M2 出口剩余两块**：消耗品右键生效、磨刀石修耐久（都等 S8 的两张解析卡）。
4. **挂账第 13 轮**（需要一次带客户端的窗口）：`item scan` 件数 / rev 分布对账、`/redtale give` 真人手持、tooltip 真人样子、真人 pvp、真人双持 ×1.2。
