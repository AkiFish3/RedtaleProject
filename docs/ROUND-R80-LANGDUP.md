# R80 · 2026-11-20 09:4x~09:5x · 验 T-0075 + check_all 23→24 + 派 T-0076（lang 重复键）

## 1. 验收 T-0075（`check_primitive_ids.py`）

- 卡片状态：`[DONE]` → `[VERIFIED]`。独占文件归 S7。
- 验收 1（基线）：`python tools/check_primitive_ids.py` → `共 1 份原语 id 表 · 0 条问题`，EXIT=0；`-v` → `已对账 25 条表项 · 11 条标已实现 · 13 个 Effect 类`。
- 验收 2（三夹具，各自从干净副本重来）：

| 夹具 | 改动 | 输出（逐字） | EXIT |
|---|---|---|---|
| A | `DamageEffect.id()` 改 `damage_typo` | `原语 id damage_typo（DamageEffect）不在 SKILLS.md 原语表` | 1 |
| B | 删 `HealEffect.java` | `SKILLS.md 原语 heal 标已实现但无对应 Effect 类` | 1 |
| C | 新增无 `id()` 的 `GhostEffect.java` | `原语类 GhostEffect 未声明 id()` | 1 |

- 验收 3（静态）：`wc`=197≤200、`def check_ids`=1、禁用 API grep=0、`py_compile` OK；表路径 / builtin 目录指向不存在各 1 条读失败不抛栈；`build/primidfix/` 已清。
- S7 独立边界 9/9：§4 段界、`\|` 转义按接口保留 NUL、✅ 列位、行尾与块注释假 id 不计、`DEBUG_IDS` 放行、同类重复消息去重、三类按序、id 写歪只报②、目录读失败返空表。
  - 首轮边界脚本有 2 条 FAIL，均为 S7 夹具期望写错（转义竖线期望还原为 `|` 而接口逐字是 NUL；`done` 里放了没有对应类的 `heal`），改期望后 9/9 全绿。按 R65-a 判「夹具错非代码错」，不打回。
- **裁定 S8 提问**（§13.83 R80-a）：接口第③类写「done 不在已声明 id 集合」，验收 A 却要求「恰好 1 条」。按 R79-a「可机器判定的验收条款优先于接口措辞」裁定按验收口径实现——声明 id 命中，或类名按 snake_case→PascalCase+Effect 命中，均算有类；id 写歪只报②，类被删才报③。采纳实现，不打回。

## 2. 接线

- `tools/check_all.py` `TOOLS` 末尾追加 `check_primitive_ids.py` → `共 24 个脚本 · 跑成 24 · 跳过 0 · 红 0`，EXIT=0。
- 接线后 `check_checkall -v` → `清单 24 项 · 磁盘 24 项`、`共 1 份清单 · 0 条问题`；`check_tasks` 75 卡在制品 0 问题 0、`check_docs` 95 份 387 引用 0 问题、`check_contracts` 44 条 35 声明类 0 问题、`check_rounds` 0 问题。
- 本轮 `src/` 零 diff：未构建、未启停任何服务器、无构建锁；25567 / 25577 / 9376 均无 LISTEN。

## 3. 派 T-0076（`check_lang_dupes.py`，第七例对账对象）

- 与前六例不同：前六例都是「两份名单 ↔ 实体」的横向核对，本例核**一份 JSON 文件内部**。`check_lang.py` 的 `json.load` 对重复键 last-wins，缺键/占位符检查全绿时译文已被静默覆盖。
- 独占文件 `check_lang_dupes.py`（L6 新建 ≤200 行）+ `build/langdupfix/` 夹具；零接线，接线留 S7 下轮。
- S7 定死 `read_dupes` / `report` / `main` 三个接口、固定 `zh_cn`→`en_us` 顺序、问题文案与三组合规夹具的逐字期望；`check_all` 不在 S8 可改范围。

## 4. 口径

见 `PLAN.md` §13.83 R80-a~d。要点：验收条款优先于接口措辞；对账对象按「断裂后是否两边都绿、运行期才崩」判定，不按是否长得像前例；探针夹具期望必须先手工推到底再写；在建文件文档写裸名。
