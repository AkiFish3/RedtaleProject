# R68 · 验收 T-0063（专业观测面）+ D24 v1 收口 + 派 T-0064（显示开关解析）

日期：2026-11-19 14:50-15:1x · 里程碑：M7 / D24 收口，下一步回 D20 显示口径（Q-023）
本轮无 Java 生产代码改动（`src/` 只含 S8 交付的 T-0063 两个文件）→ 未新增自研类，未启第二次测试服。

## 1. 验收 T-0063 → `[VERIFIED]`

| # | 卡面验收 | 实测 | 判 |
|---|---|---|---|
| 1 | `gradle build --offline` EXIT=0；jar 内 `ProfessionCommand.class`=1；`wc -l` ≤ 200 | RC=0（`BUILD SUCCESSFUL in 813ms`，3 up-to-date）；`grep -c`=**1**；`wc`=**113** | 过 |
| 2 | 测试服四条命令逐字 | 见 §1.1 | 过 |
| 3 | 写面 grep=0；`TreeService\|TreePoints` grep=0；`RedtaleCommand.java` diff ≤2 | `award(` / `applyAttributes(` / `removeAll(` / `ProgressStore` 合计 **0**；`TreeService\|TreePoints`=**0**；`git diff --stat` `RedtaleCommand.java` **1+0** | 过 |

### 1.1 RCON 逐字（测试服 `Done (2.089s)`，起停各一次，已停）

```text
> redtale profession list
[redtale] 专业 2 个 · 属性 2 条
  redtale_project:mining · 采矿 · 上限 100 · 属性 1 条 · grants 4/0/0
  redtale_project:smelting · 冶炼 · 上限 80 · 属性 1 条 · grants 0/2/3

> redtale profession info mining
[redtale] 专业 redtale_project:mining · 采矿 · 上限 100
  属性 1 条：mining_efficiency +0.01/级
  grants：mine 4 条 / craft 0 条 / smelt 0 条

> redtale profession info smelting
[redtale] 专业 redtale_project:smelting · 冶炼 · 上限 80
  属性 1 条：additional_experience +0.005/级
  grants：mine 0 条 / craft 2 条 / smelt 3 条

> redtale profession info nope
[redtale] 没有这个专业：nope（用 /redtale profession list 看清单）
```

补充：`redtale reload` 后 `list` 逐字不变（`39 份配置文件`，`2 个专业（2 条属性）`）；`redtale validate` → `问题 0 条`；整轮日志 `redtale` 零 WARN / 零 ERROR。

### 1.2 一条记录不改码：全名要加引号

`redtale profession info redtale_project:mining` **裸写**报
`Expected whitespace to end one argument, but found trailing data ...le_project:mining<--[HERE]`；
改成 `redtale profession info "redtale_project:mining"` **通过**，输出与裸名一致。

根因：Brigadier `StringReader.allowedInUnquotedString` 只有 `0-9 A-Z a-z _ - . +`，**不含 `:`**；`StringArgumentType.string()` 与 `word()` 用的是同一套无引号字符集。实现用的是 `string()`（卡面要求），`find(raw)` 也确实裸名/全名都认 → **不是实现缺陷**，与 R2 `/redtale attr` 踩过的是同一个坑。已写 `PLAN.md` §13.71 R68-a。

### 1.3 未验

- 规则 8「玩家上下文追加一行」——无客户端，FakePlayer 不进玩家分支。挂 `REALPLAYER-WINDOW.md` F6（已标「控制台四段已验，只差玩家行」）。
- 规则 9「专业 0 个」空态：`reload` 会重落默认件，控制台无法真造空态；由 S8 探针 13/13 覆盖（非独立验证，记录在卡）。

## 2. D24 v1 收口

- `PROFESSION.md` §8：L5 行改「R67 派 T-0063，**R68 已验收**」；L1~L6 至此全部落地。
- 专业面剩余只有**真人**项：P1~P5（F 组 F1~F5）+ F6 玩家行。控制台侧已无缺口。

## 3. 派单 T-0064（在制品 0 → 1/3）

`core/config/DisplaySpec`（Q-023 默认 A 的第一层）：`display.json5` → 不可变 `Settings(showDps, showAttributeBreakdown, assumeTarget, source)`。
按 R68-c 只派**派生层**：默认件、`DEFAULT_FILES` 35→36、`ContentTooltip` DPS 行、`RedtaleCore` 接线全部留 S7（R69）。
卡里钉死 7 条语义（定位精确相等 / 无文件零告警 / 根非对象 / 非布尔单键回落 / `assume_target` 五类非法 / 未知键按出现序 / `source`）+ 三条机器验收（含两条零 IO grep）。
派单池 C13 结项、新增 C15（本轮派）。

## 4. 环境与收尾

- `check_all`：**16 个脚本 · 跑成 16 · 跳 0 · 红 0**（`check_defaults` 清单 35/磁盘 35；`check_contracts` 41 条契约 / 32 类 / 0 提示；`check_docs` 83 份 / 344 引用 / 0 提示；`check_tasks` 64 张卡 / 在制品 1 / 0 问题）。
- 构建锁取删 2 次（第一次 `-q` 管道吃掉了退出码，重跑取真实 RC —— 记一条：`| tail` 后的 `$?` 是 `tail` 的）。
- 测试服 1 次启停（`Done (2.089s)`），已停；25567 / 25577 无 LISTEN（仅 TIME_WAIT 的客户端连接）。
- 正式服 `E:\server-1.21.1` 整轮未动。
- 测试服 `mods/` 内 jar 已更新为 **673823 B**（本轮构建产物）。
