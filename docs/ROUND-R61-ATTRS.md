# R61 · 验收 T-0056（`check_attrs.py`）+ 接线 `check_all`（10→11）+ 派 T-0057

日期：2026-11-19 06:28–06:5x · 里程碑：M0/M7（工程面收口：属性注册表对齐、数值面安全网）

本轮无 Java 改动（`src/` 零 diff）→ **未构建、未启测试服**（正式服 / 测试服整轮未动）。改动全在 `tools/` 与文档。

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0056 `tools/check_attrs.py` | 验收 | `[DONE]` → **`[VERIFIED]`**（3/3 + 边界 4 组） |
| T-0057 `tools/check_numeric.py` | 派单 | `[OPEN]`（在制品 1/3） |

T-0056 三条验收：

1. `python tools/check_attrs.py` → `EXIT=0`，末行逐字 `共 3 份来源 · 0 条问题`
2. 五组独立负向夹具（三份来源各复制到临时目录，**每组只改一处**）→ 全部 `EXIT=1`
3. `wc -l`=**150** ≤200 / `grep -c "def clean_json5"`=**0** / `check_all.py` 当时未被碰（仍 `共 10 个脚本 · 红 0`）

## 2. 五组负向夹具（逐组独立，不是一次全改）

| # | 改动 | 命中 |
|---|---|---|
| ① | 文档删 `projectile_damage` 一行 | 条数不一致 79/80/80 · 段落 A 计数 12/11 · 代码有文档无 · 顺序第 5 位 |
| ② | 代码 `physical_damage` → `physical_damagX` | 文档有代码无 · 代码有文档无 · 落点不一致 · 顺序第 3 位 |
| ③ | 自建 `c(...)` 改成 `v(...)` | `落点不一致：physical_damage（文档 自建 / 代码 v）`，仅 1 条 |
| ④ | 文档同一条 id 复制成两行 | `重复 id：physical_damage（文档 第 2 次）` · 条数 81 · 段落 A 13 |
| ⑤ | `zh_cn` 删 1 条属性键 | `zh_cn 缺属性键：attribute.redtale_project.physical_damage`，仅 1 条 |

边界独立复核 4 组：

- 文档把 `## 3.` 改成 `## 三、` → 首行 `文档缺 §2 或 §3 标题，无法定位属性表`，随后 82 条（代码全成「代码有、文档无」，符合卡面「按代码顺序逐条报」）
- `EXPECTED_COUNT` 改名 → `代码里读不到 EXPECTED_COUNT` + `条数不一致：文档 80 / 代码 80 / EXPECTED_COUNT 缺失`
- `en_us.json` 截断 → **只报 1 条**解析失败，另一份完整，**不连锁误报缺键**
- java 文件缺失 → `代码文件读不到` + 163 条仍带汇总行
- 全复原 → `EXIT=0`

## 3. 已知噪声：`序号不连续` 会级联（不判退）

删 1 行文档 → 之后每行的序号都「不连续」，逐行报 = 76 条，真信号（条数不一致）虽在首行但屏幕被刷满。

判据：卡面第 7 条只写「断 → `[问题] 序号不连续：第 <i> 行是 <实际序号>`」，**没有**写「只报第一处」（第 5 条顺序检查才写了）。S8 逐行实现 = 按卡面原文执行 → **不判退**，记 §13.64 R61-a。

要收敛就另开卡把「只报第一处」写进验收，不在验收时顺手改别人的实现。

## 4. 接线：`check_all.py` 10 → 11

```
$ python tools/check_all.py
...
check_deps.py      OK      共 4 份来源 · 0 条问题
check_attrs.py     OK      共 3 份来源 · 0 条问题

共 11 个脚本 · 跑成 11 · 跳过 0 · 红 0
EXIT=0
```

这是 R60 §13.63「下一步第 1 条」的预判，落地即兑现：**每接一个新对账脚本，末行的三个数就同时 +1**——「跑成数」本身就是覆盖面证据。

## 5. 新卡 T-0057：为什么是数值面

这四份配置（`attack_speed` / `crit` / `damage` / `progression`）在运行期全部「坏值 → WARN → 回落默认」，代码里明写着不抛异常：

- `AttackSpeedRules.parse`：`floor <= 0` → 告警 + 按 1.0 处理（`min_attack_delay_ticks = 0` 是 D19 唯一保留的红线，delay 到 0 → `getAttackCooldownProgress` 除零 → 每 tick 出手）
- `MultiplierRules.parse`：`percent_attributes` 里的 token 走 `AttributeSpecs.resolve`，解析不到就**静默不贡献**——把 `physical_damage` 拼成 `phsical_damage`，玩家伤害掉 100%，只有一条 WARN
- `conditional_attributes` 的键只认 `TargetClass.KEYS`（pvp / pve / undead），写 `boss:` 整段跳过
- `CritRules.parse`：`chance_attribute` / `power_attribute` 是**逻辑 id 字符串**，写错就是恒 0 暴击
- `sanity.hardCeiling` 是 D16 的物理硬顶（1e9），写大了等于没有

编译器不看、`/redtale validate` 不看、reload 只吐 WARN。起飞前对账是唯一能拦住它的位置（§13.64 R61-b）。

卡面写死了三件 S8 容易自由发挥的事：

1. **`attack_speed.json5` 用单引号写键**（`'minecraft:haste': {...}`），共享清洗器 `check_progress.clean_json5` 不吃 → 先做 5 行 `'k':` → `"k":` 预处理，**不许复制第四份清洗器**（现状已有 3 份）
2. **属性 token 只认三形态**（裸名 / `redtale_project:` 前缀 / `minecraft:` 全名），不复制 `AttributeSpecs.resolve` 的点号宽松口径
3. **接线 `check_all.py` 是 S7 在 VERIFIED 之后做的事**，S8 不碰

## 6. 收尾

- 卡板：T-0056 `[VERIFIED]` + T-0057 `[OPEN]`（在制品 1/3）
- 台账：PLAN §13 R61 行 + §13.64（四条口径）+ 下一步 R62
- 协作板 §3 一行（含派单），镜像同步
- 夹具已清（`.tmp_s7_attrfix.py`、`/tmp/s7attr/`）
