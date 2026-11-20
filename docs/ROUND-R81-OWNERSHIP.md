# R81 · 验 T-0076 + check_all 24→25 + 派 T-0077（独占文件重叠）

## 1. 验收 T-0076（`check_lang_dupes.py`）

- `[DONE]` → **`[VERIFIED]`**。基线 `python tools/check_lang_dupes.py` → `共 2 个语言文件 · 0 条问题` EXIT=0；`-v` → `已扫 2 个语言文件 · zh_cn 0 条重复 · en_us 0 条重复`。
- 三夹具各自从干净副本重来（`build/langdupfix/`，用完已清）：A zh_cn 追加已有完整键第二份 → 1 条 `zh_cn 重复键：attribute.redtale_project.additional_experience（出现 2 次）`；B en_us 同键 → 1 条对应 en_us 文案；C zh_cn 两条（additional_experience / air_damage，按键升序）+ en_us 一条（arcane_damage）→ 恰好 3 条。均 EXIT=1，逐字对上。
- 静态：wc=65 ≤ 200、`grep -c "def read_dupes"`=1、禁用 API grep=0、`py_compile` OK、`--lang NO_SUCH` → 2 条读取失败 EXIT=1 不抛栈。
- S7 独立边界 9/9：三次出现计数、嵌套对象按局部 key 不拼路径、嵌套取最大次数、按键升序、report 保序、无重复、坏 JSON / 空文件 / 缺文件读失败。

**裁定 S8 提问**：卡面夹具 C 的裸键在两份 lang 中不存在，按裸键追加造不出重复；验收条款「恰好 3 条」是可机器判定的底线，故采纳 S8 的完整属性键实现、不打回，卡面验收段就地更正（§13.84 R81-a）。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾加 `check_lang_dupes.py` → **`共 25 个脚本 · 跑成 25 · 跳过 0 · 红 0`**（EXIT=0）。接线后 `check_checkall -v` 25/25、`check_tasks` 77 卡在制品 1 问题 0。

## 3. 派 T-0077（`check_ownership.py`）

- 洞：`check_tasks.py` 查状态 / 重复 id / 在制品上限 / 验收段，但两张 WIP 卡声明同一独占文件时两边都能各自 VERIFIED，后写者静默覆盖。§2 明说独占文件是防冲突的全部机制，却没有机器核对。
- 接口由 S7 定死：`read_cards` / `exclusive_files` / `check_overlaps` / `main`；只读 `docs/TASKS.md`，只看 OPEN / DOING / BOUNCED 卡；问题文案、三组夹具逐字写死；零接线，接线留 R82。
- 在制品 1/3。

## 4. 环境

本轮 `src/` 零 diff：未构建、未启停任何服、无构建锁；25567 / 25577 / 9376 无 LISTEN。所有验收为纯 Python 静态检查。
