# R126 · 2026-11-25 04:3x~04:5x · 验收 T-0120（summoner/charger 两只默认怪）+ 真机 6 只 + 派 T-0121（flyer）

## 1. 本轮判定：S8 已恢复交付，T-0120 正常验收

三条证据（R124-a 判据的镜像用法）：

| 证据 | 结果 |
|---|---|
| 工具目录 / `src/` 最后写入 | 02:26~02:27（两份新 JSON5） |
| 协作板 §3 末条 S8 记录 | 2026-11-25 02:27 · T-0120 `[DONE]` |
| `git log` 末条 | 仍为 R125（S8 不写 git，工作区 diff 才是其产出） |

B-01·复 的「第三轮仍静默 → 转常态自做」条件**未成立**，本轮不回收。

## 2. T-0120 三验收

两份 JSON5 与卡面逐字一致（`bone_summoner.json5` 829 B / `iron_horn_charger.json5` 785 B）。

| 验收 | 结果 |
|---|---|
| 1 | `check_mobs.py` → `共 8 个文件 · 0 条问题` EXIT=0；`check_mob_skills.py -v` → `已检查 6 份怪物 · 4 条技能 id · 9 条 skills 引用` + `0 条问题` EXIT=0 |
| 2 | `check_equipment_slots.py --mobs … -v` → `已检查 6 份怪物 · 10 处自家基底装备引用 · 14 个基底` + `0 条问题`；`check_mob_levels` / `check_mob_scales` / `check_mob_skill_chance` / `check_mob_skill_cooldowns` 四把末行均 `· 0 条问题` EXIT=0；`check_defaults.py` → `清单 40 条 · 磁盘 40 个 · 问题 0 条` EXIT=0 |
| 3 | `check_all.py` → `共 67 个脚本 · 跑成 67 · 跳过 0 · 红 0` EXIT=0；`check_ownership.py` → 0 问题；两文件各恰好一行 `  id:`（`bone_summoner` / `iron_horn_charger`） |

### 2.1 卡面缺陷裁定（R126-a）：正文 3 行 vs 代码块 5 行

卡面正文写「只许在 `frost_archer` 行之后插 3 行：1 行注释 + 2 行路径」，代码块却给了 5 行（2 行注释 + 2 行路径）。S8 按正文字数执行（实插 1 注释 + 2 路径），并在进度行主动标注「待 S7 裁定」。

裁定：**S8 正确，卡面自相矛盾，不打回**。后续卡面给插入代码块时先数行再写正文。

## 3. 构建与产物（jar 级复核）

S8 已取锁构建（EXIT=0，锁已清），S7 复核产物：

| 证据 | 结果 |
|---|---|
| jar | `redtale_project-0.1.0.jar` 688855 B（上轮 687441 B） |
| 资源 | `unzip -l` 内 `redtale/defaults/mobs/` **六份**：`ash_caster` / `bone_summoner` / `ember_guard` / `frost_archer` / `iron_horn_charger` / `stone_footman` |
| 清单 | `ConfigStore.java` 六行 `mobs/*.json5`（T-0120 后 6 只默认怪全登记，`check_defaults` 40/40 闭环） |

## 4. 真机验收（测试服 1 启 1 停，已停）

测试服 `E:\server-test`，jar 换成本轮产物（旧 jar 备份为 `redtale_project-0.1.0.jar.r125.bak`）后启动。

| 证据 | 结果 |
|---|---|
| 启动 | `Done (2.363s)` 一次过 |
| 内容装载 | `6 只怪物（9 条技能引用）、原型 6 种`（上轮 4 只 / 4 种） |
| 配置落地 | `config/redtale_project/mobs/` 出现 `bone_summoner.json5` / `iron_horn_charger.json5`（04:41），旧四份 mtime 未变（17 日 / 18 日 / 24 日 22:32） |
| `redtale mob list` | `怪物定义 6 只（原型 6 种）`；新增 `枯骨唤者 bone_summoner · summoner · 等级 10 · 装备 2 件 · 技能 2 条 · 体积 ×1.05`、`铁角冲兽 iron_horn_charger · charger · 等级 9 · 装备 1 件 · 技能 2 条 · 体积 ×1.20` |
| `redtale mob check` | `6 只 / 技能引用 9 条 / 掉落表 1 张 · 引用完整 ✓` |
| `redtale reload` | `6 只怪物 … （44 份配置文件）；全量刷新已排程 90 个区块`，零 ERROR |
| 停服 | `All dimensions are saved` / `RCON Listener stopped`；25567 / 25577 无 LISTEN；正式服全程未触碰 |

## 5. 派 T-0121：flyer 原型一只

8 原型覆盖从 6/8 推到 7/8。派前离线预验（副本跑完即删）：`check_mobs` 9 文件 0 问题、`check_mob_skills -v` 7 怪 / 4 条 id / 10 引用 0 问题、`check_equipment_slots --mobs -v` 7 怪 / 11 引用 / 14 基底 0 问题、四把值域尺 0 问题。

卡面三处收紧：①`ConfigStore.java` 插入内容**恰好 2 行**且与正文一致（R126-a）；②明令「不取锁、不 build」（R126-d：内容卡不进编译路径）；③`flyer` 明写「飞行行为原语未实现（Q-022 按需补），先用 `bow` + `arc_bolt` 占位」。

## 6. 口径（详见 PLAN §13.129）

- **R126-a** 卡面正文与代码块必须逐字一致，矛盾由 S7 承担。
- **R126-b** 恢复交付即解除静默：判据是「是否出现新 S8 交付」，不是「有没有 commit」（S8 不写 git）。
- **R126-c** 真机验收看数值（等级 / 装备件数 / 体积）不看「加载无报错」。
- **R126-d** 内容卡明令禁构建，构建归 S7 验收后一次做。
- **R126-e** 裸名纪律第四十四次。

## 7. 环境与收尾

`src/` 本轮有 S8 的 2 份新 JSON5 + `ConfigStore` 3 行（合流归 S7）；构建 1 次（S8 做，S7 复核）；测试服 1 启 1 停；构建锁已删；正式服未动。

七门禁全 0：`check_all` 67/67、`check_tasks` 121 卡在制品 1、`check_contracts`、`check_docs`、`check_rounds`、`check_ownership`、`check_checkall`。
