# 轮次文档索引（R63 建，`check_docs.py` 的孤儿判据）

> 为什么有这份文件：`docs/ROUND-R*.md` 写到现在 60 份，除了 `PLAN.md` §13 那张大表以外**没有任何入口**。
> R62 做 `docs/REALPLAYER-WINDOW.md` 时就是因为「29 条挂账散在 20 多个小节里，靠人肉记忆必漏」——
> 同一件事在轮次文档上又发生了一遍。本文件是它们的唯一入口。
>
> **维护规则**：每轮收尾**新加一份 `ROUND-*` 就必须在上表追加一行**（一行就够：轮次 · 一句话 · 路径）。
> 不做的话 `check_docs.py` 会给一条 `[提示] 未被任何文档引用的孤儿：docs/ROUND-....md`——
> 提示不计问题数、不影响退出码，但它是「这份文档以后没人找得到」的唯一信号。
>
> 表格按轮次号排序；早期两份标题里没写轮次号（`ROUND-M1-ATTR` / `ROUND-M3`）按时间顺序补成 R2 / R3。
> 口径与决策记在 `PLAN.md` §13（每轮四~六条），本表只做导航，不承载结论。

| 轮次 | 一句话 | 文件 |
|---|---|---|
| R1 | M0 地基（2026-11-15） | `docs/ROUND-M0.md` |
| R2 | M1-ATTR · 80 条属性注册 + 来源拆解（2026-11-15 19:54–20:10） | `docs/ROUND-M1-ATTR.md` |
| R3 | M3 · 数值核心 + 声明式攻速 + 第一个 mixin | `docs/ROUND-M3.md` |
| R4 | M1 收尾：L2 伤害乘区（2026-11-15 21:55~22:05） | `docs/ROUND-M4-DAMAGE.md` |
| R5 | M2 第一步：内容数据组件 + 14 基底物品 + 白板化 + tooltip | `docs/ROUND-R5-ITEMS.md` |
| R6 | M2 第二步：条件乘区（pve / pvp / undead） | `docs/ROUND-R6-COND.md` |
| R7 | 验收 T-0001 + 打通武器动作链路 | `docs/ROUND-R7-WEAPON.md` |
| R8 | 数据包自动启用（R7 遗留闭合）+ 验收 T-0002 | `docs/ROUND-R8-PACK.md` |
| R9 | 近战暴击入口（M2） | `docs/ROUND-R9-CRIT.md` |
| R11 | 词条 roll 落地（D22 第二层）+ 验收 T-0004 | `docs/ROUND-R11-AFFIX.md` |
| R12 | 物品更新落地（D33 的 `ensure` + 三个刷新点） | `docs/ROUND-R12-UPGRADE.md` |
| R14 | D33 第 ④ 个刷新点（reload 后分帧全量刷新） | `docs/ROUND-R14-SWEEP.md` |
| R15 | 消耗品右键生效（D23 第二层）+ 验收 T-0008 | `docs/ROUND-R15-CONSUME.md` |
| R16 | 修理结算上线（D23 第二半）+ 验收 T-0009 / T-0010 | `docs/ROUND-R16-REPAIR.md` |
| R17 | 2026-11-16 12:31-12:45 · 验收 T-0011 + D33 惰性兜底 ⑤（结算路径） | `docs/ROUND-R17-LAZY.md` |
| R18 | 2026-11-16 13:39-14:0x · 验收 T-0012 + M3 第 3 步（`ContentData` 容器扩容 / schema 1→2） | `docs/ROUND-R18-SCHEMA.md` |
| R19 | M3 第 4 步：镶嵌 / 取出结算 + 孔位接线 | `docs/ROUND-R19-SOCKET.md` |
| R20 | M3 第 6 步：套装件数统计 + 属性下发 | `docs/ROUND-R20-SET.md` |
| R21 | M4 开工：`redtale_project:scripted` 注册成功 + L1→L0 生成接线 | `docs/ROUND-R21-M4.md` |
| R22 | M4 第二块：`post_attack` → 脚本层真跑通（V9/V10）+ V8 数值对账 | `docs/ROUND-R22-M4B.md` |
| R23 | M4 第三块：基底附魔归属（内容武器终于吃得上附魔）+ 空互斥 tag 收口 | `docs/ROUND-R23-TAGS.md` |
| R24 | `soul_reap` 真回血（M4 V9 落地）+ V11 硬指标演练 | `docs/ROUND-R24-HEAL.md` |
| R25 | M5 开工：技能 schema + 24 条原语 + 派 T-0021 | `docs/ROUND-R25-SKILLS.md` |
| R26 | 2026-11-17 05:22–06:10 · T-0019 验收（含测试服）+ 派 T-0022 + 伤害类别池落地 | `docs/ROUND-R26-CATEGORY.md` |
| R27 | 2026-11-17 06:29-07:05 · M4 收尾（V4 可获取性 tag）+ M5-A 第一条原语 | `docs/ROUND-R27-OBTAIN.md` |
| R28 | M5-A 第 1 步：技能快照层 + 验收 T-0021 | `docs/ROUND-R28-SKILLREG.md` |
| R29 | M5-A 引擎（`SkillEngine` + `/redtale skill`） | `docs/ROUND-R29-SKILLENGINE.md` |
| R30 | M5-B 物品绑定技能 + 冷却减免（2026-11-17 10:31-10:47） | `docs/ROUND-R30-M5B.md` |
| R31 | 验收 T-0025 + `/redtale skill info` + 派 T-0027 | `docs/ROUND-R31-SKILLINFO.md` |
| R32 | 验收 T-0026（`buff`）+ M5-C 放量（`damage_aoe` / `debuff`） | `docs/ROUND-R32-PRIMITIVES.md` |
| R33 | 2026-11-17 · 验收 T-0027 + M5-C 控场前两条（`knockback` / `pull`） | `docs/ROUND-R33-SHUFFLE.md` |
| R34 | `stun` 原语（M5-C 控件第 3 条）+ 验收 T-0028 + 派 T-0030 | `docs/ROUND-R34-STUN.md` |
| R35 | M5 收口第一步：法力进引擎（`mana_cost` + 三条属性 + 六组探针） | `docs/ROUND-R35-MANA.md` |
| R36 | 验收 T-0030 + M6 第二层（`MobRegistry` 快照） | `docs/ROUND-R36-MOBREG.md` |
| R37 | 验收 T-0031（`dash`）+ 8 个模板 EntityType（M6 第三步第 1 小步） | `docs/ROUND-R37-DASH.md` |
| R38 | 验收 T-0032（`/redtale mob check`）+ `MobSpawner` + `/redtale mob spawn`（M6 第三步第 2 小步） | `docs/ROUND-R38-SPAWN.md` |
| R39 | 2026-11-17 · 验收 T-0033 + M6 第三步配套（`#minecraft:undead`）+ D14 资源重载补漏 | `docs/ROUND-R39-UNDEAD.md` |
| R40 | 2026-11-18 · 验收 T-0034/T-0035 + 怪物技能释放（M6 第三步第 3 小步） | `docs/ROUND-R40-MOBCAST.md` |
| R41 | M6 第四层：掉落接线（`MobSpec.drops` → `DropSpec` → `DropRoll` → 地上的物品） | `docs/ROUND-R41-DROPS.md` |
| R42 | 验收 T-0037 + M7 契约定稿 + D26 钩子核验 | `docs/ROUND-R42-PROGRESSION.md` |
| R43 | 2026-11-18 09:18-09:35 · 验收 T-0038 + M7 第三层存档落地 | `docs/ROUND-R43-PROGRESS.md` |
| R44 | 验收 T-0039 + M7 第二层快照（ProgressRegistry / ProgressRules） | `docs/ROUND-R44-PROGRESSION.md` |
| R45 | 2026-11-18 11:43-12:1x · 验收 T-0040 + M7 L4（`ProgressService`）+ 派 T-0041 | `docs/ROUND-R45-LEVELCMD.md` |
| R46 | 验收 T-0041 + M7 L5 `grant_exp` + D26 schema 定稿 | `docs/ROUND-R46-EXP.md` |
| R47 | 2026-11-18 · 验收 T-0042 + M6 第二层刷怪快照（D26） | `docs/ROUND-R47-SPAWNREG.md` |
| R48 | M6 第三层：自然刷怪替换（`SpawnReplacer`） | `docs/ROUND-R48-SPAWNREPLACE.md` |
| R49 | 2026-11-18 · 验收 T-0044 + `/redtale level info` 的「当前」段 + 派 T-0045 | `docs/ROUND-R49-LEVELNOW.md` |
| R50 | M7 技能树 L2 快照层 + 验收 T-0045 | `docs/ROUND-R50-TREE.md` |
| R51 | 2026-11-18 18:59-19:2x · 验收 T-0046 + 存档 v2（技能树分树持久化）+ 派 T-0047 | `docs/ROUND-R51-STORE.md` |
| R52 | 验收 T-0047 + `spawn.json5` 开关持久化（M6 收口）+ 派 T-0048 | `docs/ROUND-R52-SPAWNTOGGLE.md` |
| R53 | 验收 T-0048 + M7 L5 `TreeService`（技能树第一次真正影响面板） | `docs/ROUND-R53-TREESERVICE.md` |
| R54 | 等级 → 技能点（M7 出口）+ 验收 T-0049 | `docs/ROUND-R54-POINTS.md` |
| R55 | 2026-11-18 23:4x-00:2x · 验收 T-0050 + `per_tick_cap` 落盘（M6 最后一个挂账收口） | `docs/ROUND-R55-CAP.md` |
| R56 | 验收 T-0051（`check_items`）+ `tree info` 补「当前等级能拿多少点」+ 派 T-0052 | `docs/ROUND-R56-TREEINFO.md` |
| R57 | 验收 T-0052 + `unlock_skill` 接服务层（M7 出口闭环） | `docs/ROUND-R57-UNLOCK.md` |
| R58 | 验收 T-0053 + `tree skills <tree>` 观测面 + 派 T-0054 | `docs/ROUND-R58-SKILLS.md` |
| R59 | 验收 T-0054 + 修 `gen_attr_lang.py` 的破坏性覆写 + 对账总入口 | `docs/ROUND-R59-LANG.md` |
| R60 | 验收 T-0055 + `check_deps.py`（依赖面 / 命名面）+ 派 T-0056 | `docs/ROUND-R60-DEPS.md` |
| R61 | 验收 T-0056（`check_attrs.py`）+ 接线 `check_all`（10→11）+ 派 T-0057 | `docs/ROUND-R61-ATTRS.md` |
| R62 | 验收 `check_numeric` + 契约面立项 + 真人窗口清单 | `docs/ROUND-R62-NUMERIC.md` |
| R63 | 验收 `check_contracts` + 文档面收口（索引 / 路径 / 提示清零） | `docs/ROUND-R63-DOCS.md` |
| R64 | 验收 `check_docs` + 接线 `check_all`（13→14）+ D24 专业契约 + 派 T-0060 | `docs/ROUND-R64-PROFSPEC.md` |
| R65 | 验收 T-0060（`ProfessionSpec`）+ D24 L2 `ProfessionRegistry` + 两份默认件 + 派 T-0061 | `docs/ROUND-R65-PROFREG.md` |
| R66 | 验收 T-0061（`check_tasks.py`）+ 接线 14→15 + 派 T-0062 + D24 L3+L4（存档 v3 / 三事件 / 属性下发 / `exp profession`） | `docs/ROUND-R66-PROFESS.md` |
| R67 | 验收 T-0062（`check_professions.py`）+ 接线 15→16 + 派 T-0063（`/redtale profession list\|info`）+ 真人窗口 F 组（专业 P1~P5） | `docs/ROUND-R67-PROFFIX.md` |
| R68 | 验收 T-0063（`/redtale profession list\|info`，D24 L5 收口）+ 派 T-0064（`DisplaySpec`，Q-023 默认 A 的 L1） | `docs/ROUND-R68-PROFFIX-VERIFY.md` |
| R69 | 验收 T-0064（`DisplaySpec`）+ Q-023 L2 落地（`display.json5` / DPS 行 / 属性明细行）+ 派 T-0065（`/redtale display`） | `docs/ROUND-R69-DISPLAY.md` |
| R70 | 验收 T-0065（`/redtale display`，Q-023 收口）+ 派 T-0066（`SpawnBudget` 纯函数收口 per_tick_cap 双份真相）+ CONTRACTS §4.41 登记显示观测面 | `docs/ROUND-R70-DISPLAY.md` |
| R71 | 补完中断轮：验收 T-0066（`SpawnBudget`）+ 接两处委托 + 测试服验两路径语义（命令守卫 vs 配置回落）+ 派 T-0067（`check_spawn.py`） | `docs/ROUND-R71-BUDGET.md` |
| R72 | 验收 T-0067（`check_spawn.py`）+ 接线 `check_all` 16→17 + 派 T-0068（`ConfigDiff` 纯函数） | `docs/ROUND-R72-CHECKSPAWN.md` |
| R73 | 验收 T-0068（`ConfigDiff` 纯函数）+ 接过接线（`ConfigStore.mtimes()` + reload 变更摘要，测试服验四形态）+ 派 T-0069（`check_display.py`） | `docs/ROUND-R73-RELOADDIFF.md` |
| R74 | 验收 T-0069（`check_display.py`）+ 接线 `check_all` 17→18 + 派 T-0070（`check_checkall.py`，清单安全网） | `docs/ROUND-R74-CHECKLIST.md` |
| R75 | 验收 T-0070（`check_checkall.py`）+ 接线 `check_all` 18→19 + 派 T-0071（`check_commands.py`，命令树挂载对账） | `docs/ROUND-R75-COMMANDTREE.md` |
| R76 | 验收 T-0071（`check_commands.py`）+ 接线 `check_all` 19→20 + 派 T-0072（原语注册对账）+ 修 `SKILLS.md` projectile 表漂移 | `docs/ROUND-R76-REGISTRY.md` |
| R77 | 验收 T-0072（`check_primitives.py`）+ 接线 `check_all` 20→21 + 派 T-0073（lang 生成器前缀对账） | `docs/ROUND-R77-PREFIX.md` |
| R78 | 验收 T-0073 + 接线 `check_all` 21→22 + 派 T-0074（轮次索引对账） | `docs/ROUND-R78-INDEX.md` |
| R79 | 验收 T-0074 + 接线 `check_all` 22→23 + 派 T-0075（原语 id ↔ 原语表对账） | `docs/ROUND-R79-PRIMID.md` |
| R80 | 验收 T-0075 + 接线 `check_all` 23→24 + 派 T-0076（lang 文件内重复键对账） | `docs/ROUND-R80-LANGDUP.md` |
| R81 | 验收 T-0076 + 接线 `check_all` 24→25 + 派 T-0077（在制品卡独占文件重叠对账） | `docs/ROUND-R81-OWNERSHIP.md` |
| R82 | 验收 T-0077 + 接线 `check_all` 25→26 + 派 T-0078（事件监听器注册对账） | `docs/ROUND-R82-EVENTREG.md` |
| R83 | 验收 T-0078 + 接线 `check_all` 26→27 + 派 T-0079（伤害类型引用 ↔ 注册对账） | `docs/ROUND-R83-DAMAGETYPE.md` |
| R84 | 验收 T-0079 + 接线 `check_all` 27→28 + 派 T-0080（刷怪规则 conditions 六维值域对账） | `docs/ROUND-R84-SPAWNCOND.md` |
| R85 | 验收 T-0080 + 接线 `check_all` 28→29 + 派 T-0081（技能 conditions args 值域对账） | `docs/ROUND-R85-SKILLARGS.md` |
| R86 | 验收 T-0081 + 接线 `check_all` 29→30 + 派 T-0082（脚本 conditions args 值域对账） | `docs/ROUND-R86-SCRIPTARGS.md` |
| R87 | 验收 T-0082 + 接线 `check_all` 30→31 + 派 T-0083（效果 target 枚举值域对账） | `docs/ROUND-R87-EFFECTTARGET.md` |
| R88 | 验收 T-0083 + 接线 `check_all` 31→32 + 派 T-0084（怪物装备物品 id ↔ 14 基底对账） | `docs/ROUND-R88-MOBEQUIP.md` |
| R89 | 验收 T-0084 + 接线 `check_all` 32→33 + 派 T-0085（消耗品 use.script 原语 id 对账） | `docs/ROUND-R89-USESCRIPT.md` |
| R90 | 验收 T-0085 + 接线 `check_all` 33→34 + 派 T-0086（伤害乘区 damage_types 白名单形态对账） | `docs/ROUND-R90-DMGWHITELIST.md` |
| R91 | 验收 T-0086 + 接线 `check_all` 34→35 + 派 T-0087（技能树 requires 可达性对账） | `docs/ROUND-R91-TREEREACH.md` |
| R92 | 验收 T-0087 + 接线 `check_all` 35→36 + 派 T-0088（附魔 exclusive_set 引用形态对账） | `docs/ROUND-R92-ENCHEXCLUSIVE.md` |
| R93 | 验收 T-0088 + 接线 `check_all` 36→37 + 派 T-0089（宝石类型孔位可达性对账） | `docs/ROUND-R93-GEMTYPES.md` |
| R94 | 验收 T-0089 + 接线 `check_all` 37→38 + 派 T-0090（附魔脚本参数同步对账） | `docs/ROUND-R94-ENCHARGSYNC.md` |
| R95 | 验收 T-0090 + 接线 `check_all` 38→39 + 派 T-0091（词条等级可达性对账） | `docs/ROUND-R95-AFFIXREACH.md` |
| R96 | 验收 T-0091 + 接线 `check_all` 39→40 + 派 T-0092（套装档位装备槽可达性对账） | `docs/ROUND-R96-SETREACH.md` |
| R97 | 验收 T-0092 + 接线 `check_all` 40→41 + 派 T-0093（物品绑定技能完整 id 引用对账） | `docs/ROUND-R97-ABILITYREFS.md` |
| R98 | 验收 T-0093 + 接线 `check_all` 41→42 + 派 T-0094（怪物绑定技能完整 id 引用对账） | `docs/ROUND-R98-MOBSKILLS.md` |
| R99 | 验收 T-0094 + 接线 `check_all` 42→43 + 派 T-0095（词条池词条 id 完整字符串引用对账） | `docs/ROUND-R99-AFFIXPOOLREF.md` |
