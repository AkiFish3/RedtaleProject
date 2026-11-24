# R124 · 2026-11-24 16:2x · 回收 T-0118 自做并验收（刷怪整数静默饱和）+ check_all 66→67 + 派 T-0119（两只默认怪）

## 1. 本轮判定：S8 静默 → 收回 T-0118

三条独立证据（R124-a）：

| 证据 | 结果 |
|---|---|
| 工具目录最后写入时间 | 08:52（本队 S7 自己接 `check_all`），此后 7.5 小时零新增 |
| 协作板 §3 末条 S8 记录 | 07:2x T-0117 交付，之后再无 S8 行 |
| `git log` 末条 | 仍为 R123（T-0117 验收 + 派 T-0118） |

T-0118 卡自 08:4x 派单起停在 `[OPEN]`、进度行空 8 小时，形态与 B-01（T-0019/T-0020 连挂两轮）一致 → 按先例收回自做，**不重派同内容卡**（§13.30 R27-d：重派会让 S8 回来改 S7 刚写完的文件）。

## 2. T-0118 自做与自验（3/3）

产物：`check_spawn_int_saturation.py`（150 行，新建，零接线）。接口严格照卡面：`INT_MIN` / `INT_MAX` 常量、`_loads`（JSON5 shim：去注释 + 单引号归一 + 裸键加引号 + 去尾逗号）、`read_rules`（`(rows, ok)`，目录不可读返 `([], False)`）、`_saturates`、`check_rules`（`sorted`、保稳定、不去重）、`main`（`--dir` / `-v`）。

| 验收 | 结果 |
|---|---|
| 1 基线 | `共 1 份刷怪整数饱和口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 1 份规则 · 2 处整数落点 · 0 个静默饱和` |
| 2 六夹具 A~F | 逐字 EXIT=0/1/1/1/0/1：B `priority 值 2147483648`、C `per_chunk_cap 值 -2147483649`、D `priority 值 1000000000000.0`（`1e12` 解析成 float）、E 边界合法 + 小数跳过 0 条、F 三文件三报文按 `sorted` 顺序 |
| 3 静态 + 函数边界 | wc=150 ≤200、`def check_rules`=1、禁用 grep=0、`py_compile` OK、单目录 NO_SUCH 恰好 1 条读失败 EXIT=1 不抛栈；`_saturates` 三真 / 十假（`2147483647` / `-2147483648` / `0` / `1.6` / `True` / `False` / 字符串 / `None` / `nan` / `inf`）；`check_rules` 缺键跳过、重复两条不合并、输出 `sorted` |

接线 `check_all` 66→**67**（`共 67 个脚本 · 跑成 67 · 跳过 0 · 红 0`）。

## 3. 派 T-0119：两只默认怪（内容面）

`MobSpec.BASES` 冻结 8 原型，默认内容只覆盖 `caster`（`ash_caster`）与 `elite`（`ember_guard`）。本卡补 `melee`（`stone_footman`）与 `ranged`（`frost_archer`）两只，纯 JSON5、零 Java（D17）。

派前预验（R124-b，离线副本，跑完即删）：`check_mobs` 6 文件 0 问题、`check_equipment_slots --mobs -v` 4 怪 / 7 引用 / 14 基底 0 问题、`check_mob_skills -v` 4 怪 / 4 条 id / 5 引用 0 问题。属性键只用 `AttributeSpecs` 已注册键（`max_health` / `attack_damage` / `armor` / `movement_speed` / `projectile_damage`），`drops` 段留空（默认表只有 `ember_guard`，写不存在的表会被 `check_mobs` 判红），不写 `undead`（Q-020 按原型打 tag，会污染整个原型）。

## 4. 口径（详见 PLAN §13.127）

- **R124-a** 静默回收要看三条独立证据，别看单卡进度行。
- **R124-b** 内容卡也要派前预验：S7 先跑绿整套在役尺，再逐字写进卡面。
- **R124-c** 内容卡明令禁 gradle：纯 JSON5 不进编译路径，S8 取锁只会挡 S7 的真构建。
- **R124-d** 原型 tag 不能顺手加：`undead: true` 落到整个 `base`。
- **R124-e** 裸名纪律第四十二次。

## 5. 环境

`src/` 零 diff、纯 Python、未构建、未取构建锁、未启停任何服；正式服 9376 未动、测试服 25567/25577 无 LISTEN。

## 6. 下轮

1. 验 T-0119（三条命令 + 文件名 = id）；过后池 C71 续派 `summoner` / `charger`。
2. T-0119 若仍无人领（S8 连续静默 2 轮）→ 自做两个 JSON5（已预验，10 分钟量级），不重派。
3. Q-021 / Q-022 / Q-023 无答复按默认走；真人窗口 A~F 等 Aki。
