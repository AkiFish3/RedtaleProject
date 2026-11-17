# R41 · M6 第四层：掉落接线（`MobSpec.drops` → `DropSpec` → `DropRoll` → 地上的物品）

日期：2026-11-18 06:46-07:05 · 里程碑：M6 第四层（死亡掉落）
卡板：验收 **T-0036**（`mobs/DropRoll`，3/3 + S7 独立探针 **27/27**）→ `[VERIFIED]`；**派 T-0037**（`/redtale drop list`），在制品 1/3。

## 1. 做了什么

| 层 | 类 | 变化 |
|---|---|---|
| 解析 | `mobs/MobSpec` | `Definition` **10 → 11 字段**（`+drops`，位置在 `skills` 之后、`source` 之前）；新 `drops(root,path,w)`：缺省 `""`、非字符串 → `"" + 1 WARN`、**trim 后原样保留裸 id** |
| 快照 | `core/RedtaleCore` | 新 `drops` 字段（`Map<String, DropSpec.Definition>`）+ `drops()` 访问器；`reload()` 里 `DropSpec.parse(all, warnings)`；回显/日志带「N 张掉落表」 |
| 掷骰 | `mobs/DropRoll` | S8 交付（T-0036），本轮验收，零改动 |
| 执行 | `mobs/MobDrops`（新） | `LivingDropsEvent` → 认 tag `redtale_mob/<id>` → 取 `def.drops()` → 表 → `DropRoll.roll` → `event.getDrops().add(new ItemEntity(...))`；`ExperienceOrb.award(level, pos, xp)` |
| 观测 | `core/command/MobCheckCommand` | 头行加「掉落表 N 张」；新问题类型「掉落表 <ref> 不存在（现有 N 张：…）」 |
| 内容 | `defaults/drops/ember_guard.json5`（新）、`defaults/mobs/ember_guard.json5`（+`drops:`） | `DEFAULT_FILES` +1 |
| 接线 | `core/hook/GameHooks` | 构造期注册 `new MobDrops()` |

## 2. 验收证据（测试服起停 3 次，均已停，25567/25577 无 LISTEN）

启动：`Done (2.137s)` / `Done (2.138s)` / `Done (2.108s)`；日志 `1 张掉落表`/`2 张掉落表`；redtale **零 ERROR / 零 WARN**，全局 `ERROR` 仍 4 条（RecipeManager 既有噪声）。

| 组 | 做法 | 结果 |
|---|---|---|
| A 基线 | `redtale mob check` | `2 只 / 技能引用 3 条 / 掉落表 1 张 · 引用完整 ✓` |
| B 确定性掉落 | 夹具 `drops/t_always.json5`（`minecraft:stone` 1~1 chance 1.0）+ `mobs/t_drop.json5` → spawn → `kill` | `execute as @e[type=item]` → `{count: 1, id: "minecraft:stone"}` |
| C 经验 | 同上（表 `xp: 7`） | `@e[type=experience_orb]` count ≥1，`Value` = **7s** |
| D 内容 id 掉落 | 表里写 `redtale_project:gem_ruby`（**全名**，走容错分支） | 掉出 `{id: "redtale_project:amulet", components: {redtale_project:content: {content_id: "gem_ruby"}, custom_name: "赤红宝石", rarity: rare}}` |
| E 概率条目 | `ember_guard` 表（金粒 0.8×2 / 火焰粉 0.35 / 红宝石 0.1）连杀 4 只 | 金粒聚合为 1 个物品实体 `count: 4`；火焰粉第 4 只出现；红宝石 4 只未出（0.1³ 量级正常） |
| F 悬空引用 | 夹具 `mobs/t_baddrop.json5`（`drops: "nope"`）→ reload | `mob check`：`4 只 / 掉落表 2 张 / 问题 1 个` + `掉落测试2 t_baddrop: 掉落表 nope 不存在（现有 2 张：ember_guard, t_always）`；spawn+kill 后日志 1 条 WARN `怪物 t_baddrop 引用的掉落表 nope 不存在（掉落按原版处理）`，服务端照跑 |
| G 清理回归 | 删夹具 → reload | `2 只 / 掉落表 1 张 / 引用完整 ✓`、`validate 问题 0 条`、29 份配置 |

构建：`gradle build --offline` EXIT=0（jar **499381 B**）；`gradle selftest --offline` 仍 **78 PASS**。

## 3. 五条口径（已进 `PLAN.md` §13.44）

- **R41-a** `MobSpec.drops` 是裸 id，不补命名空间（R22 `script` 字段同款坑）。
- **R41-b** 掉落表 `item` 两种写法：内容 id **裸名**、物品 id **带命名空间**；`redtale_project:x` 也容错认成内容 id。
- **R41-c** xp 用 `ExperienceOrb.award`，不用 `LivingExperienceDropEvent`（后者只在玩家击杀时触发 —— 对照实验：`/kill` 一只原版牛 → **0** 个经验球）。
- **R41-d** 掉落是加项：不 `clear()` 原版战利品、不重设经验。
- **R41-e** `DEFAULT_FILES` 只在**文件缺失**时落地 → 老配置不会自动长出新键（测试服 R38 的 `ember_guard.json5` 就没有 `drops:`，第一轮实测「死怪零产出」由此而来）。**加新字段的默认值必须写进发布说明，不能指望 reload 补。**

## 4. 踩到的坑（记牢）

1. **内容 id ≠ 物品 id**：14 基底各只有一个物品 id（`redtale_project:amulet`），`gem_ruby` 是内容 id。只查 `BuiltInRegistries.ITEM` 时自建内容永远掉不出来（第一版实测：WARN「掉落物品 id 未知：redtale_project:gem_ruby」）。解法 = 先查 `contentItems()`，命中就 `ItemFactory.build(content, rnd)`。
2. **scoreboard tag 不能写进选择器**：`tag=redtale_mob/t_drop` 报 `Expected end of options`（tag 值只吃 `[0-9A-Za-z_.-+]`，`/` 不在内），加引号也没用 → 验「某只怪」只能按 `type=` 选。
3. **`RandomSource` ≠ `java.util.Random`**：`entity.getRandom()` 返回 `RandomSource`，`DropRoll` 吃 `java.util.Random`（纯函数层不引 MC）→ `new Random(entity.getRandom().nextLong())` 派生。
4. **`/kill` 不掉经验**（无玩家击杀）：原版经验球只发给玩家击杀；这条既解释了 R41-c，也解释了为什么早期「经验验收」一直为 0。
5. **默认件不覆盖已有文件**：改默认 `mobs/*.json5` 之后，测试服/正式服的老副本仍缺新键 —— 本轮手动给测试服副本补了 `drops:` 才验出 B/C/D 组。

## 5. 未验证 / 下一步

- **未验证**：玩家击杀时「原版经验 + 表内 xp」两层相加（无客户端）；装备槽的原版掉落与我们的表并存（概率 8.5%，样本不足）；掉落物被玩家吸附后的 `ContentData` 是否与 `/redtale give` 一致（同一 `ItemFactory.build`，理论一致）。
- **下一步**：T-0037（`/redtale drop list`，S8）；`LivingDropsEvent` 的「只对自建怪生效」已成立，M6 可开始收口 → 后面是刷怪替换（D26）与 M7 等级经验。
