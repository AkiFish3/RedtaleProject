# R122 · 验收 T-0116 名称字段静默改写 + check_all 64→65 + 派 T-0117（孔位整段丢弃静默早退）

- 时间：2026-11-24 07:1x（S7 移植工程队列）
- 里程碑：M3 / M7 内容安全网持续加厚（对账网第 48 例）

## 1. 验收 T-0116 `check_name_coercions.py`

卡面三条全部机器复跑通过，S8 零偏差不打回：

- 基线 `共 1 份名称静默改写口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 7 份物品 · 7 处物品 name · 1 份技能树 · 1 处树 name · 10 处节点 name · 3 个静默改写落点`。
- S7 独立六夹具 A~F 逐字 6/6，EXIT=0/1/1/1/1/0：B items `name:123`、C 树顶层 `name:true`、D 节点数字、E `{}`+`[]`+`false` 三条按 `sorted`、F 合法串/缺 name/非 dict 跳过。
- 静态：wc=151 ≤200、`def check_names`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 双目录各 1 条读失败、末行 2 条问题 EXIT=1 不抛栈；函数边界 24 坏值形态（8 值 × 3 落点）+ 10 跳过形态 + 重复两条不合并 + `sorted`。
- 卡板 T-0116 `[DONE]` → `[VERIFIED]`；接线 `check_all` 64→**65**，接线后 `共 65 个脚本 · 跑成 65 · 跳过 0 · 红 0`、`check_checkall` 0 条问题。
- S7 探针两处自错自改，不打回 S8：①夹具 JSON5 裸键间漏逗号导致解析失败（首轮 B~E 全绿是探针坏，不是尺子坏）；②把空白串 `'   '` 误列坏值，卡面明确「不核 name 空白串、留池」，实现跳过正确。探针已清。

## 2. 本轮 S7 最小一步：全类排除 + 源码逐字 + 干净副本反事实（同出口异落点升级派单）

- 派前只读全类扫描 142 Java / 约 60 把尺 / 36 份默认 JSON5。已覆盖项逐条排除：mobs level/scale/skill chance/cooldown、drops rolls/xp/min/max/chance、affix weight/item_level、item durability/use ints/name、tree ppl/icon/name、profession base、damage_aoe args、enchant exclusivity/args sync、damage types/equipment slots 等；有 WARN 出口（`consume` / `repair.mode` / `SkillSpec` 冷却等）、空基线（`strip_vanilla_effects` / `repair.cost.amount`）、S7 独占区（`core/attr/` / `core/math/Settle.java` / `core/damage/` / 注册表 / mixin）均不派。
- 真洞逐字：`SocketSpec.java:54-57` `int count = count(...); if (count == 0) return Layout.EMPTY;` —— 早退在 `types(...)` 之前，显式 `types` 一个不读；`:78-95` 缺 count 直接 `return 0`（`:80`），显式 `0` 避开 `<0`（`:86-89`）与 `>6`（`:90-93`）两个 WARN 分支；`:30` `EMPTY = new Layout(0, List.of())`。对照同文件六处 WARN（`:82/:87/:91/:104/:109/:117`）反证该出口故意静默。后果：`Layout.count()==0` → 无孔，但 `gem` 段仍进 `ContentData.gemStats`，「有宝石数值、无孔可镶」且日志无痕。
- 旧尺作用域逐把核对：`check_items.py:77-82` 把 0 当合法下界；`check_socket_fallbacks.py:103-105` 判据写死 `0 < count`，管 `:120` 短补不管 `:56` 早退；`check_gem_types.py:103-112` 收 types 进可达集合、count==0 且 types 存在时直接 `continue`。全 tools 仅此四把读 `sockets`。
- 反事实：`shutil.copytree` 全份默认配置到 `build/socketdiscard_s7/`，把 `demo_longsword.json5` 的 `count: 2` 改 `0`，三把旧尺 EXIT/末行逐字不变（全绿 `0 条问题`）；副本已清，`src/` 零 diff（对账网第 48 例）。基线 7 物品 / 1 sockets / 2 项 types / 0 坏形态逐字核对。
- 结论：C69 四判据齐，按 R120-a「同族多点一次收口」的反面——**同解析器不同出口分卡**：T-0115 管短补/空白回落，本轮新卡只管 `:56` 整段早退，接口由 S7 定死（`_loads` / `read_items` / `check_discards` / `main`），六夹具 + 函数边界。

## 3. 口径（§13.125）

| 编号 | 结论 |
|---|---|
| R122-a | **同出口不同落点分卡**：T-0115 与 T-0117 同在 `SocketSpec`、同读 `items/`，但 `:56` 早退与 `:120` 短补是不同出口、不同判据、不同文案，不合并成一把超 200 行的尺。 |
| R122-b | **合法下界不等于已覆盖**：`check_items.py` 把 `count=0` 放行是因为 0 合法，不是因为验证过「types 是否被吞」；判据要盯「解析后还剩下什么」。 |
| R122-c | **夹具目录语义先疑自己**：旧尺 `--dir` 指默认配置根目录而非 `items/`，首轮探针传错目录造出 1 条读失败，按 R65-a 修正后才判旧尺全绿。 |
| R122-d | **探针坏值要与卡面「不要做」对照**：空白串 name 属明确留池，不得塞进坏值表；先读卡面边界再写断言。 |
| R122-e | **裸名纪律第四十次**：派单当轮文档与台账在建脚本只写裸名 `check_socket_discards.py`。 |

## 4. 环境

- `src/` 零 diff、纯 Python 操作、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服 25567/25577 无 LISTEN。
- 七门禁全 0；check_all 65 全绿。
