# R64 · 验收 `check_docs`（T-0059）+ `check_all` 13→14 + D24 专业契约

> 2026-11-19 10:05-10:4x。本轮**无 Java 改动**（`src/` 零 diff）：验收 + 接线 + 写 spec + 派卡。

## 1. 卡板

- **T-0059 `check_docs.py`** `[DONE]` → **`[VERIFIED]`（3/3）**
  - 基线 `python tools/check_docs.py` **EXIT=0**，末行逐字 `共 78 份文档 · 322 条路径引用 · 0 条提示 · 0 条问题`。
  - 负向三组：`build/docfix` 坏引用 → `[问题] build/docfix/ROUNDS.md:78 引用的路径不存在：docs/NOPE.md` **EXIT=1**；`--docs nope/docs` → `文档目录不存在` **EXIT=1**；只含 `ROUND-X.md` 的目录 → `[提示] 未被任何文档引用的孤儿` 且 **EXIT=0**。
  - `wc`=89 ≤200、`grep -c "def clean_json5"`=0、`check_all` 逐字 `共 13 个脚本 · 跑成 13 · 跳过 0 · 红 0`。
- **裁定 S8 的口径提问**（`token②` 反引号内外）：**采纳实现（整行匹配）**。卡面第 1 条把基线钉在 321，而 R63 实测口径就是整行；照字面「去反引号」得 304，反而对不上卡面自己写的基线 ⇒ 卡面自相矛盾，同 R43-a / R56 的判法。
- **派 T-0060**（`core/progression/ProfessionSpec`，D24 L1 纯解析 + `resolve`），在制品 **1/3**。

## 2. 接线

`tools/check_all.py` 的 `TOOLS` 末尾追加 `check_docs.py` → 末行 **`共 14 个脚本 · 跑成 14 · 跳过 0 · 红 0`**（R63 下一步第 1 条的预判当场兑现）。

## 3. 新件 `docs/PROFESSION.md`（D24 契约）

为什么先写 spec 再派卡：专业要动**存档版本**（v2→v3）与**第三族累加器**，schema 一旦让 S8 现发明，后面 L2~L4 全跟着返工 —— R51 的扁平 `nodes` 就是反面教材（做了一半发现节点 id 只在树内唯一）。

钉死的内容：

| 段 | 结论 |
|---|---|
| §1 边界 | 专业 = 第三族经验累加器；**v1 只发经验、不改掉落**；不做方块再生（D27 已否）、不做 GUI、不做互斥 |
| §2 三族对照 | 主等级 `level/` · 职业 `job/<职业>/` · 专业 `profession/<专业>/`；三族可同时持有，等级一律派生不存 |
| §3 schema | 与 `jobs/` 同构 + `grants`；`max_level` 默认 100（职业是 50）；重复 id **保留先出现**（与 `jobs/` 同一条，不学 `skill_trees/` 的后者覆盖） |
| §4 `grants` | 三源 `mine`/`craft`/`smelt`；值只收整数 `0..1000`；优先级 **精确 id > 命中 tag 取大 > `default` > 0**，**顺序无关**；`mine` 查 block tag、`craft`/`smelt` 查 item tag |
| §4.4 计数 | `mine` 按方块一次；`craft` 按结果 id **不乘件数**（否则「原木→4 木板」是刷经验入口）；`smelt` 每件一次 |
| §6 存档 | `ProgressStore` v2→v3（`professions` 桶），旧档零告警；**升版必须早于任何写入路径** |
| §8 分层 | L1 解析（S8）→ L2 注册表（S7）→ L3 存档+事件（S7）→ L4 属性下发（S7）→ L5 命令面（S8）→ L6 工具（S8） |
| §9 验收 | P1~P8，P1~P5 需真人（后续并入 `REALPLAYER-WINDOW.md` F 组），P6~P8 控制台 + 夹具可验 |
| §10 不做 | 六条，含「不把 `mine`/`craft`/`smelt` 开放给 `jobs/`」 |

## 4. 本轮口径（§13.67）

- **R64-a**：派单卡的「基线数字」与「token 口径描述」冲突时，**按基线判**（基线是实测、口径是描述），并把口径改成与基线一致的那一种。反过来按描述判，会让一个本来正确的实现红掉。
- **R64-b**：专业 v1 **只发经验、不改掉落**。`drop-table` / `vanilla-drops` 属「改掉落」，与 `drops/` + `MobDrops` 会形成第二套掉落真相 —— 这是 R41 那条「内容 id ≠ 物品 id」的同类风险。
- **R64-c**：`grants` 的匹配必须**顺序无关**（精确 > tag 取大 > default > 0）。把「谁写在前面」当语义，等于把 JSON5 键序变成接口的一部分；运营调一下行序就变了数值，且没有任何报错。
- **R64-d**：专业 id 与职业 id **共用命名空间但分桶**，重叠只告警不拦。拦了的话「已经做了 `mining` 职业、现在想做 `mining` 专业」变成不可能；放行 + L2 一条告警，成本最低且可解释。

## 5. 环境

- `python tools/check_all.py` → `共 14 个脚本 · 跑成 14 · 跳过 0 · 红 0`（EXIT=0）
- 未构建（`src/` 零 diff、无 Java 改动）、未取构建锁、**未启停任何服务器**（正式服/测试服整轮未动）
- 夹具已清：`build/docfix` / `build/docfix3`

## 6. 下一步（R65）

1. 验 **T-0060**（`ProfessionSpec`：build RC=0 / 探针 ≥30 无 FAIL / `wc ≤ 200` / `net.minecraft`=0 / `AttributeSpecs`=0）。
2. **D24 L2**：`ProfessionRegistry`（80 条表校验 + 与 `jobs/` 的 id 重叠告警 + `DEFAULT_FILES` 加默认件 33→35 份）。
3. **真人一次性验收窗口**照 `docs/REALPLAYER-WINDOW.md` 等人（M4 剩 V4/V5/V6、M6 剩 B 组、M7 剩 C 组）。
4. Q-021 / Q-022 / Q-023 仍未答复，默认各按「免费」「按需补」「服务端 `display.json5`」走。
