# R69 · 验收 T-0064（`DisplaySpec`）+ Q-023 落地 L2（DPS / 属性明细行）+ 派 T-0065

日期：2026-11-19 15:56-16:1x · 里程碑：M7 之后回 D20 显示口径（Q-023 默认 A，L1 已验 / L2 本轮落地）

## 1. 验收 T-0064 → `[VERIFIED]`

| # | 卡面验收 | 实测 | 判 |
|---|---|---|---|
| 1 | `gradle build --offline` EXIT=0；jar 内 `DisplaySpec.class`=1；`wc -l` ≤ 200 | RC=**0**（`BUILD SUCCESSFUL in 5s`，3 up-to-date）；`grep -c`=**1**；`wc`=**73** | 过 |
| 2 | `probe_run DisplaySpecProbe` → `PASS ≥ 25 / FAIL 0` + 末行 `全绿` | `PASS=54 FAIL=0`，末行 `全绿`（54 条覆盖 12 组语义） | 过 |
| 3 | `net.minecraft`=0；`Files.|Path.of|FileReader`=0；`git status` 只有卡上文件 | 两条 grep 均 **0**；`git status --short` = `M docs/TASKS.md` + `?? .../DisplaySpec.java` | 过 |

### 1.1 裁定 S8 的口径提问（§13.72 R69-a）

S8 报：语义 5 写「`null` / 空白串视为缺省」（= 不告警），验收 2 却把「空串」列进「五种非法形态」。

裁定：**以语义段的行为条款为准** —— 空串 / 纯空白 = 缺省写法，静默回落、不告警。理由：验收是可执行投影，投影与行为条款分叉时改投影，而不是让行为跟着错的投影走；验收段重述枚举本身就是第二份真相。已就地改写验收 2 的括注并标注 `R69-a 裁定`，探针「空串位特判 + 断言零告警」保留。

S8 的第二条观察（`ConfigFile.root()` 返回 `JsonObject`，非对象分支不可达）按 R65-a 记**死分支**，不返工：`null` 根仍可达，语义 3 仍有覆盖。

## 2. Q-023 L2 落地（D20）

| 文件 | 改动 |
|---|---|
| `redtale/defaults/display.json5`（新） | 三键 + 逐条注释：服务端全服生效、`/redtale reload` 即可、`assume_target` v1 只标注不做护甲推算 |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` 35→**36**（`display.json5` 排在 `progression.json5` 前） |
| `core/RedtaleCore.java` | 字段 `display` + `display()` getter + reload 一行 `DisplaySpec.parse(all, warnings)`（紧跟 `critRules`）+ `import core.config.DisplaySpec` |
| `items/ContentTooltip.java` | DPS 行（`settled.value() * perSecond`）+ 属性明细行（新私有 `breakdown()`，`forEachModifier` 逐条带 `operation` 名） |

两条设计纪律写进代码注释：DPS **绝不重算**（复用同一个 `settle()` 与同一条 `AttackSpeedCurve`）；明细走 `forEachModifier`（组件真相）而不是 `rolledStats`（配置真相），因为 D21 白板化后两者会分叉。

## 3. 实测（测试服 1 次启停，`Done (1.988s)`，已停：25567/25577 无 LISTEN）

```text
# 启动：默认件落地、40 个文件（39→40）
配置重载完成：2 条脚本，40 个文件，10 件装备内容，…

# 基线（show_dps / show_attribute_breakdown 默认 false）
> redtale tooltip demo_longsword
  ≈ 15 /次（physical_damage +25%）
  攻速 12.5 tick · 1.6 次/秒（未持有，不含药水/急迫）
  词条 2：heavy · of_the_bear
  …（无 DPS 行、无属性明细行）

# 两开关置 true 后 reload
> redtale tooltip demo_longsword
  ≈ 12.096 /次（physical_damage +20%）
  攻速 12.5 tick · 1.6 次/秒（未持有，不含药水/急迫）
  DPS ≈ 19.354（假想目标 minecraft:zombie，不含护甲/暴击）
  属性 5 项：暴击率 +0.12（add_value） · 攻击伤害 +9.08（add_value） · 攻击速度 -2.4（add_value） · 物理伤害 +20%（add_value） · 最大生命 +2.2（add_value）
```

手算：12.096 × 1.6 = **19.3536** ≈ 19.354，DPS 行与结算同源 ✅

负向夹具与边界：

| 夹具 | 期望 | 实测 |
|---|---|---|
| `show_dps: 1` / `assume_target: 'Minecraft Zombie'` / `bogus_key: 7` | 3 条告警，各键独立回落 | reload 回 **`3 条告警`** ✅ |
| `assume_target: ""` | 静默回落（R69-a） | reload **零告警** ✅ |
| 删 `display.json5` + reload | 默认件重建、回 40 份 | 文件重建、`40 份配置文件`、零告警 ✅ |
| `validate` / `selftest` | 问题 0 / 102 PASS | `问题 0 条` / `PASS 102` ✅ |

## 4. 派单 T-0065（在制品 1/3）

`/redtale display` —— 显示口径只读观测面：`DisplayCommand` 新建 ≤200 行 + `RedtaleCommand` **1 行**接线；接口 `node()` + 纯函数 `text(Settings, int)`；文案两行逐字（`source` 空串写「未载入（默认值）」、告警行仅 `warningCount > 0` 出现、返回值按 `showDps` 给 1/0）。

派它的理由是 R69-b：**文件 ≠ 生效值**。运营看 `display.json5` 得到的答案是「文件里写了什么」，不是「现在全服按什么在显示」—— 文件被删会重建、键写歪会回落，两种情况下 `cat` 都像正常配置。

## 5. 环境

- `gradle build --offline`：第 1 次 **RC=1**（`RedtaleCore` 缺 `import DisplaySpec`，4 条符号错）→ 补 import 后 RC=0；构建锁取删 3 次
- jar **679205 B**、`check_all` **16/16 全绿**、卡板 **65 张 · 在制品 1 · 问题 0**
- 测试服整轮只起停 1 次（`Done (1.988s)`），正式服未动（25565/25575 仍是 pid 9376）
