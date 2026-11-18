# R46 · 验收 T-0041 + M7 L5 `grant_exp` + D26 schema 定稿

- 日期：2026-11-18 12:56–13:2x
- 里程碑：**M7 进度**（L5 收尾）＋ **M6**（D26 契约先行）
- 构建：`gradle build --offline` exit 0，jar **552838 B**；`selftest` **78 PASS**
- 测试服：`E:\server-test` 起停 **2 次**（`Done (2.052s)` / 第二次同版），均已停，25567 / 25577 无 LISTEN

## 1. 卡板

| 卡 | 结果 |
|---|---|
| **T-0041** `tools/check_progress.py` | `[DONE]` → **`[VERIFIED]`**（3/3 + 附加两条） |
| **T-0042** `mobs/SpawnRuleSpec` | **新派**（契约 = 新写的 `docs/SPAWN-RULES.md`） |

在制品 **1/3**。

### T-0041 验收证据

| 验收项 | 命令 | 结果 |
|---|---|---|
| 默认件 | `python tools/check_progress.py` | `共 2 个文件 · 没有发现问题 ✓` EXIT=0 |
| 真实回归 | `--dir E:/server-test/config/redtale_project` | 同末行 EXIT=0 |
| 坏夹具 ①`mining_speed` | `--dir build/t41/bad1` | `[问题] levels/main.json5: 属性 mining_speed 不在 80 条表内` · `1 条问题` EXIT=1 |
| 坏夹具 ②`max_level: 1` | `--dir build/t41/bad2` | `max_level 必须是 >=2 的整数` · EXIT=1 |
| 坏夹具 ③跨目录同 id | `--dir build/t41/bad3` | `jobs/miner.json5: id redtale_project:main 与 levels/main.json5 重复` · EXIT=1 |
| 附加：JSON5 损坏 | `--dir build/t41/bad4` | `JSON5 解析失败：…` · EXIT=1 |
| 附加：无目录 | `--dir build/t41/none` | `没有 levels/ 与 jobs/ 目录（跳过）` EXIT=0 |
| 规模/依赖 | `wc -l` / `grep -c "^import "` | 219 行 / 5（`argparse json os re sys`，全白名单） |

S8 提的四处口径：

1. Δ① `generic.attack_damage` 归一到 `minecraft:generic.attack_damage`（Java `resolve` 原来不认）→ **采纳，但改 Java 侧**（见 §2 R46-a），检查器不动。
2. Δ② 空串 id 单报一条 `id 为空` → 采纳（本属「不含冒号」一类，文案更准）。
3. Δ③ `curve` 非对象只报一条 → 采纳。
4. Δ④ 读不到属性表时汇总 `共 0 个文件 · 1 条问题` → 采纳。

## 2. 本轮自研

### R46-a `AttributeSpecs.resolve` 加 `generic.` 别名

- 位置：`core/attr/AttributeSpecs.java`（`!t.contains(":")` 分支里，`minecraft.` / `redtale_project.` 之后）
- 行为：`generic.attack_damage` → `minecraft:generic.attack_damage`（找不到仍落回裸路径 `minecraft:generic.<t>`）
- 为什么改运行期不改工具：对账工具比运行期**宽松** ⇒ 绿灯放行 + 上线静默丢属性，正是 T-0041 要消灭的失败类型；反过来（工具严、运行期宽）只会误报。
- 实测（夹具 `levels/t_generic.json5`）：`level info "redtale_project:t_generic"` → `属性 2 条：minecraft:generic.max_health +0.5/级 · minecraft:generic.attack_damage +0.25/级`，**零告警**；删夹具后回落 `主等级 1 条 · 按级属性 3 条`。

### R46-b `grant_exp` 原语（M7 第三条经验来源）

- 新类 `core/script/builtin/GrantExpEffect`（id `grant_exp`）：
  - `args.amount`（必填）：`<=0` / 非数字 → WARN 一次 + 跳过；`> HARD_CAP(1e9)` → 按硬顶发 + 日志
  - `args.target`：`caster`（缺省）/ `target`；只认**玩家**（怪物没有累加器 → WARN 跳过）
  - 结算：`ProgressService.award(server, player, amount, SRC_PRIMITIVE)` —— 同一条 `award`，不新建第二份表
- `Builtins.registerAll` +1 行（在 `projectile` 之后）；`redtale status` 原语 effect **12 → 13**
- 观测面 `/redtale exp primitive <amount>`：把原语当**真实调用**跑（`ScriptEngine.runCalls` → 注册表 → 原语 → `award`），先 `store.clear(uuid)` 清场
- D17 记账：原语预算 24 → **25**，`docs/SKILLS.md` §4 加第 25 行、`grep -c "^| [0-9]"` 断言 25

### R46-c D26 契约定稿

新 `docs/SPAWN-RULES.md`（11 节）：钩子事实（R42-c/d）、`spawn_rules/*.json5` schema、字段表、`conditions` 六维语义、`per_chunk_cap` 的「现存实体数」近似、三层分层（`SpawnRuleSpec` / `SpawnRegistry` / `SpawnReplacer`）、五条红线、告警口径、交付顺序、可机器验的部分。

## 3. 实测（`/redtale exp primitive`）

| 组 | 命令 | 输出 |
|---|---|---|
| A | `exp primitive 5000` | `累计 0 → 5000（Δ5000，请求 5000，期望 5000）✓` · `主等级 1 → 18` · 职业合计 Δ0 |
| B | `exp primitive 0` | `Δ0（期望 0）✓` + 日志 `grant_exp 的 args.amount 缺失 / 非数字 / <= 0，已跳过` |
| C | `exp primitive 1200000000000` | `Δ1000000000（期望 1000000000）✓` · `主等级 1→100` + 日志 `超过硬顶 1000000000，按硬顶发放（D16 异常保护）` |
| D | `exp primitive -5` | `Δ0（期望 0）✓` |

日志（`logs/latest.log`，UTF-8）：
```
[script] grant_exp · [Minecraft] +5000（请求 5000）· 主等级 1→18 · modifier 2 条
```
回归：`redtale reload` → 32 份配置 / 10 件内容 / 3 技能 / 2 怪 / 1 掉落表 / 2 等级定义；`redtale validate` → `问题 0 条`。

## 4. 坑与口径

- **假红**（R46-c）：第一版 `exp primitive` 的期望写成 `Δ == 请求值`，硬顶路径 C 组直接打 ✗ —— 实际行为正确。修法是让期望按「原语自己的保护规则」算，并把 `HARD_CAP` 提成 `public` 给命令复用，不抄第二份数字。
- **Lambda 里非 effectively final**（R30/R35 老坑复发一次）：`delta` / `jobDelta` / `lvAfter` 都要先落 `final` 局部量，否则 `sendSuccess(() -> ...)` 编不过。
- **`long` 参数下界**：`LongArgumentType.longArg(Long.MIN_VALUE)` 才能让 `-5` 进到原语（下界默认 0 会在 Brigadier 层就拒），负值路径才验得到。
- **`store.clear(uuid)` 必须每次先做**：FakePlayer UUID 固定，不清场就会「上一轮顶到 100 → 这一轮 A 组直接满级」。

## 5. 未验 / 挂账

- `grant_exp` 在**真实技能 / 附魔脚本**里的落地（本轮只验原语本体 + 命令观测面）
- 真人击杀 → `MobDrops` → `award(kill)` 全链（R45 遗留）
- `/redtale level info` 补「当前玩家等级」（R45 计划里的漏项，本轮没做，已写进 §13.49 下一步 4）
