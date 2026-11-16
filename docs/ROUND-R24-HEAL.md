# R24 · `soul_reap` 真回血（M4 V9 落地）+ V11 硬指标演练

> 2026-11-16 21:07–21:25 · 里程碑 **M4**（8/11 验收项已绿）· 验收 T-0018 → `VERIFIED` · 派 T-0020

## 1. 本轮结论（一句话）

`soul_reap` 从「只打一行日志」变成**真的回血**：新增 `heal` 原语 + 脚本上下文带受害者实体，实测 `8.00 → 10.00（+2.00）`；同时 V11「加第 N 个附魔只写 JSON5」**首次端到端跑通**（零编译）。

## 2. 验收 T-0018（`/redtale validate` 补第 5 条 + 裸名提示）

| 项 | 命令 | 结果 |
|---|---|---|
| 1 | `gradle build --offline` | exit **0**，jar 330921 B |
| 2 | `grep -c "set_id" ValidateCommand.java` | **2**（≥1 ✓） |
| 2 | `grep -c "个套装" ValidateCommand.java` | **1**（卡面要求 1 ✓） |
| 3 | 测试服功能四跑 | 见下 |

**功能四跑**（测试服，`Done (2.037s)`）：

```
基线     ：校验：10 件内容 / 1 个词条池 / 3 条词条定义 / 1 个套装 · 问题 0 条（外部 weapon_attributes 引用 1 条未校验）
          （没有引用问题）
造三处坏 ：sets/emberforged.json5 piece → demo_longsword_typo
          items/probe_badset.json5   → set_id: "ghostset"
          affix_pools/sword_common   → applies_to 留 "axe"（无命名空间）
reload 后：问题 3 条
            sword_common: applies_to 的 axe 没写命名空间（会被当成 minecraft:axe）
            emberforged: piece demo_longsword_typo 不存在
            probe_badset: set_id ghostset 没有对应套装定义（写错一个字母就静默不生效）
还原后   ：问题 0 条 + （没有引用问题）
```

三行文案**逐字**对上卡面；S8 提的三处口径全部认账（裸名按实际值渲染、`pieces()` 声明序、问题行追加序）。

## 3. 自研 R24-1：脚本层双实体上下文 + `heal` 原语

### 3.1 改了什么

| 文件 | 改动 |
|---|---|
| `core/script/ScriptContext.java` | 新增 `Entity target` 字段 + `target()` / `target(Entity)` / `hasTarget()`；新工厂 `ofEntity(Entity caster, Entity target)`（caster 是玩家 → `player()`，否则只有 `vars.attacker`） |
| `core/script/builtin/HealEffect.java` | **新建**（`id = "heal"`）：`target`（caster/target）、`ratio`、`per_level`、`flat`；负数归零、`1e9` 硬顶、目标死亡跳过、目标缺失 WARN 一次 |
| `core/script/builtin/Builtins.java` | 注册 1 行 |
| `enchants/ScriptedEnchantmentEffect.java` | 第 4 参改名 `affected`，`ctx = ScriptContext.ofEntity(item.owner(), affected)`，并把 `attacker` / `affected` 塞进 `vars` |
| `core/script/ScriptEngine.java` | `onCooldown` 加 `ctx.server() == null` 守卫（实体上下文的兜底） |
| `resources/redtale/defaults/scripts/enchants_soul_reap.json5` | 追加 `heal` 一条（`ratio 0.08` / `per_level 0.02`） |

### 3.2 为什么按「最大生命比例」而不是「按伤害抽血」

`EnchantmentEntityEffect#apply(level, enchLevel, item, affected, pos)` 的签名里**没有伤害值**，`post_attack` 组件也不传。要按伤害抽血必须让伤害管线把数值递进脚本上下文 —— 那是结构改动，排到 M5 和技能层一起做。这一版用「目标最大生命 × (ratio + per_level × (等级-1))」：可预测、可验收。

### 3.3 实测（测试服，两段启动）

场景：`/fill` 造 9×9 石台 → `summon husk` + `item replace ... weapon.mainhand` 给 `soul_reap 2` 剑 → `damage 12` 把尸壳打到 8 血 → 旁边放村民等它打。

```
[script] [soul_reap] 触发 · 施法者 server · 等级 2 · heal_ratio=0.08 · per_level=0.02
[script] heal · test_husk 8.00 → 10.00（+2.00，等级 2，目标 caster）
[script] [soul_reap] 触发 · 施法者 server · 等级 2 · heal_ratio=0.08 · per_level=0.02
[script] heal · test_husk 10.00 → 12.00（+2.00，等级 2，目标 caster）
```

`20 × (0.08 + 0.02 × (2-1)) = 2.00`，数值精确。

**`target` 分支**：把脚本改成 `target: 'target'` 后重跑，日志目标变成**受害者**：

```
[script] heal · Villager 20.00 → 20.00（+0.00，等级 2，目标 target）
```

`+0.00` 是因为满血（`LivingEntity#heal` 到上限即停），不是没生效 —— 判据是「目标」字样与实际实体。

## 4. V11 硬指标演练（M4 出口条件原文）

只写一份 JSON5，**全程没编译**：

1. 写 `config/redtale_project/enchantments/frost_edge.json5`（`id: redtale_project:frost_edge`，`minecraft:damage` linear 1.0/级+0.5）。
2. `/redtale reload` → `24 份配置文件` → 生成 `world/datapacks/redtale_project/data/redtale_project/enchantment/frost_edge.json`（`description` 只留 translate 键、字段顺序与 §3.1 白名单一致）。
3. **同启动内** `/enchant ... redtale_project:frost_edge 1` → `Can't find element 'redtale_project:frost_edge' of type 'minecraft:enchantment'`（复现 R21-c：附魔是启动期 datapack registry）。
4. **重启** → `Done (2.064s)` → `/enchant @e[type=husk] redtale_project:frost_edge 3` → **`Applied enchantment enchantment.redtale_project.frost_edge III`**。

**边界**：运行期新增的附魔**没有中文名**（`tools/gen_enchant_lang.py` 只扫仓库默认配置），tooltip 会显示裸键。演练文件归档到 `D:/redtale-work/drills/v11_frost_edge.json5` 并从测试服移除（保持 T-0019 的 2 条基线）。

## 5. 派单

**T-0020 · [OPEN] 附魔可获取性 tag 生成**（`in_enchanting_table` / `tradeable` / `on_random_loot`）
- 独占：新建 `enchants/EnchantObtainTags.java` + `RedtaleCore.java` 追加 1 行（第 164 行之后）。
- 接口：`public static int generateAll(Path, List<ConfigFile>, List<String>)`，与 `EnchantableTags` 同构；`replace: false`、values 取 `enchantments/*.json5` 的 `id`、升序去重、文本相等不重写。
- 在制品：T-0019 + T-0020 = **2/3**（T-0018 已 VERIFIED 出列）。

## 6. 环境与噪声

- 构建 `gradle build --offline` exit 0（**334577 B**）、`selftest` **70 PASS**、`offline_check` 未单跑（本轮无编译候选）。
- 测试服起停 **2 次**：`Done (2.037s)` / `Done (2.064s)`，均已停、25567/25577 无 LISTEN。
- redtale 零 ERROR/WARN；4 条 `RecipeManager` 解析失败 + 若干第三方 `refmap.json` WARN 是既有噪声（R7 起就在）。
- 构建锁已删（`.queue/build.lock`）。

## 7. M4 出口盘点

| 项 | 状态 |
|---|---|
| V1 effect 注册 / V2 生成合法 / V3 进注册表 / V7 互斥 / V8 数值 / V9 脚本 / V10 缺脚本不崩 / V11 只写 JSON5 | **绿（8/11）** |
| V4 附魔台可出 | 缺 tag（T-0020）+ 需客户端 GUI |
| V5 铁砧可合 / V6 砂轮可去 | 需客户端 GUI |

## 8. 未验证 / 挂账

- `heal` 的 `flat` 参数与 `per_level` 在**等级 1** 时的边界（应等于 `ratio`）未单独跑 —— 由公式与 `Math.max(0, level-1)` 保证，未实测。
- V4/V5/V6 与「附魔 tooltip 中文名」仍需一次带客户端的窗口。
- `target` 分支的非零回血没跑出来（村民试过两次都被满血掩盖 / `/damage` 被 `resistance 4` = 100% 免伤挡住），只验到落点正确 —— 记入 R24-c 的排查提示。
