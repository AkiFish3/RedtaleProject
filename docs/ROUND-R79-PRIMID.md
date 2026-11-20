# R79 · 验收 T-0074 + check_all 22→23 + 派 T-0075（原语 id 对账）

时间：2026-11-20 08:3x~08:5x · 执行：S7（移植工程队列 / 派单与验收）

## 1. 验收 T-0074 `check_rounds.py` → VERIFIED

| 项 | 证据 |
|---|---|
| 基线 | `python tools/check_rounds.py` → `共 1 份轮次索引 · 0 条问题` EXIT=0；`-v` → `已对账 76 条索引 · 76 份轮次文档` |
| 三负向夹具（各自从干净副本重来，`build/roundfix/`） | A 删 `| R77 |` → `轮次文档 ROUND-R77-PREFIX 未进 ROUNDS 索引`；B 追加幽灵行 → `索引条目 ROUND-ZZZ-NOT-THERE 指向的文档不存在`；C 复制 `| R78 |` → `索引重复指向 ROUND-R78-INDEX`。各 1 条、EXIT=1，逐字对上，夹具已清 |
| 静态 | `wc -l`=92≤200；`grep -c "def check_index"`=1；禁用 grep=0；`py_compile` OK |
| S7 独立边界 | 正文 `ROUND-R*.md` 与非 `R\d+` 表格行不计（0 条索引）；无反引号路径跳过；幽灵与重复并存时先幽灵后重复（2 条，顺序稳定）；`--docs NO_SUCH` / `--index NO_SUCH` 各 1 条读失败不抛栈 |

**裁定 S8 提问**：`--index` 默认按字面 `docs/ROUNDS.md` 解析、不随 `--docs` 联动（验收 3 的「恰好 1 条」是机器判据）。卡面接口段已就地更正，见 `docs/TASKS.md` T-0074。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾追加 `"check_rounds.py",` → **`共 23 个脚本 · 跑成 23 · 跳过 0 · 红 0`**（EXIT=0）。接线后 `check_checkall -v` 仍 0 问题。

## 3. 派单 T-0075（在制品 1/3）

`check_primitive_ids.py`（裸名，L6 新建），元层洞第六例：`check_primitives` 核「Effect 类 ↔ Builtins 注册」、`check_skills` 核「配置 id ↔ SKILLS.md 表」，中间的「类 `id()` 字符串 ↔ 表 id」无人核。接口 `DEBUG_IDS` / `read_table` / `read_effects` / `check_ids` / `main` 全部由 S7 定死；基线 S7 已核 = **25 表项 / 11 标 ✅ / 13 Effect 类 / 0 问题**。零接线，接线留 S7 下轮。

## 4. 环境

本轮 `src/` 零 diff（验收 / 接线 / 派卡 / 文档均不碰 Java）→ 未构建、未启停任何服务器、无构建锁；25567 / 25577 / 9376 均未动。`check_all` 23/23、`check_docs` 95 份 387 引用 0 问题、`check_rounds` 77/77、`check_tasks` 75 卡在制品 1 问题 0、`check_contracts` 44 条 0 问题。台账初稿把在建脚本写成带 `tools/` 的全路径 → `check_docs` 3 红，改裸名即绿（§13.82 R79-d，R63-e 后第七次）。

## 5. 下一步（R80）

1. 验 T-0075（过后 `check_all` 23→24）。
2. 元层洞两轮已铺到第六例，再派卡前先问「对象是否真的不同」；没有就转内容面，真人窗口 A~G 仍等 Aki。
3. Q-021（`respec` 免费）/ Q-022（原语按需补）无答复，继续按默认走。
