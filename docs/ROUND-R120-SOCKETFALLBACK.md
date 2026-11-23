# R120 · 2026-11-24 04:4x · 验 T-0114 + check_all 62→63 + 派 T-0115（宝石/孔位静默回落族）

## 1. 卡板

- T-0114 `check_damage_aoe_args.py` `[DONE]` → **VERIFIED**（S8 零偏差不打回）。
  - 从仓库根跑基线：`共 1 份范围伤害参数口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 4 份技能 · 2 份脚本 · 1 处 damage_aoe 调用 · 3 个值域键`。
  - S7 独立六夹具 A~F 逐字 6/6，EXIT=0/1/1/1/0/1：A 复制默认（0 条、4/2/1）；B `radius:40` 单条夹顶；C `falloff:0` 单条回落；D `scripts/b` max_targets 6.9 + `skills/a` falloff 1.5 两条按文案升序；E 4 技能/0 脚本/3 调用 0 条；F `max_targets:-2` + `falloff:-0.5` 两条、`radius:0` 按 D16 跳过。
  - 静态：wc=200 ≤200、`def check_calls`=1、禁用 grep=0、`py_compile` OK；`--dir NO_SUCH` 双目录各 1 条读失败、共 2 条 EXIT=1 不抛栈。
  - 函数边界 22 形态：radius 40 报/-2 跳、max_targets 6.9 报/0 跳/-0.5 报、falloff 1.5 报/4.0 夹顶、非 dict data/非 list effects/非 dict args/非 damage_aoe 全跳、重复不合并。
  - **口径裁定**：S8 卡进度报「卡面 B/C/D 文案前缀写裸名 `s.json5:`，与接口 §4728/4729 定死 `skills/`/`scripts/` + 文案升序冲突」。按 R104-a「接口公式优先于卡面示例」，S8 按冻结接口实现正确，不打回；卡面示例裸名属描述简写。S7 探针 1 处自错（误把 falloff 4.0 写成"跳过"，实际应报夹顶）自改后全过，不算 S8 偏差。探针 `build/aoeargsfix/` 已清。
  - 已接 `tools/check_all.py`（R120，TOOLS 62→63，全集 `共 63 个脚本 · 跑成 63 · 跳过 0 · 红 0` EXIT=0）。

- **派 T-0115** `check_socket_fallbacks.py`（`items/*.json5` 的 sockets/gem 静默回落族对账，单文件零接线，对账网第四十六例），在制品 1/3。

## 2. S7 最小一步：全类排除留证 + 源码逐字核实 + 反事实

Explore 只读全量扫 142 Java / 60 尺 / 默认配置；按四判据（显式配置形态 / 静默改写零 WARN / 旧尺全绿 / 单文件可验）逐条取证。

- **数值面新洞：未发现**。79 处 `(int)`/`getAsInt()`/`getAsDouble()`/`Math.min/max` 调用逐点排除：
  - 有 WARN 保护（不算洞）：`ProfessionSpec`/`ProgressSpec` 的 max_level/curve/attributes/grants、`SkillTreeSpec` 的 pos/max_points/requires/effects、`SpawnSettings.per_tick_cap`。
  - 已被在役尺覆盖：`SocketSpec.count` 小数 → `check_items.py:78`；`gem.tier` → `check_items.py:67-68`；`nodes[].cost` → `check_trees.py:75-77`；`points_per_level` → `check_tree_ppl.py`。
  - 运行期计算（非配置解析）：`Settle`/`StatValue`/`ManaPool`/`TreePoints`/`AttackSpeedCurve`/`CritRules` 等。
  - D16 有意夹取：掉落 xp 1e6 / count 64 / 负数归零。
  - `respec` 段被忽略是注释明写的有意设计。

- **Top1 落真洞（C68 升级 T-0115）**：`SocketSpec.java` 三落点同源（开放词汇尾出口、零 WARN），逐字：
  1. `:120` `while (out.size() < count) out.add(ANY);` —— types 短补 any；对照 `:116-118` 超长有 WARN。
  2. `:132-133` `trim()` + `value.isEmpty() ? ANY : ...` —— gem.type 空白串回落 any；对照 `:128-130` 非字符串有 WARN。
  3. `:159` `return e.getAsString().trim();` —— gem.exclusive_group 空白串回落空串；对照 `:156` 非字符串有 WARN。下游 `Socketer.java:192` 空串跳过互斥 → 同组宝石可无限镶嵌（玩法面，比纯显示面重）。
  - 常量从源码冻结：`ANY="any"`（`:39`）、`MAX_COUNT=6`（`:37`）。
  - 旧尺零覆盖：`check_items.py:75-82` 只核 count 整数与 types 元素字符串、不比长度、不读 gem.type/exclusive_group；`check_gem_types.py:91-94` 对空白串 `_nonblank()` 跳过、`:106-115` 只收现有元素；全 tools grep `exclusive_group` 零命中。
  - S7 内存反事实三坏值（types 短 / gem.type 空白 / exclusive_group 空白）在 `check_items` + `check_gem_types` 上报错集合与基线逐字 SAME（0→0），判据 3 成立。
  - 基线非空：`items/` 7 份 / sockets 1 处 / gem 2 处 / 0 问题。对账网第 46 例。

- **排除**：C66 三处 `name`（显示面，优先级低，连续留池）；`exclusive_group` 非字符串回落（源码 `:156` 有 WARN，不算洞）；item id / enchant tag / profession grants 注册表真值（离线无真值）。

## 3. 口径 §13.123

- R120-a：**同族多点一次收口**——`SocketSpec` 的 three ANY/空串出口是同一解析器的开放词汇尾出口，合成一卡（单文件、单目录、六夹具），不因"三条"拆三卡。
- R120-b：**玩法面优先于显示面**——exclusive_group 空白串导致互斥失效（可无限镶嵌），比 C66 name 静默成串影响重，插队到 C66 前。
- R120-c：**"types 短补 any"不是 D16 异常保护**——D16 的零 WARN 缺省是数值异常保护；开放词汇数组长度不足补项是内容容错，显式写歪应被尺子报。
- R120-d：**卡面示例裸名从接口**——B/C/D 文案前缀以冻结接口为准；卡面举例的裸文件名是描述简写，S8 按接口实现不算偏差（R104-a 延伸）。
- R120-e：**裸名纪律第三十八次**——派单当轮只写裸名 `check_socket_fallbacks.py`。

## 4. 环境

`src/` 零 diff、纯 Python、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服无 LISTEN。七门禁全 0 问题。

## 5. 收尾 / 下一步

- 收尾：`docs/ROUND-R120-SOCKETFALLBACK.md`、PLAN §13 R120 + §13.123、`docs/ROUNDS.md`、`docs/TASKS.md`（T-0114 VERIFIED + T-0115 OPEN + 池 C68 结项）、`docs/CONTRACTS.md` §4.91/§4.92、`tools/check_all.py`、协作板 §3。
- 下轮：验 T-0115（过后 check_all 63→64）；C66 显示面单卡；六原型默认怪仍属 S7 内容面；Q-021/022/023 无人答、真人 A~F 等 Aki。
