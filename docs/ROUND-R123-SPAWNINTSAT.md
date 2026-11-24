# R123 · 验收 T-0117 孔位整段丢弃 + check_all 65→66 + 派 T-0118（刷怪大整数静默饱和）

- 时间：2026-11-24 08:4x（S7 移植工程队列）
- 里程碑：M6 / D16 内容安全网继续加厚（对账网第 49 例）

## 1. 验收 T-0117 `check_socket_discards.py`

卡面三条全部机器复跑通过，S8 零偏差不打回：

- 基线 `共 1 份孔位丢弃口径对账表 · 0 条问题` EXIT=0；`-v` 逐字 `已检查 7 份物品 · 1 处 sockets 段 · 0 个静默丢弃落点`。
- S7 独立六夹具 A~F 逐字 6/6，EXIT=0/1/1/0/0/1：B 显式 `count: 0`、C 删 `count` 行同文案；D 短 types（归另一把尺）不报；E 0 + 空 types 不报；F 两份 bad 按 `sorted` 共 2 条。
- 静态：wc=143 ≤200、`def check_discards`=1、禁用 grep=0、`py_compile` OK、`--dir NO_SUCH` 恰好 1 条 `[问题] items 目录读取失败` EXIT=1 不抛栈；函数边界 12 形态（显式 0/缺键/bool/float/负数/超 6/合法 2/types 空/缺/非 list/sockets 非 dict/data 非 dict）+ 重复两条不合并 + `sorted`。
- 卡板 T-0117 `[DONE]` → `[VERIFIED]`；接线 `check_all` 65→**66**，接线后 `共 66 个脚本 · 跑成 66 · 跳过 0 · 红 0`、`check_checkall` 0 条问题。
- S7 探针两处自错自改，不打回 S8：①`--dir` 传成 `items/` 子目录造出假读失败（R65-a 旧坑）；②函数边界期望值误写 `[False]`，正确为 `[]`。探针已清。

## 2. 本轮 S7 最小一步：全类排除 + 源码逐字 + 干净副本反事实（整数饱和 ≠ 小数截断）

- 派前只读全类扫描 13 个 `*Spec.java` / 65 把尺 / 36 份默认 JSON5。已覆盖项逐条排除：mobs level/scale/skill chance/cooldown、drops rolls/xp/min/max/chance、affix weight/item_level、item durability/use ints/name、tree ppl/icon/name/cost 上界之外、profession base、damage_aoe args、enchant exclusivity/args sync、socket 三族、damage types/equipment slots 等；有 WARN 出口（`consume` / `repair.mode` / `SpawnRuleSpec.action` / `SkillSpec` 冷却等）、空基线（`repair.cost.amount` / `conditions.y`）、S7 独占区（`core/attr/` / `core/math/Settle.java` / `core/damage/` / 注册表 / mixin）均不派。
- 真洞逐字：`SpawnRuleSpec.java:113-118` `integer()` 中 `:116` 只对 `!isFinite(v) || Math.rint(v) != v` 告警；合法大整数走 `:117` `return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));` 静默饱和。落点 `:47 priority`（默认 0）、`:48 per_chunk_cap`（默认 0，再 `Math.max(0, ...)`）；对照同文件 `:56/:73/:81/:88/:97/:107` 等 WARN 反证该出口故意静默。
- 玩法后果：`SpawnRegistry.java:29-31` 按 priority 降序比较，越界规则并列最高后退化为 id 升序；`SpawnReplacer.java:210` 的 `countInChunk(...) >= rule.perChunkCap()` 在 cap 饱和成 2147483647 后永不成立，每区块限量失效。
- 旧尺作用域逐把核对：`check_mobs.py:143-148` 对 cap 只判 `_num()` 与 `>=0`、对 priority 只判 Python `isinstance(int)`——Python int 无上界；`check_spawn_conditions.py:70-86` 只核 conditions 数值类型（light 另有 0~15 值域，y 无上界）；`check_spawn.py` 读的是 `spawn.json5` 的 `per_tick_cap`，不同对象。全 tools 仅此两把读 `spawn_rules` 的这两个字段。
- 反事实：`copytree` 全份默认配置到 `build/spawnint_s7_probe/`，把 `priority: 100` 改 `2147483648`、`per_chunk_cap: 1` 改 `1000000000000`，两把旧尺 EXIT/末行逐字不变（`共 4 个文件 · 0 条问题` / `共 1 份刷怪规则条件表 · 0 条问题`）；副本已清，`src/` 零 diff（对账网第 49 例）。基线 1 规则 / 2 处落点 / 0 坏形态逐字核对。
- 结论：C70 四判据齐。与 T-0109~T-0112 的「小数 `(int)` 截断」是不同代码路径，另卡；`conditions.light` 已有值域尺、`y.min/max` 无默认基线，按 R123-d 排除。新卡接口由 S7 定死（`_loads` / `read_rules` / `_saturates` / `check_rules` / `main`），六夹具 + 函数边界。

## 3. 口径（§13.126）

| 编号 | 结论 |
|---|---|
| R123-a | **整数饱和 ≠ 小数截断**：前者 `Math.rint(v)==v` 成立、绕过 WARN 后被 `Math.min/max` 夹到 int 边界；后者是 `getAsInt()` 丢小数。判据与常量都不同。 |
| R123-b | **排序退化也算玩法面**：priority 饱和使覆盖关系改变，虽不崩、不显示，仍按玩法面优先于显示面。 |
| R123-c | **旧尺类型系统盲区**：`isinstance(x, int)` 只能证明「是整数」，不能证明「塞得进 Java int」；跨语言边界按目标类型冻结常量。 |
| R123-d | **同形态异出口分卡**：`conditions.light` 已有值域尺、`y.min/max` 无默认基线，均不并入本卡；先读卡面「不要做」再写断言。 |
| R123-e | **裸名纪律第四十一次**：派单当轮文档与台账在建脚本只写裸名 `check_spawn_int_saturation.py`。 |

## 4. 环境

- `src/` 零 diff、纯 Python 操作、未构建、未启停任何服、无构建锁；正式服常驻未动、测试服 25567/25577 无 LISTEN。
- 七门禁全 0；check_all 66 全绿。
