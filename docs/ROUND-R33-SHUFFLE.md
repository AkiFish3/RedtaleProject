# R33 · 2026-11-17 · 验收 T-0027 + M5-C 控场前两条（`knockback` / `pull`）

## 1. 本轮做了什么

| 事 | 结果 |
|---|---|
| 验收 T-0027（`content list` 技能列） | **VERIFIED**（3/3） |
| 派 T-0029（`core/skill/ManaPool`） | OPEN，在制品 T-0028 + T-0029 = 2/3 |
| 自研 `knockback` / `pull` 两条控场原语 | 上线，7 组实测 |
| `SKILLS.md` §4 第 5/6 行 | `—` → `✅ R33`（`grep -c "^| [0-9]"` 仍 24） |

## 2. T-0027 验收（3/3）

| # | 命令 | 实测 |
|---|---|---|
| 1 | `gradle build --offline` | EXIT=0，jar **407814 B** |
| 2 | `grep -c abilitiesText …/ContentListCommand.java` | **2**（1 定义 + 1 调用） |
| 2 | `grep -c "带技能 " …/ContentListCommand.java` | **1** |
| 3 | 测试服 `/redtale content list` | 10 行**每行末尾**都有 `· 技能 `；`demo_longsword` = `技能 1: redtale_project:gale_slash`；汇总 `… · 套装定义 1 个 · 带技能 1 件`（与实测 1 件一致） |

实现与卡面逐条对上：`abilitiesText` 空 → `-`、非空 → `N: id1 · id2`；汇总计数用 `withAbilities`（无 stream）。redtale 零 ERROR/WARN。

## 3. 两条原语

| 文件 | 内容 |
|---|---|
| `core/script/builtin/KnockbackEffect.java` | `knockback`：`strength`(0.4) · `radius`(**0** = 只推受害者，>0 = 圈心内活体，上限 32)；位移走原版 `LivingEntity#knockback` + `hurtMarked = true`；方向退化（水平重合）定死 `+X` + 一条 WARN |
| `core/script/builtin/PullEffect.java` | `pull`：方向取反 + `radius` 缺省 **6**；**直接调 `KnockbackEffect.shove(...)`**，位移只有一份实现 |
| `builtin/Builtins.java` | +2 行注册 |

`javap -c` 读到的原版实现（不是猜的）：

```
knockback(strength, x, z):
  strength *= 1.0 - getAttributeValue(KNOCKBACK_RESISTANCE)
  if (strength <= 0) return
  hasImpulse = true
  vec = normalize(x, 0, z).scale(strength)
  setDeltaMovement(old.x/2 - vec.x, onGround ? min(0.4, old.y/2 + strength) : old.y, old.z/2 - vec.z)
```

⇒ 内部算的是 `-normalize(x, z)`，所以「推离」必须传**反**方向（`-normalize(目标 − 施法者)`），「拉向」传正方向。

## 4. 实测（测试服起停 2 次，`Done (2.262s)`，均已停、25567/25577 无 LISTEN）

验法沿用 R32-e：附魔 `post_attack` → 脚本 → 打真实体。但位移**不在受害者身上对账**（受害者同时吃「原版平砍击退 + 本原语 + 与攻击者的实体碰撞」三份力），改在**旁观者**身上：`radius > 0` 把一头 `NoAI` 牛圈进来，它不被攻击、不走位、不吃碰撞，只吃我们一份力。

| # | 场景 | 观测 | 结论 |
|---|---|---|---|
| A | `knockback` `radius 6`，尸壳在 x≈3.1、牛在 x=-2.0 | 牛 `Motion.x` = **-0.798** | 推离（-X，远离尸壳）✅ |
| B | 同位置改 `pull` `radius 6` | 牛 `Motion.x` = **+0.797** | 拉向（+X，朝尸壳）✅ |
| C | 受害者 `knockback_resistance = 1.0` | `推动 1/2 · Villager(0.6格 Δ0.00)` | 原版抗性生效（不是自己拼速度）✅ |
| D | `strength: 0` | 41 次触发 / WARN **1** 条（去重）/ 牛 `Motion` 全程 `0.0` | `0` 真的是「不推」✅ |
| E | `skill info redtale_project:t_shove` | `✅ knockback` / `✅ pull` / `✓ 全部原语已注册` | 注册表已冻结进 jar ✅ |

负路径另记：`radius 0` 且没有受害者上下文时 → 1 条 WARN `radius 为 0 但没有受害者上下文，已跳过`（不静默、不猜目标）。

## 5. 本轮踩到并修掉的坑（最重要的一条）

`DamageEffect.num(args, key, def)` 对**非正数**返回 **缺省值**（那是给 `radius` / `falloff` 这类正数量设计的，R32-d 的共用口径）。照抄它读 `strength`，`strength: 0` 会被静默换成 `0.4` —— 实测过（配置写 `0`，日志仍打 `强度 0.40`，牛照样被推）。

改为两条原语自己写 `raw()`：缺键 / `null` / 非数字 / `NaN` / `Inf` → 缺省，**非正数原样返回**，由调用方判跳过。教训写进 `PLAN.md` §13.36 R33-c：**共用辅助方法要按语义挑，不能按「就近」挑**。

## 6. 其它两条环境事实

1. **`NoAI` 实体不跑 `travel`（`Pos` 永不变化），但 `Motion` 照样读得到、也不衰减** —— 验位移方向用 `Motion` 比用 `Pos` 干净。反过来，`NoAI` 木桩的 `Pos` 位移恒 0，别拿它当「没生效」。
2. **重复击退会把 `Motion` 收敛到 `0.8` 不动点**（每周期 `old/2 ± 0.4`），此时日志里「本轮 Δ」显示 `0.00` 但**仍在推**。别把 `Δ0.00` 读成失效 —— 要判「有没有推」，先看 `Motion` 的符号与量级，再看 `Δ`。

## 7. 环境

- `gradle build --offline` EXIT=0，jar **413740 B**；`gradle selftest --offline` **78 PASS**
- 测试服起停 2 次（`Done (2.260s)` / `Done (2.262s)`），均已停；redtale 零 ERROR（4 条 RecipeManager 既有噪声）
- 夹具全部移除：`skills/t_shove.json5` 删除、`scripts/enchants_soul_reap.json5` 还原成仓库默认件、测试实体 kill、配置回 25 份 / 技能 2 条
- 构建锁已删
