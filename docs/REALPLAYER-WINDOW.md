# 真人验收窗口 · 一次性清单（R62 汇总）

> 为什么有这份文件：M4 的 V4/V5/V6、M6 的刷怪替换与 `per_tick_cap` 行为、M7 的玩家路径、
> D20 的 tooltip —— 全部只能由**真人客户端**触发（无客户端时 `NaturalSpawner` 不跑、GUI 面不存在、
> 命令源是控制台没有 UUID）。这些项散在 `PLAN.md` §13 的十几轮台账里，开一次服容易漏。
> 本文按「一次上线收完」排序，每条给命令 + 期望输出 + 判据。

**前置**：测试服 `E:\server-test`（25567 / RCON 25577），jar 为当轮 `build/libs` 产物，
`/redtale reload` 回 `40 份` 且 `validate` 回 `问题 0 条`。**收完停服**（正式服常驻 12G）。

图例：`[ ]` 未验 · 判据写「逐字」的必须按字面对，写「差值」的按数值对。

---

## A 组 · 显示与面板（10 分钟内，全在客户端）

| # | 动作 | 期望 | 判据来源 |
|---|---|---|---|
| A1 | `[ ]` `/redtale give sword_cinder 1` → 手持看 tooltip | 首行中文名 + 数值行「**单次伤害 X · 攻速 Y tick（Z 次/秒）**」，**没有 DPS 行** | D20 |
| A2 | `[ ]` `/redtale attr redtale_project:physical_damage`（玩家执行） | 按 modifier id 分组打印，能拆出 `tree/…` / `level/…` / `item.…` / `effect.haste`；合计 = 面板值 | D18 |
| A3 | `[ ]` `/redtale attr minecraft:generic.attack_speed` 同上 | 急迫生效时出现 `minecraft:effect.haste` 一条；我们自己的 `attack_speed/…` **不与它重复计** | D18/D19 |
| A4 | `[ ]` 喝咖啡（Farmer's Respite）/ 喝茶，再看 A3 | 只多别人的 modifier，我们**没有**再加一份（`effect_attack_speed` 默认全 0） | D18 |
| A5 | `[ ]` `/redtale level info redtale_project:main`（玩家执行） | 末行多一段「当前」：`Lv7 · 本级 21/44 · 累计 1234`（控制台执行时**没有**这段） | CONTRACTS §4.30 |
| A6 | `[ ]` `/redtale skill mana`（玩家执行） | 法力面板出当前/上限/回复，数值与本轮 `ManaPool` 配置一致 | §4.21 |
| A7 | `[ ]` `/redtale mob spawn test_husk <x> <y> <z>` | 客户端看得见**体积按配置缩放**、头上名牌正确；不是默认大小 | D4 |
| A8 | `[ ]` 把 `display.json5` 的 `show_dps` 与 `show_attribute_breakdown` 都改 `true` → `/redtale reload` → 再看 A1 那把剑 | 多出两行：`DPS ≈ N（假想目标 minecraft:zombie…）` 与 `属性 N 项：…`；DPS 的 N = 单次伤害 × 次/秒（手算对得上） | Q-023 / D20 |

## B 组 · 刷怪（M6 最后两项）

| # | 动作 | 期望 | 判据 |
|---|---|---|---|
| B1 | `[ ]` `/redtale spawn replace` 确认 `开 · 每 tick 上限 N` | 状态行与 `spawn.json5` 同源（改文件 + reload 后一致） | R52 |
| B2 | `[ ]` 夜里在未照亮区域站 3~5 分钟 | 原版怪被替换成自建怪；日志出现**首次命中**一行，之后按采样计数 | D26 |
| B3 | `[ ]` `/redtale spawn replace cap 1` → 找一片怪多的刷怪笼/暗区 | 同一 tick 只补 1 只（日志计数），不是一次补一批 | R55-d 遗留 |
| B4 | `[ ]` 还原 `cap 8` | 状态行回 8，磁盘 `per_tick_cap: 8` | R55 |

## C 组 · 进度与技能树（M7 出口）

| # | 动作 | 期望 | 判据 |
|---|---|---|---|
| C1 | `[ ]` 记下 `/redtale level info` 当前值 → **持武器杀 10 只自建怪** → 再看 | 经验涨；涨的量 = 表内 `xp` × `mob_exp_multiplier`，**且原版经验球照掉**（两层相加，不互相顶掉） | R45 挂账 |
| C2 | `[ ]` 升级瞬间看角色面板 | 最大生命等按 `levels/main.json5` 的按级属性变化；`/redtale attr` 里出现 `level/…` 来源 | R44 挂账 |
| C3 | `[ ]` `/redtale tree state warrior_vanguard` | 玩家段列出已点节点、可用点数（= min(上限, 每级点数×等级)） | §4.33/§4.34 |
| C4 | `[ ]` `/redtale tree take warrior_vanguard <root>` | 成功；`/redtale attr` 立即出现 `tree/warrior_vanguard/…` 的 modifier，数值 = 配置值 | R53 |
| C5 | `[ ]` `/redtale tree skills warrior_vanguard` | 玩家段 `声明 N 条 · 已解锁 M 条` + 每条 `· 已解锁/未解锁` | R58 |
| C6 | `[ ]` 在**未解锁**时 `/redtale skill cast redtale_project:quake_slam` | 被拦，文案说明「技能树未解锁」；解锁后再放则成功并进冷却 | R57 |
| C7 | `[ ]` `/redtale tree untake warrior_vanguard <root>` → `/redtale tree respec warrior_vanguard` | `untake` 有依赖者即拒；`respec` 一次退回全部点数，属性前缀残留 0 | R53 |
| C8 | `[ ]` `/redtale skill cast redtale_project:gale_slash` 连按两次 | 第一次放出、第二次报冷却剩余 tick；法力不足时报「法力不足」且**不记冷却** | §4.16 |

## D 组 · 附魔 GUI 面（M4 唯一剩余 3 项）

| # | 动作 | 期望 | 判据 |
|---|---|---|---|
| D1 | `[ ]` 附魔台：30 级 + 剑，刷 20 次 | 至少 1 次出 `redtale_project:*` 附魔；中文名正常 | V4 |
| D2 | `[ ]` 铁砧：两本 I 级合成 | 得 II 级，等级扣对 | V5 |
| D3 | `[ ]` 砂轮：附魔剑放进去 | 附魔消失、返经验 | V6 |
| D4 | `[ ]` 已附锋利 V 的剑再附超级锋利 | 互斥生效（拒绝或替换，**不并存**） | V7 已绿，回归 |

## E 组 · 战斗细节（能顺手验就验，不阻塞验收）

| # | 动作 | 期望 |
|---|---|---|
| E1 | `[ ]` 两个玩家对打 | `conditional_attributes.pvp` 生效，`/redtale damage` trace 看得出多乘了一档 |
| E2 | `[ ]` 打自建亡灵怪 | `pve + undead` 两键同时堆叠（不是二选一） |
| E3 | `[ ]` 被怪 `stun` | 玩家真的动不了（不是只有怪能被打断） |
| E4 | `[ ]` 站远处看自建怪 | `tick` 触发路径的 AI 技能会放（不是只在 `post_attack` 里放） |
| E5 | `[ ]` 用带 `dash` 的技能 | 客户端有位移（服务端位移已验，客户端表现未验） |
| E6 | `[ ]` 击杀带 `drops` 的怪 | 表内掉落与原版装备槽掉落**并存**，不互相吞 |

---

## F 组 · 专业经验（D24 · P1~P5，需真人，10 分钟）

> 前置：`/redtale exp profession` 记下 `mining` / `smelting` 的累计值，F1~F5 每条只比**差值**。
> 之所以要真人：三个事件（`BlockEvent.BreakEvent` / `ItemCraftedEvent` / `ItemSmeltedEvent`）只有真玩家触发，
> 控制台的 FakePlayer 不进这三个钩子（R66 探针是绕过事件直接调 `ProfessionService.award` 的）。

| # | 动作 | 期望 | 判据 |
|---|---|---|---|
| F1 | `[ ]` 挖 1 个**石头** | `mining` +3（精确 id `minecraft:stone` 胜过后面的 tag） | P1 |
| F2 | `[ ]` 挖 1 个**原木** | `mining` +2（只命中 `#minecraft:logs`）；同一次命中两条 tag 时取**大**不加和 | P2 |
| F3 | `[ ]` 挖 1 个**泥土/草方块**（没配过的方块） | `mining` +1（`default` 兜底）；日志**零告警** | P3 |
| F4 | `[ ]` 原木 → 4 木板，合 1 次；再把 `craft.default` 临时改 `1` + `/redtale reload` 后重做一次 | 第一次 `smelting` +0（`craft.default: 0`）；改后 +1 —— **不是 +4**（不乘产出件数） | P4 |
| F5 | `[ ]` 熔 5 个铁矿（产出 `minecraft:iron_ingot`） | `smelting` +15（5 次 × 3，每件一次） | P5 |
| F6 | `[ ]` `/redtale profession list` / `info mining`（T-0063 落地后） | 定义面三行文案逐字；玩家上下文 `info` 多一行 `玩家 <名字>：专业 …` | L5 · **控制台四段已验（R68），只差玩家行** |

## 记录方式

每验完一组，在 `PLAN.md` §13 当轮行的「挂账」里划掉对应条目并写实测值；
不过的项当轮记 `docs/ROUND-R<n>-*.md`，不要只写「有 bug」——写清**期望值 / 实测值 / 命令原文**。

## 当前挂账总账（截至 R67）

- 未验 **A1~A7 / B1~B4 / C1~C8 / D1~D4 / E1~E6 / F1~F6**，其中 **D1~D3 = M4 出口**、**B2/B3 = M6 出口**、**C1~C7 + F1~F5 = M7 出口**。
- 已绿但依赖真人回归的：V7（互斥）、V8（数值对账）、V9（脚本触发）、V10（脚本缺失不崩）、V11（加第 N 个附魔只写 JSON5）。
