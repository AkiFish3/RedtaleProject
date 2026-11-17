# R34 · `stun` 原语（M5-C 控件第 3 条）+ 验收 T-0028 + 派 T-0030

> 2026-11-17 15:14–15:40 · S7（派单 + 验收 + 自研）· 测试服起停 3 次，均已停（25567 / 25577 无 LISTEN）

## 1. 卡板

| 动作 | 卡 | 结果 |
|---|---|---|
| 验收 | **T-0028** `tools/check_defaults.py` | `[DONE]` → **`[VERIFIED]`**，3/3（另补 exit 2 与反向分支） |
| 派单 | **T-0030** `mobs/MobSpec` v1（M6 第一层解析器） | 新建卡，`[OPEN]` |
| 在制品 | T-0029（ManaPool，14:05 派，仍 `OPEN`）+ T-0030 | **2/3** |

### 1.1 T-0028 验收（3/3）

| # | 命令 | 实测 |
|---|---|---|
| 1 | `python tools/check_defaults.py; echo EXIT=$?` | `清单 21 条 · 磁盘 21 个 · 问题 0 条` · `EXIT=0` |
| 2 | 临时插 `"items/__probe__.json5"` | `[仅清单有] items/__probe__.json5` + `问题 1 条` + `EXIT=1`；还原后 `EXIT=0`、`git diff --stat ConfigStore.java` 空 |
| 3 | 移走真实 `crit.json5` | `[仅清单有] crit.json5` + `EXIT=1`（**卡面写「仅磁盘有」是我写错**，语义上就是「仅清单有」，采纳 S8 偏差说明） |

补测（S8 未贴的两支）：

- 多放一份未登记 `crit_extra.json5` → `[仅磁盘有] crit_extra.json5` + `EXIT=1`；还原后 `EXIT=0`、`git status --short src/main/resources` 空。
- 沙箱（只有 `tools/` 的空目录）→ `找不到 src/main/java/.../ConfigStore.java` + `EXIT=2`，无 traceback。
- 脚本 81 行、零第三方依赖、只读、`os.chdir` 自定位（从 `/tmp` 调用同样 21/21）。

## 2. 自研：`stun` 原语

新增 `core/script/builtin/StunEffect.java`（约 220 行）+ `Builtins` 1 行 + `GameHooks` 2 处（tick 轮询、`EntityJoinLevelEvent` 兜底）。

| 设计点 | 做法 | 理由 |
|---|---|---|
| 定住手段 | `Mob#setNoAi(true)` + `Navigation#stop()` + 水平速度归零 | 1.21.1 没有「禁移动」接口；`slowness 255` 是速度乘数，会被装备/药水/第三方 modifier 抵消「慢慢挪」，且怪照样能打人 |
| 玩家退路 | `slowness` 7 级（`amplifier 6`） | 仅对非 `Mob` 活体生效，日志标注「退路」——不是等价替代 |
| 在册标记 | scoreboard tag `redtale_stunned` | 1.21.1 **已无** `Entity#getPersistentData()`（编译期实证）；tag 随 NBT 保存且 `/data get entity … Tags` 可读，是唯一能区分「本来没 AI」与「被我们定住」的凭据 |
| 恢复 | `GameHooks#onServerTickPost` → `StunEffect.tick()`，按截止 `gameTime` 轮询（空闲 `isEmpty` 一次返回） | 延时任务在关服/`reload` 会丢 → 怪永久定住 |
| 重启残留 | `EntityJoinLevelEvent` → 带 tag 的实体入世即 `setNoAi(false)` + 清 tag | 关服时正在挨打的怪，下次上线不该变木桩 |

## 3. 测试服实测（3 次启动，均已停）

夹具：`scripts/enchants_soul_reap.json5` 追加一条 `stun`（`target: target` / `radius: 3` / `duration_ticks: 120`），
走「尸壳持 `redtale_project:soul_reap` 铁剑 → `post_attack` → 脚本 → `stun`」。平台 `fill` 到 y=99，实体站 y=100。

| 组 | 场景 | 实测 |
|---|---|---|
| A | 尸壳 + 铁傀儡（受害者）+ 牛（旁观者 `NoAI:1b`，距受害者 1.5 格） | 日志 `[script] stun · Iron Golem 120t` + `stun · Cow 120t`；`Tags` 两者都是 `["redtale_stunned"]`；**尸壳无 tag**（施法者被排除） |
| B | 只留目标 | 日志 `stun · 半径 3.0 内只有目标本身`（边界分支） |
| C | 到期（杀尸壳后 8s） | 日志 `stun 解除 · Cow（到点）` / `stun 解除 · Iron Golem（到点）`；`Tags` 变空；`NoAI` 保持原值 |

**本轮抓到并修掉的两个 bug（都只有实测能暴露）**：

1. **施法者被自己的控场圈定住**：第一版 `radius` 分支不排除施法者 → 尸壳打一拳把自己定住 6 秒，现象是「怪打一下站住不动」。
   修法：`casterOf(ctx)`（先 `vars.attacker`，再 `player()`）加入排除条件。
2. **续定覆盖 `wasNoAi`**：连击会反复重入 `freeze`，每次都读当前 `isNoAi()` → 第二次读到的是「已被我们置 true」，
   到点恢复时把怪**永久定住**（实测铁傀儡解除后 `NoAI` 仍是 `1b`）。修法：在册记录存在时沿用首次的 `wasNoAi`，只推截止时刻。

## 4. 数字

| 项 | 值 |
|---|---|
| `gradle build --offline` | exit 0，jar **420488 B**（R33 为 413740 B） |
| `gradle selftest --offline` | **78 PASS**（`stun` 无离线可测面，断言数不变） |
| jar 条目 | `script/builtin/StunEffect` ×2（主类 + `$Frozen` record） |
| `grep -c "^\| [0-9]" docs/SKILLS.md` | **24**（预算不变），第 7 行 `—` → `✅ R34` |
| 原语现状 | 8 / 24（`heal` / `damage` / `damage_aoe` / `buff` / `debuff` / `knockback` / `pull` / `stun`） |

## 5. 未验证 / 挂账

1. **玩家被定住**：无客户端，`slowness 7 级` 退路只走代码路径，未实测（退路本身不是等价替代，已写进 CONTRACTS §4.20）。
2. **重启残留清理**：夹具里的实体在停止服务器那一刻已不在定住状态（回合间隔内到期），`onEntityJoin` 分支未拿到日志证据 → 挂账。
3. **「原本 AI 开启的怪恢复后回到 AI 开启」**：最后一次夹具里两只牛混在一起，读数被污染，未干净分离；代码路径与「原本 NoAI 的怪保持 NoAI」同一行（`f.wasNoAi()`），风险低但**仍记为未验证**。
4. 老挂账不变：V4「附魔台真刷出」、tooltip 真人客户端、pvp 真目标（第 5 / 15 / 30 轮同项）。

## 6. 夹具状态

- 测试服 `config/redtale_project/scripts/enchants_soul_reap.json5` **已还原成仓库默认件**（无 `stun`）。
- 平台与实体已清理（`fill air` + `kill @e[type=!player,distance=..300]`），测试服已停（25567 / 25577 无 LISTEN）。
- **测试服地形坑（新记）**：`E:/server-test` 世界在 x≈0 一带地表约 y≈60，y=48 以下是实心；
  随机挑 y  summon 会让实体卡在方块里被顶到地表（表现为「自己往上飘」）。**立靶统一先 `fill` 一块 y=99 的空中平台再站 y=100**。
