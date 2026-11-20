# R85 · 2026-11-21 01:1x · 验 T-0080 + check_all 28→29 + 派 T-0081（技能 conditions args 值域对账）

## 本轮结论

- **验收 T-0080（`tools/check_spawn_conditions.py`）→ VERIFIED**：3/3 全过。
  - 验收 1 基线：`python tools/check_spawn_conditions.py` → 末行 `共 1 份刷怪规则条件表 · 0 条问题` EXIT=0；`-v` 含 `已检查 1 份规则 · 7 个条件维度`。
  - 验收 2 六组夹具从干净目录各自重来：A `time:"nite"`→`time 非法值：nite`、B `difficulty:["normal","insane"]`→`difficulty 非法值：insane`、C `light:{min:3,max:20}`→`light 越界（0~15）`、D `outside:1`→`outside 必须是布尔值`、E `biome:"minecraft:plains"`→`biome 必须是非空字符串数组`、F 未知键 `fog:3`→`未知 conditions 维度：fog`，各恰好 1 条逐字命中 EXIT=1，用完即删。
  - 验收 3 静态：wc=145≤200、`def check_conditions`=1、禁用 grep=0、`py_compile` OK、`--rules NO_SUCH` 恰好 1 条 `刷怪规则目录读取失败` EXIT=1 不抛栈。
  - S8 卡内五处口径（①difficulty 逐条报/形状一条 ②light 越界优先于 min>max ③空数组合法/空字符串非法 ④目录预检 ⑤单引号 shim）全属卡面接口措辞内的合理实现，按 R84-a 采纳不打回（§13.88 R85-a~c）。
- **接线 `check_all` 28→29**：`tools/check_all.py` 末尾追加 `check_spawn_conditions.py`，`共 29 个脚本 · 跑成 29 · 跳过 0 · 红 0`；`check_checkall -v` 29/29。
- **派 T-0081（新建 `check_skill_args.py`）**：单文件零接线，核 `skills/*.json5` 的 `conditions[].args` 值域；接口 `read_skills`/`check_args`/`main` 由 S7 定死，六组夹具 A~F 逐字，基线 4 技能/0 问题（S7 已跑判定逻辑核过）。在制品 1/3。

## 真洞核实（本轮 S7 最小一步）

按 R84-b「派卡前先核覆盖面」，逐个查已有 check 的属性/值域核对象：

- `check_items._stats` 核 items/affixes/pools/sets 的属性 id 归一 → 已覆盖。
- `check_progress.check_file` 核 levels/jobs 的 `attributes{}` → 已覆盖。
- `check_mobs.check_drop` 核 drop 的 `chance∈[0,1]`、`rolls≥1`、`min<=max` → 已覆盖。
- `check_skills.check_skill` 核 `conditions[i].type` 是否合法、`args` 是否对象，**从不核 args 取值** → 真洞。

运行期证据：`HealthBelowCondition.test` 用 `args.get("ratio").getAsDouble()` 直接与 `getHealth()/getMaxHealth()` 比，`ratio:1.5` 恒真；`ChanceCondition.test` 里 `chance>=1.0` 直接 return true、`chance<=0.0` 直接 return false，`chance:-0.2` 恒假。两者都无值域夹、无 WARN，编译绿、全 check 绿、技能触发条件永久失效——符合 R83-b/R84-c「玩法悄悄退化且无人被叫醒」判据。

排除项：effects 的 `amount`/`radius`/`falloff` 在 `DamageAoeEffect`/`KnockbackEffect` 里有 `clamp01`/`Math.min(MAX_RADIUS)` 运行期夹，写歪被收敛、非静默洞（R85-d），卡面明确不核。

## 环境

- `src/` 零 diff、未构建、未启停任何服、无构建锁；25567/25577/9376 无 LISTEN。纯 Python 轮次。
- `check_all` 29/29、`check_tasks` 81 卡在制品 1 问题 0、`check_ownership` 0、`check_docs` 100 份 408 引用 0 问题、`check_contracts` 46 条 0 问题、`check_checkall` 29/29。

## 下一步（R86）

1. 验 T-0081（过后 `check_all` 29→30）。
2. 对账网已第十二例，继续按 R80-b/R81-b/R82-b/R83-a/R84-b/R85-d 判据找真洞；无真洞转内容面，不硬凑。
3. Q-021（respec 免费）/ Q-022（原语按需补 13/25）无答复，继续按默认走；真人窗口 A~G 等 Aki。
