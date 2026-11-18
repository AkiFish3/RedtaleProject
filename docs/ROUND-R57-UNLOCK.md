# R57 · 验收 T-0052 + `unlock_skill` 接服务层（M7 出口闭环）

时间：2026-11-19 01:5x-02:3x · 工作目录 `E:\RedtaleProject` · 测试服 `E:\server-test`（已停）

## 1. 卡板

| 卡 | 结局 | 证据 |
|---|---|---|
| T-0052 `TreeUnlocks` | `[DONE]` → **`[VERIFIED]`** | ①`build --offline` EXIT=0、jar 内 `TreeUnlocks.class`=1；②S8 探针 `36/36`（≥25，无 FAIL）；③`wc`=73（≤150）、`net.minecraft`=0、`TreePoints\|TreeService`=0 |
| T-0053 `tools/check_skills.py` | **新派**（`[OPEN]`） | 技能配置是唯一没有起飞前对账的配置面 |

S7 独立探针 `S7UnlockProbe` **54/54**（A~N 十四组，不复用 S8 断言）：
`def==null` 五签名 / `selected==null` 七项 / 未知节点忽略 / 定义序≠选择序（含 `HashSet` 入参）/
双节点同技能去重保首次（位置取定义序先出现者）/ 空白 `skill` 与空白 `passive id` 跳过 /
`gained` 与 `lost` 双向与顺序 / `describe` 不去重且无首尾换行 / 四列表 `add` 抛 `UnsupportedOperationException` /
空树 / `null` 效果容错。

## 2. 自研：M7 出口最后一块

`TreeService` 新增（**只读派生，零存档变更**）：

```
gatedSkillPaths() / gated(raw) / unlockedSkills(player) / isUnlocked(player, raw) / unlockedText(player) / pathOf(raw)
```

`SkillCommand.cast` +6 行门槛；`TreeCommand.probe` 新增 O / P / Q 三组。

四条口径（PLAN §13.60）：
- **R57-a** 只有被树 `unlock_skill` 声明过的技能才设门槛 —— 一刀切会让现有 4 条技能里 2 条（`arc_bolt` / `ember_burst`）立刻不可放。
- **R57-b** 解锁集不落存档，现算；存档里再存一份 = 第二份真相（R50-c 同源）。
- **R57-c** 门槛只在 `/redtale skill cast`；`SkillEngine.cast` 不判（否则 FakePlayer 的 `skill probe` 全被打成未解锁）。
- **R57-d** `gated` / `isUnlocked` 比对走 path（全名与裸名同一条，R50-b 已栽过一次）。

## 3. 实测（测试服 1 次启停，已停）

```
[redtale] tree probe · 主等级 100（点数上限 21）
  O 点 gale → OK · 解锁 gale_slash 全名 true / 裸名 true / 门槛（arc_bolt 免门槛）true ✓
  P 退 gale → 解锁集 0 条 / 文案空态 true ✓
  Q 解锁文案 →   redtale_project:gale_slash ✓
[redtale] tree probe 17/17 通过
```

回归：`tree list` 2 行 / `tree info warrior_vanguard` 11 行逐字（T-0047 基线）/ `validate 0 问题` /
`reload 37 份` / `exp probe 7/7` / `skill list` 4 条；redtale 零 ERROR（4 条 `RecipeManager` 噪声，来自 simplyswords / brewinandchewin）。

**未验**：`/redtale skill cast` 被拦文案的真实玩家路径（命令要 `getPlayerOrException`，控制台不可达）→ 与 M6 其它真人项同批挂账。

## 4. 数字

jar **640121 B** · `selftest` **102 PASS** · `offline_check sources 133 exit 0` · 构建锁取删 3 次（`.queue` 收工为空）·
测试服端口 25567 / 25577 无 LISTEN · `save-all` 后正常 stop（`All dimensions are saved`）。
