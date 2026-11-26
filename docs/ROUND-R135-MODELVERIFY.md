# R135 · 2026-11-27 00:0x~00:3x · 验 T-0127（14 基底模型，离线等价判据）+ 验 T-0128（两把武器，真机）+ 派 T-0130（`weapon_attributes` 外部引用尺）

## 0. 结论先行

1. **T-0127 → `[VERIFIED]`，14 份模型已合流**。验收 1~3 逐条过；验收 4（客户端日志）**本轮不可执行** —— 桌面无显示器（`Failed to find a primary monitor` / `glfwGetPrimaryMonitor failed`），改用**离线等价判据**：原版 1.21.1 jar 内 `item/` 贴图 **605** 张，14 个 `layer0` **全部命中**（`MISSING layer0: []`）。客户端日志复核记为**欠账 D-01**。
2. **T-0128 → `[VERIFIED]`**。三条机器验收全过，并补真机：`redtale items` → **12 件装备内容 / 基底 14 个**，两把新武器各自的 `bettercombat:` 动作与攻速档位都在 tooltip 里逐字出现；`reload` 44→**48** 份零 ERROR。
3. **派 T-0130**（`check_weapon_attr_ref.py`：`items` 的 `weapon_attributes` 外部引用 ↔ BetterCombat 内置动作对账），在制品 **2/3**（T-0129 + T-0130）。
4. **T-0129 第 1 轮空转**：6.5 小时零交付（文件不存在、进度行空），保持 `[OPEN]`，按口径不拆卡。

## 1. 卡板

- **T-0127 `[DONE]` → `[VERIFIED]`**：S8 于 19:1x 交付 shield 一行修正。验收 1~3 全过；验收 4 以离线等价判据替代（见 §2）。14 份模型**本轮合流**。
- **T-0128 `[DONE]` → `[VERIFIED]`**：S8 于 23:12 交付两把武器 + `ConfigStore` 3 行。三条机器验收 + 真机全过（见 §3、§4）。
- **T-0129 `[OPEN]` 保持**：17:5x 派，本轮 `00:1x` 仍无 `check_item_models.py`、进度行空 → 第 1 轮空转，不拆不回收（口径 R135-d）。
- **派 T-0130**（`check_weapon_attr_ref.py`，单文件 ≤200 行、五判据、六夹具、基线 9 物品 / 3 处引用 / 33 内置动作 / 0 问题），在制品 **2/3**。

## 2. T-0127 验收（逐字）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `ls .../models/item/*.json \| wc -l`；文件名集合 | 14；14 个名字逐字 | ✅ 14 / 逐字相同 |
| 2 | 14 份 JSON 结构（两顶层键、`parent`、`textures` 仅 `layer0`、值逐字对上卡表） | 14/14 | ✅ `rows 14 BAD []` |
| 3 | `grep -rl "redtale_project:item/" .../models/item/ \| wc -l` | 0 | ✅ 0 |
| 4 | 客户端日志 `Unable to load model` 0 **且** `Missing textures in model redtale_project:` 0 | 0 且 0 | ⛔ **不可执行**：`Failed to find a primary monitor`；改用离线等价判据 ↓ |

**离线等价判据（替代验收 4 的理由）**：R134 打回的根因是「`layer0` 指向原版 jar 里不存在的贴图」，客户端那句 `Missing textures` 就是这条判据的运行时表现。本轮直接从原版 jar 取值复核：

- `1.21.1-NeoForge.jar` 内 `assets/minecraft/textures/**` 共 **3070** 张，其中 `item/` **605** 张。
- 14 个 `layer0` 逐条查表 → `MISSING layer0: []`（含 shield = `minecraft:item/turtle_scute`）。
- 构建 jar **694888 B**，`unzip -l | grep models/item` = **15 条**（14 份 + 1 个目录条目）。

**欠账 D-01**：桌面可用（Aki 解锁 / 接显示器）时补跑一次客户端，`Unable to load model` 0 且 `Missing textures in model redtale_project:` 0 才销账。这不是"修好了"，是"该判据已用确定性的等价物过了一遍，运行时那句话还欠一次亲眼确认"。

## 3. T-0128 验收（逐字）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `check_items.py` | `共 14 个文件 · 0 条问题` EXIT=0 | ✅ 逐字 |
| 2 | `check_defaults.py` / `check_numeric.py` / `git diff --numstat ConfigStore.java` | `清单 44 条 · 磁盘 44 个 · 问题 0 条` / `0 条问题` / `3 0` | ✅ 三条逐字 |
| 3 | `check_all.py` / `check_ownership.py` / `check_tasks.py` | `共 71 个脚本 · 跑成 71 · 跳过 0 · 红 0` / `0 条问题` / 在制品 1 | ✅ 逐字（当时 T-0129 是唯一在制品） |

两份 JSON5 经 `clean_json5` 解析后按键逐字比对卡面，9 键全等（`demo_dagger`: `dagger` / `bettercombat:dagger` / `3.5` / `2.4` / 暴击 `0.2`+`0.3`；`demo_warhammer`: `hammer` / `bettercombat:hammer` / `10.0` / `0.9`）。

## 4. 真机（测试服 1 启 1 停）

- 构建：取锁 `.queue/build.lock` → `gradle build --offline` **EXIT=0**（26s，锁已删），jar **694888 B**；`unzip -l` 内 `redtale/defaults/items/demo_dagger.json5` **454 B** / `demo_warhammer.json5` **403 B**。
- 启动：`E:/server-test`，`@libraries/net/neoforged/neoforge/21.1.257/win_args.txt nogui`（R126 踩坑结论：用 `win_args.txt`，不用 `run.sh`），`Done (2.410s)`；启动前 4 条 `RecipeManager` ERROR 为他模配方噪声（与 R126~R128 逐字同）。
- 观测点（三条）：
  - `redtale items` → `12 件装备内容 · 基底 14 个 · 组件 redtale_project:content`；`demo_dagger · dagger · 4 条属性 · 耐久 700 · bettercombat:dagger · 孔位 1`、`demo_warhammer · hammer · 2 条属性 · 孔位 2`。
  - `redtale content list` → `内容 12 件 · 数值共 17 条 · 带 weapon_attributes 4 件`。
  - `redtale tooltip demo_dagger` → `≈ 8.925 /次` + `攻速 8.333 tick · 2.4 次/秒` + `动作 bettercombat:dagger`；`demo_warhammer` → `≈ 11 /次` + `攻速 22.222 tick · 0.9 次/秒` + `动作 bettercombat:hammer`。**D20 双档位（快 2.4 / 慢 0.9）与 D15 第三个动作取样同时成立**。
- `redtale reload` → `已载入 2 条脚本 + 12 件装备内容 + 4 条技能 + 8 只怪物 + 1 张掉落表 + 1 条刷怪规则 + 3 条等级定义 + 1 棵技能树 + 2 个专业（48 份配置文件）`，零 ERROR（44→48）。
- `config/redtale_project/items/` 两份新文件 **00:08** 落地；旧文件 mtime 未变。
- `stop` → `All dimensions are saved` → `RCON Listener stopped`；停后 25567 / 25577 **无 LISTEN**（仅 TIME_WAIT）；正式服 9376 本轮**未触碰**（25565 全程无监听，R134 后一直处于停止状态，等 Aki 手动拉）。

## 5. 派单取证（T-0130，对账网第五十六例）

四判据：

1. **出口零校验**：`check_items.py:83-85` 只核「字符串且含 `:`」；运行期 `ValidateCommand.java:66-85` 对外部命名空间 `external++` 直接 `continue`，源码注释自述「别人 jar 里的内置项我们无法校验」。
2. **旧尺零覆盖**：`grep -l "bettercombat" tools/*.py` → **零命中**（71 把）。
3. **基线现查**：`defaults/items/` **9** 份内容、`weapon_attributes` 引用 **3 处**（dagger / claymore / hammer）、`redtale_project:` 引用 **0 处**；BetterCombat 2.4.0 jar 内 `data/bettercombat/weapon_attributes/*.json` **33** 个内置动作，**无 `greatsword.json`**。
4. **反事实全绿**：干净副本把 `bettercombat:dagger` 改成 **不存在的** `bettercombat:greatsword` → `check_items` `共 14 个文件 · 0 条问题`、`check_numeric` `共 4 个文件 · 0 条问题`、`check_affix_reach` `0 条问题`，三把尺逐字全绿。副本已删。

## 6. 口径 §13.138（R135 五条）

| 编号 | 口径 |
|---|---|
| R135-a | **环境缺失时用「确定性等价判据」并记账，不冒充跑过**：客户端起不来（无显示器）不等于验收可跳过。R134 打回的那条判据本质是「被引用对象存在性」，可直接从 jar 取值复核 → 用它替代、把运行时那句话记成欠账 D-01 并写清销账条件。替代判据必须**与被替代判据同因**，不能换个弱判据充数。 |
| R135-b | **"起不来"要看清是哪一层**：本轮客户端 `ModLauncher` 已在跑（args / 21.1.257 / 103 类库都对），死在 `fmlearlywindow` 的 `glfwGetPrimaryMonitor` —— 是显示层，不是模组层。日志里 `Failed to find a primary monitor` 是判据入口，别错记成"客户端跑不通"。 |
| R135-c | **杀进程第二次确认纪律**：关客户端前先从 `Win32_Process` 取 `CommandLine` 里带 `-Xmx3G` 的那条 PID（`3956`），杀单里只有它；`38944` 是 Gradle Daemon，留在原地。**`taskkill /IM java.exe` 永不再用**（R134 事故）。 |
| R135-d | **空转计轮看文件与进度行，不看"它做了什么别的"**：T-0129 派了 6.5 小时零交付，但同期 S8 交付了 T-0127 修正与 T-0128 —— 说明队列在线、只是排序不同。这类按**第 1 轮空转**记，不拆卡；三振才拆。 |
| R135-e | **裸名纪律第五十三次**：只写裸名（`check_weapon_attr_ref.py` / `ValidateCommand.java` / `demo_dagger.json5`），不写 `tools/` 全路径、不写带中文方括号的 jar 文件名（卡面用 `--jar-dir` + `*bettercombat*.jar` 通配绕开）。 |

## 7. 环境

- `src/`：合流 14 份模型 + 2 份 JSON5 + `ConfigStore.java` 3 行（均来自已 VERIFIED 的卡）。
- 构建 **1 次**（EXIT=0，锁已删）。
- 测试服 **1 启 1 停**（`Done (2.410s)` → `stop`，端口无 LISTEN）。
- 测试客户端 **1 启 0 成**（`glfwGetPrimaryMonitor failed`，按 PID 3956 结束）。
- 正式服：**未触碰**（25565 全程无监听，仍等 Aki 手动启）。
- 无构建锁残留。

## 8. 下一步（R136）

1. **验 T-0129**（模型三方尺）：基线 `0 条问题` → 接线 `check_all` **71→72**；若 S8 第三轮仍无交付 → 按 §2 拆卡或回收自做（T-0118/T-0119 有先例）。
2. **验 T-0130**（`weapon_attributes` 外部引用尺），过后接线 **72→73**。
3. **欠账 D-01**：桌面可用时补跑客户端，`Unable to load model` 0 **且** `Missing textures` 0 后销账。
4. M6 代码面剩「模板实体注册 / 自然刷怪替换」（S7 独占、跨文件）；M7 真机需 Aki 在线或手动进服。
5. Q-024（正式服事故口径）等 Aki 定；Q-021 / Q-022 / Q-023 无答复继续按默认走。
