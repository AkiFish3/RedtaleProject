# R28 · M5-A 第 1 步：技能快照层 + 验收 T-0021

2026-11-17 · 里程碑 **M5（技能）** · 在制品：T-0022 / T-0023 / T-0024（3/3 封顶）

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0021 `SkillSpec` 解析器 | 验收 | **VERIFIED**（3/3 + S7 独立探针 `build/probe/S7SkillProbe.java` **50/50**） |
| T-0022 `SkillCooldowns` | 未领 | 仍 `OPEN`（05:52 派单） |
| T-0023 `/redtale enchant obtain` | 未领 | 仍 `OPEN`（06:50 派单） |
| T-0024 `/redtale content list` 行字段刷新 | **新派** | `OPEN`，独占 `ContentListCommand.java` |

在制品 = T-0022 + T-0023 + T-0024 = 3/3，本轮不再派。

### 1.1 T-0021 验收证据

1. `gradle build --offline` exit 0，jar **360261 B**（S8 自报同值）。
2. `grep -c "Definition> parse("` = **1**；`unzip -l` 见 `core/skill/SkillSpec.class` 与 `SkillSpec$Definition.class` 两条。
3. `S7SkillProbe` **50/50 PASS**，断言面（不复用 S8 的 `SkillProbe`）：

- 入参容错：`configs=null` 空表零告警 / `warnings=null` 不 NPE。
- 前缀筛：`items/x.json5`、`skills/b.json`、`skills/c.json5.bak` 静默跳过；`skills/sub/d.json5` 收，id 保斜杠。
- `id` 五态：缺失 / 非字符串 / 空白 / 不含 `:` 各跳过 1 条告警；`' p:gale '` trim 后可用。
- 重复 id：留先出现（`cooldown 10` + `source first.json5`）+ 1 条告警。
- `name` 缺省 `skill.redtale.gale/slash`（斜杠不改写）；非字符串回落 + 1 告警。`icon` 缺省 `minecraft:paper`。
- `cooldown_ticks`：缺省 0 / 120 原样 / `-5`→0 / `1e10`→`1e9` / `'x'`→0，后三者各 1 告警。
- `conditions`：缺失空表 / 非数组空表 + 1 告警 / 单项缺 `type` 只丢该项（两项坏 = 2 告警）。
- `effects`：缺失、非数组、空数组、全坏 各整条跳过 + 1 告警；单项坏保留其余且顺序不变。
- `args`：缺失 = `ScriptCall.EMPTY` **同一实例**；非对象 → 空 args + 1 告警；对象原样透传（含嵌套键名不改）。
- 不可变：`Map.put` / `effects.add` / `conditions.add` 全抛 `UnsupportedOperationException`。
- 键序：同一批 configs 两次解析逐字一致。

### 1.2 S8 提的四处口径偏差 → **全部采纳**

| # | S8 的做法 | 裁决 |
|---|---|---|
| ① | `id` 缺失与非字符串合并成一条 WARN 文案 | 采纳（行为一致，只是文案合并） |
| ② | `effects` 全坏时 WARN = 单项告警 + 1 条「整条跳过」 | 采纳（卡面「整条跳过 + 1 条 WARN」的字面读法） |
| ③ | 重复 id 判定放在 `effects` 校验之前 | 采纳（与卡面规则编号 3 < 8 一致） |
| ④ | `name` 为空串时原样保留 | 采纳（卡面只规定「非字符串」回落；空串属内容作者的显式选择） |

## 2. 自研：`SkillRegistry`（M5-A 第 1 步）

新类 `core/skill/SkillRegistry.java`：

```java
public static final SkillRegistry EMPTY;
public static SkillRegistry load(List<ConfigFile> all, List<String> warnings);
public Map<String, SkillSpec.Definition> all();   // 不可变，保配置顺序
public SkillSpec.Definition get(String id);       // null 安全
public boolean has(String id);
public int size();
public List<String> sortedIds();                  // 升序，输出/对账用
public List<String> warnings();                   // 与解析同源，load 时快照
public int effectCount();
public List<String> primitiveTypes();             // 去重升序，校验「原语是否存在」的输入
public String describe();                         // "2 条技能（4 条 effect，原语 4 种）"
```

`RedtaleCore`：加 `skills` 字段 + `skills()` 访问器 + reload 一行接线；reload 回显与日志带技能计数。

**为什么单开一类**：`SkillSpec` 必须保持纯函数（离线探针）；快照要能被命令、校验、怪物 AI 三处共用同一份不可变视图，并把告警与定义绑在一起换代（R28-c）。

## 3. 默认技能配置（进 `DEFAULT_FILES`）

| 文件 | id | 冷却 | 效果 |
|---|---|---|---|
| `skills/gale_slash.json5` | `redtale_project:gale_slash` | 120 tick | `console_log` + `damage 6.0`（缺省 `skill_physical`） |
| `skills/ember_burst.json5` | `redtale_project:ember_burst` | 200 tick | `console_log` + `damage 8.0`（显式 `skill_magical`） |

`name` 直接写中文可读名（`疾风斩` / `灼焰爆`）——理由见 `PLAN.md` §13.31 R28-b：schema 缺省是 lang 键，但仓库没有生成 `skill.*` 的通道，写 lang 键只会显示裸键。

## 4. 测试服实测（起停 2 次，均已停）

| 场景 | 结果 |
|---|---|
| 首次启动 | `Done (2.070s)`；`config/redtale_project/skills/{gale_slash,ember_burst}.json5` 落地 |
| 日志 | `配置重载完成：2 条脚本，25 个文件，10 件装备内容，2 条技能（4 条 effect，原语 4 种）。` |
| 负向（K6 同型） | 写 `skills/probe_bad.json5`（缺 id + `effects: []` + `cooldown_ticks: "x"`）→ RCON 回显 `1 条告警`；日志 `skills/probe_bad.json5: id 缺失或不是字符串（必填），已跳过这条技能`；技能数仍 2；零 redtale ERROR |
| 正向（K1 同型） | 只再加一份合法 `skills/probe_new.json5`（不换 jar）→ 技能 **2 → 3 条**（5 条 effect）；删除后回 2 条 |
| 回显最终形态 | `已载入 2 条脚本 + 10 件装备内容 + 2 条技能（25 份配置文件）` |
| 停服 | 25567 / 25577 无 LISTEN |

## 5. 数字

- `gradle build --offline` exit 0，jar **364183 B**
- `gradle selftest --offline` **78 PASS**（无新增单测；`SkillSpec`/`SkillRegistry` 用独立探针）
- `S7SkillProbe` 50/50、`S7SkillRegProbe` 14/14
- 配置默认文件 25 份（23 + 2 份技能）

## 6. 下一步（R29）

1. 验收 T-0022 / T-0023 / T-0024。
2. `SkillEngine`（T-0022 一 VERIFIED 就写；前置是 `SkillCooldowns` 合流）+ `/redtale skill cast`（`RedtaleCommand` 追加行等 T-0023 释放）。
3. `SkillRegistry.primitiveTypes()` 接进 `/redtale validate`。
