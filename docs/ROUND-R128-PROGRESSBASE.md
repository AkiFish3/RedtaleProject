# R128 · 2026-11-25 17:0x · 验收 T-0122（`ambient` 原型，M6 内容面 8/8）+ 真机 8 只 + 派 T-0123（进度曲线 `curve.base` 超硬顶）

## 1. 卡板

| 卡 | 状态变化 | 证据 |
|---|---|---|
| T-0122 `mobs/gloom_moth.json5`（`ambient` 原型） | `[DONE]` → **`[VERIFIED]`** | 三条验收逐条全过，见 §2 |
| T-0123 `check_progress_base.py`（`curve.base > 1e9` 静默夹顶） | 新派 `[OPEN]` | 对账网第 50 例；派前源码逐字取证，见 §4 |

在制品 `1/3`（`check_tasks.py`：`共 123 张卡 · 在制品 1 · 问题 0 · 提示 0`）。

## 2. T-0122 验收（3/3）

| # | 命令 | 实测末行 | 期望 | 判 |
|---|---|---|---|---|
| 1 | `check_mobs.py` | `共 10 个文件 · 0 条问题` | 同 | ✅ |
| 1 | `check_mob_skills.py -v` | `已检查 8 份怪物 · 4 条技能 id · 10 条 skills 引用` + `共 1 份怪物技能引用表 · 0 条问题` | 同（零技能：引用条数冻结 10） | ✅ |
| 2 | `check_equipment_slots.py --mobs … -v` | `已检查 8 份怪物 · 11 处自家基底装备引用 · 14 个基底` + `0 条问题` | 同（零装备：11 未变） | ✅ |
| 2 | `check_mob_levels` / `_scales` / `_skill_chance` / `_skill_cooldowns` | 四把末行均 `· 0 条问题` | 0 问题 | ✅ |
| 2 | `check_defaults.py` | `清单 42 条 · 磁盘 42 个 · 问题 0 条` | 同 | ✅ |
| 3 | `check_all.py` | `共 67 个脚本 · 跑成 67 · 跳过 0 · 红 0` | 同 | ✅ |
| 3 | `check_ownership.py` | `共 1 份卡板 · 0 条问题` | 0 问题 | ✅ |
| 3 | id 行 | 恰好 1 行 `  id: "gloom_moth",`（第 5 行） | 1 行且 = 文件名 | ✅ |

**改动逐字核对**：`ConfigStore.java` diff 恰 `+2` 行（1 注释 + 1 路径，位置在 `"mobs/gale_harrier.json5",` 之后）；`gloom_moth.json5` 508 B、与卡面逐字一致。S8 零偏差，不打回。

**卡面缺陷（R128-a）**：卡面代码块给的缩进是 16 空格，正文写「缩进 12 空格」，邻居行全是 12 空格。S8 照代码块执行（它无法自判该信哪个），不算偏差；S7 合流时按正文改成 12 空格，记入台账。这是同类卡面矛盾第三次（R126-a 行数）。

## 3. 构建 + 真机（8 只 / 8 原型）

**构建**（S7 取锁 `gradle build --offline`，`BUILD SUCCESSFUL in 23s`，锁已删）：
- `build/libs/redtale_project-0.1.0.jar` = **690121 B**（上轮 689534 B）。
- `unzip -l` → `redtale/defaults/mobs/` **8 份**（新增 `gloom_moth.json5` 508 B）。

**真机（测试服 1 启 1 停，正式服全程未触碰）**：

| 观察点 | 实测 |
|---|---|
| 启动 | `Done`，之后零 ERROR；启动日志 `8 只怪物（10 条技能引用，原型 8 种）` |
| 配置落地 | `config/redtale_project/mobs/gloom_moth.json5` 508 B @ 17:11；旧七份 mtime 未变（17 日 21:23 / 18 日 06:53 / 24 日 22:32 / 25 日 04:41 / 11:01） |
| `redtale mob list` | `怪物定义 8 只（原型 8 种）`，新增行 `幽尘蛾 gloom_moth · 原型 ambient · 等级 2 · 装备 0 件 · 技能 0 条 · 体积 ×0.50`（三列与卡面 JSON5 一致：`level 2` / 无 equipment/skills / `scale 0.5`） |
| `redtale mob check` | `8 只 / 技能引用 10 条 / 掉落表 1 张 · 引用完整 ✓` |
| `redtale reload` | `已载入 2 条脚本 + 10 件装备内容 + 4 条技能 + 8 只怪物 + 1 张掉落表 + 1 条刷怪规则 + 3 条等级定义 + 1 棵技能树 + 2 个专业（46 份配置文件）`（45→46），零 ERROR |
| 停服 | `stop` 后 25567 / 25577 无 LISTEN；正式服 PID 9376 仍占 25565 / 25575 |

**M6 内容面收口**：`MobSpec.BASES` 8 原型（melee / ranged / caster / summoner / charger / flyer / elite / ambient）各有 1 只默认怪（`stone_footman` / `frost_archer` / `ash_caster` / `bone_summoner` / `iron_horn_charger` / `gale_harrier` / `ember_guard` / `gloom_moth`）。M6 剩余工作不是内容，而是「模板实体注册」「自然刷怪替换」两块代码面。

## 4. 派 T-0123（`check_progress_base.py`）

**为什么是这张**：M6 内容面收口后转 M7（进度面）。S7 只读扫了 M7 三个目录（`levels/` 1 份、`jobs/` 2 份、`professions/` 2 份）+ 进度相关 Java，按四判据（真洞 / 可离线判 / 旧尺未覆盖 / 基线非空）逐条过：

| 候选 | 判 |
|---|---|
| `levels/`+`jobs/` 的 `curve.base > 1e9` | **真洞，本卡收**：`ProgressSpec.java:130` 的 `num` 只判 `!isNum \|\| !isFinite \|\| v <= min` 才 WARN，`base > 1e9` 原样返回；出口 `ExpCurve.java:43-45` 把每级经验静默夹到 `MAX_STEP_EXP = 1_000_000_000`。旧尺 `check_progress.py:151-161` 只核 `curve` 里「按 mode 应有 base/growth/exponent 键」，不读值域；`check_profession_base.py` 只扫 `defaults/professions`。基线 3 文件 / 3 处 / 0 问题 |
| `levels/`+`jobs/` 的 `curve.base` 为 0 / 负 | 排除：`ProgressSpec` 用 `v <= min`，0 与负数已 WARN（与 `professions` 的 `v < min` 不同，见 R128-b） |
| `curve.growth` / `exponent` 超硬顶（计算后） | 留池 C72：要复算运行期曲线数学，等于在 Python 里抄第二份 `ExpCurve`（R128-c） |
| `max_level` 缺键回落 100/50 | 排除：缺键回落是有意默认；拼错键另有「未知键」面，属另一类 |
| `professions[].max_level` 缺键回落 100 | 排除：同上，且 `ProfessionSpec` 有未知键 WARN |
| `levels/jobs[].max_level > 1000` | 排除：源码 `:58-61` 有 WARN |

**卡面要点**（详见 `docs/TASKS.md` T-0123）：接口 `_loads` / `read_files(root, subdir)` / `check_base(rows)` / `main`，常量 `BASE_DEFAULT = 100.0`、`MAX_STEP_EXP = 1000000000`、`SUBDIRS = ("levels", "jobs")`；文案逐字 `…: curve.base 值 <v> 超过 MAX_STEP_EXP=1000000000 静默夹顶（解析零 WARN）`；多子目录读失败**逐目录计**（`--dir NO_SUCH` → 2 条 `[问题]`，R128-d）；六夹具 + 函数边界 + ≤200 行 + 零接线。

## 5. 环境与门禁

- `src/` 本回合流 1 份新 JSON5 + `ConfigStore.java` 2 行（含缩进修正，归 S7）。
- 构建 1 次（取锁 → 构建 → 删锁）；测试服 1 启 1 停；正式服未触碰；无残留锁。
- 七门禁：`check_all` 67/67、`check_tasks` 123 / 在制品 1、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership`、`check_checkall` 全 0。
