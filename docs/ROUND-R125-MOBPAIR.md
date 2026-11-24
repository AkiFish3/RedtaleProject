# R125 · 2026-11-24 22:2x~22:5x · 回收 T-0119 自做并验收（melee/ranged 两只默认怪）+ 真机 4 只 + 派 T-0120（summoner/charger）

## 1. 本轮判定：S8 连续第二轮静默 → 收回 T-0119

三条独立证据（R124-a）：

| 证据 | 结果 |
|---|---|
| 工具目录最后写入时间 | 16:21（本队 S7 自己接 `check_all`），此后 6 小时零新增 |
| 协作板 §3 末条 S8 记录 | 07:2x T-0117 交付，之后再无 S8 行 |
| `git log` 末条 | 仍为 R124（派 T-0119 那一提交） |

T-0119 自 16:3x 派单起停在 `[OPEN]`、进度行空逾 6 小时。R124 已预告「S8 若连续静默 2 轮 → 自做两 JSON5」，本轮条件成立 → 按 B-01·执行 先例收回自做，**不重派同内容卡**（§13.30 R27-d）。

## 2. T-0119 落地与三验收

两份 JSON5 照卡面逐字落地（`stone_footman.json5` 691 B / `frost_archer.json5` 621 B）。

| 验收 | 结果 |
|---|---|
| 1 | `check_mobs.py` → `共 6 个文件 · 0 条问题`；`check_mob_skills.py -v` → `已检查 4 份怪物 · 4 条技能 id · 5 条 skills 引用` + `0 条问题` |
| 2 | `check_equipment_slots.py --mobs … -v` → `已检查 4 份怪物 · 7 处自家基底装备引用 · 14 个基底` + `0 条问题`；`check_mob_levels` / `check_mob_scales` / `check_mob_skill_chance` / `check_mob_skill_cooldowns` 四把末行均 `· 0 条问题` EXIT=0 |
| 3 | `check_ownership.py` 0 问题；两文件各恰好一行 `  id:`（`stone_footman` / `frost_archer`）；`check_all` **首轮红 1**，见下 |

### 2.1 卡面缺陷（R125-a）：内容卡漏了「默认文件清单登记」

首轮 `check_all` → `check_defaults.py 红 1 · 清单 36 条 · 磁盘 38 个 · 问题 2 条`。根因：`ConfigStore.DEFAULT_FILES` 是首次运行时把 classpath 默认配置落到 `config/` 的唯一入口，新默认文件不登记 → 只存在于 jar 里、`config/` 里没有、`check_defaults` 判红；而 T-0119 卡面写的是「禁止改任何 Java」+「验收 3 = `check_all` 67/67」，两条互斥。

处置：在 `mobs/ash_caster.json5` 之后补 3 行（1 注释 + 2 路径），`check_defaults` → `清单 38 条 · 磁盘 38 个 · 问题 0 条`，`check_all` → `共 67 个脚本 · 跑成 67 · 跳过 0 · 红 0`。卡面已补「S7 补记」，派单池 C71 记入纪律。

## 3. 构建与产物（jar 级证据）

取锁 `gradle build --offline` EXIT=0（37s），锁已删。

| 证据 | 结果 |
|---|---|
| jar | `redtale_project-0.1.0.jar` 687441 B（上轮 686121 B） |
| 资源 | `unzip -l` 内 `redtale/defaults/mobs/` 四份：`ash_caster` / `ember_guard` / `frost_archer` / `stone_footman` |
| 清单 | `ConfigStore.class` 常量池含四条 `mobs/*.json5`（`grep -ao` 去重后 4 条） |

## 4. 真机验收（测试服 1 次启停，已停）

测试服 `E:\server-test`，jar 换成本轮产物后启动，`Done (` 一次过。

| 证据 | 结果 |
|---|---|
| 启动日志 | `内容装载完成 … 4 只怪物（5 条技能引用）`（上轮 2 只） |
| 配置落地 | `config/redtale_project/mobs/` 出现 `frost_archer.json5` / `stone_footman.json5`（22:32 写入），旧两份未被覆盖（17 日 / 18 日 mtime 未变） |
| `redtale mob list` | `怪物定义 4 只（原型 4 种）`：`stone_footman · 原型 melee · 等级 5 · 装备 1 件 · 技能 1 条 · 体积 ×0.95`、`frost_archer · 原型 ranged · 等级 7 · 装备 2 件 · 技能 1 条 · 体积 ×1.00` |
| `redtale mob check` | `4 只 / 技能引用 5 条 / 掉落表 1 张 · 引用完整 ✓` |
| `redtale reload` | `… 4 只怪物 …（42 份配置文件）；全量刷新已排程 90 个区块`，零 ERROR |
| 停服 | `stop` → `All dimensions are saved` / `RCON Listener stopped`；25567 / 25577 无 LISTEN，正式服 9376（25565 / 25575）未动 |

## 5. 派 T-0120：summoner / charger 两只默认怪

8 原型覆盖从 4/8 推到 6/8。派前离线预验（R124-b，副本跑完即删）：`check_mobs` 8 文件 0 问题、`check_mob_skills -v` 6 怪 / 4 条 id / 9 引用 0 问题、`check_equipment_slots --mobs -v` 6 怪 / 10 引用 / 14 基底 0 问题、四把值域尺 0 问题。

卡面三处收紧：①把 `ConfigStore.java` 列为**独占文件**并逐字给出插入位置（R125-a）；②构建改为「取到锁才构建、锁被占则记未构建」，不删别人的锁；③`summoner` 明写「`summon` 原语未实现（Q-022 按需补），先用既有两条法术技能占位，等原语落地只改 skills 段」。

## 6. 口径（详见 PLAN §13.128）

- **R125-a** 内容卡必须登记默认文件清单：新默认文件不写进 `ConfigStore.DEFAULT_FILES`，`check_defaults` 必红，验收与「禁改 Java」互斥。
- **R125-b** 派前预验要用**整套**在役尺（上一轮只跑三把，漏了 `check_defaults`，导致 S8 照抄必红）。
- **R125-c** 真机验收只认三个观察点：启动日志的怪物计数、`config/` 是否落地、`mob list` 的原型/等级/装备；不看「加载无报错」。
- **R125-d** 内容卡的构建是可选步：S8 可能被锁挡住，卡面写清「锁被占就记未构建」，不许删别人的锁。
- **R125-e** 裸名纪律第四十三次。

## 7. 环境与收尾

`src/` 本轮有实质改动（2 新 JSON5 + `ConfigStore` 3 行）；构建 1 次（EXIT=0）；测试服 1 启 1 停；构建锁已删；正式服 9376 未动。

七门禁全 0：`check_all` 67/67、`check_tasks` 120 卡在制品 1、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership`、`check_checkall`。
