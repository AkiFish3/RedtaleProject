# R49 · 2026-11-18 · 验收 T-0044 + `/redtale level info` 的「当前」段 + 派 T-0045

## 0. 一句话

验收 `check_enchants` 并通过（3/3 + 4 项补测 + 4 条口径裁决，其中一条判为**卡面笔误**）；补上挂了两轮的 `/redtale level info` 当前等级——把它写成**纯函数**，18 条边界离线验穿，控制台输出逐字不变；派 T-0045 开 M7 最后一块（技能树）的 L1。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0044 `tools/check_enchants.py` | 验收 | **VERIFIED** 3/3 + 4 项补测 |
| T-0045 `core/progression/SkillTreeSpec` | **派** | OPEN（在制品 1/3） |

### 1.1 T-0044 验收明细

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `python tools/check_enchants.py` | `共 2 个文件 · 没有发现问题 ✓` / EXIT=0 | 同 |
| 2 | `--dir E:/server-test/config/redtale_project` | 同口径 EXIT=0 | 同 |
| 3 | 三份坏夹具（各自单独目录） | 命中指定文案 + EXIT=1 | `effects 引用的脚本 minecraft:enchants/soul_reap 不存在（现有 0 个：）` / `max_level 必须是 1~255 的整数` / `description 缺失（没有中文名）` |

补测（卡面没列，但按「三振」纪律自己加）：

| 项 | 结论 |
|---|---|
| 跨文件重复 id | `id redtale_project:super_sharpness 与 enchantments/a.json5 重复` ✓（同一份文件复制成 `a.json5`/`b.json5`） |
| datapack 双向 | 配置有磁盘无 → `未生成：soul_reap.json`（**计问题**）；磁盘有配置无 → `[提示] enchantment/only_on_disk.json: 仅磁盘有：only_on_disk.json`（**不计问题**） |
| 接口签名 | `load_script_ids / collect / check_file / main` 四函数按序齐；`import` 只有 `argparse/json/os/sys` + `check_progress.clean_json5` |
| 行数 | 189 ≤ 260 |

### 1.2 四条口径裁决（S8 请裁）

| # | S8 的做法 | S7 裁定 |
|---|---|---|
| ① | 第 3 项「**同一文件内** id 重复」与文案「与**先出现的相对路径**重复」冲突，按跨文件实现 | **采纳，卡面笔误**。文案自己就写了「相对路径」，同一文件内不会有第二个相对路径。与 R43-a / R45 同源：卡面语义与自己写的文案打架时按语义判，并记「笔误」而不是默默改 |
| ② | 第 7 项脚本清单含 `greeting` 也计入 N | **采纳**。工具列举的是「运行期真能加载到的脚本」，多列不算错；少列才会误导 |
| ③ | `[提示] enchantment/x.json: 仅磁盘有：x.json` 两段都带文件名 | **采纳不改**。稳定可 grep 优于好看 |
| ④ | 单引号 → 双引号字面量转换后再交 `clean_json5` | **采纳**，但补一条边界写进卡：**块注释 `/* */` 两边都不支持**（Java `core/config/Json5` 只认 `//`）。实测带 `/* */` 的文件工具判「解析失败」，运行期同样坏 → 工具与运行期口径一致，**不是假阳性** |

## 2. 自研：`/redtale level info` 的「当前」段

R45（`/redtale exp`）落地时留了个尾巴：`level info` 只看得到**定义**，看不到「我这级到哪儿了」。挂了两轮，本轮收掉。

### 2.1 设计（R49-a）

```
currentLine(ExpCurve, long totalExp)   // 纯函数，零 MC 依赖 → 离线探针直测
        ↓
current(ServerPlayer, id, def)         // 5 行胶水：取 store 的 totalExp
        ↓
info(): 仅当 src.getPlayer() != null 时追加一行
```

- 输出：`\n  当前 Lv7 · 本级 21/44 · 累计 1234`；满级 `Lv100 · 本级已满 · 累计 <n>`
- **控制台执行时不追加**（`getPlayer()` 为 null）→ T-0040 已 VERIFIED 的文案**逐字不变**，零回归。

### 2.2 为什么抽纯函数

绑了 `ServerPlayer` 就只能等真人上线（本轮测试服无玩家）。抽出来之后 16 条断言当场跑完，其中包括最容易错的两处：

- 满级不能出 `/0`：`expForLevel(maxLevel)` 返回 **0**，所以必须 `level >= maxLevel` 优先判；
- 恰好升级的边界：`total == totalExpForLevel(L)` → 本级归零、分母换成 `expForLevel(L)`。

### 2.3 探针（`build/probe/LevelLineProbe.java`，16/16）

| 组 | 断言 |
|---|---|
| A | L1 起点 `Lv1 · 本级 0/100 · 累计 0`；L1 末 `99/100` |
| B | 恰好 L2 `Lv2 · 本级 0/112 · 累计 100`；L3 起点 `212`；L3 中段 `124/125` |
| C | 满级 `Lv100 · 本级已满 · 累计 62143714`，且**不含 `/0`** |
| D | 超顶（`totalExpAtMax + 5e6`）→ 仍 `Lv100 · 本级已满`，累计照实 |
| E | 退化曲线（`geometric(1, …)` 实际 `maxLevel=2`）→ L1 `0/100` / L2 已满 / 无 `/0` |
| F | 文案无小数、无正号（下游逐字 grep） |

**本轮最值得记的一条**：E 组第一版按「`maxLevel = 1`」写期望，红了两条 —— 错的是**期望**不是代码（`ExpCurve` 构造里 `maxLevel = Math.max(2, n)`）。反过来这条边界正好证明「`/0` 不可能出现」：`level < maxLevel` 时 `expForLevel(level)` 恒 > 0。已落 `PLAN.md` §13.52 R49-c。

## 3. 实测（测试服，起停 1 次）

| 项 | 证据 |
|---|---|
| 启动 | `Done (1.954s)`；`进度存档就绪：1 名玩家` |
| `level info "redtale_project:main"` | 与 R45 基线**逐字相同**（3 行，**无** `当前` 行）：`上限 100 · 来源 levels/main.json5` / `曲线：…` / `属性 2 条：…` |
| `level list` | `主等级 1 条 · 职业 1 条 · 按级属性 3 条`（不变） |
| `reload` | `33 份配置文件`；`全量刷新已排程 90 个区块` |
| `exp probe` | **7/7**：A Lv7·23.0 / B 满级 69.5 / C 到顶不动 / D 归零 20.0 / E 双累加器 `miner=0` / F kill ×1.0 实发 1000 / G `mining_efficiency Δ0.06` |
| `validate` | `问题 0 条` |
| 停服 | RCON `stop` → 25567 / 25577 均无 LISTEN |

数字：`build --offline` exit **0**（jar **582962 B**）、`selftest` **78 PASS**、构建锁取/删一次（`.queue` 现为空）。

## 4. 未验证 / 挂账

- **`current()` 的玩家路径**（`src.getPlayer() != null` 那 5 行）：测试服无真人在线，RCON 是控制台上下文。与 R45「真人 `/attribute` 面板」、R48「真人自然刷怪」同批，等 Aki 上线一次验完。
- 顺手又踩一次：`StringArgumentType.string()` **不吃**未加引号的 `ns:id`（`redtale level info redtale_project:main` → `Expected whitespace to end one argument`，加引号才通）。这是第四次复发，命令面凡是吃 `ns:id` 的参数一律 `ResourceLocationArgument.id()` 或让调用方加引号。

## 5. 派单 T-0045

`core/progression/SkillTreeSpec` —— 技能树解析器 v1（`docs/SKILL-TREE.md` §3 schema 原样，14 条解析口径 + 逐字告警文案，独占一个新类 ≤200 行 + 自写探针 ≥25 断言，A~J 十组）。

选它的理由：M7 出口条件里「点满一条分支并解锁技能 / 洗点能完整回滚」是最后一块，而它的依赖全在（`ProgressRegistry` / `SkillRegistry` 都已 VERIFIED）。按 R42 的教训，先把 L1 纯解析做掉再谈修饰符与存档，schema 已被文档钉死 → 不放手让 S8 设计。

## 6. 下一步（R50）

1. 验收 T-0045。
2. M7 技能树 L2：`SkillTreeRegistry`（`classes` 对齐 `ProgressRegistry.jobs()`、`unlock_skill` 过 `SkillRegistry`）+ `skill_trees/` 进 `DEFAULT_FILES` + 默认 `warrior_vanguard.json5`（§3 例子原样）。
3. `spawn replace on|off` 开关持久化（新建 `spawn.json5`）。
