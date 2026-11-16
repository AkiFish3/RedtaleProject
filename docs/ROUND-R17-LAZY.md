# R17 · 2026-11-16 12:31-12:45 · 验收 T-0011 + D33 惰性兜底 ⑤（结算路径）

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| **T-0011**（`tools/gen_item_lang.py`，14 基底物品 lang） | 验收 | **VERIFIED（3/3）** |
| **T-0013**（`SocketSpec` 孔位 + 宝石解析器，M3 第一层） | 派单 | 在制品 T-0012 + T-0013 = 2/3 |

T-0011 验收证据：

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `python tools/gen_item_lang.py` | exit 0 | **0**，输出「解析到 14 个基底 id」，未手工抄表（正则读 `ItemBase.java`） |
| 2 | `grep -c '"item.redtale_project\.'` | 14 / 14 | zh **14** / en **14**；`attribute.*` 仍 **66 / 66**；`death.attack.*` 1 条仍在（各 81 键） |
| 2b | jar 内实测 | 14 / 66 | `unzip -p … assets/redtale_project/lang/zh_cn.json` → **14 / 66** |
| 3 | 连跑两次 `md5sum` | 不变 | zh_cn `8f869d988d1576e3c3a4bab1edaa5870`、en_us `41df1b72c8905121fe93b54cad958871`，两次逐字相同 |

附带确认：UTF-8 **无 BOM**（首字节 `7b 0d 0a` = `{` + CRLF，与原文件同形）；`gradle build --offline` exit 0（jar 254478 B → 本轮改动后 255210 B）。

S8 提的两处偏差处理：

1. `gen_attr_lang.py` 是**整体覆写**，单跑一次会抹掉这 14 个 item 键 → 接受为**执行顺序约束**（先 attr 后 item），本轮不改脚本。
2. id 数 ≠ 14 只在 stderr 警告、不中止 → 接受（真加第 15 基底时不该硬失败）。

## 2. D33 惰性兜底 ⑤：结算路径上的 `ensure`

D33 有四个主动刷新点（登录 / 开容器 / 区块加载 / reload 分帧），全会漏：本 tick 刚由配方产出的物品、排队还没轮到的容器。结算路径是这些物品**唯一一定会经过**的地方，所以在 L2 乘区里补一次。

落点 `core/damage/DamageLayer#ensureAttackerMainHand`（`DamageLayer` 是 S7 独占文件）：

```java
// onIncomingDamage 里，白名单判定通过之后、读属性求和之前
ensureAttackerMainHand(source.getEntity());
```

四条实现口径（写进代码注释，避免以后被「顺手优化」掉）：

| # | 口径 | 理由 |
|---|---|---|
| ⑤-a | 只处理**攻击者主手** | L2 只读攻击者属性，别的槽位读了也没用；遍历背包会把热路径变成背包扫描 |
| ⑤-b | **不放 tooltip** | `ItemTooltipEvent` 客户端也触发，在客户端改物品数据 = 让客户端凭空造属性（R10-e） |
| ⑤-c | `ensure` 抛 `RuntimeException` → **按旧版结算**并 WARN | 结算路径上崩一次 = 一次打不出伤害，宁可让这件物品旧一版 |
| ⑤-d | `UPGRADED` 且是 `ServerPlayer` → `inventoryMenu.broadcastChanges()` 一次 | `ensure` 改了同一个 `ItemStack` 实例（内存里已新），客户端仍是旧组件，必须推 |

## 3. 测试服实测（1 次启动，`Done (2.563s)`，已停服）

道具手法（**下次直接复用**，不需要写探针代码）：原版 `/damage <目标> <值> redtale_project:verification_strike by <攻击者>` 会把「攻击者」塞进 `DamageSource`，于是 `DamageLayer` 正常跑；用 `/item replace entity … weapon.mainhand with …` 手写一个过期件当攻击者主手。

```
summon husk {NoAI,Invulnerable,PersistenceRequired}      # 攻击者，别让它烧死/跑掉
summon cow  {NoAI,PersistenceRequired}                    # 目标
item replace entity @e[tag=rt_atk] weapon.mainhand with redtale_project:sword[
  redtale_project:content={content_id:"t_upg_sword",content_rev:0,schema:1,
    rolled_stats:{"minecraft:generic.attack_damage":5.0d},
    rolled_keys:["minecraft:generic.attack_damage"]}]
damage @e[tag=rt_target] 5 redtale_project:verification_strike by @e[tag=rt_atk]
```

**A 组（第一次打，rev 落后 → 应升级）**：

```
前： {"redtale_project:content": {rolled_stats:{"minecraft:generic.attack_damage":5.0d},
      rolled_keys:["minecraft:generic.attack_damage"], content_id:"t_upg_sword"}}
后： {"minecraft:attribute_modifiers": {modifiers:[{amount:5.0d,
        id:"redtale_project:content/generic.attack_damage", slot:"mainhand", ...}]},
      "minecraft:custom_name": '"更新测试剑"',
      "minecraft:rarity":"epic",
      "redtale_project:content": {…, content_id:"t_upg_sword", content_rev: 3},
      "minecraft:max_damage": 500}
```

对上 D33 的三分支：`content_rev 0 → 3`（定义 rev 3）、`rolled_stats` 的 `attack_damage` **5.0 保留**（在 `rolled_keys` 里）、定义型字段（名称 / 稀有度 / 耐久上限 / 属性面板）**全部重建**。

**B 组（同一件再打一次，rev 相等 → 快路径）**：components 输出**逐字不变**，无第二次写回。

**C 组（负路径，第一次试跑踩到的）**：先拿 `demo_longsword` 做过期件 → 组件一个都没变。原因不是缺陷：那份配置**没写 `rev`**（缺省 0），而我手写的 `content_rev` 也是 0 → 走的是 `UNCHANGED` 快路径。**记一条**：拿 `demo_*` 当「过期件」永远测不出升级，得用 `t_upg_sword`（rev 3）这类带 rev 的定义。

日志：redtale 零 ERROR / 零 WARN；`ERROR` 计数仍为 **4**（R7 起的 RecipeManager 既有噪声）。`gradle selftest --offline` **70 PASS**（未加新用例——惰性兜底是 MC 事件路径，离线不可测，实证在测试服）。

## 4. M3 开工：`ITEM-MODEL.md` §5（孔位与套装）定稿

派解析器之前先定 schema（§13.19 的下一步第 2 条），本轮补完 §5：

- **三层身份**：孔位（物品定义 `sockets`）/ 宝石（另一件内容的 `gem` 段）/ 镶嵌结果（`ContentData.sockets[]` + `gemStats{}`）/ 套装（独立 `sets/*.json5` + 物品 `set_id`）。
- **关键取舍**：宝石数值**不并进 `rolled_stats`**，单独 `gemStats`。同一属性若被词条与宝石各加过，并在一个键里**拆宝石就扣不准**（会把词条那部分一起扣）。
- **孔类型词汇开放**（不做枚举校验），匹配只有一条规则：「宝石 `type` 为空 / `any` / 等于孔 `type`」即可镶。
- **套装加成是派生值**：按「已穿戴命中件数」实时算，写固定 modifier id `redtale_project:set/<setId>/<count>`，先移除再加（D18 同一条纪律）。
- 落地顺序表（§5.5）：解析器归 S8，`ContentData` 结构变更 / 镶嵌结算 / tooltip 归 S7。

配套接口升可见性（`AffixPools`，**零行为变更**）：`parseStats(JsonObject, String, List<String>)` 与 `normalizeKey(String)` 由 private 升 public——宝石与套装的 `stats` 必须走**同一条**解析与归一规则，否则「词条与宝石写同一个属性」会往同一个 modifier id 加两条（R11-b 的教训）。存在形式是**包装方法**，原私有实现一行未动。

## 5. 下一步（R18）

1. **验收 T-0012**（`/redtale content check`，rev 漏加检测）与 **T-0013**（`SocketSpec`）。
   T-0012 的功能验收要三次运行（建基线 → 改文件不加 rev → 加 rev），归 S7。
2. **M3 第 3 步**：`ContentData` 加 `sockets[]` / `gemStats{}`，`CURRENT_SCHEMA` 1 → 2，`ItemUpgrader` 加迁移分支。
3. **挂账第 16 轮（需一次带客户端的窗口）**：`rev` 落后物品 → `/redtale item upgrade` 计数 → 客户端 tooltip 立刻变；真人右键磨刀石 / 消耗品；`item scan` 真人背包分布；tooltip 真人样子；真人 pvp；BetterCombat 双持 ×1.2；本次惰性兜底的 `broadcastChanges` 在真人客户端是否立刻刷新。
