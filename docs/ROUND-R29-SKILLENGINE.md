# R29 · M5-A 引擎（`SkillEngine` + `/redtale skill`）

2026-11-17 09:03–09:35 · 里程碑 M5-A（第 2 块，M5-A 主体完成）

## 1. 本轮卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0022 `SkillCooldowns` | 验收 | **`[VERIFIED]`** 3/3 + S7 独立探针 `build/probe/S7CdProbe.java` **54/54**（不复用 S8 的 `CooldownProbe`） |
| T-0023 `/redtale enchant obtain` | 验收 | **`[VERIFIED]`** 3/3 + S7 独立探针 `build/probe/s7obtain/…/S7ObtainProbe.java` **19/19** |
| T-0024 `content list` 行字段 | — | 仍 `[OPEN]`（07:50 派，本轮未交付） |
| **T-0025**（新） | 派单 | `/redtale validate` 补第 6 条「技能引用的原语是否存在」（单文件 `ValidateCommand.java`，只读） |

派单后**在制品 = T-0024 + T-0025 = 2/3**（T-0024 OPEN 已计）。

### 1.1 T-0022 验收证据

- `gradle build --offline` EXIT=0（jar 371570 B，本次 3 up-to-date）
- `grep -c "net.minecraft\|net.neoforged" SkillCooldowns.java` = **0**（纯类）；jar 内 `core/skill/SkillCooldowns.class` = 1
- 独立探针 54 条：卡面 16 条全覆 + 额外断言 `(100,+Inf)=100`、`(3,0.7)=1`（四舍五入而非向下）、
  1 tick 打折仍记账、到期自动删键（`size` 归零）、`prune` 计数、id 大小写敏感 / 不 trim、空值四方法不抛

### 1.2 T-0023 验收证据

- 三条验收全过：build EXIT=0 / `grep -c EnchantObtainCommand.node()`=1 且 jar 内 class=1 / 独立探针 19/19
- 测试服三分支实测（RCON）：
  - 常态：`已生成 3 条附魔` + 三行 `已收录 2/3  缺：redtale_project:frost_edge`（`frost_edge` 是 R24 演练落盘残留）+ 末行 `有 1 条附魔拿不到`
  - 移走 `tradeable.json`：该行变 `未生成`（**不是** `0/3`），末行并集 N=3
  - 移走 `frost_edge.json`：三行 `2/2  ✓`，末行 `三条全齐 ✓`
  - `enchant list` 与 `enchant obtain` 在同一个 `enchant` 节点下共存
  - 两次夹具（tag / 附魔 json）均已还原

## 2. 自研：`SkillEngine`

新类 `core/skill/SkillEngine.java`（约 130 行），落 SKILLS §2 的五步链路的 ③④⑤：

```
cast(player, skillId)
  → NO_PLAYER / NO_SKILL（快照里没有这条）
  → ③ conditions 逐条 test：未知 type 跳过（与 ScriptEngine#passConditions 同口径）、
     判 false → CONDITION（带回失败的 type），一条 effect 都不跑、冷却不记账
  → ④ SkillCooldowns.tryStart(UUID, id, cd, 0.0, now)：不过 → COOLDOWN + 剩余 tick
  → ⑤ core.engine().runCalls(effects, ctx, "技能 <id>")
```

配套：

| 文件 | 改动 |
|---|---|
| `core/script/ScriptEngine.java` | `runCalls(calls, ctx)` 保留，新增 `runCalls(calls, ctx, String owner)` 重载（旧方法委托，标签 "物品脚本"）——否则技能报错会被写成「物品脚本的 effect」 |
| `core/RedtaleCore.java` | 新增 `private final SkillEngine skillEngine`（**跨 reload 存活**，reload 清冷却 = 免费刷技能）+ `skillEngine()` 访问器 |
| `core/command/SkillCommand.java`（新建） | `/redtale skill list` / `cast <id>` / `probe <id>` / `cd <id>` |
| `core/command/RedtaleCommand.java` | 末尾 `.then(SkillCommand.node())`（+2 行，T-0023 已 VERIFIED 释放该文件） |

`probe` 的设计（无客户端时的唯一数值入口）：FakePlayer 站在木桩牛（1000 血 / `NoAI`）前 1 格、自己算 yaw/pitch 看向它，
**A 组零属性 → B 组 `physical_damage +0.25`**（两次之间 `cooldowns().clear(uuid)`，否则第二次直接冷却中），
打印两组的掉血与比值。比值 1.25 = 物理技能过 L2 乘区；1.00 = 法术技能不吃物理（分轴正常）；其它 = 有东西在 `setAmount`。

## 3. 实测（测试服 `E:\server-test`，起停 3 次，均已停）

| 判据 | 证据 |
|---|---|
| K1 配置零编译 | 只改 `config/redtale_project/skills/*.json5`（换成仓库默认中文名副本）+ reload → `skill list` 立即显示 `灼焰爆` / `疾风斩`，jar 未换 |
| K2 冷却 | 连放 `gale_slash`：第一次 `跑了 2 条 effect`、牛掉血 6；第二次 `技能 redtale_project:gale_slash 冷却中：43 tick` + 掉血 **0**；第三次 `37 tick`（递减） |
| K3 条件 | `ember_burst`（`health_below 0.9`）在满血 FakePlayer 上 → `条件不满足（health_below），一条 effect 都没跑`，掉血 0，日志**无** `[skill] ember_burst` |
| K4 原语序列 | `gale_slash` 的 `console_log` + `damage` 顺序全跑：日志 `[script] [skill] gale_slash by [Minecraft]` 且有 6 点掉血 |
| K5 走管线 | `probe gale_slash`：A 组 **6** → B 组（+25% 物理）**7.5**，比值 **1.25 ✓**；`probe t_magical`（法术 8.0）：A 组 8 → B 组 8，比值 **1.00**（分轴正常，R26） |
| K6 坏配置不崩 | 4 份坏技能（缺 id / id 无冒号 / effects 空 / `cooldown_ticks:"x"`）→ reload 回显 `4 条告警`，四条文案逐字对（`id 缺失或不是字符串` / `不含 ':'` / `effects 没有有效项` / `cooldown_ticks 不是数字，已用 0`），服务端照跑，`t_bad_cd` 以 cd=0 生效 |
| 零冷却 | `effectiveTicks(0,x)=0` 且 `start(0)` 不留记录 → `tryStart` 恒 true（由 S7CdProbe 覆盖；`cast` 要真人 UUID，控制台跑不了） |

其它：`build --offline` EXIT=0（jar **385865 B**）、`selftest` **78 PASS**、redtale **零 ERROR**（4 条 RecipeManager 既有噪声）。
构建锁已删；`25567/25577` 无 LISTEN。

## 4. 口径（写进 PLAN §13.32）

- **R29-a** 引擎只做「放一次」，条件不过**不记账**（否则「条件不满足」也吃冷却）。
- **R29-b** 冷却表 `SkillEngine` 持有、**跨 reload 存活**；`reload` 不清冷却。
- **R29-c** 技能的目标来自**视线 raycast**（`AIM_RANGE = 8` 格，只认活体、排除自己），打不中就是 null，
  由 `damage` 原语自己跳过 + WARN —— 引擎**不替原语找目标**。
- **R29-d** `runCalls` 加 `owner` 标签重载，日志区分「物品脚本」与「技能 <id>」；行为零变更。
- **R29-e** `probe` 是**验收工具**，会 `cooldowns().clear()`；不写进玩法路径（M5-B 右键不走它）。

## 5. 下轮

1. 验 **T-0024**（`content list` 行字段，S7 跑测试服看七个字段）与 **T-0025**（`validate` 第 6 条）。
2. **M5-B**（跨 3 文件，S7）：`ItemContent` 15→16 加 `abilities`、`ItemFactory` 写进 `ContentData`、
   `ContentItem#use` 在没有 `use` 段时施放绑定技能（K7）。
3. `cooldown_reduction` 属性接入（SKILLS §6，M5-B 一并做）。
4. V4 的「附魔台真刷出」仍需客户端窗口，继续挂账。
