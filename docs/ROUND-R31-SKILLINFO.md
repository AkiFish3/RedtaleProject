# R31 · 验收 T-0025 + `/redtale skill info` + 派 T-0027

日期：2026-11-17 11:41-11:55 ｜ 队列：S7（派单 + 验收 + 自研）｜ 卡板在制品：T-0026 + T-0027 = 2/3

## 1. 卡板

| 卡 | 状态变化 | 依据 |
|---|---|---|
| T-0025 | `[DONE]` → **`[VERIFIED]`** | 3/3（build / 两条 grep / 测试服功能三点） |
| T-0026 | `[OPEN]`（两轮无人领，本轮不催不拆） | 单卡上限 3/3 未超；T-0025 释放后仍只有 1 张在制品 |
| T-0027 | **新派** | 见 `docs/TASKS.md`（`content list` 补「技能」列 + 汇总 `带技能 K 件`） |

### T-0025 验收证据（逐条）

1. `gradle build --offline` → `BUILD SUCCESSFUL in 831ms`，`compileJava UP-TO-DATE`；jar **393750 B**（与 S8 自报的 11:15 产物一致，即真编过）。
2. `grep -c "checkSkillPrimitives" ValidateCommand.java` = **2**；`grep -c "引用了未注册的原语"` = **2**。
   `javap -p` 复核：`private static void checkSkillPrimitives(RedtaleCore, java.util.List<String>)` 与
   `public static int validate(CommandContext<CommandSourceStack>)` 均在（签名没被改）。
3. 测试服（`E:\server-test`，`console_r31.log`，`Done (2.276s)`）：
   - 基线 `redtale validate` → `… 技能 2 条 · 问题 0 条（外部 weapon_attributes 引用 1 条未校验）` + `（没有引用问题）`
   - 写 `config/redtale_project/skills/t_bad.json5`（`conditions:[{type:"no_such_condition"}]`、`effects:[{type:"dash"}, {type:"console_log",…}]`）
     → `redtale reload` = `3 条技能（26 份配置文件）`
   - `redtale validate` → `… 技能 3 条 · 问题 2 条`
     ```
     redtale_project:t_bad: conditions 引用了未注册的原语 no_such_condition
     redtale_project:t_bad: effects 引用了未注册的原语 dash
     ```
     连跑两次逐字相同（同技能同 type 只报一次成立）
   - 删文件 → `reload`（`2 条技能 / 25 份配置文件`）→ 回到 `技能 2 条 · 问题 0 条` + `（没有引用问题）`

## 2. 自研：`/redtale skill info <id>`

改动只在 `core/command/SkillCommand.java`（+1 个 literal、+2 个静态方法，约 60 行）：

```
[redtale] 技能 redtale_project:gale_slash · 疾风斩 · 冷却 120 tick · 图标 minecraft:iron_sword
  来源 skills/gale_slash.json5
  conditions 1 条：
    ✅ always
  effects 2 条（按序执行）：
    ✅ console_log {"text":"[skill] gale_slash by %player%"}
    ✅ damage {"amount":6.0}
  被内容绑定 1 件：demo_longsword
  ✓ 全部原语已注册（放得出去）
```

为什么做这条：`validate` 只说「有问题 N 条」，不说是哪份配置、哪件武器在用、缺哪个原语是不是致命（缺 condition 原语 vs 缺 effect 原语后果不同）。
`skill info` 把「这条技能放出去会经过哪些原语、缺哪个、谁绑了它」一次打全，注册状态直接问 `core.registries()` —— 与执行期同源（R31-a）。

### 实测（测试服 `console_r31b.log`，`Done (2.233s)`）

| 场景 | 输出要点 |
|---|---|
| `gale_slash`（物理） | 3 条原语全 ✅；`被内容绑定 1 件：demo_longsword` |
| `ember_burst`（法术） | `damage {"amount":8.0,"damage_type":"redtale_project:skill_magical"}` args 摘要正常；无内容绑定 |
| `t_bad2`（坏技能：`dash` + `no_such_condition`） | `✗ 未注册 no_such_condition` / `✗ 未注册 dash {"distance":4}` / `✅ console_log` / 尾部 `✗ 2 条原语未注册（放了只会 WARN；/redtale validate 也报这几条）` |
| `redtale_project:nope` | `没有技能 redtale_project:nope（现有 2 条：…ember_burst · …gale_slash）`，不抛异常 |

回归：`validate` 仍 `技能 2 条 · 问题 0 条`；`content list` 10 件照旧；redtale 零 ERROR（4 条 RecipeManager 既有噪声）。
`build --offline` exit 0（jar **395979 B**）、`selftest` **78 PASS**。测试服起停 2 次，均已停（25567 / 25577 无 LISTEN）。

## 3. 本轮为什么没做 M5-C 原语

`buff` / `damage_aoe` / `debuff` 三条都要在 `Builtins.registerAll` 里加一行，而 `Builtins.java` 是 T-0026 的独占文件。
「派出去的卡独占的文件，在 VERIFIED 之前 S7 不改」是双队列防冲突的全部机制 —— 破坏它就等于让两人同改一个文件。
所以本轮 S7 的三件事（验收 / `skill info` / 派 T-0027）**全部与 `Builtins.java` 正交**（R31-e）。

## 4. 环境

- 构建锁：`.queue/build.lock` 本轮取用，收尾已删。
- 测试服日志：`E:\server-test\console_r31.log`（验收 T-0025）、`console_r31b.log`（`skill info`）。
- 夹具已清理：`skills/t_bad.json5`（删）、`skills/t_bad2.json5`（删）。测试服技能回到 2 条。
