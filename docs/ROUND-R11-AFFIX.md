# R11 · 词条 roll 落地（D22 第二层）+ 验收 T-0004

2026-11-16 05:54-06:30 · S7 移植队列（兼 S8 派单/验收）

## 1. 卡板：验收 T-0004，派 T-0007

| 卡 | 动作 | 结果 |
|---|---|---|
| T-0004 附魔 schema → datapack 生成器 | 验收 | **VERIFIED**（3/3 + 转换正确性 15/15） |
| T-0006 `ItemUpgrader.plan` | — | 仍 `OPEN`（S8 未领） |
| T-0007 `/redtale item scan` | 新派 | `OPEN`，在制品 2/3 |

**T-0004 验收证据**

1. `gradle build --offline` exit 0（jar 188144 B）。
2. `grep -c "public static int generateAll"` = 1。
3. `unzip -l | grep -c "enchants/EnchantmentGenerator"` = 2（主类 + `$Emit`）。
4. 转换正确性——离线探针 `build/probe/EnchProbe.java`（jar + server-test `libraries`，真 `ResourceLocation.tryParse`），**15/15 PASS**：
   6 份输入 → written=**3**；顶层键序 `description,max_level,weight,anvil_cost,slots,supported_items,min_cost,effects`；
   `description` = `{"translate":"enchantment.redtale_project.super_sharpness"}`、文件内零中文；
   `effects` 原样透传（`minecraft:linear` 未变形）；`primary_items: null` 不写；
   子目录 id → `data/redtale_project/enchantment/combat/soul_reap.json` 且 lang key 用点号；
   外部 ns → `data/othermod/enchantment/custom.json`；缺 `supported_items` → 1 条告警并跳过；
   `enchantments/notyn5.json`（后缀不符）与 `items/foo.json5`（前缀不符）都不处理；二次生成字节不变。

S8 提的两处偏差（`JsonNull` 视缺省不告警 / `effects` 非必填）**认账不打回**：`ENCHANT-API.md` §3 样例把
`primary_items: null` 当正常写法，卡面的「JsonNull → 告警」与文档冲突，取文档。

## 2. 自研：词条 roll（D22 第二层）

### 2.1 改了什么

| 文件 | 改动 |
|---|---|
| `items/AffixPools.java` | `Def.stats` 从 `Map<String,Double>` 升到 `Map<String,StatValue>`（R9 记的欠账）；`stats` 解析同时收「裸数字」与「曲线对象 `{base, per_item_level, spread, max_spread}`」；**键做属性归一**（`AttributeSpecs.resolve` → 全名），未知属性告警跳过 |
| `items/AffixRoller.java`（新） | 纯函数 roll：`poolFor(pools, 基底)` + `roll(pool, defs, itemLevel, Random, warnings)`；槽位 / 等级区间 / 同组互斥 / 不放回 / 数值累加 |
| `items/ItemFactory.java` | `build(content)` → `build(content, Random)`；roll 结果三样一起写进 `ContentData`：数值并进 `rolledStats`、键进 `rolledKeys`、id 进 `affixes`；`modifiers` 用**合并后**的 stats 建面板；新增 `ITEM_LEVEL = 1` 与 `rollAffixes(...)` |
| `items/ContentTooltip.java` | 新增一行 `词条 N：a · b`（只列 id，不重复列数值——数值已在上面的伤害/攻速行体现） |
| `core/RedtaleCore.java` | reload 时 `parseDefs` / `parsePools`，加 `affixDefs()` / `affixPools()` 两个 volatile 快照 |
| `core/config/ConfigStore.java` | `DEFAULT_FILES` 加 4 份（3 词条定义 + 1 词条池），否则新装环境永远摇不出词条 |
| `src/main/resources/redtale/defaults/{affixes,affix_pools}/` | `sharp` / `heavy` / `of_the_bear` + `sword_common` 池 |
| `src/selftest/.../SelfTest.java` | 新增 `affix()` 15 项（解析两种写法、池命中、等级区间、同组互斥、不放回、null 安全） |

### 2.2 为什么「键必须归一」

词条数值会并进 `ContentData.rolledStats`，而 `ItemFactory.modifiers` 用 `modifierId(属性路径)` 当 id。
同一个属性同时出现 `attack_damage` 与 `minecraft:generic.attack_damage` 两种写法时，会往**同一个 id** 上加两条
modifier —— 面板直接翻倍。所以 `AffixPools.stats` 与 `ItemContent.parse` 用同一条归一路径，重复键告警保先出现的。

### 2.3 实测（测试服 `E:\server-test`，`Done (` 后）

`/redtale reload` → `已载入 1 条脚本 + 4 件装备内容（13 份配置文件）`（原 9 份 + 新增 4 份词条配置）。

`/redtale tooltip demo_longsword` 连打三次（每次新建栈 = 每次重摇）：

| 次 | 词条 | 单次伤害 | 对账 |
|---|---|---|---|
| 1 | `sharp · of_the_bear` | ≈ **12.096** /次（physical +20%） | (1 + 7 + sharp 2.08) × 1.2 = **12.096** |
| 2 | `heavy · of_the_bear` | ≈ **15** /次（physical +25%） | (1 + 7 + 4) × (1 + 0.2 + 0.05) = **15** |
| 3 | `heavy · of_the_bear` | ≈ **15** /次 | 同上 |

三条结论：
- **同组互斥生效**：`sharp`（group=damage）与 `heavy`（同组）从不共存。
- **曲线形态生效**：`sharp` 的 `per_item_level 0.08` 在等级 1 贡献 +0.08（2.0 → 2.08），与 `StatValue.valueAt` 一致。
- **数值进的是同一个池**：`heavy` 的 `physical_damage 0.05` 与内容本身的 0.2 相加成 25%（R6-a 口径），不是连乘。

服务端零 ERROR（4 条 `RecipeManager` 解析失败是 R8 起就有的 simplyswords/brewinandchewin 噪声，不归我们）。
测试服 `stop` 后 25567 无监听。

### 2.4 未做（下轮）

- 词条**重铸**（重 roll + 扣费，D28 经济桥）与 `/redtale affix` 命令（要看分布/手动 roll 才需要）。
- `ItemUpgrader` 里对 `affixes` 的过滤（等 T-0006 交付；R11 已把「词条键进 `rolledKeys`」铺好，迁移走「在 rolledKeys 里 → 保旧值」这条规则）。
- `/redtale content list` 的「词条」列仍是占位（S8 的卡 T-0002 已 VERIFIED → 文件归我；下轮换真实计数，避免与 T-0007 的 `RedtaleCommand` 插入冲突）。

## 3. 这一轮的口径（供后续沿用）

| # | 口径 | 理由 |
|---|---|---|
| R11-a | 词条统计口径：**数值并进 `rolledStats`，`rolledKeys` 记键，`affixes` 记 id**，三者一次写全 | tooltip / 结算 / 迁移三处读同一份；少写一样就会出现「tooltip 有词条、面板没数值」 |
| R11-b | 属性键在**解析期**归一（`AttributeSpecs.resolve`），不在消费期归一 | 消费期（`modifiers`）归一只能救显示，救不了「同 id 两条 modifier」；重复键必须在数据进内存前就消失 |
| R11-c | roll 走 `java.util.Random`，不走 `RandomSource` | `AffixRoller` 要能被 `gradle selftest` 离线跑（零 MC 依赖）；seed 固定即可验收 |
| R11-d | 物品等级固定 `ITEM_LEVEL = 1` | 等级系统（M3）未接；写成一个常量，接上时只改一处，`per_item_level` 的行为立刻可预测 |
| R11-e | 默认词条配置随 jar 下发（`DEFAULT_FILES`），**不是**只在 `resources` 里躺着 | `ensureDefaults` 是白名单机制，不进清单 = 新环境没有词条池 = 静默无词条 |
