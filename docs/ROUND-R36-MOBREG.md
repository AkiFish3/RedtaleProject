# R36 · 验收 T-0030 + M6 第二层（`MobRegistry` 快照）

> 2026-11-17 17:30-18:10 · 里程碑 **M6 怪物**（第一层已验、第二层落地）· 在制品 T-0031 + T-0032 = 2/3

## 1. 卡板

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0030 `mobs/MobSpec` | `[DONE]` → **`[VERIFIED]`** | 3/3 全过；S8 探针 90/90，S7 独立探针 `S7MobSpecProbe` **71/71** |
| T-0031 `dash` 原语 | 保持 `[OPEN]`（S8 本轮未交付） | — |
| T-0032 `/redtale mob check` | **新派**（M6 配套观测面） | 独占 `MobCheckCommand.java` + `RedtaleCommand` 1 行 |

T-0030 的四处口径偏差全部采纳（不改卡面）：
1. `attributes` 套 `{stats: …}` 载体转手给 `AffixPools.parseStats` —— 归一规则仍只有那一份（实测裸名 `attack_damage` → `minecraft:generic.attack_damage`）。
2. `scale <= 0` / 非有限 → **静默**回落 1.0（卡面括号里写的是「非数字才告警」）。
3. `cooldown_ticks` 非数字补一条告警（卡面只给 `chance` 写了告警，这里与既有解析器同口径）。
4. base 非法的条目不占 id（实测「坏 base + 好 base 同 id」→ 好那条被收下）。

**探针教训**：第一版断言按「越界必告警」写（`chance: 3` / `cooldown_ticks: -5`），红了两条 —— 卡面明写「夹取本身不告警」。夹取静默是对的，改断言不改代码。

## 2. 自研：M6 第二层

新类 `mobs/MobRegistry`（与 `core/skill/SkillRegistry` 同构）：

| 方法 | 用途 |
|---|---|
| `EMPTY` / `load(all, warnings)` | 空快照 / 从 `ConfigFile` 列表建快照（只挑 `mobs/`，告警随快照保存） |
| `all()` / `get(id)` / `has(id)` / `size()` | 不可变视图（保文件顺序）+ 空安全取值 |
| `sortedIds()` | 升序 id（对账用） |
| `warnings()` | 本轮的解析告警（换代时一起换掉） |
| `countByBase()` / `skillCount()` | 按原型分件数 / 技能引用总数（`describe` 的素材） |
| `describe()` | `N 只怪物（M 条技能引用，原型 K 种）` |

接线：`RedtaleCore.mobs()` + `reload()` 一行 + `reload` 日志与回显带上怪物数；默认内容进 `DEFAULT_FILES`：
- `mobs/ember_guard.json5`：`elite` / `scale 1.15` / `level 12` / 剑 + 胸甲 / 1 条技能（`gale_slash`）
- `mobs/ash_caster.json5`：`caster` / `max_mana 80` + `mana_regeneration 2` + `magical_damage` + `fire_damage` / 法杖 + 头盔 / 2 条技能

## 3. 实测（测试服 1 次启动 `Done (2.239s)`，已停）

| 证据 | 结果 |
|---|---|
| 启动日志 | `配置重载完成：2 条脚本，28 个文件，10 件装备内容，3 条技能（6 条 effect，原语 4 种），2 只怪物（3 条技能引用，原型 2 种）` |
| 配置告警 | **0 条**（两份默认件的属性键全部命中 80 条表） |
| `/redtale reload` | `2 条脚本 + 10 件装备内容 + 3 条技能 + 2 只怪物（28 份配置文件）` |
| `/redtale validate` | `问题 0 条`（无回归） |
| 热加（D17） | 手写 `mobs/t_probe_mob.json5`（`charger`）→ **2→3 只（29 份）**；删文件 → **回落 2 只（28 份）**；全程零 jar 更换 |
| 端口 | 25567 / 25577 无 LISTEN，`All dimensions are saved` |

`ERROR` 仍只有 4 条 `RecipeManager` 既有噪声（simplyswords ×2 / brewinandchewin / minecraft:red_dye，R8 起就有）。

## 4. 机器断言

```
gradle build --offline            → EXIT=0，jar 445907 B
gradle selftest --offline         → 78 PASS（无回归）
S7MobSpecProbe                    → 71/71
S7MobRegProbe                     → 39/39
grep -c net.minecraft .../mobs/MobSpec.java → 0
wc -l .../mobs/MobSpec.java       → 197
```

## 5. 三条口径（→ `PLAN.md` §13.39）

- **R36-a** 怪物层与技能层同构三层：`MobSpec`（纯解析）/ `MobRegistry`（快照）/ 后续 `MobSpawner`（执行）。**快照层先落地，命令卡才许派** —— 否则命令只能自己再解析一遍配置（第二份真相）。
- **R36-b** `describe()` 是「解析到底吃到内容没有」的唯一廉价证据：`2 只怪物（3 条技能引用，原型 2 种）` 一次证实三件事（两份默认件都进了 / 技能引用条数对 / base 只用 2 种）。只报 `size()` 看不出「技能段被丢」这类半失效。
- **R36-c** 默认 `mobs/*.json5` 的每条属性键必须命中 80 条表，否则 `AffixPools` 静默丢弃。`ash_caster` 初稿写了 `spell_power`（上游名，D11 排除），改 `magical_damage` + `fire_damage` 后零告警 —— 验法是「零告警」反证。

## 6. 未验证（挂账）

- 真人刷怪 / 实体生成 / 模型与体积（M6 第三、四步，`EntityType` 注册还没做）。
- `MobSpec` 的 `Equipment`/`SkillRef` 在实体上真正生效（要等装备六槽装配）。
- 旧挂账不变：V4 附魔台真刷出、tooltip 真人客户端、pvp 真目标、玩家被 `stun`、真人 `/redtale skill mana`。
