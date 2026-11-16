# R25 · M5 开工：技能 schema + 24 条原语 + 派 T-0021

2026-11-17 05:22~06:0x · S7

## 0. 结论先行

| 项 | 结果 |
|---|---|
| 卡板 | **无 `[DONE]` 可验**（T-0019 / T-0020 仍 `OPEN`，S8 自 11-16 19:04 起零交付）→ 本轮只派不验 |
| 派单 | **T-0021** `core/skill/SkillSpec.java`（技能定义解析器，纯函数）。在制品 3/3 封顶 |
| 自研 | 新 `docs/SKILLS.md`（M5 的接口契约）：五步释放链路 + `skills/*.json5` v1 schema + **24 条原语总表** + 伤害口径 + K1~K8 验收矩阵 |
| 构建 | `gradle build --offline` exit 0（jar **334577 B**，本轮零代码改动）；`selftest` **70 PASS** |
| 卡点 | S8 静默 10 小时；M4 出口 8/11，剩 V4 / V5 / V6 全是 GUI 面 |

## 1. 为什么这一轮不写代码

M5 出口条件是「右键释放技能，冷却消耗正确」。上一轮（R24）的下一步已经写死顺序：**先写 schema，再派解析器**，理由写在 `PLAN.md` §13.27 第 3 条 —— S8 没有设计上下文，一放手就发明 API（R9 之后 8 张卡里出现过 4 次「按文档定位置请 S7 知悉」）。

所以本轮把「技能长什么样」一次性定死，卡里只留「照 `docs/SKILLS.md` §3 写」。

## 2. `docs/SKILLS.md` 定下了什么

| 节 | 内容 | 关键取舍 |
|---|---|---|
| §1 边界 | 技能 / 附魔 / 套装 / 技能树 四者分工表 | 技能树只管「能不能用」，不重复做属性 |
| §2 释放链路 | 绑定 → 触发 → 条件 → 门槛 → 执行 | **技能没有 `trigger` 字段**（R25-a）；执行直接复用 `ScriptEngine.runCalls` |
| §3 schema | `id` / `name` / `icon` / `cooldown_ticks` / `conditions` / `effects` + 8 条解析口径 | 一个文件一条技能；`id` 必须含 `:`，**不补命名空间**；`effects` 全坏 → 整条跳过 |
| §4 原语表 | **24 条**（直伤 4 / 控场 4 / 位移 3 / 投射 3 / 增益 4 / 减益 3 / 召唤 2 / 表现 1） | 现状只有 `heal` ✅；其余 23 条走 D17 记账换 jar |
| §5 伤害口径 | `damage` → `hurt(白值)` → `DamageLayer` 乘 → 原版护甲 | 三条红线：绝不 `setAmount` / 类型必须在 `redtale_project:` 下 / `damage_type` 是启动期 registry |
| §6 冷却与资源 | M5-A 内存冷却（UUID）· M5-B 法力池 · M5-C 物品消耗 | 冷却下限 1 tick，不设 0 冷却（同 D19 物理地板） |
| §7 绑定 | 命令 → 物品 `abilities` → 键位 → 怪物 AI | `ContentData.abilities` **字段已存在**（M2 预留），缺的是 `ItemContent` 侧与 `ContentItem#use` |
| §8 验收 | K1~K8 | K5 = 白板 10 → `physical_damage 0.2` 时 12（证明吃 L2） |
| §9 交付顺序 | M5-A / B / C | 解析器给 S8，引擎与跨文件改动归 S7 |
| §10 不做 | 10 条 | 不做技能等级、热键栏 GUI、曲线弹道、`spell_engine` 互操作 |

机器可判的唯一断言：`grep -c "^| [0-9]" docs/SKILLS.md` = **24**（本表行数 = 原语条数）。

## 3. 派单：T-0021

- 独占文件只有 1 个新建：`src/main/java/com/redtale/project/core/skill/SkillSpec.java`（新包）。
- 接口冻结（卡里逐字给出）：`Definition(id, name, icon, cooldownTicks, conditions, effects, source)` + `parse(configs, warnings)`。
- 验收 3 条：`build --offline` exit 0 / `grep -c "Definition> parse("` = 1 / 离线探针 `SkillProbe` **≥18 断言**（列表写进卡里，避免 S8 只测 happy path）。
- 明确不做：不建 `SkillEngine`、不建默认配置、不改 `RedtaleCore` / `RedtaleCommand` / `ScriptEngine`（接线归 S7）。
- 为什么解析器不校验原语存在：那要摸 `ScriptRegistries`（启动期冻结），解析器就不纯了，离线探针也做不了。原语存在性留给运行期 `runCalls` 的 WARN 与 `/redtale validate`。

## 4. 数字与证据

```
gradle build --offline        → exit 0，build/libs/redtale_project-0.1.0.jar = 334577 B（与 R24 同尺寸，无代码改动）
gradle selftest --offline     → [selftest] PASS 70  /  BUILD SUCCESSFUL in 4s
grep -c "^| [0-9]" docs/SKILLS.md → 24
卡板状态：VERIFIED 17 / OPEN 3（T-0019、T-0020、T-0021）/ DROPPED 1
```

## 5. 卡点与待拍板

1. **S8 静默 10 小时**：最后一次交付是 11-16 19:04 的 T-0018；之后 T-0019（20:12 派）、T-0020（21:15 派）无人领，进度行空。已在协作板 §5 开 `B-`。下一轮若仍静默，**收回自做**（它们卡着 M4 的 V4），这在本轮 §13.28 的下一步里写明。
2. **Q-018（新）**：法术技能吃哪条属性轴 —— 默认 **A = 分轴**（物理技能 → `physical_damage`，法术技能 → `spell_power` + 既有条件乘区），需要给 `MultiplierRules` 加一个「伤害类别」维度。等 Aki 拍板前先按默认走。
3. M4 的 V4 / V5 / V6 是附魔台 / 铁砧 / 砂轮，**只能等一次带客户端的窗口**（V4 还依赖 T-0020 的 tag）。

## 6. 留下的路径

- `docs/SKILLS.md`（新，M5 契约）
- `docs/TASKS.md`（T-0021）
- `docs/CONTRACTS.md`（§2 `core.skill` / §5 `skills/`）
- `PLAN.md` §13 R25 行 + §13.28（R25-a~e）
- `docs/ROUND-R25-SKILLS.md`（本文件）
