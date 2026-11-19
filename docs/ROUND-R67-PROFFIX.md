# R67 · 2026-11-19 13:38-14:0x · 验收 T-0062 + 接线 15→16 + 派 T-0063（专业观测面）+ 真人窗口 F 组

## 1. 卡板：T-0062 `check_professions.py` `[DONE]` → `[VERIFIED]`

三条验收全过：

| # | 命令 | 实测 |
|---|---|---|
| 1 | `python tools/check_professions.py` | 末行逐字 `共 2 个文件 · 0 条问题`，`EXIT=0` |
| 2 | `--dir build/proffix`（3 份夹具，6 类问题） | `EXIT=1`，六类各 1 条，末行 `共 3 个文件 · 6 条问题` = `[问题]` 行数 6（自洽） |
| 3 | `wc -l` / 两处 grep | `173` ≤ 200 / `def clean_json5` = 0 / `from check_progress import` = 1 |

**S7 独立边界五组**（不重复 S8 的自测）：

| 组 | 夹具 | 实测 |
|---|---|---|
| A | `max_level: true` | 报 `max_level 必须是 2..1000 的整数：True`（`bool` 不算 int，符卡面第 6 条） |
| B | `mode:"geometric"` + `growth:1.0` | 报 `curve.growth 必须大于 1：1.0`（R65-b 那条「显式写默认值不算错」未被反向破坏） |
| C | 两份同 `id` 的文件 | 报**后出现**的 `d2.json5 · id redtale_project:dup 重复定义（先出现在 d1.json5）` |
| D | 属性键三形态 + 拼错键 | `minecraft:armor_toughness`（`generic.` 补位）/ `redtale_project:physical_damage` / 裸 `physical_damage` **全放行**；`bogus` 照报 |
| E | `read_attrs()` 返回 `None` | **恰好**多 1 条 `属性表 · 属性表读不到`，跳过属性键检查其余照跑（11 → 11 条），`EXIT=1` |

## 2. 接线：`check_all` 15 → 16

`tools/check_all.py` 的 `TOOLS` 末尾追加 `check_professions.py`（每行带「—— R67 接上」注释，与前 15 行同形）。

```
共 16 个脚本 · 跑成 16 · 跳过 0 · 红 0
```

R66 下一步第 1 条的预判（「过后 `check_all` 应变 16」）当场兑现。

## 3. 派单：T-0063 `/redtale profession list|info`（D24 L5）

在制品 0/3 → 派 1 张。挑 C13 的理由：D24 的 L1~L4 + L6 全部落地，**只缺观测面**；现在运营问「有哪些专业、吃什么、给什么属性」只能靠翻 JSON5。

卡面把三件事钉死，不留设计空间：

- **文案逐字**：`list` 两行头 + 每专业一行（`· 上限 · 属性 N 条 · grants 4/0/0`）、`info` 三行（属性行 + grants 行）；`grants` 计数**不含 `default`**、顺序固定 mine→craft→smelt。
- **数字格式**：属性值 `"+" + String.valueOf(double)`（`+0.01` / `+0.005`，不裁尾零、不乘 100）；上限走 `maxLevel()`。
- **不接写面**：硬验收 `grep -cE "award\(|applyAttributes\(|removeAll\(|ProgressStore"` = 0 —— 观测面读 `RedtaleCore.get().professions()` 快照，不碰存档、不碰 `AttributeMap`。

另外三条禁写：不改 `ExpCommand`（`professionLine` 是 R66 已验收的字面量）、不给 `ExpCurve` 加 `mode()/exponent()/growth()`（本卡不需要曲线数字，加访问器就是白改已 VERIFIED 的文件）、不做 `probe`。

`info` 的玩家行复用 `ExpCommand.professionLine(def, total)` ——**不写第二个格式化器**（R57-b 同一条：派生值只留一处）。

## 4. 自研：真人窗口 F 组（P1~P5 → 6 条）

`docs/REALPLAYER-WINDOW.md` 追加 **F 组**：专业经验。为什么必须真人 —— 三个事件（`BlockEvent.BreakEvent` / `ItemCraftedEvent` / `ItemSmeltedEvent`）只有真玩家触发；R66 的 `exp profession probe` 是**绕过事件直接调 `award`**，验的是「写桶 + 下发」，不是「钩子接上了」。

六条的期望值全部按两份默认件算过，不是「大概涨一点」：

| # | 动作 | 期望 | 依据 |
|---|---|---|---|
| F1 | 挖石头 | `mining` +3 | 精确 id `minecraft:stone: 3` **胜过**同一方块也命中的 `#minecraft:base_stone_overworld: 2` |
| F2 | 挖原木 | +2 | 只命中 `#minecraft:logs: 2`；两条 tag 同时命中取**大**不加和 |
| F3 | 挖泥土 | +1 | `default: 1` 兜底，日志零告警 |
| F4 | 原木→4 木板 | +0（`craft.default: 0`）；把 `craft.default` 改 1 后 **+1 不是 +4** | P4「不乘产出件数」—— 第一版期望写「+1」是错的：default 就是 0 |
| F5 | 熔 5 个铁矿 | +15 | `minecraft:iron_ingot: 3` × 5 次 |
| F6 | `/redtale profession list` 与 `info`（T-0063 后） | 定义面逐字 | L5 |

F4 那条是本轮唯一「差点写错」的地方：原以为「原木→木板」能直接验乘算，实际 `craft.default: 0` 让它发 0 经验 —— 乘不乘都看不出来。改成「先验 0、再把 `default` 调 1 验 +1」才真正钉住公式。

顺带修两处过期数字：`REALPLAYER-WINDOW.md` 前置的 `reload 回 37 份` → **39 份**（R66 后是 39，写 37 会让人以为配置少了两份）；`PROFESSION.md` §9 的「追加一组 F」改成「F 组，R67 已追加」。

## 5. 本轮无 Java 改动

`src/` 零 diff → 未构建、未启测试服（正式服/测试服整轮未动）、无构建锁。

理由：T-0063 的独占文件含 `RedtaleCommand.java`（1 行接线），按「VERIFIED 前不碰派出去的文件」这条唯一的防冲突机制，本轮不动 `core/command/`。原打算做的 Q-023（`display.json5` 三开关）也因此顺延 —— 它要动 `RedtaleCommand` 旁边的面包屑不假，但更要紧的是 Q-023 还没答复，而在制品已占 1 格。

## 6. 数字

| 项 | 值 |
|---|---|
| `check_all` | `共 16 个脚本 · 跑成 16 · 跳过 0 · 红 0` |
| `check_tasks` | `共 63 张卡 · 在制品 1 · 问题 0 · 提示 0` |
| `check_docs` | `共 82 份文档 · 339 条路径引用 · 0 条提示 · 0 条问题` |
| 卡板 | T-0062 VERIFIED、T-0063 OPEN（在制品 1/3） |
| 构建锁 | 未取（无构建） |
| 测试服 | 未启 |
