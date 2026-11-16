# R26 · 2026-11-17 05:22–06:10 · T-0019 验收（含测试服）+ 派 T-0022 + 伤害类别池落地

## 一、卡板

| 卡 | 状态变化 | 依据 |
|---|---|---|
| T-0019 `/redtale enchant list` | `[DONE]` → **`[VERIFIED]`** | build exit 0；`grep -c EnchantListCommand.node()`=1；jar 内该类 1 条；测试服 RCON 实测 + 负向验证（见下） |
| T-0020 附魔可获取性 tag | 仍 `OPEN`（11-16 21:15 派，两轮无人领） | — |
| T-0021 `SkillSpec` 解析器 | 仍 `OPEN`（R25 派） | — |
| T-0022 `SkillCooldowns` 冷却表 | **新派**（05:52） | 在制品 3/3 封顶，本轮只派这一张 |

## 二、T-0019 验收记录（S7 跑的）

命令链：

```
gradle build --offline            → EXIT=0，jar 340493 B
grep -c EnchantListCommand.node() → 1
unzip -l … | grep EnchantListCommand → 1 条
```

测试服（jar 已入 `E:\server-test\mods`，`Done (2.353s)`，RCON 25577）：

```
> redtale enchant list
已生成附魔 3 条
  redtale_project:frost_edge · L3 · #minecraft:enchantable/sharp_weapon · minecraft:damage
  redtale_project:soul_reap · L2 · #minecraft:enchantable/sharp_weapon · minecraft:post_attack
  redtale_project:super_sharpness · L3 · #minecraft:enchantable/sharp_weapon · minecraft:damage
```

- **3 条 vs 卡面预期 2 条**：`data/redtale_project/enchantment/frost_edge.json` 是 R24 手测留下的落盘产物
  （`config/redtale_project/enchantments/` 里只有 `soul_reap` / `super_sharpness` 两份）。
  命令读的是**生成结果**，多出来的这条正是「配置改了但数据包没重新生成」的可视化——**不算缺陷**，记进台账。
- 三行的 `id` / `max_level` / `supported_items` / 触发组件与磁盘 JSON 逐字段核对一致，升序正确。
- 负向验证：`mv config/redtale_project/scripts scripts.off` → `soul_reap` 行尾出现 ` · 脚本不存在`；改回即消失。
- 采纳 S8 的口径偏差：默认脚本写的是 `id: 'enchants/soul_reap'`（单引号），卡面只写了双引号两种形态，
  照抄会误报。实装正则键与值都允许单双引号。

## 三、自己写的：伤害类别池（Q-018 默认 A 落地）

| 文件 | 改动 |
|---|---|
| `core/damage/MultiplierRules.java` | 加第 5 分量 `List<Category>`；`record Category(String match, List<String> attributes)`；新增 `attributesFor(String)`（first-match，未命回落 `percentAttributes`）；把匹配抽成 `normalise` / `matches`，`appliesTo` 与 `attributesFor` 共用；`parse` 支持 `category_attributes`；`describe()` 打类别池 |
| `core/damage/DamageLayer.java` | 第 90 行：`multiplier.percentAttributes()` → `multiplier.attributesFor(typeId)` |
| `data/redtale_project/damage_type/skill_physical.json` / `skill_magical.json` | **新增两个冻结伤害类型**（`effects: hurt`，`scaling: never`） |
| `assets/redtale_project/lang/{en_us,zh_cn}.json` | 两条死亡提示 `death.attack.redtale_skill_physical` / `_magical` |
| `redtale/defaults/damage.json5` | `multiplier` 段加 `category_attributes`（缺省 = 两条默认；显式 `[]` = 关分轴） |
| `src/selftest/.../SelfTest.java` | +8 条断言（默认两轴 / 回落 / 自定义解析 / 坏项告警 / `[]` 关轴 / first-match） |

为什么不是「每个技能一个 `damage_type`」：`damage_type` 是启动期 datapack registry，按内容注册要重启，
撞 D17。两个通用类型 + 一张映射表，加技能只写 JSON5（R26-a）。

线上实测：

```
> redtale damage
  乘区：伤害类型 redtale_project:* · 白名单外只观测不改值 · 百分比属性 physical_damage ·
        类别池 redtale_project:skill_magical=magical_damage / redtale_project:skill_physical=physical_damage ·
        条件乘区 pvp=pvp_damage / pve=pve_damage / undead=undead_damage
> damage @e[type=pig,limit=1] 4 redtale_project:skill_magical   → Applied 4.0 damage to Pig
> damage @e[type=pig,limit=1] 4 redtale_project:skill_physical   → Target is invulnerable（无敌帧，正常）
> redtale damage → 观测：进场 2 次 · 白名单内 2 次 · 实际改值 0 次
```

「进场 2 / 白名单内 2」= 两个新类型都注册成功且都命中白名单；「改值 0」= 无攻击者时属性池为 0，符合预期。

## 四、机器断言

| 断言 | 值 |
|---|---|
| `gradle build --offline` | exit 0，jar **344079 B** |
| `gradle selftest --offline` | **78 PASS**（上轮 70，+8） |
| `grep -c "EnchantListCommand.node()"` | 1 |
| `grep -c "^| [0-9]" docs/SKILLS.md` | 24 |
| 测试服启停 | 两次都 `Done` + `All dimensions are saved` 干净退出，`console_r26.log` / `console_r26b.log` |

## 五、卡点 / 下一步

- **M4 出口仍 8/11**：V4（附魔台）/ V5（铁砧）/ V6（砂轮）全是 GUI 面，等一次带客户端的窗口；V4 另依赖 T-0020。
- **T-0020 两轮无人领**：R27 若仍 `OPEN` → 收回自做（`DROPPED (S8 静默，S7 自做)`）。
- R27 计划：验 T-0020/T-0021/T-0022；`SkillEngine`（引擎 + `/redtale skill list|cast` + 默认两份 `skills/*.json5`）。
