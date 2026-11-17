# R40 · 2026-11-18 · 验收 T-0034/T-0035 + 怪物技能释放（M6 第三步第 3 小步）

## 0. 一句话

配置里的 `skills:` 现在真的会放出来：`MobSkillCaster` 每 10 tick 找目标掷骰 → `SkillEngine.castMob` 走与玩家**同一条**执行链；
冷却、条件、原语全部复用，没有第二份真相。顺带修掉一个会让「冷却」验收**恒假通过**的坑。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0034 `tools/gen_entity_lang.py` | 验收 | **VERIFIED** 3/3 |
| T-0035 `mobs/DropSpec` | 验收 | **VERIFIED** 3/3（S7 独立探针 52/52） |
| T-0036 `mobs/DropRoll` | 派单 | OPEN（在制品 1/3） |

## 2. T-0034 验收证据

```
$ python tools/gen_entity_lang.py
解析到 8 个原型 id：melee, ranged, caster, summoner, charger, flyer, elite, ambient
  写入 src\main\resources\assets\redtale_project\lang\zh_cn.json（93 键，entity 键 8 → 8）
  写入 src\main\resources\assets\redtale_project\lang\en_us.json（93 键，entity 键 8 → 8）
EXIT=0

zh_cn 93 8 66 14     en_us 93 8 66 14          # 总键 / entity / attribute / item
md5 两次运行：zh 73193e14… / en 2afe2065…     # 逐字相同（幂等）
wc -l = 117（≤130）；py_compile EXIT=0
```

采纳 S8 的排序偏差：`dict(sorted(...))` 会让 `enchantment.*` 排进字母序中间，键集不变即合规 → **排序口径统一为整体键排序**，不再要求 diff 只增不删。

## 3. T-0035 验收证据

```
build --offline EXIT=0；unzip -l | grep mobs/DropSpec.class = 1
wc -l = 118（≤190）；grep -c net.minecraft = 1；grep -c Registr = 0
S7 独立探针 build/probe/S7DropProbe.java ： PASS 52 / FAIL 0 / TOTAL 52
```

S7 探针自编 A~J 十组（不复用 S8 的 `DropSpecProbe`），除卡面行为 3~11 外补了：
`warnings=null` 不 NPE、子目录 `drops/归档/q.json5` 合法、重复 id 的 `source` 是首个文件、
`chance=NaN`（`new JsonPrimitive(Double.NaN)`）→ 1.0 + 一条告警、`Map` 与 `drops` **双**不可变、保序非字典序。

两条探针 bug 是**断言写错**不是代码错（已改断言）：子目录那组一开始按「应被忽略」写、`drops` 合法项数少算一条（`min:-4` 夹成 0 仍合法）。

## 4. M6 第三步第 3 小步：怪物技能释放

### 4.1 结构

| 文件 | 内容 |
|---|---|
| `mobs/MobSkillCaster`（新，163 行） | `tick` 10 tick 节拍 + 只认 `redtale_mob/<id>` tag + 目标取 `mob.getTarget()`；`tryCast` 掷骰 / `forceCast` 忽略概率；**零成员状态** |
| `core/skill/SkillEngine` +67 行 | `castMob(caster,target,skillId,cooldownTicks)`：条件 → 冷却只查 → 记冷却 → effects；**不算法力**、冷却可覆盖 |
| `core/hook/GameHooks` +2 行 | `ServerTickEvent.Post` 接 `MobSkillCaster.tick` |
| `core/command/MobSpawnCommand` | `/redtale mob cast <id> [roll\|<skill>]` + 16 格复用 + 失败带状态 |

### 4.2 实测（测试服起停 5 次，`Done (2.042 / 2.057 / 2.188 / 2.117s)`）

| 组 | 命令 | 输出 | 结论 |
|---|---|---|---|
| A | `mob cast ember_guard`（新生） | `施放 redtale_project:gale_slash · CAST · 2 条 effect · 目标血量 4.0/10.0` | 白值 6 未被原语自乘（10→4） |
| B | 立刻再放 | `未施放（全部失败：redtale_project:gale_slash(COOLDOWN 106t)）` | 冷却真的生效（复用同一只怪） |
| C | `mob cast ember_guard "redtale_project:gale_slash"` | `未施放（COOLDOWN 112t）` | EXPLICIT 路径同冷却表 |
| D | `mob cast t_chance0 roll` | `未施放（…：redtale_project:gale_slash(chance 0)）` | chance 过滤 |
| E | `mob cast t_chance1 roll` ×2 | 首次 `CAST`（10→4），立刻再放 `(COOLDOWN 92t)` | 掷骰 + 门槛两段都通 |
| F | `mob cast t_noskill` | `t_noskill 没配 skills，放不了技能` | 负向 |
| G | `mob cast no_such_mob` | `没有怪物 no_such_mob（现有 5 只：…）` | 负向 |
| H | 删三份夹具 + `reload` | `2 只怪物（28 份配置文件）`，`mob check 引用完整 ✓`，`validate 问题 0 条` | 回落干净 |

redtale 零 ERROR / 零 WARN；4 条 `RecipeManager` 失败是 R7 起的既有噪声。

### 4.3 五条事实（写进 PLAN §13.43）

1. **冷却键 `(UUID, 技能 id)`** → 验证「冷却是真的」必须复用同一只施法者。第一版命令每次现生成 → 新 UUID → 冷却表干净 → 连放两次都 CAST（靶牛 10→4→0），**看起来像「冷却没实现」，其实是账本换了**。
2. `StringArgumentType.word()` **不吃冒号** → 点名技能的参数必须 `string()`；RCON 侧再加引号才传得进 `ns:id`（`... "redtale_project:gale_slash"`）。
3. 「未施放」必须带状态。EXPLICIT 路径原来把 `failedCondition()`（冷却时为 null）当 detail → 输出 `未施放（null）`，与「技能不存在」无法区分。
4. 场上残余夹具会骗人：一只 `Invulnerable:1b` 的旧村民被选成目标，`damage` 原语 `hurt()` 返回 false、日志写「被原版拦下」，血量 20→20 → 看着像原语坏了。
5. 生物施法者必须灌 `vars.attacker`，否则 `damage` 原语拿不到攻击者（伤害无归属、不吃攻击者面板）。

## 5. 未验证（挂账）

- `tick` 路径的**真人 AI 选目标**：R37-b 事实「无玩家在线 AI 不跑」→ 本轮用 `mob cast … roll` 覆盖同一方法（`tryCast`），真实 AI 触发留给有客户端的轮次。
- 生物施法者面板上的 `physical_damage` 类乘区（`vars.attacker` 已接，但怪身上未实测堆叠）。
- 客户端侧的施法表现（无客户端）。

## 6. 数字

`build --offline` exit 0（jar **490223 B**）、`selftest` **78 PASS**、`S7DropProbe` **52/52**、测试服起停 5 次全部干净退出（25567/25577 无 LISTEN）、构建锁已删。
