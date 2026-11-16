# R21 · M4 开工：`redtale_project:scripted` 注册成功 + L1→L0 生成接线

日期：2026-11-16 17:28–18:05 · 队列：S7 · 里程碑：**M3 已收尾，M4 第 1 块落地**

## 0. 一句话

`ENCHANT-API.md` §8 的 **R1 风险（内置表不能 `RegisterEvent`）已实测解除**；`enchantments/*.json5` 现在能生成真·原版
`Enchantment` 并**在下次启动**被 `/enchant` 接受。`/reload` 加不进 L0 这条 1.21.1 硬边界也一并钉死在
`PLAN.md` §13.24 R21-c。

## 1. 卡板

| 动作 | 卡 | 结果 |
|---|---|---|
| 验收 | **T-0015** `/redtale validate` | **3/3 机器验 + 功能四跑 → `VERIFIED`** |
| 派单 | **T-0017** `tools/gen_enchant_lang.py` | 新派（在制品 = T-0016 + T-0017 = 2/3） |
| 未派 | — | T-0016 仍 `OPEN`，S8 本轮尚未领 |

### 1.1 T-0015 验收证据

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `gradle build --offline` | 退出码 0 | **0**（jar 313235 B） |
| 2 | `grep -c "ValidateCommand::validate" RedtaleCommand.java` | 1 | **1** |
| 3 | `unzip -l jar \| grep -c "core/command/ValidateCommand"` | 1 | **1**（无内部类） |

功能四跑（测试服 `Done (1.980s)`，造三份坏配置）：

```
校验：11 件内容 / 3 个词条池 / 3 条词条定义 · 问题 4 条（外部 weapon_attributes 引用 1 条未校验）
  t_bad_wa: weapon_attributes redtale_project:missing_reach 缺定义文件 weapon_attributes/missing_reach.json5
  t_bad_pool: 引用了未定义的词条 ghost_affix
  t_bad_pool: applies_to 的 redtale_project:wand 不是 14 基底之一
  t_zero_pool: 权重合计 <= 0，永远不会被摇到
```

- 连跑两次 `diff` 为空（可复现）；`t_bad_wa` 在启动期另外触发了一条「找不到定义」的 WARN（解析层自查，与本命令无关）。
- 删掉三份坏配置 → `/redtale reload`（`20 份配置文件 / 10 件装备内容`）→ `问题 0 条` + `  （没有引用问题）`。
- **S8 报的口径确认并复现**：`applies_to: ["sword"]`（漏命名空间）被 `ResourceLocation.tryParse` 补成
  `minecraft:sword`，按卡面「非 `redtale_project` 跳过」→ **0 问题**。这是真漏报，记 `PLAN.md` §13.24 R21-e，
  R22 补一条「裸名疑似笔误」提示（不改判定语义）。

## 2. 自研 M4-1：唯一自定义附魔效果

新类 `src/main/java/com/redtale/project/enchants/ScriptedEnchantmentEffect.java`（record，约 130 行）：

- 字段：`script`（`ResourceLocation`）+ `args`（`CompoundTag`，`optionalFieldOf` 缺省空）。
- 注册：`RegisterEvent` → `Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE`（**内置表**，不是 `DeferredRegister` 目标）。
- `apply(...)`：把 `args` 的标量拆成 `vars`，追加 `enchant_level` / `enchant_item` / `enchant_pos`，
  按 `script.toString()` 调 `ScriptEngine.fireById`。
- 失败策略：脚本不存在 → `WARN` **一次**（`Set` 去重）；执行异常 → `ERROR` 一次，不外抛。

**开局体检日志**（`GameHooks.onServerStarted`，两次启动复现）：

```
[RedtaleProject] 附魔实体效果注册：redtale_project:scripted 存在=true（表内共 14 条）
```

14 条 = 原版 13 个实体效果 + 我们 1 个 ⇒ **R1 解除，不需要 mixin 进 `EnchantmentEntityEffect.bootstrap` 的兜底方案**。

## 3. 自研 M4-2：L1 → L0 接线

- `RedtaleCore.reload()` 在 `weapon_attributes` 生成之后追加一行 `EnchantmentGenerator.generateAll(<world>/datapacks/redtale_project, all, warnings)`，
  与武器动作**同一个数据包根**，由既有 `PackEnabler` 一次选中。
- `ConfigStore.DEFAULT_FILES` +2 份：`enchantments/super_sharpness.json5`（纯数值，`minecraft:damage` + `linear`）、
  `enchantments/soul_reap.json5`（`minecraft:post_attack` + `redtale_project:scripted`），各带 `description` / `description_en`。

生成物（`reload` 后立即出现，字段顺序 = §3.1 白名单，`description` 只留 `translate` 键）：

```
world/datapacks/redtale_project/data/redtale_project/enchantment/super_sharpness.json
world/datapacks/redtale_project/data/redtale_project/enchantment/soul_reap.json
```

**生效需要重启**（本轮 5 次起停的最后一次验证）：

```
> enchant @e[type=minecraft:zombie,limit=1] redtale_project:super_sharpness 3
Applied enchantment enchantment.redtale_project.super_sharpness III to Zombie's item
> enchant @e[type=minecraft:zombie,limit=1] redtale_project:soul_reap 2
Applied enchantment enchantment.redtale_project.soul_reap II to Zombie's item
```

名称仍是裸 lang 键（`enchantment.redtale_project.…`）= 正常，lang 归 T-0017。

## 4. 两条新事实（写进 PLAN §13.24）

1. **`/reload` 加不了 L0 附魔**。同一份 JSON 在包内、`/reload` 之后 `/enchant` 仍报
   `Can't find element 'redtale_project:super_sharpness' of type 'minecraft:enchantment'`，重启即好。
   `enchantment` 是**启动期加载的 datapack registry**（与 `damage_type` 不同），热更边界按 `ENCHANT-API` §8 R2 兜底。
2. **启动期生成的包，本次启动必然看不到**。`RedtaleCore.reload()` 跑在 `ServerStartedEvent`，晚于注册表加载；
   所以「首次加附魔要重启两次」是本轮实测序列的自然结果（第一次生成 + 第二次加载），不是 bug。

## 5. 环境与收尾

- 测试服 `E:\server-test` 起停 **5 次**（`Done (1.980/1.973/1.964s)` 等），**最后一次已停**，25567/25577 无 LISTEN。
- redtale zer ERROR；`ERROR` 总数仍 **4**（`RecipeManager` 的 simplyswords ×2 / brewinandchewin / `minecraft:red_dye`，R7 起的既有噪声）。
- 构建：`gradle build --offline` exit 0，jar **319698 B**；锁已删（`.queue/build.lock` 不存在）。
- 测试服残留验收件：`world/datapacks/redtale_project/data/redtale_project/enchantment/t_scripted.json`
  （手写、`script` 指向不存在的 `enchants/t_probe_missing`，留作 V10「脚本缺失不崩」的现成素材）。

## 6. 下轮（R22）

1. 验收 T-0016 / T-0017。
2. V9/V10：写 `scripts/enchants_soul_reap.json5`（`id: "enchants/soul_reap"`）让 `post_attack` 真跑；故意写错 id 验「只 WARN 一次」。
3. V8：白板剑 vs `super_sharpness` 剑打同一种怪，用 `/redtale damage` 的 trace 对 `linear` 结果。
4. `/redtale validate` 加第 5 条（套装引用，`SetCommand.danglingSets()` 已备）+ 裸名 `applies_to` 提示。
