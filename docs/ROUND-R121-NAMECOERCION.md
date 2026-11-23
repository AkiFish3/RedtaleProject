# R121 · 验收 T-0115 孔位回落族 + check_all 63→64 + 派 T-0116（名称字段静默改写）

- 时间：2026-11-24 05:5x（S7 移植工程队列）
- 里程碑：M3 / M7 内容安全网持续加厚（对账网第 47 例）

## 1. 验收 T-0115 `check_socket_fallbacks.py`

卡面三条全部机器复跑通过，S8 零偏差不打回：

- 基线 `python tools/check_socket_fallbacks.py` → `共 1 份孔位回落口径对账表 · 0 条问题` EXIT=0；`-v` → `已检查 7 份物品 · 1 处 sockets 布局 · 2 处 gem 定义 · 3 个静默回落落点`。
- S7 独立六夹具：A 复制默认 → 0；B types 短 → 1 条具名文案；C gem.type 空白 → 1 条；D exclusive_group 空白 → 1 条；E 合法写法 → 0；F 缺键与非字符串跳过 → 0。逐字 EXIT=0/1/1/1/0/0。
- 静态：wc=147 ≤200、`def check_fallbacks`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 1 条 `[问题] items 目录读取失败` EXIT=1 不抛栈；函数边界 30 形态、重复两条不合并。
- 卡板 T-0115 `[DONE]` → `[VERIFIED]`；接线 `tools/check_all.py` 63→**64**，接线前 `check_checkall` 正确报漏接 1 条、接线后 0 条，`共 64 个脚本 · 跑成 64 · 跳过 0 · 红 0`。

## 2. 本轮 S7 最小一步：全类排除 + 源码逐字 + 干净目录反事实（C66 升级派单）

- 派前复核排除：`check_items.py:126` 的 name 校验在 `check_set()` 内（只管 sets/，不管 items 顶层）；`check_enchants.py:164` 只管附魔名；`check_professions.py:47` 只管专业名；`check_trees.py:45-125` 不读树 name/节点 name；`check_tree_icons.py` 只核 icon。全仓无一把尺覆盖三处 name。
- 真洞逐字：`ItemContent.java:162,240-246` 顶层 name primitive 走 `getAsString()`（数字/bool 静默成串）、null/数组/对象回落 id，零 WARN；`SkillTreeSpec.java:76,145-147` 树顶层 name 非字符串 → null 直进 Definition，零 WARN；`:54-55` 节点 name 非字符串 → 静默回落 `tree.node.<nid>`，零 WARN。
- 反事实：`build/namefix/` 干净副本三处 name 全改 `123`，`check_items` / `check_trees` / `check_numeric` / `check_defaults` 四把旧尺末行逐字全绿 EXIT=0（对账网第 47 例），副本已清。基线 7 物品 / 7 name、1 树 / 1 顶层 + 10 节点 name 逐字核对一致。
- 结论：C66 四判据齐，按 R118-b「显示面单卡、不与 icon 合并」升级为 **T-0116**，接口由 S7 定死（`_loads` / `read_items` / `read_trees` / `check_names` / `main`），六夹具 + 函数边界，单文件零接线。

## 3. 口径（§13.124）

| 编号 | 结论 |
|---|---|
| R121-a | **反事实用干净副本、不改 `src/`**：`shutil.copytree` 到 `build/namefix/` 再改副本，四把旧尺全绿才判真洞；副本用完即删，`src/` 零 diff。 |
| R121-b | **旧尺读同名字段不等于覆盖**：`check_items.py:126` 在 `check_set()` 内、`check_professions.py:47` 只核专业名，不能因全 tools grep 到 `name` 就排除候选；必须看作用域。 |
| R121-c | **显示面同族一次收口**：items 顶层 / 树顶层 / 节点三处 name 同一静默改写族，合成一卡（单文件、双目录、六夹具），不拆三卡。 |
| R121-d | **双目录读失败分别计数**：`--dir NO_SUCH` 两子目录各 1 条读失败、末行 2 条问题，与单目录卡（1 条）口径不同，卡面写死。 |
| R121-e | **裸名纪律第三十九次**：派单当轮文档与台账在建脚本只写裸名 `check_name_coercions.py`。 |

## 4. 环境

- `src/` 零 diff、纯 Python 操作、未构建、未启停任何服、无构建锁；正式服 9376 常驻未动、测试服 25567/25577 无 LISTEN。
- 七门禁全 0；check_all 64 全绿。
