# R45 · 2026-11-18 11:43-12:1x · 验收 T-0040 + M7 L4（`ProgressService`）+ 派 T-0041

## 1. 卡板

| 卡 | 结果 | 证据 |
|---|---|---|
| T-0040 `/redtale level list\|info` | **[VERIFIED]** 3/3 | build `EXIT=0`、jar 内 `LevelCommand`=1、`wc -l`=116、`grep -c ProgressSpec`=0；测试服 `Done (2.001s)` 下 `level list` 三行逐字相符 |
| T-0041 `tools/check_progress.py` | 派出（改派一次） | 原定 `LevelModifiers` 被 L4 内联，12:05 换成起飞前对账工具 |

在制品 **1/3**（T-0041 OPEN）。

### T-0040 的两处判断

1. **负向文案「a, b」vs「a,b」**：卡面正文写「升序 id 逗号分隔」，样例却带空格。实现 `String.join(",", …)` → 按正文裁定**实现正确、样例笔误**，不打回（同 R43-a 的「语义优先于定点样例」）。
2. **`max_level: 2` 的 `L2→L3 -`**：卡面要求但默认件没有这种定义 → 临时 `levels/t_edge2.json5` 补测，`-` 与 `满级累计 50` 都对，夹具已删、已 reload 还原。

## 2. 自研 M7 L4：`core/progression/ProgressService`

```
award()            → 主等级 + 命中的职业各加一份 total_exp（kill 吃 mob_exp_multiplier）
levelOf()          → 等级 = 曲线(total_exp) 现算（R42-b）
applyAttributes()  → 声明 id 全移除 → 按本级 plan 重加（ADD_VALUE）
onLogin/applyAll   → 登入 / reload 后重算
```

- 新增 `core/command/ExpCommand`：`/redtale exp [add|sync|probe]`，`probe` 用 `FakePlayerFactory`，控制台可跑。
- 接线三处：`RedtaleCommand` +1 行、`RedtaleCore.reload()` 在线玩家重算、`GameHooks.onPlayerJoin` 重算；`MobDrops` **同一处**记 `total_exp`。

### 本轮唯一的真 bug（值得记住）

第一版查属性用 `ResourceLocation.parse("mining_efficiency")` → 被补成 `minecraft:mining_efficiency` → `getHolder` 空 → **职业那条 modifier 静默不下发**（等级正常、面板只差一点、日志零告警）。
根因：`AttributeSpecs` 里**自建属性 id 是裸 path**，原版才带 `minecraft:` 命名空间。改走 `AttributeSpecs.resolve` + `RedtaleAttributes.holder` 即通。
抓到它的不是日志，是探针 **G 组**（每条声明属性：`当前值 − 基值 == Σ plan`）。

## 3. 实测（`exp probe` 7/7）

```
A +1000 → Lv 7 · 最大生命 23.0（应 23.0）           ✓
E 双累加器（command）→ miner=0 t_cmd=1000           ✓   夹具 jobs/t_cmd.json5
F kill ×1.5 → 实发 1500（应 1500）                  ✓   改 progression.json5 再跑同一支探针
G 按级属性 Σplan → mining_efficiency Δ5.08（应 5.08）✓   两职业同属性相加
B 加满 → Lv 100/100 · 69.5                          ✓
C 到顶再加 10 万 → 不动                             ✓
D 归零 → 20.0（回基线）                             ✓
```

- 起停 6 次，全部 `Done (2.0~2.6s)`，最终已停（25567/25577 无 LISTEN，只剩 TIME_WAIT）。
- `redtale exp` / `exp add` 控制台 → 「只能由玩家执行」；`validate` 0 问题；redtale 零 ERROR（4 条 RecipeManager 既有噪声）。
- `build --offline` exit 0（jar 548580 B）、`selftest` 78 PASS。

## 4. 未验证（挂账）

- 真人 `/redtale exp add` 后 `/attribute` 面板读数、登录重算日志。
- **击杀路径**：`MobDrops` 里 `event.getSource().getEntity() instanceof ServerPlayer` 这段只在无玩家时编译过、没被真跑（无客户端，怪不主动打人）。本轮 F 组是直接调 `award(…, SRC_KILL)` 验的倍率与累加，不等于事件链验过。
- K4（改曲线 → reload → 等级变、exp 不变）：`levelOf` 与 `applyAttributes` 各自验过，缺真人面板读数。

## 5. 下一步（R46）

1. 验收 T-0041（`tools/check_progress.py`）。
2. M7 L5 收尾：`grant_exp` 原语（`primitive` 源，换 jar + D17 记账 + `SKILLS.md` 预算 +1）。
3. M6 收口最后一块：D26 自然刷怪替换（先写 `docs/SPAWN-RULES.md`）。
