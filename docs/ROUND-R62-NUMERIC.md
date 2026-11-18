# R62 · 验收 `check_numeric` + 契约面立项 + 真人窗口清单

> 2026-11-19 07:3x-08:0x · S7 · 本轮**无 Java 改动**（`src/` 零 diff），未构建、未启测试服。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0057 `tools/check_numeric.py` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + S7 独立 14 组边界） |
| T-0058 `tools/check_contracts.py` | 派单 | `[OPEN]`，在制品 **1/3** |

### T-0057 验收细节

1. 基线：`python tools/check_numeric.py` → `EXIT=0`，末行逐字 `共 4 个文件 · 0 条问题`。
2. 负向：
   - 夹具 A（五项同时坏）：`EXIT=1`，5 条全命中 —— 物理地板 `0.0` / `crit.power_attribute` 拼错 / `percent_attributes` 拼错 /
     `conditional_attributes` 加 `boss` / `hardCeiling=2.0e9`。
   - 夹具 B（截断 `crit.json5` + 地板 0）：`EXIT=1`，得 `解析失败` 与 `attack_speed` 各 1 条 ⇒ **一份坏掉不连带其余三份**。
3. `wc`=178≤200、`grep -c "def clean_json5"`=0、`check_scripts` `EXIT=0`、`py_compile` OK。

S7 另跑 14 组边界（全按卡面文案命中）：缺文件 / `--java` 读不到（只报 1 条并跳过属性解析类检查，物理地板照查）/
`progression` 顶层数组 / haste `per_level=0.1` / `damage_types ["*"]` / `condCount=true` 与 `1.5` / category `type` 重复 /
`mob_exp_multiplier` 2e9·-0.5·`"fast"` / `max_chance=-1` / `effect_attack_speed` 值非对象。

不提退项：`condCount=true` 打印 `True`（Python `str(True)` 而非 `true`）——卡片只钉「必须是 ≥0 的整数」与文案前缀，形态口径未写死，同 R61-a。

## 2. 接线

`tools/check_all.py` 末尾追加 `check_numeric.py`：

```
共 11 个脚本 · 跑成 11 · 跳过 0 · 红 0   →   共 12 个脚本 · 跑成 12 · 跳过 0 · 红 0
```

R61「下一步（R62）」第 1 条的预判当场兑现。

## 3. 新增 `docs/REALPLAYER-WINDOW.md`

动机：**剩下的活全部卡在真人**。本轮盘了一遍：

| 里程碑 | 剩余项 | 为什么不能离线验 |
|---|---|---|
| M4 | V4 附魔台 / V5 铁砧 / V6 砂轮 | 全是 GUI 面，RCON 点不了 |
| M6 | 自然刷怪替换、`per_tick_cap` 行为 | 无玩家在线 `NaturalSpawner` 根本不跑；`fire` 每条命令落不同 tick |
| M7 | `take`/`untake` 玩家路径、升级面板、击杀给经验 | 命令源是控制台，没有 UUID |

清单按「一次上线能连着做」分五组（A 显示 7 / B 刷怪 4 / C 进度技能树 8 / D 附魔 3 / E 战斗 6 = 29 条），
每条给命令原文 + 期望 + 判据来源，并标注哪几条是真正的出口条件。

## 4. 新开 Q-023

D20 写的「客户端 `client.json5` 自行切换」在当前实现下不成立：数值行是**服务端算好写进 lore** 的
（`ContentTooltip` → `ItemFactory`），客户端读不到配置、也改不了已写好的 lore；三个开关 `grep` 全仓 0 命中。
默认走 A（服务端 `display.json5` + reload 生效），已登记 wiki `S7-20261119-073854`。

## 5. 数字

| 项 | 值 |
|---|---|
| `check_all` | `共 12 个脚本 · 跑成 12 · 跳过 0 · 红 0`（EXIT=0） |
| jar / 构建 | 本轮未构建（`src/` 零 diff） |
| 测试服 | 未启停（25567/25577 未动） |
| 构建锁 | 未取（无构建） |
