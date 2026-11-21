# R93 · 2026-11-21 10:1x · 验 T-0088 + check_all 36→37 + 派 T-0089（宝石类型孔位可达性）

## 1. 验收 T-0088 `check_enchant_exclusive.py`（3/3 + S7 边界 18/18 + 读失败/排序 3 形态）→ VERIFIED

- 验收1 基线：`python tools/check_enchant_exclusive.py` → `共 2 份附魔互斥引用表 · 0 条问题` EXIT=0；`-v` → `已检查 2 条 exclusive_set 引用 · 2 份附魔配置`。
- 验收2 六夹具（各自从干净目录重来）逐字：A `redtale_projce` → 1 条 `命名空间未知：redtale_projce` EXIT=1 · B 自家 ns=0 · C `minecraft`=0 · D `#redtale_project:` → 1 条 `路径非法` EXIT=1 · E `exclusive_set: 5` → 1 条 `必须是字符串` EXIT=1 · F 无字段=0（`-v` 为 `0 条引用 / 1 份配置`）。六组全对。
- 验收3 静态：wc=143≤200、`def check_ref`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条 `enchantments 目录读取失败` EXIT=1 不抛栈；临时夹具目录已清。
- S7 独立边界 18/18：`check_ref` 的 None / 非字符串 / 无冒号 / 未知 ns / 空 path / `..` / 合法 tag / 单条 id / 空白与中段 `#` / 大小写命名等十类形态；`read_configs` 缺目录返 `None`、坏文件 2 条不抛栈、文件名升序。S8 自测数字与 S7 复跑逐字一致，不打回。

## 2. 接线 `check_all` 36→37

- `tools/check_all.py` TOOLS 末尾追加 `check_enchant_exclusive.py`（R93）。
- 结果：`共 37 个脚本 · 跑成 37 · 跳过 0 · 红 0`。全套门禁：check_checkall / check_tasks（89 卡在制品 1）/ check_contracts（53 条）/ check_docs（108 份 452 引用）/ check_rounds / check_ownership 全 0 问题。

## 3. 派 T-0089 `check_gem_types.py`（单文件零接线，在制品 1/3）

- 真洞定位（36 把尺子都不核类型值）：
  - 配置：`items/gem_ruby.json5` 的 `gem.type` = `offensive`、`items/gem_sapphire.json5` = `defensive`；`items/demo_longsword.json5` 的 `sockets.types` = `["offensive", "any"]`。
  - `SocketSpec.java:26-28` 的 `Layout` 注释明写「孔类型词汇开放（不做枚举校验）」；`:97-122` `types()` 对字符串只 `toLowerCase()`、非字符串才换 `any` + WARN；`:124-134` `type()` 只 `trim + toLowerCase()`，拼错一字不告警。
  - `Socketer.java:142-146` `accepts()` 是唯一匹配规则：任一侧 `any` 才放行，否则字符串严格相等 → 拼错即 `TYPE_MISMATCH`；玩家侧只有一次操作回显，reload 零告警、日志零痕迹。
  - `check_items.py:57-88` `check_item()` 只核 `sockets.types` 元素是字符串、gem 段只核 stats/tier，从不进值域。
- 定尺原则：核**开放词汇跨字段可达性**，不写死枚举白名单——否则尺子自己违反 `SocketSpec` 设计（R93-b）。判据：每个非 `any` 宝石类型至少有一个孔类型同值或为 `any`；`count>0` 但无 `types` 按运行期补位语义视作 `any`。
- 基线 S7 离线按接口跑判定逻辑：7 份物品 / 2 种宝石类型（`offensive`、`defensive`）/ 2 种孔类型（`offensive`、`any`）/ 0 不可达。
- 接口 `ANY` / `read_items` / `gem_types` / `socket_types` / `unreachable` / `main` 由 S7 定死，六夹具 A~F，零接线（S7 下轮接 check_all 36→38）。派单当轮文档在建脚本一律裸名 `check_gem_types.py`，不写全路径（R63-e 第十一次）。

## 4. S7 最小一步 = 派前全类排除留证（R88-c/R89-c/R90-c/R91-b 延续）

派前逐条排除（Explore 只读全量扫描 36 份 json5 + 相关 Java + 36 个 check 交叉核）：

| 候选 | 结论 |
|---|---|
| `spawn_rules.match[]` | 引用是原版实体 id/tag，离线无注册表 → 排除 |
| `repair.targets[]` | 真洞（解析只验形态、运行期 `Repairer.matches()` 恒 false → `TARGET_MISMATCH` 无日志），但默认空、基线 0 引用 → 按 R91-b 留池 |
| `skill_trees.nodes[].effects` 的 `passive.id` | 只核非空，但 `TreeUnlocks.passives()` 全仓无调用者 → 死代码不算真洞（R92-b）→ 排除 |
| 附魔 `scripted.args` ↔ 脚本硬编码数值 | 现象真实（日志显示新值、玩法用旧值），但键名映射 / 是否改成 `%var%` 引用属内容契约，S7 未定默认 → 留池，不让 S8 发明接口 |
| 附魔 `weight` / `anvil_cost` | 透传原版注册表，离线无法证明原版不夹，R86-b「先证无夹」不过 → 排除 |
| `gem.type` / `sockets.types[]` | 运行期明说不做枚举校验、有 3 份默认文件真实引用、拼错即失效 → **本轮落卡** |

## 5. 环境 / 收工

- 本轮 `src/` 零 diff、纯 Python；未构建、未启停任何服、无构建锁；正式服 25565/25575（PID 9376）常驻未动、测试服 25567/25577 无 LISTEN。
- 卡板 89 张 / 在制品 1；check_all 37/37；check_docs 108 份 452 引用 0 问题。
- 留下：`docs/ROUND-R93-GEMTYPES.md`、`PLAN.md` §13 R93 + §13.96、`docs/TASKS.md`（T-0088 VERIFIED + T-0089 OPEN + 池 C39）、`docs/CONTRACTS.md` §4.52/§4.53、`docs/ROUNDS.md`、`tools/check_all.py`、协作板 §3。
