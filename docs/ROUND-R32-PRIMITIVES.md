# R32 · 验收 T-0026（`buff`）+ M5-C 放量（`damage_aoe` / `debuff`）

- 时间：2026-11-17 12:49–13:15
- 里程碑：M5-C（技能原语放量）
- 卡板：**T-0026 `[DONE]` → `[VERIFIED]`**；**派 T-0028**（`tools/check_defaults.py`）；在制品 T-0027 + T-0028 = 2/3

## 1. T-0026 验收（`buff` 原语）

三条卡面验收（S8 自跑的数字全部复现）：

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradle build --offline` | exit **0**（jar **399417 B**） |
| 2 | `grep -c "new BuffEffect()"` / `grep -c 'return "buff"'` | **1** / **1** |
| 3 | `unzip -l … \| grep -c "script/builtin/BuffEffect.class"` | **1** |

功能四组（测试服 RCON；测试服本轮起停 2 次，均已停）：

1. `buff target:target`（夹具 `skills/t_buff.json5` + `skill probe`）→ 日志
   `[script] buff · Cow + minecraft:instant_damage 20t×0` —— 受害者解析、时长、等级全部落对。
2. `buff target:caster`（夹具 `t_buff_self.json5`）→ `[script] buff · [Minecraft] + minecraft:strength 200t×1`
   —— `FakePlayer` 走 `ctx.player()` 分支。
3. **真实体**（关键，R32-e）：临时给 `scripts/enchants_soul_reap.json5` 加一条 `buff caster → minecraft:glowing 600t`，
   尸壳持 `iron_sword[soul_reap:2]` 打村女 → `data get entity @e[tag=probe_h] active_effects` =
   `{duration: 387, show_icon: 1b, amplifier: 0b, id: "minecraft:glowing"}` —— 写 600、已过 213 tick，`addEffect` 真落到实体。
4. **负路径**：`effect: minecraft:no_such_effect` 连跑两次 → 只有 **1 条** WARN `buff 找不到效果：minecraft:no_such_effect`（按文案去重成立）。

S8 两处偏差：`duration_ticks<=0` 补 WARN —— 采纳；`pick` 的 `args.target` 非字符串会抛 —— 与 `HealEffect` 同源，不改，记 §13.35-c。

## 2. 本轮自研（M5-C 批次一）

| 文件 | 改动 |
|---|---|
| `core/script/builtin/DamageAoeEffect.java` | **新建**，`damage_aoe`：圈心 = 有受害者在受害者脚下、否则施法者脚下；线性 `falloff` 只乘白值；`max_targets` 按距离由近到远截断；施法者不进命中集合 |
| `core/script/builtin/DebuffEffect.java` | **新建**，`debuff`：缺省打受害者；只收 `MobEffectCategory.HARMFUL`，正面效果 WARN 跳过 |
| `core/script/builtin/DamageEffect.java` | `source`/`holder`/`caster`/`num` 由 `private` 改**包内可见**（零行为变更），供 `DamageAoeEffect` 复用同一份伤害类型回退 |
| `core/script/builtin/Builtins.java` | +2 行注册（`DamageAoeEffect` / `DebuffEffect`） |
| `docs/SKILLS.md` §4 | 第 2 / 15 / 19 行状态 `—` → `✅ R32`，并补参数与备注 |

## 3. 实测（测试服，2 次启动：`Done (2.208s)` / `Done (2.202s)`，均已停）

- 启动日志：`原语 5 种 → 7 种`（`damage_aoe` / `debuff` 进注册表）。
- `skill info redtale_project:t_aoe` → `✅ damage_aoe {"amount":10.0,"radius":4.0,"falloff":0.5,"max_targets":0}`。
- `skill probe redtale_project:t_aoe` → **A 组掉血 10 / B 组（`physical_damage +0.25`）12.5，比值 1.25** —— 范围伤害照样过 `DamageLayer`。
- **多目标**（附魔链路）：`damage_aoe · 圈心 (8.5, 71.0, 8.5) · 半径 4.0 · 命中 2/2 · 白值 10.00 · Villager(0.0格×1.00) Cow(2.0格×0.75)`
  → 牛 10 → 2.5；村女带着圈心跑了，第二击 `Cow(3.5格×0.56)` 把牛打死（落点随受害者移动，圈心跟着走）。
- `debuff` 打施法者：`[script] debuff · Husk + minecraft:slowness 600t×1`，`data get` = `{amplifier: 1b, duration: 510}`。
- `debuff` 缺省目标：`[script] debuff · Villager + minecraft:slowness 600t×1`（受害者）。
- `debuff` 写正面效果 `minecraft:strength` → 1 条 WARN `debuff 的 effect 不是负面效果：minecraft:strength（正面效果请用 buff 原语）`，零效果。
- 夹具全部移除、脚本已还原：`skills/` 回到 2 件、配置 25 份、redtale 零 ERROR（4 条 RecipeManager 既有噪声）。

## 4. 数字

- `gradle build --offline` exit 0，jar **407520 B**
- `gradle selftest --offline` **78 PASS**
- `grep -c "^| [0-9]" docs/SKILLS.md` = **24**
- 测试服 25567 / 25577 无 LISTEN；构建锁已删

## 5. 新记的三条事实（详见 `PLAN.md` §13.35）

1. **瞬时效果不结算伤害**：`minecraft:instant_damage` 走 `addEffect` 一点血都不掉，连原版 `/effect give … instant_damage` 都不掉 —— `buff` 只能挂时长型效果。
2. **`MobEffectCategory.HARMFUL` 是 `debuff` 的唯一准入判据**：正面效果必须显式 WARN，否则「debuff 能加血」会静默通过。
3. **测试服立靶要空中平台**：地形在 y=-58 是实心方块，`summon` 进去直接 `suffocated in a wall`，实体 10 秒死光，看起来像「召唤失败」。
