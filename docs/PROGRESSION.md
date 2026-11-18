# 等级 / 经验 / 职业（M7）：schema、曲线、接合点

2026-11-18 · R42 定稿。回答 D1「等级/经验/职业全做」与 D24「专业后置但复用同一经验源」在两台 1.21.1 机器上具体长什么样。
本文件是 **M7 的接口契约**：字段、曲线数学、属性下发 id、存档位置由这里定，S8 只照卡实现，不设计。

## 1. 边界：这一层不做什么

| 系统 | 给什么 | 复用谁的实现 |
|---|---|---|
| **等级/职业**（本条，M7） | 经验累加 → 等级 → 按级下发属性 | 属性走 `ATTRIBUTE-REGISTRY.md` 的 80 条；加法下发照 `MobSpawner` 的 `ADD_VALUE` 口径 |
| 技能树（D25，M7 后半） | 花点数解锁「能不能用」 | 节点 `effects[]` 复用脚本原语；点数 = Σ cost 派生值 |
| 技能（M5） | 主动释放 + 冷却 + 法力 | 已经独立；职业只影响「能不能学」，不改进技能数值 |
| 套装（M3） | 件数触发属性 | 各写各的 modifier id 前缀，互不覆盖 |
| 掉落表（M6） | 怪物 `xp` 字段 | **M7 直接吃这一份**，不新建第二份经验表 |

一句话：**等级 = 一个单调累加器 + 一张曲线表 + 一组按级属性**。加内容只写 JSON5（D17）。

## 2. 双累加器与数据流

```
① 事件   击杀（MobDrops 的 xp）/ 命令 / 脚本原语 grant_exp
② 定量   ExpSource 查表：这次事件给多少（带倍率、按来源限次）
③ 分配   主等级累加器 +（命中的）职业累加器，各自查自己的曲线
④ 升级   跨过阈值 → 记日志 + 发消息 + 重算属性
⑤ 下发   固定 modifier id 写进 `AttributeMap`（见 §6），先移除再加
```

- **两个累加器互不换算**：主等级和每个职业各存一份 `total_exp`，同一份经验按配置**分别**加进两者（D24「双累加器」）。
- **经验只增不减**：洗点/改配置不改 `total_exp`；等级是 `total_exp` 的**派生值**（`ExpCurve.levelFor`），不是独立字段。改曲线后重登即按新曲线重算等级（可能升也可能降），降级不扣属性以外的任何东西。
- **不新建触发器体系**：`②` 的入口只有三个（击杀 / 命令 / 脚本原语），加来源 = 加配置条目，不加事件。

## 3. `levels/main.json5` schema（v1，一条曲线）

```json5
// config/redtale_project/levels/main.json5
{
  id: "redtale_project:main",     // 必填，必须含 `:`；缺了整份跳过 + 一条 WARN
  name: "主等级",                  // 可选，缺省用 id 的 path
  max_level: 100,                 // 可选，缺省 100；< 2 或非整数 → 100 + 一条 WARN
  curve: {
    mode: "geometric",            // "geometric" | "polynomial"，其它 → geometric + 一条 WARN
    base: 100.0,                  // 可选，缺省 100；<= 0 → 100 + 一条 WARN
    growth: 1.12,                 // geometric 专用，可选，缺省 1.12；<= 1.0 → 1.12 + 一条 WARN
    exponent: 1.5,                // polynomial 专用，可选，缺省 1.5；<= 0 → 1.5 + 一条 WARN
  },
  attributes: {                   // 可选，按级下发（§6）；键必须是 80 条表里的键，否则丢该条 + 一条 WARN
    "minecraft:generic.max_health": 0.5,
    "minecraft:generic.attack_damage": 0.1,
  },
}
```

曲线数学（**逐字照抄，别自己发明**）：

| 模式 | 从 L 级升到 L+1 级所需经验 |
|---|---|
| `geometric` | `exp(L) = round(base × growth^(L-1))` |
| `polynomial` | `exp(L) = round(base × L^exponent)` |

- `L ∈ [1, max_level-1]`；`exp(max_level) = 0`（到顶不再涨）。
- 单级值硬顶 **`1_000_000_000`**（D16 的 `1e9` 异常保护），超出按硬顶算并 `LOG.info` 记一次。
- 累计值用 `long` 累加，同样硬顶 `1e9 × max_level` 以内（`max_level ≤ 1000` 时不会溢出 `long`）。

## 4. `jobs/*.json5` schema（v1，一个文件一个职业）

```json5
// config/redtale_project/jobs/miner.json5
{
  id: "redtale_project:miner",    // 必填，必须含 `:`
  name: "矿工",
  max_level: 50,                  // 同 main 的夹取口径
  curve: { mode: "polynomial", base: 80.0, exponent: 1.4 },
  attributes: {
    "redtale_project:mining_speed": 0.02,
  },
  exp_sources: ["kill"],          // 可选，缺省 ["kill"]；只认 §5 表里的名字，未知项丢弃 + 一条 WARN
}
```

- **多职业并存**：一个玩家可以同时持有全部已定义职业，各存各自的 `total_exp`。不做「转职 / 只能选一个」。
- 职业是**纯累加器 + 属性**，不带技能、不带配方（那些属于技能树/原版配方）。

## 5. 经验来源（`ExpSource`，v1 三条）

| 名字 | 触发点 | 取值 | 备注 |
|---|---|---|---|
| `kill` | `MobDrops` 发放掉落时的 `xp` | 表内 `xp` × `mob_exp_multiplier` | 已存在（R41-c 走 `ExperienceOrb.award`）；M7 在**同一处**再加一份 `total_exp` |
| `command` | `/redtale exp add <amount>` | 参数 | 运营入口，也是验收入口 |
| `primitive` | 脚本原语 `grant_exp` ✅ R46 | `amount` + `target`（`caster`\|`target`，只对玩家生效） | 技能/附魔可以给经验；**原语预算 24 → 25**（R46 记账，`SKILLS.md` §4 已同步）。观测面 `/redtale exp primitive <amount>`（FakePlayer，控制台可跑） |

- 倍率只有一个：`config/redtale_project/progression.json5` 的 `mob_exp_multiplier`（缺省 `1.0`，`< 0` → 0 + WARN）。
- **不做**「按方块 / 按合成 / 按在线时长」——那些要新的方块/配方事件面，等专业真正接上再说（D24 后置）。
- 一次击杀的 `xp` **同时**进主等级与所有 `exp_sources` 含 `kill` 的职业；这是「双累加器」的唯一含义。

## 6. 属性下发（固定 id，先移除再加）

- modifier id：主等级 `level/<属性 path>`（例 `level/generic.max_health`），职业 `job/<职业 path>/<属性 path>`。
- 值 = `per_level × (level - 1)`，`ADD_VALUE`（自建属性基值 0，`ADD_MULTIPLIED_TOTAL` 恒 0 —— R5 的教训）。
- **每次等级变化：先按 id 移除，再按新值加**；`level < 2` 时只移除不加。
- 到顶（`level == max_level`）不再增长，但 modifier 保留（值是顶级的）。
- 卸载/死亡/换维度不重算（modifier 在 `AttributeMap` 上，随玩家实体存活）；只有登入、`/redtale reload`、等级变化三个点重算。

## 7. 存档与热重载语义

- **存档走 `SavedData`**（`world/data/redtale_project_progress.dat`），不自己写 JSON 文件：与 world 备份/回档天然一致，也不需要处理并发写。
- 结构：`Map<UUID, PlayerProgress{ long mainExp, Map<String, Long> jobExp }>`。等级不存 —— 派生（§2）。
- 离线玩家不落盘到 `playerdata`（D33 的同一条纪律）；`SavedData` 在 `save-all` / 自动保存时随 world 走。
- `/redtale reload`：曲线/上限/属性表**立即生效**，所有在线玩家按新曲线重算等级并重下发属性；`total_exp` 不动。

## 8. 三条红线

1. **不写伤害绝对值**：等级只发属性，伤害结算仍走 `Settle` + `DamageLayer`（D12）。
2. **不覆盖别人的 modifier**：id 前缀 `level/` `job/`，与 `content/` `gem/` `set/` 不重叠。
3. **不改 `AttributeSpecs`**：80 条表冻结；职业想要的属性必须已在表内，否则该条丢弃 + WARN。

## 9. 交付顺序（层与层之间必须按顺序）

| 层 | 产物 | 谁做 | 为什么这个顺序 |
|---|---|---|---|
| L1 | `core/progression/ExpCurve`（纯函数，零 MC import） | S8（T-0038） | 曲线是唯一需要精确数学的部分，先独立可测 |
| L2 | `core/progression/ProgressSpec`（解析，T-0039 ✅）+ `ProgressRegistry` 快照（80 条表校验 + 键归一 + 跨级去重，R44 ✅）+ `ProgressRules`（`progression.json5`，R44 ✅） | S8 解析 / S7 快照 | 契约已由 §3/§4 写死；校验不能在解析层（R44-a） |
| L3 | `core/progression/ProgressStore`（`SavedData`，读/写/重登） | S7 | 涉及 world 生命周期 |
| L4 | `core/progression/ProgressService`（② ③ ④ ⑤ 全链 + 属性下发） | S7 | 跨 3 个文件 + 判定取舍 |
| L5 | `/redtale level [info|add|set|top]` + 脚本原语 `grant_exp` | S7 | 命令面在服务落地之后（R36-a 同一条：先快照后命令） |

**K1~K6（M7 出口条件）**

| # | 断言 | 怎么验 |
|---|---|---|
| K1 | 只写一份 `levels/main.json5`（零编译）→ reload 后曲线生效 | 改 `base` 后 `/redtale exp add` 的分母变 |
| K2 | 升级跨阈值 → 属性面板同步变化，且只有一条 `level/` modifier | `/attribute get` + `/redtale attr` |
| K3 | 一次击杀同时进主等级与命中职业，两份 `total_exp` 各自增长 | `/redtale level info` 两行 |
| K4 | 改曲线后 reload → 等级按新曲线重算，`total_exp` 不变 | 降 `growth` 后等级下降、exp 数字不变 |
| K5 | 到顶后不再涨，modifier 不消失 | 加满经验 → `level == max_level` |
| K6 | 坏配置只丢该条 + 一条 WARN，服务端照跑 | 四份坏 `levels/jobs` 夹具 |

## 10. 明确不做

- 不做「经验惩罚」（死亡扣经验）：D16 的「不设平衡上限」同源，扣经验是投放层的事，需要时加配置项。
- 不做「等级上限随装备提升」：那是两条累加器的耦合，等技能树落地后再说。
- 不做「职业专属配方」：D27 已定不碰工作台。
- 不做 GUI：面板属 owo（D30），M7 只给命令与数据；GUI 与技能树同批做。
