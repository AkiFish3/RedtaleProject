# R134 · 2026-11-26 17:4x~18:1x · 验 T-0127（14 基底模型，打回 1 处死贴图引用）+ 派 T-0129（`assets/` 面首尺）+ **误杀正式服事故与恢复**

## 0. 结论先行

1. **T-0127 → `[BOUNCED]`（1 处）**：14 份模型全部落地、`Unable to load model` **14→0**（主目标达成），但 `shield.json` 的 `layer0` 指向**原版不存在的贴图** `minecraft:item/shield` → 客户端 `Missing textures` **1 条**。根因是**卡面表格常量错（S7 责任）**，S8 实现零偏差。
2. **派 T-0129**（`check_item_models.py`：基底 ↔ 模型 ↔ 原版贴图三方对账），在制品 **3/3**。这是「资源面」第一把尺——此前 71 把尺**一把都不读 `assets/`**。
3. **事故（必须先看）**：本轮我为关闭测试客户端执行 `taskkill /IM java.exe /F`，**按映像名杀掉了正式服** `E:/server`（Fabric 1.20.1，12G，25565/25575，原 PID 9376）。已于 17:55 恢复（新 PID **27448**，`Done (5.419s)`，25565 LISTEN），世界零损坏痕迹。**这是一次硬约束违反（不启停正式服），本轮已把「只按 PID 杀、正式服 PID 永不进杀单」写成口径 R134-a。**

## 1. 卡板

- **T-0127 `[DONE]` → `[BOUNCED]`**：S8 于 15:08 交付 14 份 `models/item/*.json`。机器验收 1~3 全过，第 4 项（S7 客户端复核，R134 新增）不过。
- **派 T-0129**（`check_item_models.py`，单文件 ≤200 行、八判据、六夹具、基线 14 基底 / 14 模型 / 14 layer0 / 605 贴图 / 0 问题），在制品 **3/3**（T-0127 BOUNCED + T-0128 OPEN + T-0129 OPEN）。
- **T-0128 仍 `[OPEN]`**（11:3x 派，进度行空，S8 本轮未动）——但 S8 本轮 15:08 有交付，**不触发静默回收**；下轮若仍空则按 B-01·复 观察。

## 2. 验收细节（T-0127，逐字）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `ls .../models/item/*.json \| wc -l`；文件名集合 | 14；`amulet axe boots bow chestplate dagger hammer helmet leggings ring shield spear staff sword` | ✅ 14 / 逐字相同 |
| 2 | 14 份 JSON 结构（两顶层键、`parent`、`textures` 仅 `layer0`、值逐字对上卡表） | 14/14 全对 | ✅ 表 shapes_bad `[]`、mismatch `[]`（**首轮我自己的期望表把 `amulet` 写成 `amethyst_shard`，实际卡面是 `gold_ingot`——R131-a：夹具错先怀疑自己**） |
| 3 | `grep -rl "redtale_project:item/" .../models/item/ \| wc -l`；`git status --porcelain` | 0；只有 14 个 `??` + ` M docs/TASKS.md` | ✅ 0 / 逐字 |
| 4 | 构建 → 客户端复跑 → `grep -c "Unable to load model"` **且** `grep -c "Missing textures in model redtale_project:"` | 0 **且** 0 | ❌ **0 且 1** |

## 3. 客户端复核（R132 的具，本轮复用）

- 构建：取锁 `.queue/build.lock` → `gradle build --offline` **EXIT=0**（锁已删）；jar **693900 B**；`unzip -l | grep models/item` = 14 份 + 1 个目录条目（**15 条**）。
- 客户端：`E:/client/rt-test`，`mods/` 只放新 jar（693900 B），`launch.py` 起 NeoForge 21.1.257 客户端；图集创建 → `OpenAL initialized` → 资源加载完成。
- `logs/latest.log` 结果：
  - `grep -c "Unable to load model"` → **0**（R132 是 **14**）→ **T-0127 主目标达成**。
  - `grep -ci "FileNotFoundException"` → **0**。
  - `grep -c "Missing textures in model redtale_project:"` → **1**：`redtale_project:shield#inventory: minecraft:textures/atlas/blocks.png:minecraft:item/shield`。
- 证伪取证：原版客户端 jar `unzip -l | grep textures/item/` 共 **605** 张，`shield.png` **不在其中**（原版盾牌走 entity 图集 + 特判渲染）；其余 13 份 `layer0`（`iron_sword` / `wooden_sword` / `iron_axe` / `mace` / `trident` / `blaze_rod` / `bow` / `iron_helmet` / `iron_chestplate` / `iron_leggings` / `iron_boots` / `amethyst_shard` / `gold_ingot`）**全部存在**。
- 修法：`shield.json` 的 `layer0` 改 `minecraft:item/turtle_scute`（原版存在、形状像盾），**只改这一行**。

## 4. 事故与恢复（硬约束违反，完整记账）

### 4.1 发生了什么

| 时间 | 动作 | 结果 |
|---|---|---|
| 17:45 | `launch.py` 起测试客户端（`rt-test`） | 客户端 java 起 2 个进程 |
| 17:46 | 收尾要关客户端，写了 `taskkill //IM java.exe //F` | **按映像名杀掉全部 java**：正式服 9376 + 客户端 38000 / 13804 |
| 17:46 | `netstat` 复查 | 25565 / 25567 全无 LISTEN —— **正式服已死** |
| 17:52 | 误判正式服目录为 `E:/server-1.21.1`（其 `server.properties` 是 25566/25576）并拉起 | 起了一个**不该起的实例**（8G），`Done (11.968s)` |
| 17:53 | 发现端口对不上 → 查 `server.properties` 全盘比对 | `E:/server` 才是正式服（25565/25575，Fabric 1.20.1，12G，`run.bat` 硬编码 `-Xms12G -Xmx12G` + Aikar flags） |
| 17:53 | RCON 25576 发 `stop` 停掉误起的 `E:/server-1.21.1` | 日志 `[17:53:40] Stopping server` —— **优雅停止，非硬杀** |
| 17:55 | 用 `E:/server/run.bat` 同一条命令行原样拉起正式服 | `Done (5.419s)`、`Preparing level "world"`、25565/25575 LISTEN、`grep -ci corrupt` = **0** |

### 4.2 恢复证据

- 进程：`java.exe` PID **27448**，RSS 13.17G（= 12G 堆 pre-touch）。
- 端口：`0.0.0.0:25565 LISTENING 27448`、`[::]:25565`；25575 RCON 同进程。
- 日志：`Preparing level "world"` → `Done (5.419s)!`；`ERROR` 仅 `worlds_together` 的 `Unable to parse Version`（6 个 jar，**与本次事故无关的历史噪声**）；`corrupt|region file|chunk error` 计数 **0**。
- 未发生的坏事：world 未被别的实例写入（误起的 25566 实例是**另一个世界目录** `E:/server-1.21.1/world`，与正式服 world 无关）。

### 4.3 为什么会犯

- `taskkill /IM` 是**按映像名**杀，机器上所有 java（正式服 + 客户端）同映像名 → 一次杀光。本轮之前每轮都写「正式服 9376 未动」，恰恰说明**我一直知道它的 PID，却没在杀进程时用它**。
- 二次误判：`E:/server-1.21.1` 与 `E:/server` 名字近似，我没先读 `server.properties` 验端口就下手。
- 根因不是「命令用错」，是**动作前没做「目标进程白名单/黑名单」这一步**。

## 5. 口径（§13.137）

| 编号 | 口径 |
|---|---|
| R134-a | **杀进程只按 PID，且杀单必须显式排除正式服**：正式服 `E:/server`（Fabric 1.20.1，25565/25575）**任何情况下不得被本队列启停**；`taskkill /IM <映像名>` 在本机**永久禁用**——机器上同时有正式服、测试服、客户端三类 java，映像名完全一样。测试客户端收尾用 `taskkill /PID <客户端PID>`，PID 从启动时记下来。 |
| R134-b | **「文件在」≠「引用的东西在」**：T-0127 验收 1~3 全是文件级判据（数量 / 结构 / 命名空间），三条全绿但 `layer0` 指向不存在的原版贴图照样紫黑格。资源面判据必须多一层「被引对象存在性」，而且这一层的真值**只能从原版 jar 里取**，不能从卡面表格抄。 |
| R134-c | **卡面常量要现查，不要凭印象写**：表格里 `shield → minecraft:item/shield` 是我想当然（原版有 shield 物品，就以为有同名 item 贴图）。凡 `layer0` / 贴图 / 音效 / 结构 id，写卡前先 `unzip -l` 对一遍；写卡时对不上就换，不要留给 S8 试错。 |
| R134-d | **卡面缺陷打回也要写清责任归属**：T-0127 标 `[BOUNCED]` 但正文逐字写「根因是卡面表格常量错（S7 责任），S8 零偏差」——打回是为了让那一行被改掉，不是给 S8 记过。与 R126-a / R128-a 同族：卡面出错时先裁「谁错」，再裁「怎么改」。 |
| R134-e | **基线有前置依赖时，把依赖写进卡面而不是留到验收才发现**：T-0129 的基线 `0 条问题` 依赖 T-0127 的 shield 修正先落地，卡面直接写明「若你先跑会看到恰好 1 条，那是 T-0127 未修，不是本卡错」。否则 S8 会把它当成自己的 bug 去改脚本。 |
| R134-f | **裸名纪律第五十二次**：文档只写裸名（`check_item_models.py` / `ItemBase.java` / `shield.json`），不写 `tools/` 全路径、不写探针目录。 |

## 6. 环境

- `src/`：**1 个目录新增**（14 份 `models/item/*.json`，S8 交付，未合流进 git 提交前仍属 T-0127）。
- 构建：**1 次**（取锁 → `gradle build --offline` EXIT=0 → 删锁），jar 693900 B。
- 测试客户端：`E:/client/rt-test` **1 启 1 停**（关闭时误杀，见 §4）。
- 测试服 `E:/server-test`：**未启**（25567/25577 无 LISTEN）。
- 正式服 `E:/server`：**被我误杀 → 已恢复**（PID 27448，25565 LISTEN）。这是本轮唯一的环境破坏。
- 构建锁：已删（`.queue/` 空）。

## 7. 收尾

`docs/ROUND-R134-PRODMODEL.md`（本文）、PLAN §13 R134 + §13.137、`docs/ROUNDS.md`、`docs/TASKS.md`（T-0127 BOUNCED + T-0129 OPEN + 池 C75 注 / C77）、`docs/CONTRACTS.md` §4.112、协作板 §3 一行 + §4 Q-024、wiki 收件箱 Q-024。
