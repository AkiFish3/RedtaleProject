# R35 · M5 收口第一步：法力进引擎（`mana_cost` + 三条属性 + 六组探针）

2026-11-17 16:31–16:55 · 测试服起停 4 次（均已停）

## 1. 本轮结论

1. **T-0029 验收通过**（`ManaPool` 纯数据类）：build exit 0、零 MC import、138 行、S7 独立探针 **85/85**。
2. **M5 收口第一步落地**：`skills/*.json5` 的 `mana_cost` → `SkillEngine` 扣蓝 → 三条属性（`max_mana` /
   `mana_regeneration` / `mana_cost_reduction`）全接上，`/redtale skill mana probe` **六组全过**。
3. **派 T-0031**（`dash` 原语），在制品 2/3（T-0030 + T-0031）。

## 2. 卡板

| 卡 | 动作 | 依据 |
|---|---|---|
| T-0029 | `[DONE]` → **`[VERIFIED]`** | 3/3 + 独立探针 `build/probe/S7ManaProbe.java` **85/85**（不复用 S8 的 `ManaPoolProbe`） |
| T-0030 | 仍 `[OPEN]`（S8 未交付） | 15:25 派单 |
| T-0031 | **新派** | `dash` 原语：单类 + `Builtins` 2 行；`motion()` 纯函数让 20+ 条断言可离线跑 |

T-0029 的四处偏差**全部采纳**，其中 ① 值得记：`setMax(u, 250)` 后 `current` 仍是 100 —
卡面写的是「current 夹到 `[0, max]`」，S8 描述的「新玩家满蓝」只在**降**上限时表现为满。
我的第一版探针按「抬上限就补满」断言，红了 1 条；判 S8 对、我错。

## 3. 本轮自研改动（全部 S7 独占文件）

| 文件 | 改动 |
|---|---|
| `core/skill/SkillSpec.java` | `Definition` 7 → **8** 字段（`+double manaCost`）；新增 `manaCost()` 解析（缺省 0、非数字/非有限/负数 → 0 + WARN、`> 1e9` 夹取） |
| `core/skill/SkillEngine.java` | 持有 `ManaPool`（跨 reload）；`Status.NO_MANA`；门槛拆成「查冷却 → 扣蓝 → 记冷却」；`maxManaOf` / `manaRegenOf` / `manaCostReduction` / `syncMax` / `tickMana` / `manaText` / `noManaText` |
| `core/hook/GameHooks.java` | `onPlayerTick` 每 tick 调 `tickMana`（`ticks = 1`，小数余额不取整） |
| `core/command/SkillCommand.java` | `mana` / `mana set <cur> [max]` / `mana probe <id>`（A~F 六组）；`list`/`info` 补「法力」列；`cast` 识别 `NO_MANA` |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` +1：`skills/arc_bolt.json5` |
| `resources/.../skills/arc_bolt.json5` | 新默认内容：`mana_cost: 40`、冷却 40 tick、`damage 6.0` 物理 |

## 4. 实测证据（测试服 `E:\server-test`）

4 次启动：`console_r35.log`（`Done 2.131s`）、`r35b`、`r35c`、`r35d`；四次全部 `stop` 干净退出，25567/25577 无 LISTEN。

```
[redtale] 技能 3 条 · 共 6 条 effect · 原语 4 种：
  redtale_project:arc_bolt · 弧光弹 · 冷却 40 tick · 法力 40 · conditions 0 · effects 2 · skills/arc_bolt.json5
  redtale_project:ember_burst · 灼焰爆 · 冷却 200 tick · 法力 0 · ...
  redtale_project:gale_slash · 疾风斩 · 冷却 120 tick · 法力 0 · ...

[redtale] skill mana probe redtale_project:arc_bolt（mana_cost = 40）：
  A 组 满蓝 100 → 60（扣 40，状态 CAST）
  B 组 再放一次 60 → 20（状态 CAST）
  C 组 蓝不够 39 → 39（状态 NO_MANA，冷却就绪 true）
  D 组 消耗降低 50% 100 → 80（应扣 20，状态 CAST）
  E 组 回蓝 20/秒 × 20 tick → 累计 20，余额 20（应 20.0）
  F 组 max_mana =150 → 上限 150（应 150；0 才是默认 100），当前 20 → 20（抬上限不补蓝）
  ✓ 六组全过（扣蓝 / 累减 / 不足不扣 + 不记冷却 / 减免 / 回蓝 / 上限跟属性且不补蓝）
```

负向夹具（`mana_cost: "abc"` / `mana_cost: -5`）：

```
[Server thread/WARN] (RedtaleProject) 配置告警：skills/t_badmana.json5: mana_cost 不是数字，已用 0（不耗法力）
[Server thread/WARN] (RedtaleProject) 配置告警：skills/t_negmana.json5: mana_cost 为负数（-5），已用 0（不耗法力）
[redtale] 已载入 2 条脚本 + 10 件装备内容 + 5 条技能（28 份配置文件），2 条告警；...
```

删夹具后 `reload` 回 `3 条技能（26 份配置文件）`、零告警。`/redtale skill mana` 在控制台返回
`这条命令要玩家执行（法力账按 UUID 记）`（已注册、走到 `getPlayerOrException`）。全程 `[Server thread/ERROR]` = **0**。

## 5. 本轮踩的坑（已写 `PLAN.md` §13.38）

1. **F 组第一次是红的** —— 我按「加成」写期望（`max_mana` +50 → 150），实测 50。
   自建属性基值是 0（R5 事实），所以「绝对值口径 + 0 = 没配 → 100」才是实现，也是本轮定下的口径。
   **教训**：期望值要按「属性是绝对值还是加成」先问一句，再写断言。
2. `SkillCooldowns.tryStart` 是「查 + 记」一体 —— 直接当门槛会让「蓝不够」那次也吃冷却。
   拆成 `remaining()` + 少一行代价的 `start()`，C 组才有 `冷却就绪 true` 这个断言点。
3. Lambda 里引用会被重新赋值的 `boolean` 又踩一次（R30 老坑）：先落 `final` 局部量。

## 6. 未验证（挂账）

- 真人玩家的 `/redtale skill mana` 面板与 `mana set`（无客户端，第 35 轮挂账）。
- 登录还原 / 退出清账 —— **功能未做**（R35-e：持久化是下一步 `PlayerData`）。
- 装备下发 `max_mana` / `mana_regeneration` 的完整链路（装备 → `AttributeMap`）仍需真人 tick；
  本轮 F/E 组用瞬时 modifier 直挂 `AttributeMap`（R30-e 同口径）。
